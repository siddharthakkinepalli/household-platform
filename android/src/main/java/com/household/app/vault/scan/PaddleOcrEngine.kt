package com.household.app.vault.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.household.app.domain.models.vault.VisionTextPayload

/** Stub — will be activated when PaddleOCR ONNX model is bundled in assets/paddle_ocr_v4.onnx. */
class PaddleOcrEngine : OcrEngine {
    override val id = "paddleocr"
    override val engineId = "paddleocr"
    override val engineVersion = 1

    override suspend fun recognize(context: Context, imageUri: Uri): VisionTextPayload {
        // TODO: implement URI-based recognition when model is available
        return VisionTextPayload(blocks = emptyList(), fullText = "")
    }

    override suspend fun recognizeText(bitmap: Bitmap): String? {
        // TODO: load model from assets/paddle_ocr_v4.onnx, run inference
        return null  // null = skip this engine, fall through to next in OcrRouter
    }
}
