package com.household.app.domain.utils

object MerchantNameCleaner {
    fun clean(raw: String): String {
        val normalizedRaw = raw.replace(Regex("\\s+"), " ").trim()
        if (normalizedRaw.isBlank()) return raw.trim()

        val lowered = normalizedRaw.lowercase()

        // Pattern 1: German SEPA "Ihr Einkauf bei <merchant>"
        // Commerzbank inserts spaces mid-word at ~140-char line boundaries (e.g. "Google P ayment").
        // Fix: remove space before punctuation and join single uppercase fragment with next lowercase word.
        Regex("""(?i)ihr einkauf bei\s+([^,.\n]+)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return fixTruncationSpaces(it).replaceFirstChar { c -> c.uppercase() } }

        // Pattern 2: Commerzbank SEPA — "PP.{id}.PP/. <merchant>, Ihr Einkauf bei <merchant>"
        // Uses any alphanumeric ID between dots: PP.4139.PP, PP.A89F3.PP, etc.
        Regex("""(?i)pp\.[A-Z0-9]+\.pp/?\.?\s*([^,\n]+?),?\s*Ihr Einkauf""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return fixTruncationSpaces(it).replaceFirstChar { c -> c.uppercase() } }

        // Pattern 3: "PAYPAL *Merchant Name" (N26, ING card-based)
        Regex("""(?i)paypal\s*[*]\s*([^,.\n]+)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return fixTruncationSpaces(it).replaceFirstChar { c -> c.uppercase() } }

        // Pattern 4: "PP*MerchantName" (short form)
        Regex("""(?i)pp\s*[*]\s*([^,.\n]+)""").find(normalizedRaw)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.length > 2 }
            ?.let { return fixTruncationSpaces(it).replaceFirstChar { c -> c.uppercase() } }

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

    /**
     * Fix mid-word spaces inserted by Commerzbank's ~140-char CSV line truncation.
     * Examples: "Google P ayment Ireland" → "Google Payment Ireland"
     *           "Takeaway .com Payments"  → "Takeaway.com Payments"
     */
    private fun fixTruncationSpaces(text: String): String {
        var out = text
        // Remove space before punctuation: "Takeaway .com" → "Takeaway.com"
        out = out.replace(Regex("""(\w)\s+([.,/])"""), "$1$2")
        // Join single uppercase letter with following lowercase word: "P ayment" → "Payment"
        out = out.replace(Regex("""\b([A-Z])\s+([a-z])"""), "$1$2")
        return out.trim()
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
