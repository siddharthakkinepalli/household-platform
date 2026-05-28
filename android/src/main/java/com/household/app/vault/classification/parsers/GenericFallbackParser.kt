package com.household.app.vault.classification.parsers

import com.household.app.vault.classification.ClassificationSignals
import com.household.app.vault.classification.DocumentCountry
import com.household.app.vault.classification.DocumentDocType
import com.household.app.vault.classification.DocumentParser
import com.household.app.vault.extraction.ExtractedEntity
import com.household.app.vault.extraction.EntityType
import com.household.app.vault.extraction.ExtractionResult
import com.household.app.vault.normalization.DocumentNormalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Catch-all fallback parser — always applies with confidence 0.1.
 *
 * Extracts opportunistically:
 *  - Any dates found → UNKNOWN_DATE (or EXPIRY_DATE for the latest future date)
 *  - IBAN patterns → IBAN
 *  - 12-digit contiguous number → AADHAAR_NUMBER candidate (low confidence)
 *
 * Always returns partial=true since the document type is unknown.
 */
object GenericFallbackParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.ANY
    override val docType: DocumentDocType = DocumentDocType.UNKNOWN

    // IBAN: DE-specific first, then generic European
    private val RE_IBAN_DE      = Regex("""DE\d{2}[0-9A-Z]{18}""")
    private val RE_IBAN_GENERIC = Regex("""[A-Z]{2}\d{2}[0-9A-Z]{10,30}""")

    // 12 contiguous digits (potential Aadhaar)
    private val RE_12_DIGITS = Regex("""\b\d{12}\b""")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float = 0.1f

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        val today = LocalDate.now()

        // ── 1. Scan all lines for dates ───────────────────────────────────────
        val allDates = mutableListOf<Pair<String, LocalDate>>()  // raw → parsed

        for (line in signals.upperLines) {
            val parsed = DocumentNormalizer.parseDate(line)
            if (parsed != null) {
                allDates += line.trim() to parsed
            }
        }

        if (allDates.isNotEmpty()) {
            // Find the latest future date → mark it as EXPIRY_DATE
            val futureDates = allDates.filter { (_, d) -> d.isAfter(today) }
            val latestFuture = futureDates.maxByOrNull { it.second }

            for ((raw, date) in allDates) {
                val isExpiry = latestFuture != null && raw == latestFuture.first
                entities += ExtractedEntity(
                    type            = if (isExpiry) EntityType.EXPIRY_DATE else EntityType.UNKNOWN_DATE,
                    rawValue        = raw,
                    normalizedValue = date.format(ISO),
                    confidence      = if (isExpiry) 0.4f else 0.3f,
                    sourceContext   = raw
                )
            }
        }

        // ── 2. IBAN ───────────────────────────────────────────────────────────
        val ibanMatchDe = RE_IBAN_DE.find(signals.fullText)
        if (ibanMatchDe != null) {
            entities += ExtractedEntity(
                type            = EntityType.IBAN,
                rawValue        = ibanMatchDe.value,
                normalizedValue = DocumentNormalizer.normalizeId(ibanMatchDe.value),
                confidence      = 0.8f,
                sourceContext   = ibanMatchDe.value
            )
        } else {
            val ibanMatchGeneric = RE_IBAN_GENERIC.find(signals.fullText)
            if (ibanMatchGeneric != null) {
                entities += ExtractedEntity(
                    type            = EntityType.IBAN,
                    rawValue        = ibanMatchGeneric.value,
                    normalizedValue = DocumentNormalizer.normalizeId(ibanMatchGeneric.value),
                    confidence      = 0.4f,
                    sourceContext   = ibanMatchGeneric.value
                )
            }
        }

        // ── 3. 12-digit contiguous number → Aadhaar candidate ─────────────────
        val aadhaarMatch = RE_12_DIGITS.find(signals.fullText)
        if (aadhaarMatch != null) {
            val normalized = DocumentNormalizer.normalizeAadhaar(aadhaarMatch.value)
            entities += ExtractedEntity(
                type            = EntityType.AADHAAR_NUMBER,
                rawValue        = aadhaarMatch.value,
                normalizedValue = normalized ?: aadhaarMatch.value,
                confidence      = 0.3f,   // low confidence — we don't know the doc type
                sourceContext   = aadhaarMatch.value
            )
        }

        val overallConfidence = if (entities.isEmpty()) 0.1f
            else entities.map { it.confidence }.average().toFloat().coerceAtLeast(0.1f)

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = true   // always partial: doc type unknown
        )
    }
}
