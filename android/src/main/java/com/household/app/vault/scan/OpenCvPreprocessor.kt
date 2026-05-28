package com.household.app.vault.scan

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * OpenCV-based image preprocessing pipeline for OCR.
 *
 * Applied by [OcrRouter] before passing a bitmap to any [OcrEngine]:
 *  1. Grayscale conversion — reduces colour noise
 *  2. Median blur (kernel 3) — mild denoise preserving edges
 *  3. Adaptive Gaussian threshold — binarisation that handles uneven lighting
 *
 * The entire pipeline is wrapped in [runCatching] so that devices missing the
 * OpenCV native library (e.g. unusual ABIs or corrupted installs) always fall
 * back to returning the original bitmap rather than crashing.
 */
object OpenCvPreprocessor {

    /**
     * Prepares a bitmap for OCR:
     * 1. Grayscale conversion
     * 2. Adaptive threshold (Gaussian) — handles uneven lighting
     * 3. Mild denoise (medianBlur kernel 3)
     * Returns the processed bitmap, or the original if OpenCV is unavailable.
     */
    fun preprocess(src: Bitmap): Bitmap {
        return runCatching {
            val mat = Mat()
            Utils.bitmapToMat(src, mat)

            // Grayscale
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            mat.release()

            // Denoise
            val denoised = Mat()
            Imgproc.medianBlur(gray, denoised, 3)
            gray.release()

            // Adaptive threshold
            val binary = Mat()
            Imgproc.adaptiveThreshold(
                denoised, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                15, 8.0
            )
            denoised.release()

            // Back to Bitmap
            val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(binary, result)
            binary.release()
            result
        }.getOrDefault(src)  // fallback: return original if OpenCV init fails
    }
}
