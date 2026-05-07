package com.household.app.domain.utils

object MerchantNameCleaner {
    fun clean(raw: String): String {
        val normalizedRaw = raw.replace(Regex("\\s+"), " ").trim()
        if (normalizedRaw.isBlank()) return raw.trim()

        val chunks = normalizedRaw
            .split("/", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var candidate = chunks.firstOrNull().orEmpty()
        if (candidate.startsWith("LS ", ignoreCase = true) && chunks.size > 1) {
            candidate = chunks[1]
        }

        candidate = stripNoise(candidate)

        val lowered = normalizedRaw.lowercase()
        if (lowered.contains("paypal")) return "PayPal"
        if (lowered.contains("wise")) return "Wise"
        if (lowered.contains("stadt ulm")) return "Stadt Ulm"

        return candidate.ifBlank { normalizedRaw }
    }

    private fun stripNoise(input: String): String {
        var out = input
        out = out.substringBefore(" End-To-End Reference", missingDelimiterValue = out)
        out = out.substringBefore(" Mandate Reference", missingDelimiterValue = out)
        out = out.substringBefore(" ID Of Ordering Party", missingDelimiterValue = out)
        out = out.substringBefore(" Customer Reference", missingDelimiterValue = out)
        out = out.substringBefore(" Reason For Return", missingDelimiterValue = out)
        out = out.replace(Regex("\\b\\d{8,}\\b"), " ")
        out = out.replace(Regex("\\s+"), " ").trim()
        return out
    }
}