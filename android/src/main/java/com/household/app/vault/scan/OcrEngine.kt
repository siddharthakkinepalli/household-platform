package com.household.app.vault.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.household.app.domain.models.vault.VisionTextPayload

/**
 * Strategy interface for OCR backends.
 * Current implementation: ML Kit (Latin script, covers German + English offline).
 * Future: PaddleOCR (drop-in replacement — implement this interface and switch OcrEngineProvider).
 */
interface OcrEngine {
    val id: String

    /** Engine identifier used by OcrRouter for logging and cache keys. */
    val engineId: String get() = id

    /** Bump when the model changes to trigger incremental rescan of cached pages. */
    val engineVersion: Int get() = 1

    suspend fun recognize(context: Context, imageUri: Uri): VisionTextPayload

    /**
     * Pluggable bitmap-level OCR used by [OcrRouter].
     * Returns extracted text, or null if this engine cannot process the input.
     * Implementations must be thread-safe.
     */
    suspend fun recognizeText(bitmap: Bitmap): String?
}
