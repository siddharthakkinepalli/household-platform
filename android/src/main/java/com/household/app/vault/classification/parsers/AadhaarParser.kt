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
 * Parser for Aadhaar cards issued by UIDAI (India).
 *
 * Confidence tiers:
 *  0.95 – "uidai" anchor
 *  0.85 – "government of india" anchor AND longestNumberRun == 12 digits
 *  0.70 – longestNumberRun == 12 digits AND hasDevanagari
 *  0.0  – none of the above
 */
object AadhaarParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.IN
    override val docType: DocumentDocType = DocumentDocType.AADHAAR

    // Aadhaar: 4 digits, optional space, 4 digits, optional space, 4 digits
    private val RE_AADHAAR = Regex("""\d{4}\s?\d{4}\s?\d{4}""")

    // Gender keywords (English + Hindi)
    private val MALE_TOKENS   = setOf("male", "पुरुष", "m")
    private val FEMALE_TOKENS = setOf("female", "महिला", "f")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasUidai   = signals.anchorKeywords.contains("uidai")
        val hasGovIn   = signals.anchorKeywords.contains("government of india")
        val is12Digits = signals.longestNumberRun.length == 12
        return when {
            hasUidai                        -> 0.95f
            hasGovIn && is12Digits          -> 0.85f
            is12Digits && signals.hasDevanagari -> 0.70f
            else                            -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        var partial = false

        // ── 1. Aadhaar number ─────────────────────────────────────────────────
        val aadhaarMatch = RE_AADHAAR.find(signals.fullText)
        var aadhaarExtracted = false
        if (aadhaarMatch != null) {
            val normalized = DocumentNormalizer.normalizeAadhaar(aadhaarMatch.value)
            entities += ExtractedEntity(
                type            = EntityType.AADHAAR_NUMBER,
                rawValue        = aadhaarMatch.value,
                normalizedValue = normalized ?: aadhaarMatch.value,
                confidence      = if (normalized != null) 0.9f else 0.5f,
                sourceContext   = aadhaarMatch.value
            )
            aadhaarExtracted = normalized != null
        }

        // ── 2. Full name ──────────────────────────────────────────────────────
        // Look for "Name" or "नाम" keyword; take the line immediately before or after
        var nameExtracted = false
        val nameKeywords = listOf("name", "नाम")
        val nameLineIdx = signals.lines.indexOfFirst { line ->
            nameKeywords.any { kw -> line.contains(kw) }
        }
        if (nameLineIdx >= 0) {
            // Prefer the line after the keyword; if the keyword and name are on the same line,
            // extract the portion after the colon/keyword
            val sameLine = signals.upperLines.getOrNull(nameLineIdx) ?: ""
            val afterColon = sameLine.substringAfter(":").trim()
                .ifBlank { sameLine.substringAfterLast("Name").trim().trimStart(':').trim() }
                .ifBlank { sameLine.substringAfterLast("नाम").trim().trimStart(':').trim() }

            val nameCandidates = listOf(
                afterColon,
                signals.upperLines.getOrNull(nameLineIdx + 1) ?: ""
            )
            val nameRaw = nameCandidates.firstOrNull { it.isNotBlank() && it.length > 2 } ?: ""
            if (nameRaw.isNotBlank()) {
                val normalized = DocumentNormalizer.normalizeName(nameRaw)
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = nameRaw,
                    normalizedValue = normalized,
                    confidence      = 0.7f,
                    sourceContext   = sameLine
                )
                nameExtracted = true
            }
        }

        // Fallback: line just before the Aadhaar number if name not found yet
        if (!nameExtracted && aadhaarMatch != null) {
            val aadhaarLineIdx = signals.upperLines.indexOfFirst {
                RE_AADHAAR.containsMatchIn(it)
            }
            if (aadhaarLineIdx > 0) {
                val candidate = signals.upperLines[aadhaarLineIdx - 1].trim()
                // Simple heuristic: name line is letters-only, no digits
                if (candidate.isNotBlank() && candidate.none { it.isDigit() }) {
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
        }

        // ── 3. Date of birth ──────────────────────────────────────────────────
        var dobExtracted = false
        val dobKeywords = listOf("dob", "date of birth", "जन्म तिथि", "जन्मतिथि")
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

        // ── 4. Gender ─────────────────────────────────────────────────────────
        var genderExtracted = false
        for (line in signals.lines) {
            val tokens = line.split(Regex("\\s+|[/|,]")).map { it.trim() }
            val genderToken = tokens.firstOrNull { tok ->
                MALE_TOKENS.any { it.equals(tok, ignoreCase = true) } ||
                FEMALE_TOKENS.any { it.equals(tok, ignoreCase = true) }
            }
            if (genderToken != null) {
                val isMale = MALE_TOKENS.any { it.equals(genderToken, ignoreCase = true) }
                val normalized = if (isMale) "MALE" else "FEMALE"
                entities += ExtractedEntity(
                    type            = EntityType.GENDER,
                    rawValue        = genderToken,
                    normalizedValue = normalized,
                    confidence      = 0.7f,
                    sourceContext   = line
                )
                genderExtracted = true
                break
            }
        }

        // ── 5. Address ────────────────────────────────────────────────────────
        val addressKeywords = listOf("address", "पता", "addr")
        val addrLineIdx = signals.lines.indexOfFirst { line ->
            addressKeywords.any { kw -> line.contains(kw) }
        }
        if (addrLineIdx >= 0) {
            // Collect up to 3 lines after the address keyword
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

        partial = !aadhaarExtracted || !nameExtracted || !dobExtracted || !genderExtracted

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = partial
        )
    }
}
