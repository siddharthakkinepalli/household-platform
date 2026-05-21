package com.household.app.vault.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

/**
 * Receipt-specific image enhancement.
 *
 * Pipeline: scale → grayscale → CLAHE (adaptive contrast) → bilateral denoise
 *
 * Deliberately excludes:
 *  - Adaptive binarization (destroys thermal gradients and faded ink)
 *  - Perspective warp (receipts are typically flat; warp adds latency and artifacts)
 *  - Morphological operations (bleed text on thin paper)
 */
object ReceiptPreprocessor {

    private const val MAX_EDGE_PX = 1920
    private const val CLAHE_CLIP = 2.0
    private val CLAHE_TILE = Size(8.0, 8.0)
    private const val BILATERAL_D = 9          // neighbourhood diameter
    private const val BILATERAL_SIGMA = 75.0   // color + space sigma

    fun process(sourceFile: File): File {
        return runCatching {
            val raw = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return sourceFile
            val enhanced = enhance(raw)
            val outFile = File(sourceFile.parent, sourceFile.nameWithoutExtension + "_receipt.jpg")
            FileOutputStream(outFile).use { enhanced.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            outFile
        }.getOrDefault(sourceFile)
    }

    fun enhance(src: Bitmap): Bitmap {
        return runCatching {
            val scaled = scaleBitmap(src)

            val rgba = Mat()
            Utils.bitmapToMat(scaled, rgba)

            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            // CLAHE: adaptive contrast — handles uneven receipt lighting far better than fixed gamma
            val claheResult = Mat()
            Imgproc.createCLAHE(CLAHE_CLIP, CLAHE_TILE).apply(gray, claheResult)

            // Bilateral filter: edge-preserving denoise — keeps character edges sharp
            val denoised = Mat()
            Imgproc.bilateralFilter(claheResult, denoised, BILATERAL_D, BILATERAL_SIGMA, BILATERAL_SIGMA)

            val out = Mat()
            Imgproc.cvtColor(denoised, out, Imgproc.COLOR_GRAY2RGBA)

            val result = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, result)
            result
        }.getOrDefault(src)
    }

    private fun scaleBitmap(src: Bitmap): Bitmap {
        val maxEdge = maxOf(src.width, src.height)
        if (maxEdge <= MAX_EDGE_PX) return src
        val scale = MAX_EDGE_PX.toFloat() / maxEdge
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    }
}
