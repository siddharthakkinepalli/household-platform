package com.household.app.vault.scan

/**
 * Singleton OCR engine provider.
 * The engine is created once and reused across all calls — no repeated instantiation, no leaks.
 * To swap backends (e.g. PaddleOcrEngine): change the one line inside [createEngine].
 */
object OcrEngineProvider {
    val engine: OcrEngine by lazy { createEngine() }

    private fun createEngine(): OcrEngine = MlKitOcrEngine()
}
