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
 * Parser for Indian Permanent Account Number (PAN) cards.
 *
 * Confidence tiers:
 *  0.95 – PAN regex found in upperLines AND "income tax" anchor
 *  0.80 – PAN regex found in upperLines (no "income tax")
 *  0.40 – "income tax" anchor (no PAN regex visible)
 *  0.0  – none of the above
 */
object PanParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.IN
    override val docType: DocumentDocType = DocumentDocType.PAN

    // PAN format: AAAAA9999A — 5 uppercase letters, 4 digits, 1 uppercase letter
    private val RE_PAN = Regex("""[A-Z]{5}[0-9]{4}[A-Z]""")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasPan      = signals.upperLines.any { RE_PAN.containsMatchIn(it) }
        val hasIncomeTax = signals.anchorKeywords.contains("income tax")
        return when {
            hasPan && hasIncomeTax -> 0.95f
            hasPan                 -> 0.80f
            hasIncomeTax           -> 0.40f
            else                   -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        var partial = false

        // ── 1. PAN number ─────────────────────────────────────────────────────
        var panExtracted = false
        var panLineIdx = -1
        for ((idx, line) in signals.upperLines.withIndex()) {
            val match = RE_PAN.find(line)
            if (match != null) {
                val normalized = DocumentNormalizer.normalizePan(match.value)
                entities += ExtractedEntity(
                    type            = EntityType.PAN_NUMBER,
                    rawValue        = match.value,
                    normalizedValue = normalized ?: match.value,
                    confidence      = if (normalized != null) 0.9f else 0.7f,
                    sourceContext   = line
                )
                panExtracted = normalized != null
                panLineIdx = idx
                break
            }
        }

        // ── 2. Full name ──────────────────────────────────────────────────────
        // Strategy A: line immediately below a "Name" keyword
        var nameExtracted = false
        val nameKeywords = listOf("name", "नाम")
        val nameLineIdx = signals.lines.indexOfFirst { line ->
            // Match "Name" but NOT "Father's Name" to avoid confusion with strategy below
            nameKeywords.any { kw -> line.contains(kw) } &&
            !line.contains("father") && !line.contains("पिता")
        }

        if (nameLineIdx >= 0) {
            // Same-line extraction (after colon) or next line
            val sameLine = signals.upperLines.getOrNull(nameLineIdx) ?: ""
            val afterColon = sameLine.substringAfter(":").trim()
            val candidates = listOf(
                afterColon,
                signals.upperLines.getOrNull(nameLineIdx + 1) ?: ""
            )
            val nameRaw = candidates.firstOrNull { it.isNotBlank() && it.none { c -> c.isDigit() } } ?: ""
            if (nameRaw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = nameRaw,
                    normalizedValue = DocumentNormalizer.normalizeName(nameRaw),
                    confidence      = 0.7f,
                    sourceContext   = sameLine
                )
                nameExtracted = true
            }
        }

        // Strategy B: line immediately above the PAN number (common OCR layout)
        if (!nameExtracted && panLineIdx > 0) {
            val candidate = signals.upperLines.getOrNull(panLineIdx - 1)?.trim() ?: ""
            if (candidate.isNotBlank() && candidate.none { it.isDigit() } && !RE_PAN.containsMatchIn(candidate)) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = candidate,
                    normalizedValue = DocumentNormalizer.normalizeName(candidate),
                    confidence      = 0.5f,
                    sourceContext   = candidate
                )
                nameExtracted = true
            }
        }

        // ── 3. Father's name ──────────────────────────────────────────────────
        val fatherKeywords = listOf("father's name", "father name", "पिता का नाम", "पिता")
        val fatherLineIdx = signals.lines.indexOfFirst { line ->
            fatherKeywords.any { kw -> line.contains(kw) }
        }
        if (fatherLineIdx >= 0) {
            val sameLine   = signals.upperLines.getOrNull(fatherLineIdx) ?: ""
            val afterColon = sameLine.substringAfter(":").trim()
            val candidates = listOf(
                afterColon,
                signals.upperLines.getOrNull(fatherLineIdx + 1) ?: ""
            )
            val fatherRaw = candidates.firstOrNull { it.isNotBlank() && it.none { c -> c.isDigit() } } ?: ""
            if (fatherRaw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.UNKNOWN_TEXT,
                    rawValue        = fatherRaw,
                    normalizedValue = DocumentNormalizer.normalizeName(fatherRaw),
                    confidence      = 0.7f,
                    sourceContext   = "Father's Name: $fatherRaw"
                )
            }
        }

        // ── 4. Date of birth ──────────────────────────────────────────────────
        var dobExtracted = false
        val dobKeywords = listOf("date of birth", "dob", "जन्म तिथि", "जन्मतिथि")
        val dobLineIdx = signals.lines.indexOfFirst { line ->
            dobKeywords.any { kw -> line.contains(kw) }
        }
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

        partial = !panExtracted || !nameExtracted || !dobExtracted

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = partial
        )
    }
}
