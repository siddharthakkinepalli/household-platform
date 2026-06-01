package com.jugaad.core.airuntime

import android.content.Context
import android.util.Log
import com.jugaad.core.airuntime.model.InferenceResult
import com.jugaad.core.airuntime.model.ModelConfig
import com.jugaad.core.airuntime.prompt.AstroPromptBuilder
import com.jugaad.core.airuntime.runtime.LocalInferenceEngine
import com.jugaad.core.airuntime.tokenizer.BpeTokenizer
import com.jugaad.core.airuntime.tokenizer.Tokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level Vedic astrology inference model.
 *
 * Orchestrates the full inference pipeline:
 *   1. Ensure [LocalInferenceEngine] is initialized (NNAPI NPU or CPU fallback)
 *   2. Build prompt via [AstroPromptBuilder] (XML tag boundaries)
 *   3. Tokenize prompt via [BpeTokenizer]
 *   4. Generate token IDs via [LocalInferenceEngine.generate] (temperature=0.2, top-p=0.9)
 *   5. Decode token IDs back to text
 *   6. Wrap in [InferenceResult] with confidence + timing metadata
 *
 * ══ MEMORY CONTRACT ══
 * [predictTransit] and [predictNatal] are foreground-only calls (≤512 MB).
 * Background WorkManager jobs must read from Room cache — NEVER call this class from Workers.
 *
 * ══ INITIALIZATION ══
 * [initialize] dispatches to [Dispatchers.Default] internally. It is idempotent — safe to
 * call multiple times. Call it once at screen start or from a ViewModel init block.
 */
@Singleton
class AstroInferenceModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: LocalInferenceEngine
) {

    @Volatile private var tokenizer: Tokenizer? = null

    /**
     * Loads the ONNX session and BPE tokenizer from assets.
     * Dispatches heavy I/O + NNAPI bind to [Dispatchers.Default].
     * Idempotent — subsequent calls are no-ops.
     *
     * Call once before [predictTransit] or [predictNatal].
     *
     * @throws IllegalStateException if model or vocab asset files are missing.
     */
    suspend fun initialize() {
        if (engine.isInitialized && tokenizer != null) return
        withContext(Dispatchers.Default) {
            // Engine init: OrtSession + NNAPI on Dispatchers.Default (mandatory — see LocalInferenceEngine)
            engine.initialize()
            // Tokenizer init: reads vocab.json + merges.txt from assets
            if (tokenizer == null) {
                tokenizer = BpeTokenizer.fromAssets(context)
                Log.i(TAG, "BpeTokenizer loaded, vocabSize=${tokenizer!!.vocabSize}")
            }
        }
    }

    /**
     * Generates a daily transit insight from a compact chart state JSON.
     *
     * @param chartStateJson  JSON from [AstroDataProcessor.buildContextPayloadNoLagna] or
     *                        [buildContextPayload]. Example: {"0":[4,120.3,75],...}
     * @return [InferenceResult] with the generated prediction text and metadata.
     */
    suspend fun predictTransit(chartStateJson: String): InferenceResult =
        runInference(AstroPromptBuilder.buildTransitPrompt(chartStateJson))

    /**
     * Generates a personalized daily horoscope.
     */
    suspend fun predictPersonalized(natalJson: String, transitJson: String): InferenceResult =
        runInference(AstroPromptBuilder.buildPersonalizedPrompt(natalJson, transitJson))

    /**
     * Generates a natal chart summary from a compact birth chart JSON.
     *
     * @param chartStateJson  JSON from [AstroDataProcessor.buildContextPayload] (includes "L" lagna).
     */
    suspend fun predictNatal(chartStateJson: String): InferenceResult =
        runInference(AstroPromptBuilder.buildNatalPrompt(chartStateJson))

    /**
     * Low-level inference: accepts a pre-built prompt string.
     * Use [predictTransit] or [predictNatal] for standard flows.
     */
    suspend fun predict(prompt: String): InferenceResult = runInference(prompt)

    // ── Vault & Expense inference ─────────────────────────────────────────────

    /**
     * Extracts structured document fields from raw OCR text.
     *
     * Returns a raw JSON string (caller parses with org.json). On model failure
     * the outer [runCatching] in the caller should catch and fall through to the
     * rule-based parser result.
     *
     * Foreground only — do NOT call from WorkManager workers.
     */
    suspend fun extractDocumentFields(ocrText: String): InferenceResult =
        runInference(AstroPromptBuilder.buildVaultExtractionPrompt(ocrText))

    /**
     * Categorizes a bank transaction when keyword matching returns "Other".
     *
     * Returns a raw JSON string:
     *   `{"category":"Shopping","budgetCategory":"Online","confidence":0.88}`
     *
     * Foreground only.
     */
    suspend fun categorizeExpense(description: String, amountEur: Double): InferenceResult =
        runInference(AstroPromptBuilder.buildExpenseCategoryPrompt(description, amountEur))

    // ── Private pipeline ──────────────────────────────────────────────────────

    private suspend fun runInference(prompt: String): InferenceResult {
        val tok = checkNotNull(tokenizer) { "AstroInferenceModel.initialize() not called" }

        val startMs   = System.currentTimeMillis()
        val promptIds = tok.encode(prompt).also { ids ->
            require(ids.size <= ModelConfig.MAX_INPUT_TOKENS) {
                "Prompt too long: ${ids.size} tokens > MAX_INPUT_TOKENS=${ModelConfig.MAX_INPUT_TOKENS}"
            }
        }

        Log.d(TAG, "Inference start — promptTokens=${promptIds.size}")

        val genResult = engine.generate(promptIds)
        val decoded   = tok.decode(genResult.tokenIds)

        val inferenceMs = System.currentTimeMillis() - startMs
        Log.d(TAG, "Inference done — ms=$inferenceMs fromNpu=${genResult.fromNpu} confidence=${genResult.confidence}")

        return InferenceResult(
            predictionText = decoded,
            confidence     = genResult.confidence,
            modelVersion   = ModelConfig.MODEL_VERSION,
            inferenceMs    = inferenceMs,
            fromNpu        = genResult.fromNpu
        )
    }

    companion object {
        private const val TAG = "AstroModel"
    }
}

