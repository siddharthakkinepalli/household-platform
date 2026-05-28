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
 * Parser for Indian passports (TD3 MRZ format, P<IND prefix).
 *
 * Confidence tiers:
 *  0.95 – "republic of india" anchor AND hasMrzTd3
 *  0.85 – "republic of india" anchor AND "passport" anchor
 *  0.50 – hasMrzTd3 AND "passport" anchor (no explicit India signal)
 *  0.0  – none of the above
 */
object IndianPassportParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.IN
    override val docType: DocumentDocType = DocumentDocType.IN_PASSPORT

    // MRZ line 2: docnum(9) + check(1) + IND(3) + dob(6) + check(1) + sex(1) + expiry(6) + …
    private val RE_MRZ_L2 = Regex(
        """[A-Z0-9<]{9}[0-9][A-Z<]{3}([0-9]{6})[0-9][MF<]([0-9]{6})"""
    )

    // MRZ line 1 for Indian passport: P<IND followed by SURNAME<<GIVEN
    private val RE_MRZ_L1 = Regex("""P<IND([A-Z<]{39,44})""")

    // Passport number: one uppercase letter followed by 7 digits
    private val RE_PASSPORT_NUM = Regex("""[A-Z][0-9]{7}""")

    // Date in DD MMM YYYY or DD-MMM-YYYY printed on the visual zone
    private val RE_VISUAL_DATE = Regex(
        """(\d{1,2})[-\s/](JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[-\s/](\d{4})""",
        RegexOption.IGNORE_CASE
    )

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasIndia    = signals.anchorKeywords.contains("republic of india")
        val hasPassport = signals.anchorKeywords.contains("passport")
        val hasMrz      = signals.hasMrzTd3
        return when {
            hasIndia && hasMrz             -> 0.95f
            hasIndia && hasPassport        -> 0.85f
            hasMrz   && hasPassport        -> 0.50f
            else                           -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        var partial = false

        // OCR sometimes inserts spaces mid-word in MRZ zones (e.g. "A K K I N E P A L L I").
        // Strip intra-line spaces before MRZ matching so the fixed-position regexes don't miss.
        val mrzCandidates = signals.upperLines.map { it.replace(" ", "") }

        // ── 1. MRZ line 2 → DOB + Expiry ─────────────────────────────────────
        val mrzL2Match = mrzCandidates
            .mapNotNull { RE_MRZ_L2.find(it) }
            .firstOrNull()

        var dobExtracted   = false
        var expiryExtracted = false

        if (mrzL2Match != null) {
            val dobRaw    = mrzL2Match.groupValues[1]  // YYMMDD
            val expiryRaw = mrzL2Match.groupValues[2]  // YYMMDD

            val dob = DocumentNormalizer.parseMrzDate(dobRaw)
            if (dob != null) {
                entities += ExtractedEntity(
                    type             = EntityType.DATE_OF_BIRTH,
                    rawValue         = dobRaw,
                    normalizedValue  = dob.format(ISO),
                    confidence       = 0.9f,
                    sourceContext    = mrzL2Match.value
                )
                dobExtracted = true
            }

            val expiry = DocumentNormalizer.parseMrzDate(expiryRaw)
            if (expiry != null) {
                entities += ExtractedEntity(
                    type             = EntityType.EXPIRY_DATE,
                    rawValue         = expiryRaw,
                    normalizedValue  = expiry.format(ISO),
                    confidence       = 0.9f,
                    sourceContext    = mrzL2Match.value
                )
                expiryExtracted = true
            }
        }

        // ── 2. MRZ line 1 → Name (SURNAME << GIVEN NAMES) ───────────────────
        val mrzL1Match = mrzCandidates
            .mapNotNull { RE_MRZ_L1.find(it) }
            .firstOrNull()

        var nameExtracted = false
        if (mrzL1Match != null) {
            val nameRaw   = mrzL1Match.groupValues[1]
            val normalized = DocumentNormalizer.normalizeName(nameRaw)

            // Split surname / given names for fine-grained entities
            val parts = nameRaw.split("<<")
            if (parts.size >= 2) {
                val surname   = DocumentNormalizer.normalizeName(parts[0])
                val givenRaw  = parts.drop(1).joinToString(" ").replace('<', ' ').trim()
                val givenName = DocumentNormalizer.normalizeName(givenRaw)

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
                    normalizedValue = givenName,
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

        // ── 3. Passport number from visual zone ───────────────────────────────
        val passportNum = signals.upperLines
            .flatMap { RE_PASSPORT_NUM.findAll(it).toList() }
            .firstOrNull()

        var docNumExtracted = false
        if (passportNum != null) {
            entities += ExtractedEntity(
                type            = EntityType.PASSPORT_NUMBER,
                rawValue        = passportNum.value,
                normalizedValue = DocumentNormalizer.normalizeId(passportNum.value),
                confidence      = 0.7f,
                sourceContext   = passportNum.value
            )
            docNumExtracted = true
        }

        // ── 4. Expiry fallback: scan for "Date of Expiry" + DD MMM YYYY ──────
        if (!expiryExtracted) {
            val expiryLine = signals.lines.indexOfFirst {
                it.contains("date of expiry") || it.contains("expiry date")
            }
            if (expiryLine >= 0) {
                val candidates = listOfNotNull(
                    signals.upperLines.getOrNull(expiryLine),
                    signals.upperLines.getOrNull(expiryLine + 1)
                )
                for (cand in candidates) {
                    val m = RE_VISUAL_DATE.find(cand) ?: continue
                    val parsed = DocumentNormalizer.parseDate(m.value)
                    if (parsed != null) {
                        entities += ExtractedEntity(
                            type            = EntityType.EXPIRY_DATE,
                            rawValue        = m.value,
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

        // ── 5. DOB fallback: scan for "Date of Birth" keyword ─────────────────
        if (!dobExtracted) {
            val dobLine = signals.lines.indexOfFirst {
                it.contains("date of birth") || it.contains("dob")
            }
            if (dobLine >= 0) {
                val candidates = listOfNotNull(
                    signals.upperLines.getOrNull(dobLine),
                    signals.upperLines.getOrNull(dobLine + 1)
                )
                for (cand in candidates) {
                    val m = RE_VISUAL_DATE.find(cand) ?: continue
                    val parsed = DocumentNormalizer.parseDate(m.value)
                    if (parsed != null) {
                        entities += ExtractedEntity(
                            type            = EntityType.DATE_OF_BIRTH,
                            rawValue        = m.value,
                            normalizedValue = parsed.format(ISO),
                            confidence      = 0.7f,
                            sourceContext   = cand
                        )
                        dobExtracted = true
                        break
                    }
                }
            }
        }

        // ── 6. Issue date ─────────────────────────────────────────────────────
        val issueLine = signals.lines.indexOfFirst {
            it.contains("date of issue") || it.contains("issue date")
        }
        if (issueLine >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(issueLine),
                signals.upperLines.getOrNull(issueLine + 1)
            )
            for (cand in candidates) {
                val m = RE_VISUAL_DATE.find(cand) ?: continue
                val parsed = DocumentNormalizer.parseDate(m.value)
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.ISSUE_DATE,
                        rawValue        = m.value,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.7f,
                        sourceContext   = cand
                    )
                    break
                }
            }
        }

        // ── 7. Nationality ────────────────────────────────────────────────────
        entities += ExtractedEntity(
            type            = EntityType.NATIONALITY,
            rawValue        = "INDIAN",
            normalizedValue = "Indian",
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
