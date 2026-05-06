package com.household.app.vault

object RenewalHintDetector {
    private val dateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})\b""")
    private val keywords = listOf("renewal", "renew", "contract", "insurance", "policy", "lease", "expiry", "expires")

    fun shouldSuggestRenewal(ocrText: String): Boolean {
        val normalized = ocrText.lowercase()
        val hasKeyword = keywords.any { normalized.contains(it) }
        val hasDate = dateRegex.containsMatchIn(normalized)
        return hasKeyword && hasDate
    }
}
