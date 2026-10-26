package com.coobyk.smallpic

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread

/**
 * Minimal one-screen UI: pick one or many images, they are compressed to
 * roughly 10% of their original size, then either saved to Pictures/SmallPic
 * or handed to the share sheet. No permissions.
 */
class MainActivity : ComponentActivity() {

    private data class Outcome(val name: String, val originalBytes: Int, val compressed: ByteArray?)

    private val outcomes = mutableListOf<Outcome>()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(pickerLimit())
    ) { uris -> if (uris.isNotEmpty()) optimize(uris) }

    private lateinit var pickButton: Button
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var resultsList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pickButton = findViewById(R.id.pickButton)
        saveButton = findViewById(R.id.saveButton)
        shareButton = findViewById(R.id.shareButton)
        progress = findViewById(R.id.progress)
        progressText = findViewById(R.id.progressText)
        resultsList = findViewById(R.id.resultsList)

        pickButton.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        saveButton.setOnClickListener { save() }
        shareButton.setOnClickListener { share() }
    }

    private fun pickerLimit(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MediaStore.getPickImagesMaxLimit().coerceIn(2, 30)
        } else {
            9
        }

    private fun optimize(uris: List<Uri>) {
        setBusy(true, uris.size)
        outcomes.clear()
        resultsList.removeAllViews()
        thread {
            uris.forEachIndexed { index, uri ->
                val done = index + 1
                runOnUiThread {
                    progress.progress = done
                    progressText.text = getString(R.string.optimizing, done, uris.size)
                }
                val outcome = runCatching { optimizeOne(uri) }
                    .getOrElse { Outcome("Image $done", 0, null) }
                outcomes += outcome
                runOnUiThread { addResultRow(outcome) }
            }
            runOnUiThread { setBusy(false, 0) }
        }
    }

    /** Returns null as `compressed` when no output smaller than the original could be produced. */
    private fun optimizeOne(uri: Uri): Outcome {
        val name = queryName(uri)
        val input = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return Outcome(name, input.size, compress(input))
    }

    private fun queryName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: "Image"

    /**
     * Downscales in doubling sample-size steps; at each step binary-searches
     * the JPEG quality for the largest value that fits the target size.
     * Stops at the first step that reaches the target, which keeps resolution
     * as high as possible.
     */
    private fun compress(input: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val target = input.size / 10
        var best: ByteArray? = null
        var sampleSize = 1
        while (best == null || best.size > target) {
            val bitmap = decode(input, sampleSize) ?: break
            val encoded = encodeAtLargestFittingQuality(bitmap, target)
            bitmap.recycle()
            if (best == null || encoded.size < best.size) best = encoded
            if (sampleSize >= 32) break
            sampleSize *= 2
        }
        return best?.takeIf { it.size < input.size }
    }

    private fun decode(input: ByteArray, sampleSize: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(input, 0, input.size, opts) ?: return null
        if (!decoded.hasAlpha()) return decoded

        // JPEG has no alpha; flatten transparent pixels onto white.
        val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(decoded, 0f, 0f, null)
        decoded.recycle()
        return flat
    }

    private fun encodeAtLargestFittingQuality(bitmap: Bitmap, targetBytes: Int): ByteArray {
        var lo = 10
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

    private fun save() {
        val pending = outcomes.filter { it.compressed != null }
        setBusy(true, pending.size)
        thread {
            var saved = 0
            pending.forEachIndexed { index, outcome ->
                runOnUiThread { progress.progress = index + 1 }
                runCatching { save(outcome.compressed!!) }.onSuccess { saved++ }
            }
            runOnUiThread {
                setBusy(false, 0)
                toast(getString(R.string.saved_toast, saved))
            }
        }
    }

    private fun save(bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "smallpic_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SmallPic")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }

    private fun share() {
        val dir = File(cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
        val uris = outcomes.filter { it.compressed != null }.mapIndexedNotNull { index, outcome ->
            runCatching {
                val file = File(dir, shareName(outcome.name, index))
                file.outputStream().use { it.write(outcome.compressed!!) }
                FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            }.getOrNull()
        }
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun shareName(name: String, index: Int): String {
        val base = name.substringBeforeLast('.').ifBlank { "smallpic_${index + 1}" }
        return "${base}_small.jpg"
    }

    private fun addResultRow(outcome: Outcome) {
        val text = when {
            outcome.compressed == null && outcome.originalBytes == 0 -> getString(R.string.row_failed, outcome.name)
            outcome.compressed == null -> getString(R.string.row_skipped, outcome.name)
            else -> {
                val smaller = 100 - (outcome.compressed.size * 100 / outcome.originalBytes)
                "${outcome.name}\n${format(outcome.originalBytes)} \u2192 ${format(outcome.compressed.size)} ($smaller% smaller)"
            }
        }
        resultsList.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, 12, 0, 12)
        })
    }

    private fun setBusy(busy: Boolean, total: Int) {
        val ready = !busy && outcomes.any { it.compressed != null }
        pickButton.isEnabled = !busy
        saveButton.isEnabled = ready
        shareButton.isEnabled = ready
        val visible = if (busy) View.VISIBLE else View.GONE
        progress.visibility = visible
        progressText.visibility = visible
        if (busy) {
            progress.max = total
            progress.progress = 0
        }
    }

    private fun format(bytes: Int): String {
        val kb = bytes / 1024.0
        return if (kb >= 1024) {
            String.format("%.1f MB", kb / 1024)
        } else {
            String.format("%.0f KB", kb)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
