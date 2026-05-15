package com.household.app.domain.services

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Pattern

/**
 * Document Intelligence - OCR Text Analyzer
 *
 * Analyzes scanned document text to extract:
 * - Expiry dates
 * - Monthly costs (currency)
 * - Contract terms
 *
 * Uses Regex patterns to find structured data in raw OCR text.
 */
object DocumentAnalyzer {

    // Date patterns (DD.MM.YYYY, DD/MM/YYYY, YYYY-MM-DD, etc.)
    private val DATE_PATTERNS = listOf(
        // German: 13.05.2026 or 13.05.26
        Pattern.compile("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})"""),
        // ISO: 2026-05-13
        Pattern.compile("""(\d{4})-(\d{2})-(\d{2})"""),
        // US: 05/13/2026
        Pattern.compile("""(\d{1,2})/(\d{1,2})/(\d{4})"""),
        // Written: 13. Mai 2026 or May 13, 2026
        Pattern.compile("""(\d{1,2})\.?\s+(\w+)\s+(\d{4})""", Pattern.CASE_INSENSITIVE)
    )

    // Currency patterns (€, EUR, $)
    private val CURRENCY_PATTERNS = listOf(
        // German: 12,99 € or 12,99EUR
        Pattern.compile("""(\d{1,3}(?:\.\d{3})*,\d{2})\s*(?:€|EUR)"""),
        // German: €12,99
        Pattern.compile("""€\s*(\d{1,3}(?:\.\d{3})*,\d{2})"""),
        // US: $12.99
        Pattern.compile("""\$\s*(\d{1,3}(?:,\d{3})*\.\d{2})"""),
        // Plain number with EUR
        Pattern.compile("""(\d+[.,]\d{2})\s*(?:€|EUR|USD|USD)""", Pattern.CASE_INSENSITIVE)
    )

    // Contract-related keywords
    private val CONTRACT_KEYWORDS = listOf(
        "vertrag", "contract", "agreement", "mitgliedschaft", "subscription",
        "laufzeit", "kündigung", "renewal", "automatisch", "jährlich",
        "monatlich", "monthly", "annual", "premium", "basic"
    )

    private val EXPIRY_KEYWORDS = listOf(
        "ablauf", "expire", "gültig bis", "valid until", "end date",
        "kündigungsfrist", "cancellation period", "last day", "fällig am"
    )

    /**
     * Analysis Result
     */
    data class DocumentAnalysis(
        val suggestedExpiryDate: LocalDate?,
        val suggestedMonthlyCost: Double?,
        val confidence: Float,  // 0.0 to 1.0
        val detectedKeywords: List<String>,
        val rawMatches: List<String>
    )

    /**
     * Analyze OCR text for document intelligence
     *
     * @param ocrText Raw OCR text from document scan
     * @param currentDate Used as reference for relative dates
     * @return DocumentAnalysis with extracted info
     */
    fun analyze(ocrText: String, currentDate: LocalDate = LocalDate.now()): DocumentAnalysis {
        val text = ocrText.trim()

        // Extract dates
        val dates = extractDates(text, currentDate)
        val expiryDate = dates.firstOrNull()  // First found date is likely expiry

        // Extract currency
        val costs = extractCosts(text)
        val monthlyCost = costs.firstOrNull()

        // Detect keywords
        val keywords = detectKeywords(text)

        // Calculate confidence based on what we found
        val confidence = calculateConfidence(expiryDate, monthlyCost, keywords)

        return DocumentAnalysis(
            suggestedExpiryDate = expiryDate,
            suggestedMonthlyCost = monthlyCost,
            confidence = confidence,
            detectedKeywords = keywords,
            rawMatches = dates.map { it.toString() } + costs.map { it.toString() }
        )
    }

    /**
     * Extract all dates from text
     */
    fun extractDates(text: String, referenceDate: LocalDate = LocalDate.now()): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()

        // German format: DD.MM.YYYY
        val germanPattern = Pattern.compile("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""")
        val germanMatcher = germanPattern.matcher(text)
        while (germanMatcher.find()) {
            try {
                val day = germanMatcher.group(1).toInt()
                val month = germanMatcher.group(2).toInt()
                val year = normalizeYear(germanMatcher.group(3).toInt())
                dates.add(LocalDate.of(year, month, day))
            } catch (_: Exception) { /* skip invalid */ }
        }

        // ISO format: YYYY-MM-DD
        val isoPattern = Pattern.compile("""(\d{4})-(\d{2})-(\d{2})""")
        val isoMatcher = isoPattern.matcher(text)
        while (isoMatcher.find()) {
            try {
                val year = isoMatcher.group(1).toInt()
                val month = isoMatcher.group(2).toInt()
                val day = isoMatcher.group(3).toInt()
                dates.add(LocalDate.of(year, month, day))
            } catch (_: Exception) { /* skip invalid */ }
        }

        return dates.distinct().sorted()
    }

    /**
     * Extract all currency amounts from text
     */
    fun extractCosts(text: String): List<Double> {
        val costs = mutableListOf<Double>()

        // German format: 12,99 € or 12,99EUR
        val germanMoney = Pattern.compile("""(\d{1,3}(?:\.\d{3})*,\d{2})""")
        val germanMatcher = germanMoney.matcher(text)
        while (germanMatcher.find()) {
            try {
                val cleaned = germanMatcher.group(1)
                    .replace(".", "")
                    .replace(",", ".")
                costs.add(cleaned.toDouble())
            } catch (_: Exception) { /* skip invalid */ }
        }

        // US format: $12.99
        val usMoney = Pattern.compile("""\$\s*([\d,]+\.\d{2})""")
        val usMatcher = usMoney.matcher(text)
        while (usMatcher.find()) {
            try {
                val cleaned = usMatcher.group(1).replace(",", "")
                costs.add(cleaned.toDouble())
            } catch (_: Exception) { /* skip invalid */ }
        }

        return costs.distinct().sorted()
    }

    /**
     * Detect contract-related keywords in text
     */
    fun detectKeywords(text: String): List<String> {
        val found = mutableListOf<String>()
        val lower = text.lowercase()

        CONTRACT_KEYWORDS.forEach { keyword ->
            if (lower.contains(keyword)) {
                found.add(keyword)
            }
        }

        EXPIRY_KEYWORDS.forEach { keyword ->
            if (lower.contains(keyword)) {
                found.add(keyword)
            }
        }

        return found.distinct()
    }

    /**
     * Calculate confidence based on extracted data
     */
    private fun calculateConfidence(
        expiryDate: LocalDate?,
        monthlyCost: Double?,
        keywords: List<String>
    ): Float {
        var score = 0.3f  // Base score

        if (expiryDate != null) score += 0.3f
        if (monthlyCost != null) score += 0.2f
        if (keywords.size >= 3) score += 0.2f

        return minOf(1.0f, score)
    }

    /**
     * Normalize 2-digit year to 4-digit
     */
    private fun normalizeYear(year: Int): Int {
        return if (year < 100) {
            if (year > 50) 1900 + year else 2000 + year
        } else year
    }

    /**
     * Calculate days until expiry
     */
    fun daysUntilExpiry(expiryDate: LocalDate, referenceDate: LocalDate = LocalDate.now()): Int {
        return java.time.temporal.ChronoUnit.DAYS.between(referenceDate, expiryDate).toInt()
    }

    /**
     * Check if document needs attention (within notice period)
     */
    fun needsAttention(expiryDate: LocalDate?, noticePeriodDays: Int = 30): Boolean {
        if (expiryDate == null) return false
        val daysLeft = daysUntilExpiry(expiryDate)
        return daysLeft in 0..noticePeriodDays
    }

    /**
     * Suggest notice period based on document type
     */
    fun suggestNoticePeriod(type: String): Int {
        return when {
            type.contains("insurance", ignoreCase = true) -> 90
            type.contains("lease", ignoreCase = true) -> 60
            type.contains("subscription", ignoreCase = true) -> 14
            else -> 30
        }
    }
}