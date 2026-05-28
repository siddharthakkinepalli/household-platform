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
 * Parser for German Aufenthaltstitel (residence permit) cards.
 *
 * Confidence tiers:
 *  0.95 – "aufenthaltstitel" anchor
 *  0.60 – "bundesrepublik deutschland" AND hasMrzTd3 AND NOT "reisepass"
 *  0.0  – none of the above
 */
object AufenthaltstitelParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.DE
    override val docType: DocumentDocType = DocumentDocType.AUFENTHALTSTITEL

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasAT   = signals.anchorKeywords.contains("aufenthaltstitel")
        val hasBRD  = signals.anchorKeywords.contains("bundesrepublik deutschland")
        val hasMrz  = signals.hasMrzTd3
        val noReisepass = !signals.anchorKeywords.contains("reisepass")
        return when {
            hasAT                          -> 0.95f
            hasBRD && hasMrz && noReisepass -> 0.60f
            else                           -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        var partial = false

        // ── 1. Full name (Familienname / Vorname) ────────────────────────────
        var nameExtracted = false

        // Try Familienname (surname) first
        val surnameLineIdx = signals.lines.indexOfFirst { line ->
            line.contains("familienname") || line.contains("nachname")
        }
        var surnameRaw = ""
        if (surnameLineIdx >= 0) {
            surnameRaw = extractAfterKeyword(signals, surnameLineIdx)
        }

        // Vorname (given name)
        val givenLineIdx = signals.lines.indexOfFirst { line ->
            line.contains("vorname") && !line.contains("familienname")
        }
        var givenRaw = ""
        if (givenLineIdx >= 0) {
            givenRaw = extractAfterKeyword(signals, givenLineIdx)
        }

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

        // Fallback: generic "name" keyword
        if (!nameExtracted) {
            val nameLineIdx = signals.lines.indexOfFirst { it.contains("name") }
            if (nameLineIdx >= 0) {
                val nameRaw = extractAfterKeyword(signals, nameLineIdx)
                if (nameRaw.isNotBlank()) {
                    entities += ExtractedEntity(
                        type            = EntityType.FULL_NAME,
                        rawValue        = nameRaw,
                        normalizedValue = DocumentNormalizer.normalizeName(nameRaw),
                        confidence      = 0.5f
                    )
                    nameExtracted = true
                }
            }
        }

        // ── 2. Date of birth (Geburtsdatum) ──────────────────────────────────
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

        // ── 3. Expiry date (Gültig bis) ───────────────────────────────────────
        var expiryExtracted = false
        val expiryLineIdx = signals.lines.indexOfFirst {
            it.contains("gültig bis") || it.contains("gultig bis") || it.contains("ablaufdatum")
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

        // ── 4. Nationality (Staatsangehörigkeit) ──────────────────────────────
        val natLineIdx = signals.lines.indexOfFirst { it.contains("staatsangehörigkeit") || it.contains("staatsangehorigkeit") }
        if (natLineIdx >= 0) {
            val nat = extractAfterKeyword(signals, natLineIdx)
            if (nat.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.NATIONALITY,
                    rawValue        = nat,
                    normalizedValue = DocumentNormalizer.normalizeName(nat),
                    confidence      = 0.7f,
                    sourceContext   = "Staatsangehörigkeit"
                )
            }
        }

        // ── 5. Document number (Ausweis-Nr / Kartennummer) ───────────────────
        var docNumExtracted = false
        val docNumKeywords = listOf("ausweis-nr", "ausweisnr", "kartennummer", "seriennummer", "dokument-nr")
        val docNumLineIdx = signals.lines.indexOfFirst { line ->
            docNumKeywords.any { kw -> line.contains(kw) }
        }
        if (docNumLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, docNumLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.DOCUMENT_NUMBER,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeId(raw),
                    confidence      = 0.7f,
                    sourceContext   = "Ausweis-Nr"
                )
                docNumExtracted = true
            }
        }

        partial = !nameExtracted || !dobExtracted || !expiryExtracted || !docNumExtracted

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
     * Tries: text after ":" on the same line, then the next non-blank line.
     */
    private fun extractAfterKeyword(signals: ClassificationSignals, lineIdx: Int): String {
        val upperLine = signals.upperLines.getOrNull(lineIdx) ?: return ""
        val afterColon = upperLine.substringAfter(":").trim()
        if (afterColon.isNotBlank()) return afterColon

        return signals.upperLines.getOrNull(lineIdx + 1)?.trim() ?: ""
    }
}
