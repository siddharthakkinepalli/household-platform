package com.household.app.vault.scan

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Runs the full document pre-processing pipeline on a captured image file:
 *   1. Detect document corners (OpenCV Canny + contours)
 *   2. Warp to rectangle (perspective correction)
 *   3. Enhance for OCR (grayscale + contrast + adaptive binarization)
 *
 * If corner detection finds no valid quad, steps 1–2 are skipped and only
 * enhancement is applied — so the pipeline always produces a usable output.
 *
 * Returns the final processed file. The original file is never modified.
 */
object DocumentScanner {

    fun process(sourceFile: File): File {
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return sourceFile

        // Attempt perspective correction; fall back gracefully if no quad found
        val corrected = DocumentCornerDetector.detect(bitmap)
            ?.let { quad -> PerspectiveCorrector.correct(bitmap, quad) }
            ?: bitmap

        // Write corrected bitmap to a sidecar file before enhancement
        val correctedFile = if (corrected !== bitmap) {
            val f = File(sourceFile.parent, sourceFile.nameWithoutExtension + "_corrected.jpg")
            FileOutputStream(f).use { corrected.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            f
        } else {
            sourceFile
        }

        return ScanPreprocessor.enhanceForOcr(correctedFile)
    }
}
