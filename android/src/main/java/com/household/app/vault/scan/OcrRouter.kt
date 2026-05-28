package com.household.app.vault.scan

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Routes OCR requests through the engine stack:
 * 1. ML Kit (primary — fast, decent accuracy)
 * 2. [Future] PaddleOCR ONNX (high accuracy for complex layouts)
 *
 * Falls back through the list; returns the first non-null non-blank result.
 * Applies [OpenCvPreprocessor] before each engine attempt — the preprocessed
 * bitmap is shared across all engines in the chain (preprocess once, try many).
 */
object OcrRouter {
    private val engines: List<OcrEngine> = listOf(
        MlKitOcrEngine()
        // PaddleOcrEngine() — add when model file is bundled in assets/
    )

    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val preprocessed = OpenCvPreprocessor.preprocess(bitmap)
        for (engine in engines) {
            val result = runCatching { engine.recognizeText(preprocessed) }.getOrNull()
            if (!result.isNullOrBlank()) return@withContext result
        }
        ""
    }
}
