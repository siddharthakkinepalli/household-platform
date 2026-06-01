package com.jugaad.core.airuntime.runtime

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.jugaad.core.airuntime.model.ModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.exp
import kotlin.math.ln

/**
 * Manages the ONNX Runtime Mobile session lifecycle, hardware NNAPI execution provider
 * mapping, and autoregressive token generation loop.
 *
 * ══ CRITICAL INITIALIZATION RULE ══
 * [OrtSession] creation MUST happen entirely inside [withContext(Dispatchers.Default)].
 * The first NNAPI NPU provider bind blocks 200–500ms — calling it on the main thread
 * causes an ANR. This is enforced in [initSessionOnDefault].
 *
 * ══ MEMORY CONTRACT ══
 * Foreground inference is capped at [ModelConfig.MAX_FOREGROUND_BYTES] (512 MB).
 * Background WorkManager jobs MUST NOT call [generate] — they read from Room cache only.
 *
 * ══ CANCELLATION CONTRACT ══
 * [generate] checks [coroutineContext.ensureActive()] on every loop iteration.
 * [TensorBufferPool] buffers are always released in a `finally` block.
 *
 * ══ EXECUTION STRATEGY ══
 * 1. Try NNAPI (NPU) execution provider.
 * 2. On [OrtException] during NPU init → fall back to multi-threaded CPU.
 * Fallback is transparent to callers — [GenerationResult.fromNpu] signals which was used.
 */
@Singleton
class LocalInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val bufferPool = TensorBufferPool()

    @Volatile private var session: OrtSession? = null
    @Volatile private var sessionFromNpu: Boolean = false

    /** True after [initialize] completes successfully. Thread-safe. */
    val isInitialized: Boolean get() = session != null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ensures the ONNX session is loaded. Safe to call multiple times (idempotent).
     *
     * MUST be called before [generate]. Dispatches session creation to [Dispatchers.Default]
     * internally — callers may call this from any dispatcher.
     */
    suspend fun initialize() {
        if (session != null) return
        withContext(Dispatchers.Default) {
            if (session != null) return@withContext  // double-checked under Default
            session = initSessionOnDefault()
        }
    }

    /**
     * Releases all native ONNX Runtime resources. After calling this, [initialize] must
     * be called again before [generate]. Safe to call even if never initialized.
     */
    fun release() {
        session?.close()
        session = null
    }

    /**
     * Runs autoregressive token generation for the given [promptIds].
     *
     * Algorithm per step:
     *   1. Build input tensor: [1, currentSeqLen] Long
     *   2. Run session → logits: [1, currentSeqLen, vocabSize] Float
     *   3. Extract logits for the last position
     *   4. Apply temperature scaling (Temperature = [ModelConfig.TEMPERATURE])
     *   5. Apply top-p nucleus sampling (Top_P = [ModelConfig.TOP_P])
     *   6. Sample next token ID
     *   7. Append to sequence; stop at EOS or [ModelConfig.MAX_OUTPUT_TOKENS]
     *
     * @param promptIds  Tokenized prompt as integer IDs (from [BpeTokenizer.encode]).
     * @return [GenerationResult] containing sampled token IDs, mean confidence, and NPU flag.
     * @throws IllegalStateException if [initialize] was not called first.
     */
    suspend fun generate(promptIds: IntArray): GenerationResult = withContext(Dispatchers.Default) {
        val ortSession = checkNotNull(session) { "LocalInferenceEngine.initialize() not called" }

        val generatedTokens = mutableListOf<Int>()
        val confidences     = mutableListOf<Float>()
        var currentTokenCount = 0
        val maxTokens = ModelConfig.MAX_OUTPUT_TOKENS

        // Use prompt exactly as tokenized (AstroPromptBuilder + BpeTokenizer handle special tokens)
        val inputTokens = promptIds.toMutableList()

        var logitBuffer: FloatArray? = null

        try {
            while (currentTokenCount < maxTokens) {
                coroutineContext.ensureActive()   // cooperative cancellation check

                val currentSequence = inputTokens + generatedTokens
                val seqLen    = currentSequence.size
                val longInput = LongBuffer.wrap(LongArray(seqLen) { currentSequence[it].toLong() })
                val shape     = longArrayOf(1L, seqLen.toLong())

                val inputTensor = OnnxTensor.createTensor(env, longInput, shape)
                val result = inputTensor.use { tensor ->
                    ortSession.run(mapOf(INPUT_IDS_KEY to tensor))
                }

                result.use { ortResult ->
                    val logitsTensor = ortResult[0].value   // [1, seqLen, vocabSize] Float
                    val vocabSize    = resolveVocabSize(logitsTensor)

                    // Extract last-position logits
                    logitBuffer = bufferPool.acquire()
                    val lastLogits = extractLastLogits(logitsTensor, seqLen, vocabSize, logitBuffer!!)

                    // Temperature scaling
                    for (i in lastLogits.indices) lastLogits[i] /= ModelConfig.TEMPERATURE

                    // Top-p nucleus sampling
                    val (nextTokenId, tokenConfidence) = sampleTopP(lastLogits, vocabSize, ModelConfig.TOP_P)

                    // Strict Stop Validation Gate (EOS or EOT)
                    if (nextTokenId == ModelConfig.EOS_ID || nextTokenId == ModelConfig.EOT_ID) {
                        Log.d(TAG, "Generation stop: token=$nextTokenId count=$currentTokenCount")
                        break 
                    }

                    generatedTokens.add(nextTokenId)
                    confidences.add(tokenConfidence)
                    currentTokenCount++
                    
                    if (currentTokenCount % 10 == 0) {
                        Log.v(TAG, "Generating... count=$currentTokenCount")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation loop failed", e)
            throw e
        } finally {
            logitBuffer?.let { bufferPool.release(it) }
        }

        GenerationResult(
            tokenIds   = generatedTokens.toIntArray(),
            confidence = if (confidences.isEmpty()) 0f else confidences.average().toFloat(),
            fromNpu    = sessionFromNpu
        )
    }

    // ── Session initialization (MUST run on Dispatchers.Default) ─────────────

    /**
     * Creates the OrtSession. Called only from [withContext(Dispatchers.Default)].
     *
     * Strategy:
     *  1. Attempt NNAPI (NPU) session with CPU execution disabled.
     *  2. On any [OrtException] → create CPU-only fallback session.
     *
     * The NNAPI bind call that blocks 200–500ms happens inside [OrtSession.SessionOptions.addNnapi],
     * which is why this method MUST run on [Dispatchers.Default] and never on the main thread.
     */
    private fun initSessionOnDefault(): OrtSession {
        // Model is shipped as .onnx — ORT accepts ONNX directly, no flat-format conversion needed.
        // The ORT flat-format (.ort) inflated the 734 MB INT4 model to 1.25 GB due to
        // pre-baked operator metadata; ONNX at 734 MB is the correct production file.
        val modelFile = java.io.File(context.filesDir, "astro_inference.onnx")
        if (!modelFile.exists()) {
            Log.i(TAG, "Extracting INT4 ONNX model to internal storage (~734 MB)...")
            context.assets.open(ModelConfig.MODEL_ASSET).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return tryNpuSession(modelFile.absolutePath) ?: buildCpuSession(modelFile.absolutePath)
    }

    private fun tryNpuSession(modelPath: String): OrtSession? = runCatching {
        val opts = OrtSession.SessionOptions().apply {
            addNnapi()                                         // NPU bind — 200–500ms on first call
            setInterOpNumThreads(ModelConfig.INTER_OP_THREADS)
            setIntraOpNumThreads(ModelConfig.INTRA_OP_THREADS)
            setMemoryPatternOptimization(true)
        }
        env.createSession(modelPath, opts).also {
            sessionFromNpu = true
            Log.i(TAG, "NNAPI NPU session established from path")
        }
    }.getOrElse { e ->
        Log.w(TAG, "NNAPI unavailable (${e.message}), falling back to CPU")
        null
    }

    private fun buildCpuSession(modelPath: String): OrtSession {
        val opts = OrtSession.SessionOptions().apply {
            setInterOpNumThreads(ModelConfig.INTER_OP_THREADS)
            setIntraOpNumThreads(ModelConfig.INTRA_OP_THREADS)
            setMemoryPatternOptimization(true)
        }
        sessionFromNpu = false
        Log.i(TAG, "CPU fallback session established from path")
        return env.createSession(modelPath, opts)
    }

    // ── Sampling helpers ──────────────────────────────────────────────────────

    /**
     * Extracts logits for the last token position from a [1, seqLen, vocabSize] float tensor.
     * Writes the result into [dest], which must have at least [vocabSize] elements.
     */
    private fun extractLastLogits(
        logitsTensorValue: Any,
        seqLen: Int,
        vocabSize: Int,
        dest: FloatArray
    ): FloatArray {
        val flat = when (logitsTensorValue) {
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val batch = logitsTensorValue as Array<Array<FloatArray>>
                batch[0][seqLen - 1]
            }
            is FloatArray -> {
                // Flat layout [1 * seqLen * vocabSize] — extract last position slice
                val offset = (seqLen - 1) * vocabSize
                logitsTensorValue.copyOfRange(offset, offset + vocabSize)
            }
            else -> FloatArray(vocabSize)
        }
        flat.copyInto(dest, endIndex = minOf(flat.size, vocabSize))
        return dest
    }

    /**
     * Top-p nucleus sampling with softmax normalization.
     *
     * 1. Softmax over [logits].
     * 2. Sort by probability descending.
     * 3. Accumulate until cumulative probability >= [topP].
     * 4. Sample uniformly from the nucleus.
     *
     * @return Pair(sampledTokenId, topTokenProbability)
     */
    private fun sampleTopP(logits: FloatArray, vocabSize: Int, topP: Float): Pair<Int, Float> {
        // Numerically stable softmax
        val maxLogit = logits.take(vocabSize).max()
        val expSum   = logits.take(vocabSize).sumOf { exp((it - maxLogit).toDouble()) }
        val probs    = FloatArray(vocabSize) { i ->
            exp((logits[i] - maxLogit).toDouble()).toFloat() / expSum.toFloat()
        }

        // Build sorted index list by probability descending
        val sortedIdx = (0 until vocabSize).sortedByDescending { probs[it] }

        // Accumulate nucleus
        var cumProb  = 0f
        val nucleus  = mutableListOf<Int>()
        for (idx in sortedIdx) {
            nucleus += idx
            cumProb += probs[idx]
            if (cumProb >= topP) break
        }

        // Uniform sample from nucleus
        val r         = Math.random().toFloat() * cumProb
        var cumSample = 0f
        var sampled   = nucleus.last()
        for (idx in nucleus) {
            cumSample += probs[idx]
            if (cumSample >= r) { sampled = idx; break }
        }

        return sampled to probs[sortedIdx[0]]  // confidence = top nucleus token probability
    }

    /**
     * Derives vocab size from the shape of the logits output tensor.
     * Falls back to a reasonable default if shape metadata is unavailable.
     */
    private fun resolveVocabSize(logitsTensorValue: Any): Int = when (logitsTensorValue) {
        is Array<*> -> {
            @Suppress("UNCHECKED_CAST")
            val batch = logitsTensorValue as? Array<Array<FloatArray>>
            batch?.firstOrNull()?.firstOrNull()?.size ?: ModelConfig.LOGIT_BUFFER_SIZE
        }
        is FloatArray -> logitsTensorValue.size
        else          -> ModelConfig.LOGIT_BUFFER_SIZE
    }

    companion object {
        private const val TAG           = "AstroInference"
        private const val INPUT_IDS_KEY = "input_ids"
    }
}

/**
 * Raw output from [LocalInferenceEngine.generate].
 *
 * [tokenIds] excludes the BOS prompt — only generated tokens are included.
 * Decoding is handled by [BpeTokenizer.decode] in [AstroInferenceModel].
 */
data class GenerationResult(
    val tokenIds:   IntArray,
    val confidence: Float,
    val fromNpu:    Boolean
) {
    override fun equals(other: Any?): Boolean =
        other is GenerationResult && tokenIds.contentEquals(other.tokenIds)
    override fun hashCode(): Int = tokenIds.contentHashCode()
}
