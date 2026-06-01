package com.household.app.ai

import android.util.Log
import com.jugaad.core.airuntime.AstroInferenceModel
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AiCategoryResult(
    val category: String,
    val budgetCategory: String,
    val confidence: Float
)

/**
 * Upgrades "Other" expense categories using the on-device Qwen model.
 *
 * The rule-based [ExpensesCategories.categorizeTransaction] handles ~90% of
 * transactions via keyword lists. This class handles the remaining 10% where
 * no keyword matched ("Other"). Runs foreground only.
 *
 * Typical latency: 2-4s on NNAPI NPU, 15-30s on CPU fallback.
 * Result is cached per (description, month) in the caller — inference
 * is NOT called twice for the same merchant.
 */
@Singleton
class AiExpenseCategorizer @Inject constructor(
    private val model: AstroInferenceModel
) {

    /**
     * Returns an AI-derived category for [description] + [amountEur].
     * Returns null on failure — caller keeps the "Other" label in that case.
     */
    suspend fun categorize(description: String, amountEur: Double): AiCategoryResult? =
        runCatching {
            model.initialize()
            val result = model.categorizeExpense(description, amountEur)
            if (result.predictionText.isBlank()) return null
            parseResult(result.predictionText)
        }.onFailure { e ->
            Log.e(TAG, "AI expense categorization failed", e)
        }.getOrNull()

    private fun parseResult(raw: String): AiCategoryResult? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val start = cleaned.indexOf('{')
        val end   = cleaned.lastIndexOf('}')
        if (start == -1 || end == -1) return null

        return runCatching {
            val json = JSONObject(cleaned.substring(start, end + 1))
            val cat  = json.optString("category").ifBlank { "Other" }
            val sub  = json.optString("budgetCategory")
            val conf = json.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f)
            // Validate against known category list — reject hallucinated categories
            if (cat !in KNOWN_CATEGORIES) return null
            AiCategoryResult(category = cat, budgetCategory = sub, confidence = conf)
        }.onFailure { e ->
            Log.w(TAG, "Failed to parse AI category JSON: ${e.message}")
        }.getOrNull()
    }

    companion object {
        private const val TAG = "AiExpenseCat"

        private val KNOWN_CATEGORIES = setOf(
            "Food & Dining", "Transportation", "Utilities", "Entertainment",
            "Shopping", "Health & Fitness", "Housing & Property", "Transfers",
            "Cash", "Banking & Fees", "Government & Benefits", "Other"
        )
    }
}
