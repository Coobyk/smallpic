package com.coobyk.smallpic

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * One-screen optimizer: pick one or many images, they are compressed to a
 * per-image 5-15% of their original size, then either saved to
 * Pictures/SmallPic or handed to the share sheet. No permissions.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    data class Outcome(val name: String, val originalBytes: Int, val compressed: ByteArray?)

    data class UiState(
        val optimizing: Boolean = false,
        val saving: Boolean = false,
        val progressDone: Int = 0,
        val progressTotal: Int = 0,
        val outcomes: List<Outcome> = emptyList(),
    ) {
        val hasResults: Boolean get() = outcomes.isNotEmpty()
        val ready: Boolean get() = !optimizing && !saving && outcomes.any { it.compressed != null }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Decoded bitmaps are large; keep a few compressions in flight at once. */
    private val compressSlots = Semaphore(permits = 3)

    private companion object {
        const val JPEG_MIN_QUALITY = 10
        const val MAX_SAMPLE_SIZE = 32
    }

    fun optimize(uris: List<Uri>) {
        _state.update {
            it.copy(optimizing = true, progressDone = 0, progressTotal = uris.size, outcomes = emptyList())
        }
        viewModelScope.launch {
            // Results stay in pick order; completed images appear as they finish.
            val results = arrayOfNulls<Outcome>(uris.size)
            uris.indices.map { index ->
                async(Dispatchers.Default) {
                    compressSlots.withPermit {
                        val outcome = runCatching { optimizeOne(uris[index]) }
                            .getOrElse { Outcome("Image ${index + 1}", 0, null) }
                        results[index] = outcome
                        _state.update { s ->
                            s.copy(outcomes = results.filterNotNull(), progressDone = s.progressDone + 1)
                        }
                    }
                }
            }.awaitAll()
            _state.update { it.copy(optimizing = false, progressDone = 0, progressTotal = 0) }
        }
    }

    /** Returns null as `compressed` when no output smaller than the original could be produced. */
    private fun optimizeOne(uri: Uri): Outcome {
        val resolver = getApplication<Application>().contentResolver
        val name = queryName(resolver, uri)
        val input = resolver.openInputStream(uri)!!.use { it.readBytes() }
        val orientation = readExifOrientation(input)
        return Outcome(name, input.size, compress(input, orientation))
    }

    private fun queryName(resolver: android.content.ContentResolver, uri: Uri): String =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: "Image"

    private fun readExifOrientation(input: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(input))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /**
     * Per-image target between 5% and 15% of the original (an 85-95%
     * reduction), derived from the content hash so the same image always
     * yields the same result.
     */
    private fun targetBytes(input: ByteArray): Int {
        val step = (input.contentHashCode() and 0x7FFFFFFF) % 11
        return input.size * (5 + step) / 100
    }

    /**
     * Finds the largest power-of-two sample size whose minimum JPEG size fits
     * the target, probing cheaply at the lowest quality: when a probe fails,
     * the next size is estimated from the bytes-to-target ratio instead of
     * walking every step, then bisected back so resolution stays as high as
     * possible. Only the final size gets the full quality search. When even
     * the maximum sample size can't reach the target, the smallest probe is
     * returned as a fallback.
     */
    internal fun compress(input: ByteArray, orientation: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val target = targetBytes(input)
        var bestProbe: ByteArray? = null
        var failedAt = 0
        var sampleSize = 1

        while (true) {
            val bitmap = decode(input, sampleSize, orientation) ?: break
            val probe = encode(bitmap, JPEG_MIN_QUALITY)
            bitmap.recycle()
            if (probe.size <= target) {
                var fitted = sampleSize
                while (fitted / 2 > failedAt) {
                    val half = decode(input, fitted / 2, orientation) ?: break
                    val halfProbe = encode(half, JPEG_MIN_QUALITY)
                    half.recycle()
                    if (halfProbe.size > target) break
                    fitted /= 2
                }
                val finalBitmap = decode(input, fitted, orientation) ?: break
                val result = encodeAtLargestFittingQuality(finalBitmap, target)
                finalBitmap.recycle()
                return result.takeIf { it.size < input.size }
            }
            if (bestProbe == null || probe.size < bestProbe.size) bestProbe = probe
            failedAt = maxOf(failedAt, sampleSize)
            if (sampleSize >= MAX_SAMPLE_SIZE) break
            sampleSize = minOf(MAX_SAMPLE_SIZE, sampleSize * jumpMultiplier(probe.size, target))
        }
        return bestProbe?.takeIf { it.size < input.size }
    }

    /** Encoded JPEG size grows roughly with pixel count, i.e. with sample size squared. */
    private fun jumpMultiplier(probeBytes: Int, target: Int): Int {
        var mult = 1
        val ratio = sqrt(probeBytes.toDouble() / target)
        while (mult < ratio) mult *= 2
        return mult
    }

    private fun decode(input: ByteArray, sampleSize: Int, orientation: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(input, 0, input.size, opts) ?: return null
        val flat = if (!decoded.hasAlpha()) decoded else run {
            // JPEG has no alpha; flatten transparent pixels onto white.
            val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(decoded, 0f, 0f, null)
            decoded.recycle()
            flat
        }
        return applyExifOrientation(flat, orientation)
    }

    /**
     * BitmapFactory drops the EXIF orientation, so it is applied to the pixels
     * here; the re-encoded JPEG needs no tag of its own.
     */
    internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed != bitmap) bitmap.recycle()
        return transformed
    }

    private fun encodeAtLargestFittingQuality(bitmap: Bitmap, targetBytes: Int): ByteArray {
        var lo = JPEG_MIN_QUALITY
        var hi = 95
        var best = encode(bitmap, lo)
        if (best.size <= targetBytes) {
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                val candidate = encode(bitmap, mid)
                if (candidate.size <= targetBytes) {
                    best = candidate
                    lo = mid
                } else {
                    hi = mid - 1
                }
            }
        }
        return best
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    fun save(onDone: (Int) -> Unit) {
        val pending = _state.value.outcomes.filter { it.compressed != null }
        _state.update { it.copy(saving = true, progressDone = 0, progressTotal = pending.size) }
        viewModelScope.launch(Dispatchers.IO) {
            var saved = 0
            pending.forEachIndexed { index, outcome ->
                _state.update { it.copy(progressDone = index + 1) }
                runCatching { save(outcome.compressed!!) }.onSuccess { saved++ }
            }
            _state.update { it.copy(saving = false, progressDone = 0, progressTotal = 0) }
            withContext(Dispatchers.Main) { onDone(saved) }
        }
    }

    private fun save(bytes: ByteArray): Uri {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "smallpic_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SmallPic")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }

    fun share(onReady: (List<Uri>) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(app.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
            val uris = _state.value.outcomes.filter { it.compressed != null }.mapIndexedNotNull { index, outcome ->
                runCatching {
                    val file = File(dir, shareName(outcome.name, index))
                    file.outputStream().use { it.write(outcome.compressed!!) }
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                }.getOrNull()
            }
            withContext(Dispatchers.Main) { onReady(uris) }
        }
    }

    private fun shareName(name: String, index: Int): String {
        val base = name.substringBeforeLast('.').ifBlank { "smallpic_${index + 1}" }
        return "${base}_small.jpg"
    }

    fun format(bytes: Int): String {
        val kb = bytes / 1024.0
        return if (kb >= 1024) {
            String.format("%.1f MB", kb / 1024)
        } else {
            String.format("%.0f KB", kb)
        }
    }
}
