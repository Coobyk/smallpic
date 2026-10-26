package com.coobyk.smallpic

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The marker strip is drawn along the stored-image top. Applying the EXIF
 * orientation correctly must put it on the visual right for orientation 6
 * (rotate 90 CW), the left for 8 (rotate 90 CCW), the bottom for 3 (180)
 * and keep it on top for orientation 1.
 */
@RunWith(AndroidJUnit4::class)
class ExifOrientationTest {

    private val vm: MainViewModel by lazy {
        MainViewModel(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        )
    }

    /** 800x1200 stored portrait with a yellow marker strip along the stored top. */
    private fun markerBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(40, 90, 160))
        canvas.drawRect(0f, 0f, 800f, 60f, Paint().apply { color = Color.rgb(255, 210, 0) })
        return bmp
    }

    private fun isYellow(p: Int) = Color.red(p) > 200 && Color.green(p) > 150 && Color.blue(p) < 100

    private fun yellowEdgeOf(bmp: Bitmap): String {
        fun share(samples: List<Int>) = samples.count { isYellow(it) }.toDouble() / samples.size
        val top = (0 until bmp.width).map { bmp.getPixel(it, 1) }
        val bottom = (0 until bmp.width).map { bmp.getPixel(it, bmp.height - 2) }
        val left = (0 until bmp.height).map { bmp.getPixel(1, it) }
        val right = (0 until bmp.height).map { bmp.getPixel(bmp.width - 2, it) }
        val edges = mapOf("top" to share(top), "bottom" to share(bottom), "left" to share(left), "right" to share(right))
        return edges.maxBy { it.value }.takeIf { it.value > 0.5 }?.key ?: "none"
    }

    private fun transformed(orientation: Int): Bitmap =
        vm.applyExifOrientation(markerBitmap(), orientation)

    @Test
    fun orientation6PutsStoredTopOnTheRight() {
        val out = transformed(ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(1200, out.width)
        assertEquals(800, out.height)
        assertEquals("right", yellowEdgeOf(out))
    }

    @Test
    fun orientation8PutsStoredTopOnTheLeft() {
        val out = transformed(ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(1200, out.width)
        assertEquals(800, out.height)
        assertEquals("left", yellowEdgeOf(out))
    }

    @Test
    fun orientation3PutsStoredTopOnTheBottom() {
        val out = transformed(ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(800, out.width)
        assertEquals(1200, out.height)
        assertEquals("bottom", yellowEdgeOf(out))
    }

    @Test
    fun orientation1LeavesTheBitmapUntouched() {
        val out = transformed(ExifInterface.ORIENTATION_NORMAL)
        assertEquals(800, out.width)
        assertEquals(1200, out.height)
        assertEquals("top", yellowEdgeOf(out))
    }

    /** End-to-end: the pipeline output must have the EXIF-corrected aspect. */
    @Test
    fun compressPipelineBakesTheOrientationIn() {
        val input = makeJpegWithOrientation(6)
        val output = vm.compress(input, 6)
        assertNotNull("compression produced no output", output)
        val out = BitmapFactoryCompat.decode(output!!)
        assertTrue("expected landscape output, got ${out.width}x${out.height}", out.width > out.height)
    }

    /** Same stored marker image as markerBitmap(), saved with an EXIF tag. */
    private fun makeJpegWithOrientation(orientation: Int): ByteArray {
        val plainJpeg = ByteArrayOutputStream()
            .also { markerBitmap().compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val tmp = File(ctx.cacheDir, "exif_test.jpg")
        tmp.outputStream().use { it.write(plainJpeg) }
        androidx.exifinterface.media.ExifInterface(tmp.absolutePath).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return tmp.readBytes().also { tmp.delete() }
    }
}

private object BitmapFactoryCompat {
    fun decode(bytes: ByteArray): Bitmap =
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
