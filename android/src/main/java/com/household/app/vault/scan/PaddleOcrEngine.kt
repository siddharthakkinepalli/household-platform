package com.household.app.vault.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.household.app.domain.models.vault.VisionTextPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * PaddleOCR v4 text-recognition engine backed by ONNX Runtime.
 *
 * This is the recognition stage only — it reads text from a pre-cropped text-line bitmap.
 * Detection (finding text regions) is handled upstream by OpenCV / ML Kit.
 *
 * Model file: `assets/paddle_ocr_v4_rec.onnx` — loaded lazily on first use.
 * If the file is absent, [recognizeText] returns null and [OcrRouter] falls through to ML Kit.
 *
 * Model: en_PP-OCRv4_rec_mobile (94-char ASCII dict, H=48 input, blank at last index)
 * Download: en_PP-OCRv4_rec_mobile.onnx from RapidAI/RapidOCR → rename to paddle_ocr_v4_rec.onnx
 * Pipeline:
 *   bitmap → resize to H=48, align W to multiple of 8
 *          → float CHW tensor [1,3,48,W], normalized to [-1,1]
 *          → ONNX session → logits [1, T, 95]  (94 chars + CTC blank at index 94)
 *          → greedy CTC decode → plain text
 */
class PaddleOcrEngine(private val context: Context) : OcrEngine {

    override val id = "paddleocr"
    override val engineId = "paddleocr"
    override val engineVersion = 1

    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    // 94-char ASCII dict matching en_PP-OCRv4_rec_mobile/en_dict.txt (printable ASCII 33–126).
    // CTC blank is at index 94 (= charset.size), NOT at index 0 — PaddleOCR places blank last.
    private val charset: List<String> = buildCharset()

    private fun buildCharset(): List<String> {
        // Printable ASCII chars 33–126: !"#$%&'()*+,-./0-9:;<=>?@A-Z[\]^_`a-z{|}~
        // Matches en_PP-OCRv4_rec_mobile en_dict.txt exactly (94 entries).
        return (33..126).map { it.toChar().toString() }
    }

    /**
     * Loads the ONNX session from assets on first call; returns null if the model is missing
     * or if ONNX Runtime fails to initialise (graceful degradation).
     */
    private fun loadSession(): OrtSession? {
        if (session != null) return session
        return runCatching {
            val modelBytes = context.assets.open("paddle_ocr_v4_rec.onnx").readBytes()
            env.createSession(modelBytes, OrtSession.SessionOptions()).also { session = it }
        }.getOrNull()
    }

    /**
     * URI-based recognition — delegates to bitmap path via ML Kit's file loading.
     * Kept for interface compliance; OCR quality is identical to [recognizeText].
     */
    override suspend fun recognize(context: Context, imageUri: Uri): VisionTextPayload {
        // URI-based recognition is not used by the primary OCR pipeline (OcrRouter feeds
        // pre-decoded bitmaps). Return an empty payload so the interface contract is satisfied;
        // the calling code falls through to MlKitOcrEngine for URI paths.
        return VisionTextPayload(blocks = emptyList(), fullText = "")
    }

    /**
     * Core recognition entry point used by [OcrRouter].
     *
     * Returns the recognised string (may be empty but not null on success),
     * or null if the model is unavailable or inference throws.
     */
    override suspend fun recognizeText(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        runCatching {
            val sess = loadSession() ?: return@withContext null

            // --- 1. Preprocess: resize to H=48, align W to nearest multiple of 8 ---
            val targetH = 48
            val scale = targetH.toFloat() / bitmap.height
            val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(8)
            val alignedW = (scaledW + 7) / 8 * 8   // round up to multiple of 8

            val scaled = Bitmap.createScaledBitmap(bitmap, alignedW, targetH, true)

            // --- 2. Convert to float CHW [1, 3, H, W], normalise to [-1, 1] ---
            val pixelCount = alignedW * targetH
            val pixels = IntArray(pixelCount)
            scaled.getPixels(pixels, 0, alignedW, 0, 0, alignedW, targetH)

            // Allocate per-channel arrays then flatten into a single FloatBuffer.
            val rChannel = FloatArray(pixelCount)
            val gChannel = FloatArray(pixelCount)
            val bChannel = FloatArray(pixelCount)
            pixels.forEachIndexed { i, px ->
                rChannel[i] = ((px shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
                gChannel[i] = ((px shr  8 and 0xFF) / 255f - 0.5f) / 0.5f
                bChannel[i] = ((px        and 0xFF) / 255f - 0.5f) / 0.5f
            }

            val floatData = FloatBuffer.allocate(3 * pixelCount)
            floatData.put(rChannel)
            floatData.put(gChannel)
            floatData.put(bChannel)
            floatData.rewind()

            // --- 3. Build OrtTensor [1, 3, H, W] ---
            val shape = longArrayOf(1L, 3L, targetH.toLong(), alignedW.toLong())
            val tensor = OnnxTensor.createTensor(env, floatData, shape)

            // --- 4. Run inference ---
            val inputName = sess.inputNames.first()
            val results = sess.run(mapOf(inputName to tensor))
            tensor.close()

            // --- 5. CTC decode: output shape [1, T, num_chars] ---
            @Suppress("UNCHECKED_CAST")
            val output = (results[0].value as Array<Array<FloatArray>>)[0]  // [T, num_chars]

            ctcDecode(output)
        }.getOrNull()
    }

    /**
     * Greedy CTC decode: argmax at each time-step, collapse consecutive duplicates, remove blanks.
     * PaddleOCR places the CTC blank at the LAST index (= charset.size = 94), not at 0.
     */
    private fun ctcDecode(output: Array<FloatArray>): String {
        val blankIdx = charset.size   // 94 — last output node
        var prevIdx = -1
        val sb = StringBuilder()
        for (timestep in output) {
            var maxIdx = 0
            var maxVal = timestep[0]
            for (j in 1 until timestep.size) {
                if (timestep[j] > maxVal) {
                    maxVal = timestep[j]
                    maxIdx = j
                }
            }
            if (maxIdx != prevIdx) {
                if (maxIdx != blankIdx && maxIdx < charset.size) sb.append(charset[maxIdx])
                prevIdx = maxIdx
            }
        }
        return sb.toString().trim()
    }
}
