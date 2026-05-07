package com.household.app.data.refiner

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
            .map {
                it.copy(text = normalizeMerchantLine(it.text))
            }
            .filter { it.text.isNotBlank() }

        val maxBottom = normalizedLines.maxOfOrNull { it.boundingBoxBottom } ?: 0f

        val amountCandidates = normalizedLines
            .mapIndexed { index, line ->
                val parsed = parseAmount(line.text) ?: return@mapIndexed null
                val score = scoreAmountLine(line = line, index = index, maxBottom = maxBottom)
                AmountCandidate(value = parsed, score = score)
            }
            .filterNotNull()
            .sortedByDescending { it.score }

        val bestAmount = amountCandidates.firstOrNull()
        val amountConfidence = (bestAmount?.score ?: 0f).coerceIn(0f, 1f)

        val bestMerchant = selectMerchant(normalizedLines, maxBottom)
        val merchantLine = bestMerchant?.value

        val dateCandidate = normalizedLines
            .asSequence()
            .mapNotNull { parseDate(it.text) }
            .firstOrNull() ?: LocalDate.now()

        val merchantConfidence = (bestMerchant?.score ?: 0.45f).coerceIn(0f, 1f)
        val dateConfidence = if (dateCandidate == LocalDate.now()) 0.55f else 0.84f

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
        if (maxBottom > 0f && line.boundingBoxBottom >= maxBottom * 0.72f) score += 0.08f
        score += (line.confidence.coerceIn(0f, 1f) * 0.06f)
        return score.coerceAtMost(1f)
    }

    private fun selectMerchant(lines: List<TextLinePayload>, maxBottom: Float): MerchantCandidate? {
        return lines
            .mapIndexed { index, line ->
                val score = scoreMerchantLine(line = line, index = index, maxBottom = maxBottom)
                MerchantCandidate(value = line.text, score = score)
            }
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
        if (merchantSuffixes.any { lower.contains(it) }) score += 0.20f
        if (line.confidence >= 0.85f) score += 0.08f

        if (containsAmountAnchor(lower)) score -= 0.40f
        if (parseAmount(text) != null) score -= 0.45f
        if (parseDate(text) != null) score -= 0.36f
        if (merchantStopwords.any { lower.contains(it) }) score -= 0.24f
        if (isLikelyAddressLine(text)) score -= 0.35f
        if (isBoilerplateLine(text)) score -= 0.45f
        if (maxBottom > 0f && line.boundingBoxBottom >= maxBottom * 0.68f) score -= 0.18f

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
        return lower.contains("summe") ||
            lower.contains("total") ||
            lower.contains("betrag") ||
            lower.contains("amount")
    }

    private fun parseAmount(line: String): Double? {
        val regex = Regex("""([0-9]{1,5}[.,][0-9]{2})""")
        val match = regex.find(line) ?: return null
        val normalized = match.value.replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    private fun parseDate(line: String): LocalDate? {
        val match = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})\b""").find(line) ?: return null
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
        val merchantSuffixes = listOf("gmbh", "ag", "kg", "llc", "ltd", "inc", "sarl", "market", "store")
        val merchantStopwords = listOf("summe", "total", "betrag", "mwst", "ust", "tax", "cash", "card", "change")
        val locationNoise = setOf("ulm", "berlin", "munchen", "münchen", "stuttgart", "hamburg", "koln", "köln")
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
