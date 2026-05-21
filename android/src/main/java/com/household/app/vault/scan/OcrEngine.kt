package com.household.app.vault.scan

import android.content.Context
import android.net.Uri
import com.household.app.domain.models.vault.VisionTextPayload

/**
 * Strategy interface for OCR backends.
 * Current implementation: ML Kit (Latin script, covers German + English offline).
 * Future: PaddleOCR (drop-in replacement — implement this interface and switch OcrEngineProvider).
 */
interface OcrEngine {
    val id: String
    suspend fun recognize(context: Context, imageUri: Uri): VisionTextPayload
}
