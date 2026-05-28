package com.household.app.vault.classification

import com.household.app.vault.classification.parsers.AadhaarParser
import com.household.app.vault.classification.parsers.AufenthaltstitelParser
import com.household.app.vault.classification.parsers.GenericFallbackParser
import com.household.app.vault.classification.parsers.GermanPassportParser
import com.household.app.vault.classification.parsers.IndianPassportParser
import com.household.app.vault.classification.parsers.InsuranceDocParser
import com.household.app.vault.classification.parsers.MeldebescheinigungParser
import com.household.app.vault.classification.parsers.PanParser
import com.household.app.vault.extraction.ExtractionResult

/**
 * Builds [ClassificationSignals] from raw OCR text and routes to the best-matching parser.
 *
 * Selection strategy:
 * 1. All registered parsers are scored via [DocumentParser.confidence].
 * 2. The highest-scoring parser with score ≥ 0.6 is selected.
 * 3. If no parser scores ≥ 0.6, [GenericFallbackParser] is used.
 */
object ParserRegistry {

    private val MRZ_TD3_LINE = Regex("""[A-Z0-9<]{9}[0-9][A-Z<]{3}[0-9]{6}[0-9][MF<][0-9]{6}""")
    private val DEVANAGARI   = Regex("""[ऀ-ॿ]""")
    private val DIGIT_RUN    = Regex("""\d{3,}""")

    // Ordered from most specific to least specific
    private val parsers: List<DocumentParser> = listOf(
        IndianPassportParser,
        AadhaarParser,
        PanParser,
        GermanPassportParser,
        AufenthaltstitelParser,
        MeldebescheinigungParser,
        InsuranceDocParser,
        GenericFallbackParser       // always last
    )

    /**
     * Builds signals and runs the full classify + extract pipeline.
     * Returns the winning parser and its extraction result.
     */
    fun process(ocrText: String): Pair<DocumentParser, ExtractionResult> {
        val signals = buildSignals(ocrText)
        val winner = selectParser(signals)
        return winner to winner.extract(signals)
    }

    fun buildSignals(ocrText: String): ClassificationSignals {
        val rawLines = ocrText.lines()
        val lines       = rawLines.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val upperLines  = rawLines.map { it.trim() }.filter { it.isNotBlank() }
        val fullLower   = ocrText.lowercase()

        val hasMrz      = upperLines.any { MRZ_TD3_LINE.containsMatchIn(it) }
        val hasDev      = DEVANAGARI.containsMatchIn(ocrText)
        val longestNum  = DIGIT_RUN.findAll(ocrText).maxByOrNull { it.value.length }?.value ?: ""

        val anchors = mutableSetOf<String>()
        val keywordsToScan = listOf(
            "republic of india", "passport", "reisepass", "bundesrepublik deutschland",
            "uidai", "government of india", "income tax", "aufenthaltstitel",
            "meldebescheinigung", "einwohnermeldeamt", "versicherung", "insurance",
            "führerschein", "driving licence", "election commission"
        )
        keywordsToScan.forEach { kw -> if (fullLower.contains(kw)) anchors.add(kw) }

        return ClassificationSignals(
            fullText       = ocrText,
            lines          = lines,
            upperLines     = upperLines,
            hasMrzTd3      = hasMrz,
            hasDevanagari  = hasDev,
            anchorKeywords = anchors,
            longestNumberRun = longestNum
        )
    }

    private fun selectParser(signals: ClassificationSignals): DocumentParser {
        val scored = parsers.map { it to it.confidence(signals) }
        val (winner, score) = scored.maxByOrNull { it.second } ?: return GenericFallbackParser
        return if (score >= 0.6f) winner else GenericFallbackParser
    }
}
