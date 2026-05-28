package com.household.app.vault.classification.parsers

import com.household.app.vault.classification.ClassificationSignals
import com.household.app.vault.classification.DocumentCountry
import com.household.app.vault.classification.DocumentDocType
import com.household.app.vault.classification.DocumentParser
import com.household.app.vault.extraction.ExtractedEntity
import com.household.app.vault.extraction.EntityType
import com.household.app.vault.extraction.ExtractionResult
import com.household.app.vault.normalization.DocumentNormalizer
import java.time.format.DateTimeFormatter

/**
 * Parser for German rental contracts (Mietvertrag).
 *
 * Confidence tiers:
 *  0.95 – lines contain "mietvertrag"
 *  0.85 – lines contain "vermieter" AND lines contain "mieter"
 *  0.70 – lines contain "kaltmiete" OR "warmmiete"
 *  0.0  – none of the above
 */
object RentalContractParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.DE
    override val docType: DocumentDocType = DocumentDocType.RENTAL_CONTRACT

    // Monthly cost: amount + optional € + /Monat or /month
    private val RE_MONTHLY_COST = Regex(
        """(\d+[,.]?\d*)\s*€?\s*/\s*(monat|month|mo\b)""",
        RegexOption.IGNORE_CASE
    )

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasMietvertrag = signals.lines.any { it.contains("mietvertrag") }
        val hasVermieter   = signals.lines.any { it.contains("vermieter") }
        val hasMieter      = signals.lines.any { it.contains("mieter") }
        val hasKaltmiete   = signals.lines.any { it.contains("kaltmiete") }
        val hasWarmmiete   = signals.lines.any { it.contains("warmmiete") }
        return when {
            hasMietvertrag                   -> 0.95f
            hasVermieter && hasMieter        -> 0.85f
            hasKaltmiete || hasWarmmiete     -> 0.70f
            else                             -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()

        // ── 1. MONTHLY_COST ───────────────────────────────────────────────────
        val costMatch = RE_MONTHLY_COST.find(signals.fullText)
        if (costMatch != null) {
            val rawAmount  = costMatch.groupValues[1]
            val normalized = rawAmount.replace(',', '.')
            entities += ExtractedEntity(
                type            = EntityType.MONTHLY_COST,
                rawValue        = costMatch.value,
                normalizedValue = normalized,
                confidence      = 0.85f,
                sourceContext   = costMatch.value
            )
        }

        // ── 2. ISSUE_DATE (contract start date) ───────────────────────────────
        val startKeywords = listOf("mietbeginn", "vertragsbeginn", "ab dem")
        val startLineIdx = signals.lines.indexOfFirst { line ->
            startKeywords.any { kw -> line.contains(kw) }
        }
        if (startLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(startLineIdx),
                signals.upperLines.getOrNull(startLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = runCatching { DocumentNormalizer.parseDate(cand) }.getOrNull()
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.ISSUE_DATE,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.75f,
                        sourceContext   = cand
                    )
                    break
                }
            }
        }

        // ── 3. EMPLOYER_NAME (reused for landlord / Vermieter) ────────────────
        val vermieterLineIdx = signals.lines.indexOfFirst { it.contains("vermieter") }
        if (vermieterLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, vermieterLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.EMPLOYER_NAME,
                    rawValue        = raw,
                    normalizedValue = raw,
                    confidence      = 0.6f,
                    sourceContext   = signals.upperLines.getOrNull(vermieterLineIdx) ?: ""
                )
            }
        }

        // ── 4. ADDRESS (rental property) ─────────────────────────────────────
        val addrKeywords = listOf("mietobjekt", "anschrift", "wohnung")
        val addrLineIdx = signals.lines.indexOfFirst { line ->
            addrKeywords.any { kw -> line.contains(kw) }
        }
        if (addrLineIdx >= 0) {
            val addrLines = (addrLineIdx + 1..minOf(addrLineIdx + 3, signals.upperLines.lastIndex))
                .mapNotNull { signals.upperLines.getOrNull(it)?.trim() }
                .filter { it.isNotBlank() }
            val sameLine = extractAfterKeyword(signals, addrLineIdx)
            val allParts = if (sameLine.isNotBlank()) listOf(sameLine) + addrLines else addrLines
            if (allParts.isNotEmpty()) {
                val addressRaw = allParts.take(3).joinToString(", ")
                entities += ExtractedEntity(
                    type            = EntityType.ADDRESS,
                    rawValue        = addressRaw,
                    normalizedValue = addressRaw,
                    confidence      = 0.7f,
                    sourceContext   = signals.upperLines.getOrNull(addrLineIdx) ?: ""
                )
            }
        }

        // ── 5. POLICY_NUMBER (contract reference number) ──────────────────────
        val refKeywords = listOf("vertragsnummer", "nr.")
        val refLineIdx = signals.lines.indexOfFirst { line ->
            refKeywords.any { kw -> line.contains(kw) }
        }
        if (refLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, refLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.POLICY_NUMBER,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeId(raw),
                    confidence      = 0.6f,
                    sourceContext   = signals.upperLines.getOrNull(refLineIdx) ?: ""
                )
            }
        }

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = false
        )
    }

    /**
     * Returns text after ":" on the same line, or the next non-blank line.
     */
    private fun extractAfterKeyword(signals: ClassificationSignals, lineIdx: Int): String {
        val upperLine = signals.upperLines.getOrNull(lineIdx) ?: return ""
        val afterColon = upperLine.substringAfter(":").trim()
        if (afterColon.isNotBlank()) return afterColon
        return signals.upperLines.getOrNull(lineIdx + 1)?.trim() ?: ""
    }
}
