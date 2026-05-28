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
 * Parser for German / EU Driving Licences (Führerschein).
 *
 * Confidence tiers:
 *  0.95 – "führerschein" in anchorKeywords
 *  0.85 – lines contain "fahrerlaubnis"
 *  0.60 – lines contain EU driving licence category codes AND hasMrzTd3
 *  0.0  – none of the above
 */
object GermanDrivingLicenceParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.DE
    override val docType: DocumentDocType = DocumentDocType.DE_DRIVING_LICENCE

    // EU DL alphanumeric document number (5–12 chars, appears near label "1.")
    private val RE_DL_NUM = Regex("""[A-Z0-9]{5,12}""")

    // EU DL licence category codes (standalone tokens)
    private val RE_EU_CATEGORY = Regex("""\b(AM|A1|A2|A|B1|B|BE|C1|C1E|CE|C|D1|D1E|DE|D)\b""")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasFuhrerschein   = signals.anchorKeywords.contains("führerschein")
        val hasFahrerlaubnis  = signals.lines.any { it.contains("fahrerlaubnis") }
        val hasEuCategories   = signals.lines.any { RE_EU_CATEGORY.containsMatchIn(it) }
        val hasMrz            = signals.hasMrzTd3
        return when {
            hasFuhrerschein                    -> 0.95f
            hasFahrerlaubnis                   -> 0.85f
            hasEuCategories && hasMrz          -> 0.60f
            else                               -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()

        // ── 1. DRIVING_LICENCE_NUMBER ─────────────────────────────────────────
        // EU DL field "1." contains the licence number
        var dlExtracted = false
        val dlKeywords = listOf("führerschein-nr", "fahrerlaubnis-nr", "1.")
        val dlLineIdx = signals.lines.indexOfFirst { line ->
            dlKeywords.any { kw -> line.startsWith(kw) || line.contains("führerschein-nr") || line.contains("fahrerlaubnis-nr") }
        }
        if (dlLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, dlLineIdx)
            val match = RE_DL_NUM.find(raw)
            val value = match?.value ?: raw.trim()
            if (value.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.DRIVING_LICENCE_NUMBER,
                    rawValue        = value,
                    normalizedValue = DocumentNormalizer.normalizeId(value),
                    confidence      = 0.75f,
                    sourceContext   = signals.upperLines.getOrNull(dlLineIdx) ?: ""
                )
                dlExtracted = true
            }
        }
        // Fallback: scan upperLines for alphanumeric DL-like token near "1." field label
        if (!dlExtracted) {
            val fieldOneLine = signals.upperLines.firstOrNull { it.trimStart().startsWith("1.") }
            if (fieldOneLine != null) {
                val afterDot = fieldOneLine.substringAfter("1.").trim()
                val match = RE_DL_NUM.find(afterDot)
                if (match != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.DRIVING_LICENCE_NUMBER,
                        rawValue        = match.value,
                        normalizedValue = DocumentNormalizer.normalizeId(match.value),
                        confidence      = 0.75f,
                        sourceContext   = fieldOneLine
                    )
                    dlExtracted = true
                }
            }
        }

        // ── 2. FULL_NAME ──────────────────────────────────────────────────────
        // EU DL field "2." = surname, "3." = given name; fallback to "name" keyword
        var nameExtracted = false

        val surnameLineIdx = signals.upperLines.indexOfFirst { it.trimStart().startsWith("2.") }
        val givenLineIdx   = signals.upperLines.indexOfFirst { it.trimStart().startsWith("3.") }

        var surnameRaw = ""
        var givenRaw   = ""

        if (surnameLineIdx >= 0) {
            surnameRaw = signals.upperLines[surnameLineIdx].substringAfter("2.").trim()
                .ifBlank { signals.upperLines.getOrNull(surnameLineIdx + 1)?.trim() ?: "" }
        }
        if (givenLineIdx >= 0) {
            givenRaw = signals.upperLines[givenLineIdx].substringAfter("3.").trim()
                .ifBlank { signals.upperLines.getOrNull(givenLineIdx + 1)?.trim() ?: "" }
        }

        if (surnameRaw.isNotBlank() || givenRaw.isNotBlank()) {
            val fullRaw = "$givenRaw $surnameRaw".trim()
            entities += ExtractedEntity(
                type            = EntityType.FULL_NAME,
                rawValue        = fullRaw,
                normalizedValue = DocumentNormalizer.normalizeName(fullRaw),
                confidence      = 0.8f,
                sourceContext   = "EU DL fields 2. / 3."
            )
            nameExtracted = true
        }

        if (!nameExtracted) {
            val nameLineIdx = signals.lines.indexOfFirst { it.contains("name") }
            if (nameLineIdx >= 0) {
                val raw = extractAfterKeyword(signals, nameLineIdx)
                if (raw.isNotBlank()) {
                    entities += ExtractedEntity(
                        type            = EntityType.FULL_NAME,
                        rawValue        = raw,
                        normalizedValue = DocumentNormalizer.normalizeName(raw),
                        confidence      = 0.8f,
                        sourceContext   = signals.upperLines.getOrNull(nameLineIdx) ?: ""
                    )
                    nameExtracted = true
                }
            }
        }

        // ── 3. DATE_OF_BIRTH ─────────────────────────────────────────────────
        // EU DL field "3." — but "3." is given name; DOB is field "3." in some formats
        // German keyword: "Geburtsdatum"; also check field label
        val dobKeywords = listOf("geburtsdatum", "3.")
        val dobLineIdx = signals.lines.indexOfFirst { line ->
            line.contains("geburtsdatum")
        }
        // Field "3." is used for given name on EU DLs so prefer explicit keyword
        val resolvedDobIdx = if (dobLineIdx >= 0) dobLineIdx else -1
        if (resolvedDobIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(resolvedDobIdx),
                signals.upperLines.getOrNull(resolvedDobIdx + 1)
            )
            for (cand in candidates) {
                val parsed = runCatching { DocumentNormalizer.parseDate(cand) }.getOrNull()
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.DATE_OF_BIRTH,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.8f,
                        sourceContext   = cand
                    )
                    break
                }
            }
        }

        // ── 4. ISSUE_DATE ─────────────────────────────────────────────────────
        // EU DL field "4a." or "Ausstellungsdatum"
        val issueDateLineIdx = signals.lines.indexOfFirst {
            it.contains("ausstellungsdatum") || it.trimStart().startsWith("4a.")
        }
        if (issueDateLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(issueDateLineIdx),
                signals.upperLines.getOrNull(issueDateLineIdx + 1)
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

        // ── 5. EXPIRY_DATE ────────────────────────────────────────────────────
        // EU DL field "4b." or "Ablaufdatum"
        val expiryLineIdx = signals.lines.indexOfFirst {
            it.contains("ablaufdatum") || it.trimStart().startsWith("4b.")
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
                        confidence      = 0.8f,
                        sourceContext   = cand
                    )
                    break
                }
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
