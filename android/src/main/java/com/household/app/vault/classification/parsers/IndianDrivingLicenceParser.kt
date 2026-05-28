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
 * Parser for Indian Driving Licences issued by State Transport Authorities.
 *
 * Confidence tiers:
 *  0.90 – lines contain "driving licence" AND (lines contain "india" OR "transport")
 *  0.70 – lines contain "driving licence"
 *  0.60 – DL number pattern found in upperLines
 *  0.0  – none of the above
 */
object IndianDrivingLicenceParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.IN
    override val docType: DocumentDocType = DocumentDocType.IN_DRIVING_LICENCE

    // DL number: 2 uppercase letters + 11–13 digits (with optional spaces)
    private val RE_DL_NUM = Regex("""[A-Z]{2}[0-9\s]{11,13}""")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasDrivingLicence = signals.lines.any { it.contains("driving licence") }
        val hasIndia          = signals.lines.any { it.contains("india") }
        val hasTransport      = signals.lines.any { it.contains("transport") }
        val hasDlPattern      = signals.upperLines.any { RE_DL_NUM.containsMatchIn(it) }
        return when {
            hasDrivingLicence && (hasIndia || hasTransport) -> 0.90f
            hasDrivingLicence                               -> 0.70f
            hasDlPattern                                    -> 0.60f
            else                                            -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()

        // ── 1. DRIVING_LICENCE_NUMBER ─────────────────────────────────────────
        var dlExtracted = false
        for (line in signals.upperLines) {
            val match = RE_DL_NUM.find(line)
            if (match != null) {
                val rawValue = match.value
                val normalized = rawValue.replace(Regex("\\s+"), "")
                entities += ExtractedEntity(
                    type            = EntityType.DRIVING_LICENCE_NUMBER,
                    rawValue        = rawValue,
                    normalizedValue = normalized,
                    confidence      = 0.85f,
                    sourceContext   = line
                )
                dlExtracted = true
                break
            }
        }

        // ── 2. FULL_NAME ──────────────────────────────────────────────────────
        var nameExtracted = false
        val nameLineIdx = signals.lines.indexOfFirst { it.contains("name") }
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
        val dobKeywords = listOf("dob", "date of birth")
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

        // ── 4. EXPIRY_DATE ────────────────────────────────────────────────────
        var expiryExtracted = false
        val expiryKeywords = listOf("valid till", "validity", "date of expiry")
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
                        confidence      = 0.75f,
                        sourceContext   = cand
                    )
                    expiryExtracted = true
                    break
                }
            }
        }

        // ── 5. ISSUE_DATE ─────────────────────────────────────────────────────
        val issueKeywords = listOf("issue date", "date of issue")
        val issueLineIdx = signals.lines.indexOfFirst { line ->
            issueKeywords.any { kw -> line.contains(kw) }
        }
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

        // ── 6. ADDRESS ────────────────────────────────────────────────────────
        val addrLineIdx = signals.lines.indexOfFirst { it.contains("address") }
        if (addrLineIdx >= 0) {
            val addrLines = (addrLineIdx + 1..minOf(addrLineIdx + 3, signals.upperLines.lastIndex))
                .mapNotNull { signals.upperLines.getOrNull(it)?.trim() }
                .filter { it.isNotBlank() }
            if (addrLines.isNotEmpty()) {
                val addressRaw = addrLines.joinToString(", ")
                entities += ExtractedEntity(
                    type            = EntityType.ADDRESS,
                    rawValue        = addressRaw,
                    normalizedValue = addressRaw,
                    confidence      = 0.5f,
                    sourceContext   = signals.upperLines.getOrNull(addrLineIdx) ?: ""
                )
            }
        }

        val partial = !dlExtracted || !nameExtracted

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
