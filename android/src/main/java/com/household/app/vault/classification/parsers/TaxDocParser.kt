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
 * Parser for German tax documents (Steuerbescheid, Einkommenssteuererklärung, etc.).
 *
 * Confidence tiers:
 *  0.90 – "finanzamt" in anchorKeywords
 *  0.85 – lines contain "steuernummer"
 *  0.80 – lines contain "elster"
 *  0.70 – lines contain "einkommensteuer" OR "lohnsteuer"
 *  0.0  – none of the above
 */
object TaxDocParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.DE
    override val docType: DocumentDocType = DocumentDocType.TAX_DOCUMENT

    // Steuernummer: NN/NNN/NNNNN or NNN/NNN/NNNNN
    private val RE_STEUERNUMMER = Regex("""\d{2,3}/\d{3}/\d{5}""")

    // Steuer-ID (Identifikationsnummer): exactly 11 digits starting with non-zero
    private val RE_STEUER_ID = Regex("""\b[1-9]\d{10}\b""")

    // Tax year reference (e.g. 2023)
    private val RE_TAX_YEAR = Regex("""\b(20\d{2})\b""")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasFinanzamt      = signals.anchorKeywords.contains("finanzamt")
        val hasSteuernummer   = signals.lines.any { it.contains("steuernummer") }
        val hasElster         = signals.lines.any { it.contains("elster") }
        val hasEinkommensteuer = signals.lines.any { it.contains("einkommensteuer") }
        val hasLohnsteuer     = signals.lines.any { it.contains("lohnsteuer") }
        return when {
            hasFinanzamt                         -> 0.90f
            hasSteuernummer                      -> 0.85f
            hasElster                            -> 0.80f
            hasEinkommensteuer || hasLohnsteuer  -> 0.70f
            else                                 -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()

        // ── 1. STEUERNUMMER ───────────────────────────────────────────────────
        var steuernummerExtracted = false
        val steuernummerMatch = RE_STEUERNUMMER.find(signals.fullText)
        if (steuernummerMatch != null) {
            val raw        = steuernummerMatch.value
            val normalized = raw.replace("/", "").replace(Regex("\\s+"), "")
            entities += ExtractedEntity(
                type            = EntityType.STEUERNUMMER,
                rawValue        = raw,
                normalizedValue = normalized,
                confidence      = 0.9f,
                sourceContext   = raw
            )
            steuernummerExtracted = true
        }

        // ── 2. STEUER_ID (Identifikationsnummer) ─────────────────────────────
        // Find all 11-digit candidates and exclude the one already matched as Steuernummer
        val steuernummerDigits = steuernummerMatch?.value?.replace("/", "") ?: ""
        val steuerIdMatch = RE_STEUER_ID.findAll(signals.fullText)
            .firstOrNull { it.value != steuernummerDigits }
        if (steuerIdMatch != null) {
            entities += ExtractedEntity(
                type            = EntityType.STEUER_ID,
                rawValue        = steuerIdMatch.value,
                normalizedValue = steuerIdMatch.value,
                confidence      = 0.85f,
                sourceContext   = steuerIdMatch.value
            )
        }

        // ── 3. FULL_NAME ──────────────────────────────────────────────────────
        val nameKeywords = listOf("steuerpflichtiger", "name")
        val nameLineIdx = signals.lines.indexOfFirst { line ->
            nameKeywords.any { kw -> line.contains(kw) }
        }
        if (nameLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, nameLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeName(raw),
                    confidence      = 0.6f,
                    sourceContext   = signals.upperLines.getOrNull(nameLineIdx) ?: ""
                )
            }
        }

        // ── 4. UNKNOWN_DATE (tax year reference) ─────────────────────────────
        val taxYearMatch = RE_TAX_YEAR.find(signals.fullText)
        if (taxYearMatch != null) {
            entities += ExtractedEntity(
                type            = EntityType.UNKNOWN_DATE,
                rawValue        = taxYearMatch.value,
                normalizedValue = taxYearMatch.value,
                confidence      = 0.7f,
                sourceContext   = taxYearMatch.value
            )
        }

        // ── 5. ADDRESS ────────────────────────────────────────────────────────
        val addrKeywords = listOf("anschrift", "wohnung")
        val addrLineIdx = signals.lines.indexOfFirst { line ->
            addrKeywords.any { kw -> line.contains(kw) }
        }
        if (addrLineIdx >= 0) {
            val addrLines = (addrLineIdx + 1..minOf(addrLineIdx + 3, signals.upperLines.lastIndex))
                .mapNotNull { signals.upperLines.getOrNull(it)?.trim() }
                .filter { it.isNotBlank() }
            val sameLine = extractAfterKeyword(signals, addrLineIdx)
            val allParts = if (sameLine.isNotBlank()) listOf(sameLine) + addrLines else addrLines
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

        val partial = !steuernummerExtracted

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
