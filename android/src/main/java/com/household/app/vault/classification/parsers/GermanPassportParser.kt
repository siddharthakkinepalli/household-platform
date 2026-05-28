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
 * Parser for German passports (Reisepass, TD3 MRZ, P<D<< prefix).
 *
 * Confidence tiers:
 *  0.95 – "bundesrepublik deutschland" anchor AND hasMrzTd3
 *  0.85 – "reisepass" anchor AND hasMrzTd3
 *  0.50 – "reisepass" anchor (no MRZ detected yet)
 *  0.0  – none of the above
 */
object GermanPassportParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.DE
    override val docType: DocumentDocType = DocumentDocType.DE_PASSPORT

    // MRZ line 2: docnum(9) + check(1) + D<<(3) + dob(6) + check(1) + sex(1) + expiry(6) + …
    private val RE_MRZ_L2 = Regex(
        """[A-Z0-9<]{9}[0-9][A-Z<]{3}([0-9]{6})[0-9][MF<]([0-9]{6})"""
    )

    // German passport MRZ line 1: P<D<< followed by SURNAME<<GIVENNAMES
    private val RE_MRZ_L1 = Regex("""P<D<<([A-Z<]{39,44})""")

    // German 9-char document number: alphanumeric, appears in visual zone
    private val RE_DOC_NUM = Regex("""[A-Z0-9]{9}""")

    // "Gültig bis" date (German: valid until) — various common formats
    // handled by DocumentNormalizer.parseDate()

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasBRD      = signals.anchorKeywords.contains("bundesrepublik deutschland")
        val hasReisepass = signals.anchorKeywords.contains("reisepass")
        val hasMrz      = signals.hasMrzTd3
        return when {
            hasBRD      && hasMrz -> 0.95f
            hasReisepass && hasMrz -> 0.85f
            hasReisepass           -> 0.50f
            else                   -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        var partial = false

        // ── 1. MRZ line 2 → DOB + Expiry ─────────────────────────────────────
        val mrzL2Match = signals.upperLines
            .mapNotNull { RE_MRZ_L2.find(it) }
            .firstOrNull()

        var dobExtracted    = false
        var expiryExtracted = false

        if (mrzL2Match != null) {
            val dobRaw    = mrzL2Match.groupValues[1]
            val expiryRaw = mrzL2Match.groupValues[2]

            val dob = DocumentNormalizer.parseMrzDate(dobRaw)
            if (dob != null) {
                entities += ExtractedEntity(
                    type            = EntityType.DATE_OF_BIRTH,
                    rawValue        = dobRaw,
                    normalizedValue = dob.format(ISO),
                    confidence      = 0.9f,
                    sourceContext   = mrzL2Match.value
                )
                dobExtracted = true
            }

            val expiry = DocumentNormalizer.parseMrzDate(expiryRaw)
            if (expiry != null) {
                entities += ExtractedEntity(
                    type            = EntityType.EXPIRY_DATE,
                    rawValue        = expiryRaw,
                    normalizedValue = expiry.format(ISO),
                    confidence      = 0.9f,
                    sourceContext   = mrzL2Match.value
                )
                expiryExtracted = true
            }
        }

        // ── 2. MRZ line 1 → Name ─────────────────────────────────────────────
        val mrzL1Match = signals.upperLines
            .mapNotNull { RE_MRZ_L1.find(it) }
            .firstOrNull()

        var nameExtracted = false
        if (mrzL1Match != null) {
            val nameRaw    = mrzL1Match.groupValues[1]
            val normalized = DocumentNormalizer.normalizeName(nameRaw)
            val parts = nameRaw.split("<<")

            if (parts.size >= 2) {
                val surname  = DocumentNormalizer.normalizeName(parts[0])
                val givenRaw = parts.drop(1).joinToString(" ").replace('<', ' ').trim()
                val given    = DocumentNormalizer.normalizeName(givenRaw)

                entities += ExtractedEntity(
                    type            = EntityType.SURNAME,
                    rawValue        = parts[0],
                    normalizedValue = surname,
                    confidence      = 0.9f,
                    sourceContext   = mrzL1Match.value
                )
                entities += ExtractedEntity(
                    type            = EntityType.GIVEN_NAMES,
                    rawValue        = givenRaw,
                    normalizedValue = given,
                    confidence      = 0.9f,
                    sourceContext   = mrzL1Match.value
                )
            }

            entities += ExtractedEntity(
                type            = EntityType.FULL_NAME,
                rawValue        = nameRaw,
                normalizedValue = normalized,
                confidence      = 0.9f,
                sourceContext   = mrzL1Match.value
            )
            nameExtracted = true
        }

        // ── 3. Passport number (9-char alphanumeric from visual zone) ─────────
        // Avoid matching MRZ lines; look for a standalone alphanumeric field
        var docNumExtracted = false
        for (line in signals.upperLines) {
            if (line.contains('<')) continue   // skip MRZ lines
            val match = RE_DOC_NUM.find(line) ?: continue
            // Require the token to be mostly distinct (not embedded in longer run)
            val value = match.value
            if (value.length == 9) {
                entities += ExtractedEntity(
                    type            = EntityType.PASSPORT_NUMBER,
                    rawValue        = value,
                    normalizedValue = DocumentNormalizer.normalizeId(value),
                    confidence      = 0.7f,
                    sourceContext   = line
                )
                docNumExtracted = true
                break
            }
        }

        // ── 4. Expiry fallback: "Gültig bis" keyword ──────────────────────────
        if (!expiryExtracted) {
            val expiryLineIdx = signals.lines.indexOfFirst { it.contains("gültig bis") || it.contains("gultig bis") }
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
        }

        // ── 5. Nationality ────────────────────────────────────────────────────
        entities += ExtractedEntity(
            type            = EntityType.NATIONALITY,
            rawValue        = "DEUTSCH",
            normalizedValue = "German",
            confidence      = 0.9f
        )

        partial = !nameExtracted || !dobExtracted || !expiryExtracted || !docNumExtracted

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = partial
        )
    }
}
