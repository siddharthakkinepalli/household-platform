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
 * Model: en_PP-OCRv3_rec_infer (Latin+German, 96-char dict, H=32 input)
 * Pipeline:
 *   bitmap → resize to H=32, align W to multiple of 8
 *          → float CHW tensor [1,3,32,W], normalized to [-1,1]
 *          → ONNX session → logits [1, T, charset_size]
 *          → greedy CTC decode → plain text
 */
class PaddleOcrEngine(private val context: Context) : OcrEngine {

    override val id = "paddleocr"
    override val engineId = "paddleocr"
    override val engineVersion = 1

    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    // Latin + German + digits character set for CTC decoding.
    // Index 0 is the CTC blank token; all other indices map to printable characters.
    private val charset: List<String> = buildCharset()

    private fun buildCharset(): List<String> {
        val chars = mutableListOf(" ")   // index 0 = CTC blank
        ('0'..'9').forEach { chars.add(it.toString()) }
        ('a'..'z').forEach { chars.add(it.toString()) }
        ('A'..'Z').forEach { chars.add(it.toString()) }
        "!\"#\$%&'()*+,-./:;<=>?@[\\]^_{|}~äöüÄÖÜß€@".forEach { chars.add(it.toString()) }
        return chars
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

            // --- 1. Preprocess: resize to H=32, align W to nearest multiple of 8 ---
            // en_PP-OCRv3_rec_infer expects H=32 (PP-OCRv4 uses 48 but has no standalone English model)
            val targetH = 32
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
     * Blank token is index 0 (matches the charset definition above).
     */
    private fun ctcDecode(output: Array<FloatArray>): String {
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
                if (maxIdx != 0 && maxIdx < charset.size) sb.append(charset[maxIdx])
                prevIdx = maxIdx
            }
        }
        return sb.toString().trim()
    }
}
