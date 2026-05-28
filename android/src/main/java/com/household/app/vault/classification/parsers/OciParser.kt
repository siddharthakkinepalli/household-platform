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
 * Parser for OCI (Overseas Citizen of India) cards.
 *
 * Confidence tiers:
 *  0.95 – "overseas citizen" in anchorKeywords
 *  0.80 – lines contain "oci" as a standalone word
 *  0.0  – none of the above
 */
object OciParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.IN
    override val docType: DocumentDocType = DocumentDocType.OCI

    // OCI number: one uppercase letter followed by 6–8 digits
    private val RE_OCI = Regex("""[A-Z][0-9]{6,8}""")

    // Standalone "oci" word boundary check
    private val RE_OCI_WORD = Regex("""\boci\b""")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasOverseasCitizen = signals.anchorKeywords.contains("overseas citizen")
        val hasOciWord = signals.lines.any { RE_OCI_WORD.containsMatchIn(it) }
        return when {
            hasOverseasCitizen -> 0.95f
            hasOciWord         -> 0.80f
            else               -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()

        // ── 1. OCI_NUMBER ─────────────────────────────────────────────────────
        var ociNumberExtracted = false
        for (line in signals.upperLines) {
            val match = RE_OCI.find(line)
            if (match != null) {
                entities += ExtractedEntity(
                    type            = EntityType.OCI_NUMBER,
                    rawValue        = match.value,
                    normalizedValue = DocumentNormalizer.normalizeId(match.value),
                    confidence      = 0.8f,
                    sourceContext   = line
                )
                ociNumberExtracted = true
                break
            }
        }

        // ── 2. FULL_NAME ──────────────────────────────────────────────────────
        var nameExtracted = false
        val nameKeywords = listOf("surname", "name")
        val nameLineIdx = signals.lines.indexOfFirst { line ->
            nameKeywords.any { kw -> line.contains(kw) }
        }
        if (nameLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, nameLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeName(raw),
                    confidence      = 0.7f,
                    sourceContext   = signals.upperLines.getOrNull(nameLineIdx) ?: ""
                )
                nameExtracted = true
            }
        }

        // ── 3. DATE_OF_BIRTH ─────────────────────────────────────────────────
        val dobKeywords = listOf("date of birth", "dob")
        val dobLineIdx = signals.lines.indexOfFirst { line ->
            dobKeywords.any { kw -> line.contains(kw) }
        }
        if (dobLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(dobLineIdx),
                signals.upperLines.getOrNull(dobLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = runCatching { DocumentNormalizer.parseDate(cand) }.getOrNull()
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.DATE_OF_BIRTH,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.7f,
                        sourceContext   = cand
                    )
                    break
                }
            }
        }

        // ── 4. NATIONALITY ────────────────────────────────────────────────────
        val natLineIdx = signals.lines.indexOfFirst { it.contains("nationality") }
        if (natLineIdx >= 0) {
            val nat = extractAfterKeyword(signals, natLineIdx)
            if (nat.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.NATIONALITY,
                    rawValue        = nat,
                    normalizedValue = DocumentNormalizer.normalizeName(nat),
                    confidence      = 0.8f,
                    sourceContext   = signals.upperLines.getOrNull(natLineIdx) ?: ""
                )
            }
        } else {
            // Default nationality for OCI card holders
            entities += ExtractedEntity(
                type            = EntityType.NATIONALITY,
                rawValue        = "Indian",
                normalizedValue = "Indian",
                confidence      = 0.8f
            )
        }

        // ── 5. EXPIRY_DATE ────────────────────────────────────────────────────
        var expiryExtracted = false
        val expiryKeywords = listOf("date of expiry", "valid until")
        val expiryLineIdx = signals.lines.indexOfFirst { line ->
            expiryKeywords.any { kw -> line.contains(kw) }
        }
        if (expiryLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(expiryLineIdx),
                signals.upperLines.getOrNull(expiryLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = runCatching { DocumentNormalizer.parseDate(cand) }.getOrNull()
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

        // ── 6. ISSUE_DATE ─────────────────────────────────────────────────────
        val issueLineIdx = signals.lines.indexOfFirst { it.contains("date of issue") }
        if (issueLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(issueLineIdx),
                signals.upperLines.getOrNull(issueLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = runCatching { DocumentNormalizer.parseDate(cand) }.getOrNull()
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.ISSUE_DATE,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.7f,
                        sourceContext   = cand
                    )
                    break
                }
            }
        }

        val partial = !ociNumberExtracted || !nameExtracted || !expiryExtracted

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = partial
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
