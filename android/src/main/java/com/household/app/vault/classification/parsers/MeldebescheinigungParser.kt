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
 * Parser for German Meldebescheinigung (registration certificate).
 *
 * Confidence tiers:
 *  0.95 – "meldebescheinigung" anchor
 *  0.80 – "einwohnermeldeamt" anchor
 *  0.0  – none of the above
 */
object MeldebescheinigungParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.DE
    override val docType: DocumentDocType = DocumentDocType.MELDEBESCHEINIGUNG

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasMelde  = signals.anchorKeywords.contains("meldebescheinigung")
        val hasEinwoh = signals.anchorKeywords.contains("einwohnermeldeamt")
        return when {
            hasMelde  -> 0.95f
            hasEinwoh -> 0.80f
            else      -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        var partial = false

        // ── 1. Full name ──────────────────────────────────────────────────────
        // Look for "Name", "Vor- und Familienname", "Familienname", "Vorname"
        var nameExtracted = false

        val fullNameKeywords = listOf("vor- und familienname", "familienname und vorname", "name und vorname")
        val fullNameLineIdx = signals.lines.indexOfFirst { line ->
            fullNameKeywords.any { kw -> line.contains(kw) }
        }

        if (fullNameLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, fullNameLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeName(raw),
                    confidence      = 0.7f,
                    sourceContext   = "Vor- und Familienname"
                )
                nameExtracted = true
            }
        }

        // Fallback: separate Familienname + Vorname fields
        if (!nameExtracted) {
            val surnameLineIdx = signals.lines.indexOfFirst { it.contains("familienname") }
            val givenLineIdx   = signals.lines.indexOfFirst { it.contains("vorname") && !it.contains("familienname") }

            var surnameRaw = if (surnameLineIdx >= 0) extractAfterKeyword(signals, surnameLineIdx) else ""
            var givenRaw   = if (givenLineIdx >= 0)   extractAfterKeyword(signals, givenLineIdx)   else ""

            if (surnameRaw.isNotBlank() || givenRaw.isNotBlank()) {
                if (surnameRaw.isNotBlank()) {
                    entities += ExtractedEntity(
                        type            = EntityType.SURNAME,
                        rawValue        = surnameRaw,
                        normalizedValue = DocumentNormalizer.normalizeName(surnameRaw),
                        confidence      = 0.7f,
                        sourceContext   = "Familienname"
                    )
                }
                if (givenRaw.isNotBlank()) {
                    entities += ExtractedEntity(
                        type            = EntityType.GIVEN_NAMES,
                        rawValue        = givenRaw,
                        normalizedValue = DocumentNormalizer.normalizeName(givenRaw),
                        confidence      = 0.7f,
                        sourceContext   = "Vorname"
                    )
                }
                val fullRaw = "$givenRaw $surnameRaw".trim()
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = fullRaw,
                    normalizedValue = DocumentNormalizer.normalizeName(fullRaw),
                    confidence      = 0.7f
                )
                nameExtracted = true
            }
        }

        // Last resort: generic "name" keyword
        if (!nameExtracted) {
            val nameLineIdx = signals.lines.indexOfFirst { it == "name" || it.startsWith("name:") }
            if (nameLineIdx >= 0) {
                val raw = extractAfterKeyword(signals, nameLineIdx)
                if (raw.isNotBlank()) {
                    entities += ExtractedEntity(
                        type            = EntityType.FULL_NAME,
                        rawValue        = raw,
                        normalizedValue = DocumentNormalizer.normalizeName(raw),
                        confidence      = 0.5f
                    )
                    nameExtracted = true
                }
            }
        }

        // ── 2. Address (Anschrift / Wohnung / Hauptwohnung) ───────────────────
        val addressKeywords = listOf("hauptwohnung", "wohnanschrift", "anschrift", "wohnung")
        val addrLineIdx = signals.lines.indexOfFirst { line ->
            addressKeywords.any { kw -> line.contains(kw) }
        }
        if (addrLineIdx >= 0) {
            // Collect up to 3 lines after the keyword header
            val addrLines = (addrLineIdx + 1..minOf(addrLineIdx + 3, signals.upperLines.lastIndex))
                .mapNotNull { signals.upperLines.getOrNull(it)?.trim() }
                .filter { it.isNotBlank() }

            // Also check if value is on the same line after ":"
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

        // ── 3. Date of birth (Geburtsdatum) ──────────────────────────────────
        var dobExtracted = false
        val dobLineIdx = signals.lines.indexOfFirst { it.contains("geburtsdatum") }
        if (dobLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(dobLineIdx),
                signals.upperLines.getOrNull(dobLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = DocumentNormalizer.parseDate(cand)
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.DATE_OF_BIRTH,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.7f,
                        sourceContext   = cand
                    )
                    dobExtracted = true
                    break
                }
            }
        }

        // ── 4. Issue date (Ausstellungsdatum / Datum) ─────────────────────────
        var issueDateExtracted = false
        val issueDateKeywords = listOf("ausstellungsdatum", "ausstellungsdatum:", "datum der ausstellung")
        val issueDateLineIdx = signals.lines.indexOfFirst { line ->
            issueDateKeywords.any { kw -> line.contains(kw) }
        }

        // Also look for a standalone "datum" keyword that doesn't match other keywords
        val datumLineIdx = if (issueDateLineIdx < 0) {
            signals.lines.indexOfFirst { line ->
                (line == "datum" || line.startsWith("datum:")) &&
                !line.contains("geburts") && !line.contains("ablauf")
            }
        } else -1

        val resolvedIssueDateIdx = when {
            issueDateLineIdx >= 0 -> issueDateLineIdx
            datumLineIdx >= 0     -> datumLineIdx
            else                  -> -1
        }

        if (resolvedIssueDateIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(resolvedIssueDateIdx),
                signals.upperLines.getOrNull(resolvedIssueDateIdx + 1)
            )
            for (cand in candidates) {
                val parsed = DocumentNormalizer.parseDate(cand)
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.ISSUE_DATE,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.7f,
                        sourceContext   = cand
                    )
                    issueDateExtracted = true
                    break
                }
            }
        }

        partial = !nameExtracted || !dobExtracted || !issueDateExtracted

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
     * Returns the text after ":" on the same line, or the next non-blank line.
     */
    private fun extractAfterKeyword(signals: ClassificationSignals, lineIdx: Int): String {
        val upperLine = signals.upperLines.getOrNull(lineIdx) ?: return ""
        val afterColon = upperLine.substringAfter(":").trim()
        if (afterColon.isNotBlank()) return afterColon
        return signals.upperLines.getOrNull(lineIdx + 1)?.trim() ?: ""
    }
}
