package com.household.app.vault.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

/**
 * Document-mode image enhancement: scale → grayscale+contrast → adaptive binarization.
 * Use [ReceiptPreprocessor] for receipts — binarization destroys thermal gradients.
 * Writes the result to a sidecar file so the original is preserved.
 */
object ScanPreprocessor {

    private const val MAX_EDGE_PX = 1920
    private const val CONTRAST = 1.45f
    private const val BRIGHTNESS = -25f

    // Adaptive binarization block half-size and bias constant
    private const val BLOCK_HALF = 15
    private const val BINARIZE_C = 8

    /**
     * Reads [sourceFile], enhances it, writes the result next to it as
     * `<name>_enhanced.jpg`, and returns that file. If anything fails the
     * original file is returned unchanged so the pipeline keeps running.
     */
    fun enhanceForOcr(sourceFile: File): File {
        return runCatching {
            val raw = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return sourceFile
            val scaled = scaleBitmap(raw)
            val gray = applyGrayscaleContrast(scaled)
            val binary = adaptiveBinarize(gray)

            val outFile = File(sourceFile.parent, sourceFile.nameWithoutExtension + "_enhanced.jpg")
            FileOutputStream(outFile).use { binary.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            outFile
        }.getOrDefault(sourceFile)
    }

    // ── private ────────────────────────────────────────────────────────────────

    private fun scaleBitmap(src: Bitmap): Bitmap {
        val maxEdge = maxOf(src.width, src.height)
        if (maxEdge <= MAX_EDGE_PX) return src
        val scale = MAX_EDGE_PX.toFloat() / maxEdge
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt(),
            (src.height * scale).toInt(),
            true
        )
    }

    private fun applyGrayscaleContrast(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix(
            floatArrayOf(
                0.299f * CONTRAST, 0.587f * CONTRAST, 0.114f * CONTRAST, 0f, BRIGHTNESS,
                0.299f * CONTRAST, 0.587f * CONTRAST, 0.114f * CONTRAST, 0f, BRIGHTNESS,
                0.299f * CONTRAST, 0.587f * CONTRAST, 0.114f * CONTRAST, 0f, BRIGHTNESS,
                0f, 0f, 0f, 1f, 0f
            )
        )
        canvas.drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return result
    }

    /**
     * Sauvola-style adaptive binarization via summed-area table.
     * Each pixel is thresholded against the local mean of a (2*BLOCK_HALF+1)² window minus bias C.
     */
    private fun adaptiveBinarize(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Extract single-channel luminance (R channel from grayscale bitmap)
        val gray = IntArray(pixels.size) { i ->
            (pixels[i] shr 16) and 0xFF
        }

        // Build integral image (padded by 1 on each side)
        val W = w + 1
        val integral = LongArray(W * (h + 1))
        for (y in 0 until h) {
            for (x in 0 until w) {
                integral[(y + 1) * W + (x + 1)] = (
                    gray[y * w + x] +
                    integral[y * W + (x + 1)] +
                    integral[(y + 1) * W + x] -
                    integral[y * W + x]
                ).toLong()
            }
        }

        val out = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x1 = maxOf(0, x - BLOCK_HALF)
                val y1 = maxOf(0, y - BLOCK_HALF)
                val x2 = minOf(w - 1, x + BLOCK_HALF)
                val y2 = minOf(h - 1, y + BLOCK_HALF)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = (
                    integral[(y2 + 1) * W + (x2 + 1)] -
                    integral[y1 * W + (x2 + 1)] -
                    integral[(y2 + 1) * W + x1] +
                    integral[y1 * W + x1]
                )
                val mean = (sum / count).toInt()
                val pixel = gray[y * w + x]
                val color = if (pixel < mean - BINARIZE_C) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                out[y * w + x] = color
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }
}
