package com.household.app.ai

import android.util.Log
import com.jugaad.core.airuntime.AstroInferenceModel
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AiDocumentResult(
    val docType: String?,
    val name: String?,
    val documentNumber: String?,
    val issueDate: String?,        // YYYY-MM-DD or null
    val expiryDate: String?,       // YYYY-MM-DD or null
    val issuingAuthority: String?,
    val country: String?,
    val rawJson: String
)

/**
 * Enhances low-confidence vault documents using the on-device Qwen model.
 *
 * Called from the vault ViewModel when [ParserRegistry] returns a result with
 * overallConfidence < 0.30 (GenericFallbackParser territory). The AI extraction
 * runs in the foreground and is never used from a WorkManager worker.
 */
@Singleton
class AiDocumentEnhancer @Inject constructor(
    private val model: AstroInferenceModel
) {

    /**
     * Attempts to extract structured fields from [ocrText] using the local Qwen model.
     * Returns null on model init failure or JSON parse error — caller falls back to
     * the existing rule-based result in that case.
     */
    suspend fun enhance(ocrText: String): AiDocumentResult? = runCatching {
        model.initialize()
        val result = model.extractDocumentFields(ocrText)
        if (result.predictionText.isBlank()) return null
        parseResult(result.predictionText)
    }.onFailure { e ->
        Log.e(TAG, "AI document enhancement failed", e)
    }.getOrNull()

    private fun parseResult(raw: String): AiDocumentResult? {
        // Strip any markdown fences the model may have added
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val start = cleaned.indexOf('{')
        val end   = cleaned.lastIndexOf('}')
        if (start == -1 || end == -1) return null

        return runCatching {
            val json = JSONObject(cleaned.substring(start, end + 1))
            fun str(key: String) = json.optString(key).takeIf { it.isNotEmpty() && it != "null" }
            AiDocumentResult(
                docType          = str("docType"),
                name             = str("name"),
                documentNumber   = str("documentNumber"),
                issueDate        = str("issueDate"),
                expiryDate       = str("expiryDate"),
                issuingAuthority = str("issuingAuthority"),
                country          = str("country"),
                rawJson          = cleaned.substring(start, end + 1)
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed to parse AI document JSON: ${e.message}")
        }.getOrNull()
    }

    companion object {
        private const val TAG = "AiDocEnhancer"
    }
}
