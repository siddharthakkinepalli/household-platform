package com.household.app.domain.utils

object MerchantNameCleaner {
    fun clean(raw: String): String {
        val normalizedRaw = raw.replace(Regex("\\s+"), " ").trim()
        if (normalizedRaw.isBlank()) return raw.trim()

        val lowered = normalizedRaw.lowercase()

        // Pattern 1: German SEPA "Ihr Einkauf bei <merchant>"
        Regex("""(?i)ihr einkauf bei\s+([^,.\n]+)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return it.replaceFirstChar { c -> c.uppercase() } }

        // Pattern 2: "PP.*.PP , <merchant>" (PayPal SEPA reference format)
        Regex("""(?i)pp\..*?\.pp\s*,\s*([^,.\n]+)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return it.replaceFirstChar { c -> c.uppercase() } }

        // Pattern 3: "PAYPAL *Merchant Name" (multi-word)
        Regex("""(?i)paypal\s*[*]\s*([^,.\n]+)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return it.replaceFirstChar { c -> c.uppercase() } }

        // Pattern 4: "PP*MerchantName"
        Regex("""(?i)pp\s*[*]\s*([^,.\n]+)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return it.replaceFirstChar { c -> c.uppercase() } }

        // Pattern 5: German SEPA SVWZ+ remittance field — "SVWZ+<text> ABWA+" or end
        Regex("""SVWZ\+(.*?)(?:\s[A-Z]{4}\+|$)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return it.replaceFirstChar { c -> c.uppercase() } }

        if (lowered.contains("wise")) return "Wise"
        if (lowered.contains("stadt ulm")) return "Stadt Ulm"

        val chunks = normalizedRaw
            .split("/", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var candidate = chunks.firstOrNull().orEmpty()
        if (candidate.startsWith("LS ", ignoreCase = true) && chunks.size > 1) {
            candidate = chunks[1]
        }

        candidate = stripNoise(candidate)

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
