package com.jugaad.core.airuntime.prompt

import com.jugaad.core.airuntime.model.ModelConfig

/**
 * Assembles the structured prompt passed to [LocalInferenceEngine].
 *
 * ══ QWEN2.5 CHAT TEMPLATE ══
 * Qwen2.5-1.5B-Instruct was fine-tuned on the `<|im_start|>` / `<|im_end|>` chat format.
 *
 * Template:
 * ```
 * <|im_start|>system
 * <system_role>
 * {systemRole}
 * </system_role>
 * <|im_end|>
 * <|im_start|>user
 * <user_chart_state>
 * {chartStateJson}
 * </user_chart_state>
 * <|im_end|>
 * <|im_start|>assistant
 * ```
 *
 * The `<|im_start|>assistant` line at the end primes the model to generate its reply.
 * Stop condition: model emits `<|im_end|>` (token 151,645) to signal turn completion.
 */
object AstroPromptBuilder {

    // Qwen2.5 ChatML control tokens
    private const val IM_START = "<|im_start|>"
    private const val IM_END   = "<|im_end|>"

    // Semantic content tags
    private const val TAG_SYSTEM_OPEN  = "<system_role>"
    private const val TAG_SYSTEM_CLOSE = "</system_role>"
    private const val TAG_CHART_OPEN   = "<user_chart_state>"
    private const val TAG_CHART_CLOSE  = "</user_chart_state>"

    private const val DEFAULT_SYSTEM_ROLE =
        "You are a precise Vedic astrology engine. Generate a single concise daily insight " +
        "based on the planetary transit data provided. Be factual, brief (2-3 sentences), " +
        "and avoid superlatives or vague spiritual language."

    private const val NATAL_SYSTEM_ROLE =
        "You are a precise Vedic astrology engine. Provide a brief natal chart summary " +
        "(3-4 sentences) covering the ascendant, Moon sign, and dominant planetary " +
        "strengths. Be specific and grounded."

    private const val HOROSCOPE_SYSTEM_ROLE =
        "You are a precise Vedic astrology engine. Generate a personalized daily horoscope " +
        "based on the user's natal chart and today's planetary transits. Focus on " +
        "career, health, and mindset. Be factual and brief (max 4 sentences)."

    /**
     * Builds the daily transit inference prompt.
     */
    fun buildTransitPrompt(
        chartStateJson: String,
        systemRole: String = DEFAULT_SYSTEM_ROLE
    ): String = buildQwenPrompt(systemRole, chartStateJson)

    /**
     * Builds a personalized horoscope prompt combining birth chart + transit.
     */
    fun buildPersonalizedPrompt(
        natalJson: String,
        transitJson: String
    ): String {
        val combined = "{\"natal\":$natalJson,\"transit\":$transitJson}"
        return buildQwenPrompt(HOROSCOPE_SYSTEM_ROLE, combined)
    }

    /**
     * Builds the natal chart inference prompt.
     */
    fun buildNatalPrompt(
        chartStateJson: String,
        systemRole: String = NATAL_SYSTEM_ROLE
    ): String = buildQwenPrompt(systemRole, chartStateJson)

    /** Low-level: accepts pre-built system and user text strings. */
    fun buildPrompt(systemRole: String, chartStateJson: String): String =
        buildQwenPrompt(systemRole, chartStateJson)

    // ── Vault document extraction ─────────────────────────────────────────────

    private const val VAULT_SYSTEM_ROLE =
        "You are a document field extractor. Given OCR text from a scanned document, " +
        "extract key fields and return ONLY a single valid JSON object. " +
        "Fields: docType, name, documentNumber, issueDate (YYYY-MM-DD or null), " +
        "expiryDate (YYYY-MM-DD or null), issuingAuthority, country. " +
        "Use null for any field you cannot determine with confidence. " +
        "Return nothing except the JSON object — no explanation, no markdown."

    /**
     * Builds a prompt for structured field extraction from raw OCR text.
     * Expected output: a single JSON object.
     */
    fun buildVaultExtractionPrompt(ocrText: String): String {
        // Truncate OCR to avoid hitting token limits (keep first 800 chars for 0.5B model)
        val truncated = if (ocrText.length > 800) ocrText.take(800) + "..." else ocrText
        return buildQwenPrompt(VAULT_SYSTEM_ROLE, truncated)
    }

    // ── Expense categorization ────────────────────────────────────────────────

    private const val EXPENSE_SYSTEM_ROLE =
        "You are an expense categorizer for a German household budget app. " +
        "Given a bank transaction, return ONLY a single valid JSON object with these fields: " +
        "category (one of: Food & Dining, Transportation, Utilities, Entertainment, Shopping, " +
        "Health & Fitness, Housing & Property, Transfers, Cash, Banking & Fees, " +
        "Government & Benefits, Other), budgetCategory (short sub-label or empty string), " +
        "confidence (0.0 to 1.0). Return nothing except the JSON object."

    /**
     * Builds a prompt for expense categorization.
     * Expected output: `{"category":"...","budgetCategory":"...","confidence":0.9}`
     */
    fun buildExpenseCategoryPrompt(description: String, amountEur: Double): String {
        val sign = if (amountEur < 0) "expense" else "income"
        val user = "description: \"$description\", amount: ${"%.2f".format(kotlin.math.abs(amountEur))} EUR ($sign)"
        return buildQwenPrompt(EXPENSE_SYSTEM_ROLE, user)
    }

    /**
     * Assembles the full Qwen2.5 ChatML prompt string.
     */
    private fun buildQwenPrompt(systemRole: String, userContent: String): String = buildString {
        // System turn
        append(IM_START).append("system\n")
        append(TAG_SYSTEM_OPEN).append("\n")
        append(systemRole.trim()).append("\n")
        append(TAG_SYSTEM_CLOSE).append("\n")
        append(IM_END).append("\n")

        // User turn
        append(IM_START).append("user\n")
        append(TAG_CHART_OPEN).append("\n")
        append(userContent.trim()).append("\n")
        append(TAG_CHART_CLOSE).append("\n")
        append(IM_END).append("\n")

        // Assistant turn primer
        append(IM_START).append("assistant\n")
    }
}
