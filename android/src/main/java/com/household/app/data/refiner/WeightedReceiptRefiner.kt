package com.household.app.data.refiner

import android.util.Log
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.ScanField
import com.household.app.domain.models.vault.TextLinePayload
import com.household.app.domain.services.ReceiptRefiner
import com.household.app.domain.models.vault.VisionTextPayload
import java.time.LocalDate

class WeightedReceiptRefiner : ReceiptRefiner {

    override fun refine(visionText: VisionTextPayload): RefinedScan {
        val normalizedLines = visionText
            .allLines()
            .sortedBy { it.boundingBoxTop }   // ensure top-of-receipt lines get index 0-2 bonus
            .map {
                it.copy(text = normalizeMerchantLine(it.text))
            }
            .filter { it.text.isNotBlank() }

        Log.d("ReceiptRefiner", "=== OCR lines (sorted top→bottom) ===")
        normalizedLines.forEachIndexed { i, l ->
            Log.d("ReceiptRefiner", "[$i] top=${l.boundingBoxTop.toInt()} conf=${l.confidence.format()} '${l.text}'")
        }

        val maxBottom = normalizedLines.maxOfOrNull { it.boundingBoxBottom } ?: 0f

        val amountCandidates = normalizedLines
            .mapIndexed { index, line ->
                val parsed = parseAmount(line.text) ?: return@mapIndexed null
                val score = scoreAmountLine(line = line, index = index, maxBottom = maxBottom)
                AmountCandidate(value = parsed, score = score)
            }
            .filterNotNull()
            .sortedByDescending { it.score }

        // Strategy: receipt total is printed twice (SUMME + Geg. Mastercard) → prefer repeated value.
        // When multiple values repeat, pick the LARGEST (total > any single item price).
        // Fallback: largest single amount on the receipt.
        val bestAmount = run {
            if (amountCandidates.isEmpty()) return@run null
            val grouped = amountCandidates.groupBy { "%.2f".format(it.value) }
            val repeated = grouped.filter { it.value.size >= 2 }
                .entries
                .maxByOrNull { (key, _) -> key.toDoubleOrNull() ?: 0.0 }
                ?.value?.maxByOrNull { it.score }
            repeated ?: amountCandidates.maxByOrNull { it.value }
        }
        val amountConfidence = (bestAmount?.score ?: 0f).coerceIn(0f, 1f)

        val bestMerchant = selectMerchant(normalizedLines, maxBottom)
        val merchantLine = bestMerchant?.value

        val dateCandidate = normalizedLines
            .asSequence()
            .mapNotNull { parseDate(it.text) }
            .firstOrNull() ?: LocalDate.now()

        val merchantConfidence = (bestMerchant?.score ?: 0.45f).coerceIn(0f, 1f)
        val dateConfidence = if (dateCandidate == LocalDate.now()) 0.55f else 0.84f

        Log.d("ReceiptRefiner", "merchant='${merchantLine ?: "NONE"}' score=${merchantConfidence.format()} | amount=${bestAmount?.value} | date=$dateCandidate")

        return RefinedScan(
            compositeConfidence = ((amountConfidence + merchantConfidence + dateConfidence) / 3f)
                .coerceIn(0f, 1f),
            merchant = ScanField(merchantLine ?: "Unknown", merchantConfidence),
            amount = ScanField(bestAmount?.value, amountConfidence),
            date = ScanField(dateCandidate, dateConfidence)
        )
    }

    private fun scoreAmountLine(line: TextLinePayload, index: Int, maxBottom: Float): Float {
        val lower = line.text.lowercase()
        var score = 0.42f
        if (containsAmountAnchor(lower)) score += 0.42f
        if (lower.contains("eur") || lower.contains("euro") || lower.contains("€")) score += 0.10f
        if (line.text.count { it.isDigit() } >= 3) score += 0.06f
        if (index >= 3) score -= 0.06f
        // Heavy penalty for lines that look like dates (dd.mm. or dd/mm/ prefix)
        if (Regex("""\d{1,2}[.,/]\d{1,2}[.,/]""").containsMatchIn(line.text)) score -= 0.50f
        // Unit-price lines (per-kg, per-piece) are never the receipt total
        if (lower.contains(" kg") || lower.contains("/kg") || lower.contains("eur/kg") ||
            lower.contains("eur kg") || lower.contains("stk") || lower.contains("x ")) score -= 0.45f
        if (maxBottom > 0f && line.boundingBoxBottom >= maxBottom * 0.72f) score += 0.08f
        // Use real word-level confidence if available (non-zero means ML Kit provided it)
        if (line.confidence > 0f) score += (line.confidence * 0.08f)
        return score.coerceAtMost(1f)
    }

    private fun selectMerchant(lines: List<TextLinePayload>, maxBottom: Float): MerchantCandidate? {
        val candidates = lines.mapIndexed { index, line ->
            val score = scoreMerchantLine(line = line, index = index, maxBottom = maxBottom)
            Log.d("ReceiptRefiner", "  merchant score [$index] ${score.format()} '${line.text}'")
            MerchantCandidate(value = line.text, score = score)
        }
        return candidates
            .filter { it.value.isNotBlank() && !isBoilerplateLine(it.value) }
            .sortedByDescending { it.score }
            .firstOrNull { it.score >= 0.42f }
    }

    private fun scoreMerchantLine(line: TextLinePayload, index: Int, maxBottom: Float): Float {
        val text = line.text
        val lower = text.lowercase()
        var score = 0.22f

        if (index <= 2) score += 0.32f
        if (text.none { it.isDigit() }) score += 0.18f
        if (uppercaseRatio(text) >= 0.6f) score += 0.14f
        if (text.length in 4..38) score += 0.08f
        // Word-boundary suffix match: "eg" must be its own word, not a substring of "eGog"
        val words = lower.split(Regex("""\s+"""))
        if (merchantSuffixes.any { suf -> words.any { w -> w == suf } }) score += 0.20f
        // Real confidence bonus
        if (line.confidence > 0f) score += (line.confidence.coerceIn(0f, 1f) * 0.12f)
        // Sentence-like noise penalty: many lowercase words = UI element / Google bar / junk OCR
        if (words.size >= 4 && uppercaseRatio(text) < 0.30f) score -= 0.40f

        if (containsAmountAnchor(lower)) score -= 0.40f
        if (parseAmount(text) != null) score -= 0.45f
        if (parseDate(text) != null) score -= 0.36f
        if (merchantStopwords.any { lower.contains(it) }) score -= 0.24f
        if (isLikelyAddressLine(text)) score -= 0.35f
        if (isBoilerplateLine(text)) score -= 0.45f
        if (maxBottom > 0f && line.boundingBoxBottom >= maxBottom * 0.68f) score -= 0.18f
        // Penalise lines that start far from the left edge (centred footer text)
        if (line.boundingBoxLeft > 0f && line.boundingBoxRight > 0f) {
            val lineWidth = line.boundingBoxRight - line.boundingBoxLeft
            if (lineWidth > 0f && line.boundingBoxLeft / lineWidth > 0.35f) score -= 0.08f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun normalizeMerchantLine(line: String): String {
        return line
            .replace(Regex("""[^\p{L}\p{N}& .'-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(48)
    }

    private fun isBoilerplateLine(text: String): Boolean {
        val lower = text.lowercase()
        return boilerplatePatterns.any { it.containsMatchIn(lower) } ||
            locationNoise.contains(lower)
    }

    private fun isLikelyAddressLine(text: String): Boolean {
        val lower = text.lowercase()
        return addressPatterns.any { it.containsMatchIn(lower) }
    }

    private fun uppercaseRatio(line: String): Float {
        val letters = line.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        val uppercase = letters.count { it.isUpperCase() }
        return uppercase.toFloat() / letters.length.toFloat()
    }

    private fun containsAmountAnchor(line: String): Boolean {
        val lower = line.lowercase()
        return amountAnchors.any { lower.contains(it) }
    }

    private fun parseAmount(line: String): Double? {
        // Standard decimal separator (. or ,)
        val dotMatch = Regex("""([0-9]{1,5}[.,][0-9]{2})""").find(line)
        if (dotMatch != null) {
            val v = dotMatch.value.replace(',', '.').toDoubleOrNull()
            // Reject if this looks like part of a date pattern: d.m. or dd.mm.
            val isDateLike = Regex("""\d{1,2}[.,]\d{1,2}[.,]""").containsMatchIn(line)
            if (v != null && !isDateLike) return v
        }
        // Space-as-decimal (German thermal receipt OCR artifact: "28 30" → 28.30, "2 38" → 2.38)
        val spaceMatch = Regex("""(?<!\d)(\d{1,4}) (\d{2})(?!\d)""").find(line)
        if (spaceMatch != null) {
            val candidate = "${spaceMatch.groupValues[1]}.${spaceMatch.groupValues[2]}".toDoubleOrNull()
            if (candidate != null && candidate >= 0.10) return candidate
        }
        return null
    }

    private fun parseDate(line: String): LocalDate? {
        // Fuzzy: capture the whole mangled year token (e.g. "2e24") and clean OCR substitutions
        val cleaned = line.replace(Regex("""(\d{1,2}[./-]\d{1,2}[./-])\s*([A-Za-z0-9]{2,4})""")) { mr ->
            val prefix = mr.groupValues[1]
            val year = mr.groupValues[2]
                .replace(Regex("""[eEoO]"""), "0")
                .replace(Regex("""[lLiI]"""), "1")
            "$prefix$year"
        }
        // Second separator may be dropped by OCR: "15.11 2024" is valid, "15.11.2024" is valid
        val match = Regex("""\b(\d{1,2})[./-](\d{1,2})(?:[./-]\s*|\s+)(\d{2,4})\b""").find(cleaned) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        var year = match.groupValues[3].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private data class AmountCandidate(
        val value: Double,
        val score: Float
    )

    private data class MerchantCandidate(
        val value: String,
        val score: Float
    )

    private companion object {
        // German + English merchant suffixes
        val merchantSuffixes = listOf(
            "gmbh", "ag", "kg", "ohg", "kgaa", "eg", "ug",    // German legal forms
            "llc", "ltd", "inc", "sarl", "bv", "sa",           // International
            "markt", "market", "store", "shop", "handel", "handels",
            "supermarkt", "getränke", "bäckerei", "metzgerei"
        )
        // German + English amount anchors
        val amountAnchors = listOf(
            "summe", "gesamtsumme", "endbetrag", "zu zahlen", "zahlbetrag",  // DE
            "total", "betrag", "zwischensumme", "brutto", "netto",            // DE
            "amount", "subtotal", "grand total", "balance due"                // EN
        )
        val merchantStopwords = listOf(
            // German
            "summe", "betrag", "mwst", "mehrwertsteuer", "ust", "steuer",
            "rabatt", "skonto", "gutschein", "bon", "kasse", "kassierer",
            "vielen dank", "danke", "rechnung", "quittung",
            // English
            "total", "tax", "cash", "card", "change", "receipt", "invoice",
            "thank you", "cashier", "subtotal", "discount"
        )
        val locationNoise = setOf(
            "ulm", "berlin", "münchen", "munchen", "stuttgart", "hamburg",
            "köln", "koln", "frankfurt", "düsseldorf", "dusseldorf",
            "dortmund", "essen", "bremen", "hannover", "nürnberg", "nurnberg",
            "leipzig", "dresden", "bochum", "wuppertal", "bielefeld", "bonn"
        )
        val boilerplatePatterns = listOf(
            Regex("""\bst\.?\s*nr\b"""),
            Regex("""\bust\s*-?id\b"""),
            Regex("""\bsteuer\s*nr\b"""),
            Regex("""\bbon\s*nr\b"""),
            Regex("""\bkassen?\s*nr\b"""),
            Regex("""\biban\b"""),
            Regex("""\bbic\b"""),
            Regex("""\bwww\."""),
            Regex("""\bhttps?://"""),
            Regex("""\b(tel|fax)\b""")
        )
        val addressPatterns = listOf(
            Regex("""\b\d{5}\s+[\p{L}\-]+"""),
            Regex("""\b(str\.|strasse|straße|platz|allee|weg)\b""")
        )
    }
}

private fun Float.format() = "%.2f".format(this)
