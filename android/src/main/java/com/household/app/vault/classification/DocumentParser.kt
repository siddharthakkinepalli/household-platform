package com.household.app.vault.classification

import com.household.app.vault.extraction.ExtractionResult

enum class DocumentCountry { IN, DE, ANY }

enum class DocumentDocType {
    // Indian
    IN_PASSPORT, AADHAAR, PAN, IN_DRIVING_LICENCE, VOTER_ID, OCI,
    // German
    DE_PASSPORT, AUFENTHALTSTITEL, MELDEBESCHEINIGUNG, DE_DRIVING_LICENCE,
    // Generic
    INSURANCE, TAX_DOCUMENT, RENTAL_CONTRACT, EMPLOYMENT_LETTER, UTILITY_BILL,
    // Fallback
    UNKNOWN
}

/**
 * Signals derived from OCR text that help classifiers and parsers identify a document
 * without repeating expensive text scanning.
 */
data class ClassificationSignals(
    val fullText: String,
    val lines: List<String>,           // lowercased, trimmed, non-empty
    val upperLines: List<String>,      // original case (needed for MRZ, PAN, etc.)
    val hasMrzTd3: Boolean,            // detected TD3 MRZ line pattern
    val hasDevanagari: Boolean,        // presence of Devanagari script
    val anchorKeywords: Set<String>,   // pre-scanned important keyword hits
    val longestNumberRun: String       // longest contiguous digit sequence (helps detect Aadhaar etc.)
)

/**
 * Core interface for all document parsers.
 *
 * Parsers are registered in [ParserRegistry] and selected by confidence vote.
 * Each parser is stateless and pure — no Android dependencies.
 */
interface DocumentParser {
    val country: DocumentCountry
    val docType: DocumentDocType

    /**
     * Returns a confidence score 0.0–1.0 indicating how likely this parser
     * applies to the given document signals.
     * 0.0 = definitely not applicable; 1.0 = very certain.
     */
    fun confidence(signals: ClassificationSignals): Float

    /**
     * Extracts structured entities from the document text.
     * Only called when this parser wins the confidence vote.
     */
    fun extract(signals: ClassificationSignals): ExtractionResult
}
