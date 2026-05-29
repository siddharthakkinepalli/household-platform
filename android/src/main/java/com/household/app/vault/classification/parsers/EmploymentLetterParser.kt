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
 * Parser for German employment contracts / letters (Arbeitsvertrag, Arbeitszeugnis).
 *
 * Confidence tiers:
 *  0.90 – lines contain "arbeitsvertrag"
 *  0.85 – lines contain "arbeitgeber" AND lines contain "arbeitnehmer"
 *  0.75 – lines contain "arbeitszeugnis" OR "beschäftigungsverhältnis"
 *  0.0  – none of the above
 */
object EmploymentLetterParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.DE
    override val docType: DocumentDocType = DocumentDocType.EMPLOYMENT_LETTER

    // Gross salary: amount + optional € + brutto/gross/monatlich keyword
    private val RE_GROSS_SALARY = Regex(
        """(\d+[,.]?\d*)\s*€?\s*(?:brutto|gross|monatlich)""",
        RegexOption.IGNORE_CASE
    )

    // Employer company name indicators
    private val COMPANY_SUFFIXES = listOf("gmbh", "ag", "kg", "ug", "ohg", "kgaa", "se", "gmbh & co", "inc", "ltd")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasArbeitsvertrag          = signals.lines.any { it.contains("arbeitsvertrag") }
        val hasArbeitgeber             = signals.lines.any { it.contains("arbeitgeber") }
        val hasArbeitnehmer            = signals.lines.any { it.contains("arbeitnehmer") }
        val hasArbeitszeugnis          = signals.lines.any { it.contains("arbeitszeugnis") }
        val hasBeschaeftigungsverhaltnis = signals.lines.any { it.contains("beschäftigungsverhältnis") || it.contains("beschaeftigungsverhaeltnis") }
        return when {
            hasArbeitsvertrag                          -> 0.90f
            hasArbeitgeber && hasArbeitnehmer          -> 0.85f
            hasArbeitszeugnis || hasBeschaeftigungsverhaltnis -> 0.75f
            else                                       -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()

        // ── 1. EMPLOYER_NAME ──────────────────────────────────────────────────
        // After "Arbeitgeber" keyword; look for a line with GmbH/AG/KG/UG suffix
        // or a capitalised company-name-like line
        var employerExtracted = false
        var employerLineIdx = -1
        val arbeitgeberLineIdx = signals.lines.indexOfFirst { it.contains("arbeitgeber") }
        if (arbeitgeberLineIdx >= 0) {
            // Scan next few lines for a company-name line
            val candidateRange = (arbeitgeberLineIdx..minOf(arbeitgeberLineIdx + 3, signals.upperLines.lastIndex))
            val companyLine = candidateRange
                .mapNotNull { signals.upperLines.getOrNull(it)?.trim() }
                .filter { it.isNotBlank() }
                .firstOrNull { line ->
                    val lower = line.lowercase()
                    COMPANY_SUFFIXES.any { lower.contains(it) } ||
                    (line.first().isUpperCase() && line.split(" ").count { it.first().isUpperCase() } >= 2)
                }
            if (companyLine != null) {
                entities += ExtractedEntity(
                    type            = EntityType.EMPLOYER_NAME,
                    rawValue        = companyLine,
                    normalizedValue = companyLine,
                    confidence      = 0.7f,
                    sourceContext   = "Arbeitgeber"
                )
                employerExtracted = true
                employerLineIdx = signals.upperLines.indexOf(companyLine)
            } else {
                // Fallback: take the next non-blank line after the keyword
                val raw = extractAfterKeyword(signals, arbeitgeberLineIdx)
                if (raw.isNotBlank()) {
                    entities += ExtractedEntity(
                        type            = EntityType.EMPLOYER_NAME,
                        rawValue        = raw,
                        normalizedValue = raw,
                        confidence      = 0.7f,
                        sourceContext   = signals.upperLines.getOrNull(arbeitgeberLineIdx) ?: ""
                    )
                    employerExtracted = true
                }
            }
        }

        // ── 2. FULL_NAME (employee / Arbeitnehmer) ────────────────────────────
        val arbeitnehmerLineIdx = signals.lines.indexOfFirst { it.contains("arbeitnehmer") }
        if (arbeitnehmerLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, arbeitnehmerLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeName(raw),
                    confidence      = 0.7f,
                    sourceContext   = signals.upperLines.getOrNull(arbeitnehmerLineIdx) ?: ""
                )
            }
        }

        // ── 3. ISSUE_DATE (contract start date) ───────────────────────────────
        val startKeywords = listOf("beginn des arbeitsverhältnisses", "beginn des arbeitsverhaltnisses", "ab dem", "seit dem")
        val startLineIdx = signals.lines.indexOfFirst { line ->
            startKeywords.any { kw -> line.contains(kw) }
        }
        if (startLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(startLineIdx),
                signals.upperLines.getOrNull(startLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = runCatching { DocumentNormalizer.parseDate(cand) }.getOrNull()
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.ISSUE_DATE,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.75f,
                        sourceContext   = cand
                    )
                    break
                }
            }
        }

        // ── 4. GROSS_SALARY ───────────────────────────────────────────────────
        val salaryMatch = RE_GROSS_SALARY.find(signals.fullText)
        if (salaryMatch != null) {
            val rawAmount  = salaryMatch.groupValues[1]
            val normalized = rawAmount.replace(',', '.')
            entities += ExtractedEntity(
                type            = EntityType.GROSS_SALARY,
                rawValue        = salaryMatch.value,
                normalizedValue = normalized,
                confidence      = 0.80f,
                sourceContext   = salaryMatch.value
            )
        }

        // ── 5. ADDRESS (employer address) ─────────────────────────────────────
        val addrKeywords = listOf("firmensitz", "sitz")
        var addrLineIdx = signals.lines.indexOfFirst { line ->
            addrKeywords.any { kw -> line.contains(kw) }
        }
        // Fallback: use lines after EMPLOYER_NAME if no explicit address keyword
        if (addrLineIdx < 0 && employerLineIdx >= 0) {
            addrLineIdx = employerLineIdx
        }
        if (addrLineIdx >= 0) {
            val addrLines = (addrLineIdx + 1..minOf(addrLineIdx + 3, signals.upperLines.lastIndex))
                .mapNotNull { signals.upperLines.getOrNull(it)?.trim() }
                .filter { it.isNotBlank() }
            val sameLine = extractAfterKeyword(signals, addrLineIdx)
            val allParts = if (sameLine.isNotBlank() && addrKeywords.any { signals.lines.getOrNull(addrLineIdx)?.contains(it) == true })
                listOf(sameLine) + addrLines
            else
                addrLines
            if (allParts.isNotEmpty()) {
                val addressRaw = allParts.take(3).joinToString(", ")
                entities += ExtractedEntity(
                    type            = EntityType.ADDRESS,
                    rawValue        = addressRaw,
                    normalizedValue = addressRaw,
                    confidence      = 0.5f,
                    sourceContext   = signals.upperLines.getOrNull(addrLineIdx) ?: ""
                )
            }
        }

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = false
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
