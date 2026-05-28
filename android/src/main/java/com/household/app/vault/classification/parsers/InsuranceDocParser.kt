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
 * Parser for insurance documents (German Versicherung / English Insurance).
 *
 * Confidence tiers:
 *  0.80 – ("versicherung" OR "insurance") anchor AND any line contains "policy"/"police"/"policennummer"
 *  0.55 – "versicherung" OR "insurance" anchor (no policy keyword)
 *  0.0  – none of the above
 */
object InsuranceDocParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.ANY
    override val docType: DocumentDocType = DocumentDocType.INSURANCE

    // IBAN: DE-specific first, then generic European
    private val RE_IBAN_DE = Regex("""DE\d{2}[0-9A-Z]{18}""")
    private val RE_IBAN_GENERIC = Regex("""[A-Z]{2}\d{2}[0-9A-Z]{10,30}""")

    // Monthly cost: amount + optional € + /month or /monat
    private val RE_MONTHLY_COST = Regex(
        """(\d+[,.]?\d*)\s*€?\s*/\s*(monat|month|mo\b)""",
        RegexOption.IGNORE_CASE
    )

    private val POLICY_KEYWORDS = listOf("policy", "police", "policennummer")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasVersicherung = signals.anchorKeywords.contains("versicherung")
        val hasInsurance    = signals.anchorKeywords.contains("insurance")
        val hasInsuranceAnchor = hasVersicherung || hasInsurance

        val hasPolicyKeyword = signals.lines.any { line ->
            POLICY_KEYWORDS.any { kw -> line.contains(kw) }
        }

        return when {
            hasInsuranceAnchor && hasPolicyKeyword -> 0.80f
            hasInsuranceAnchor                     -> 0.55f
            else                                   -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        var partial = false

        // ── 1. Policy number ──────────────────────────────────────────────────
        var policyExtracted = false
        val policyKeywordsSearch = listOf(
            "policennummer", "police-nr", "policenr",
            "policy number", "policy no", "policy #",
            "versicherungsnummer", "versicherungsschein"
        )
        val policyLineIdx = signals.lines.indexOfFirst { line ->
            policyKeywordsSearch.any { kw -> line.contains(kw) }
        }
        if (policyLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, policyLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.POLICY_NUMBER,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeId(raw),
                    confidence      = 0.7f,
                    sourceContext   = signals.upperLines.getOrNull(policyLineIdx) ?: ""
                )
                policyExtracted = true
            }
        }

        // ── 2. Expiry / renewal date ──────────────────────────────────────────
        var expiryExtracted = false
        val expiryKeywords = listOf(
            "gültig bis", "gultig bis", "ablaufdatum",
            "valid until", "valid to", "expiry date", "expiry",
            "renewal date", "renewal"
        )
        val expiryLineIdx = signals.lines.indexOfFirst { line ->
            expiryKeywords.any { kw -> line.contains(kw) }
        }
        if (expiryLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(expiryLineIdx),
                signals.upperLines.getOrNull(expiryLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = DocumentNormalizer.parseDate(cand)
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.EXPIRY_DATE,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.7f,
                        sourceContext   = cand
                    )
                    expiryExtracted = true
                    break
                }
            }
        }

        // ── 3. Monthly cost ───────────────────────────────────────────────────
        val costMatch = RE_MONTHLY_COST.find(signals.fullText)
        if (costMatch != null) {
            entities += ExtractedEntity(
                type            = EntityType.MONTHLY_COST,
                rawValue        = costMatch.value,
                normalizedValue = costMatch.groupValues[1].replace(',', '.'),
                confidence      = 0.7f,
                sourceContext   = costMatch.value
            )
        }

        // ── 4. IBAN ───────────────────────────────────────────────────────────
        // Try DE-specific first for higher confidence, then generic
        val ibanMatchDe = RE_IBAN_DE.find(signals.fullText)
        if (ibanMatchDe != null) {
            entities += ExtractedEntity(
                type            = EntityType.IBAN,
                rawValue        = ibanMatchDe.value,
                normalizedValue = DocumentNormalizer.normalizeId(ibanMatchDe.value),
                confidence      = 0.9f,
                sourceContext   = ibanMatchDe.value
            )
        } else {
            val ibanMatchGeneric = RE_IBAN_GENERIC.find(signals.fullText)
            if (ibanMatchGeneric != null) {
                entities += ExtractedEntity(
                    type            = EntityType.IBAN,
                    rawValue        = ibanMatchGeneric.value,
                    normalizedValue = DocumentNormalizer.normalizeId(ibanMatchGeneric.value),
                    confidence      = 0.5f,
                    sourceContext   = ibanMatchGeneric.value
                )
            }
        }

        partial = !policyExtracted || !expiryExtracted

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = partial
        )
    }

    /**
     * Extracts the value portion from a keyword line.
     * Returns text after ":" on the same line, or the next non-blank line.
     */
    private fun extractAfterKeyword(signals: ClassificationSignals, lineIdx: Int): String {
        val upperLine = signals.upperLines.getOrNull(lineIdx) ?: return ""
        val afterColon = upperLine.substringAfter(":").trim()
        if (afterColon.isNotBlank()) return afterColon
        return signals.upperLines.getOrNull(lineIdx + 1)?.trim() ?: ""
    }
}
