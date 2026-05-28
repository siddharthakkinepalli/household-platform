package com.household.app.vault.scan

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Routes OCR requests through the engine stack, returning the first non-null non-blank result.
 *
 * Default engine order (after [init] is called):
 *   1. [PaddleOcrEngine] — high-accuracy ONNX-based recognition (skipped if model not in assets)
 *   2. [MlKitOcrEngine]  — fast offline fallback, always available
 *
 * [OpenCvPreprocessor.preprocess] is applied once before any engine attempt; the preprocessed
 * bitmap is shared across all engines in the chain (preprocess once, try many).
 *
 * Call [init] once at application/worker startup with an application [Context] so that
 * [PaddleOcrEngine] can load its ONNX model from assets.  It is safe to call [init] multiple
 * times — subsequent calls are no-ops if PaddleOcrEngine is already registered.
 */
object OcrRouter {

    // Starts with only MlKitOcrEngine; PaddleOcrEngine is prepended by init().
    private val engines: MutableList<OcrEngine> = mutableListOf(MlKitOcrEngine())

    /**
     * Registers [PaddleOcrEngine] as the primary engine (tried before ML Kit).
     *
     * Must be called before any [recognizeText] invocations that should use PaddleOCR.
     * Safe to call from any thread; idempotent — calling it more than once does nothing.
     *
     * Typical call site: top of [VaultDocumentParserWorker.doWork].
     */
    fun init(context: Context) {
        if (engines.none { it is PaddleOcrEngine }) {
            // Prepend so PaddleOCR is attempted first; ML Kit remains as fallback.
            engines.add(0, PaddleOcrEngine(context.applicationContext))
        }
    }

    /**
     * Runs the bitmap through the engine chain and returns the first non-blank result.
     * Returns an empty string if all engines fail or return blank.
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val preprocessed = OpenCvPreprocessor.preprocess(bitmap)
        for (engine in engines) {
            val result = runCatching { engine.recognizeText(preprocessed) }.getOrNull()
            if (!result.isNullOrBlank()) return@withContext result
        }
        ""
    }
}
