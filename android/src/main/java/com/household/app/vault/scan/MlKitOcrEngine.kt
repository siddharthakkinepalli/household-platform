package com.household.app.vault.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.household.app.data.refiner.toVisionTextPayload
import com.household.app.domain.models.vault.VisionTextPayload
import com.household.app.vault.parser.ImagePreProcessor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Latin-script OCR engine.
 * Covers German, English, and all other Latin-based languages offline.
 * The model is bundled with the APK via the text-recognition-bundled-common dependency.
 *
 * To switch to PaddleOCR: implement [OcrEngine] and update [OcrEngineProvider].
 */
class MlKitOcrEngine : OcrEngine {

    override val id = "mlkit-latin"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(context: Context, imageUri: Uri): VisionTextPayload {
        val inputImage = InputImage.fromFilePath(context, imageUri)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { text -> cont.resume(text.toVisionTextPayload()) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
                .addOnCanceledListener { cont.cancel() }
        }
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): VisionTextPayload {
        val inputImage = InputImage.fromBitmap(ImagePreProcessor.optimizeForOcr(bitmap), 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { text -> cont.resume(text.toVisionTextPayload()) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
                .addOnCanceledListener { cont.cancel() }
        }
    }

    /**
     * [OcrEngine] bitmap interface used by [OcrRouter].
     * Delegates to [recognizeFromBitmap] and returns the plain full-text string.
     * The bitmap is expected to have already been preprocessed by [OpenCvPreprocessor]
     * before arriving here; [ImagePreProcessor.optimizeForOcr] is still applied on top
     * as a second contrast-boost pass (cheap Android-only op, no OpenCV required).
     */
    override suspend fun recognizeText(bitmap: Bitmap): String? =
        runCatching { recognizeFromBitmap(bitmap).fullText.takeIf { it.isNotBlank() } }
            .getOrNull()

    fun close() = recognizer.close()
}
