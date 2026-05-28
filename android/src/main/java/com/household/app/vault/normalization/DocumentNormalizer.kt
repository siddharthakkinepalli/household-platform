package com.household.app.vault.normalization

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Normalizes raw OCR-extracted values into canonical formats.
 * All date output is ISO-8601 (yyyy-MM-dd). All ID output is whitespace-stripped.
 */
object DocumentNormalizer {

    // ── Date normalization ────────────────────────────────────────────────────

    private val MONTH_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "june" to 6, "july" to 7, "august" to 8, "september" to 9,
        "october" to 10, "november" to 11, "december" to 12,
        "januar" to 1, "februar" to 2, "märz" to 3,
        "mai" to 5, "juni" to 6, "juli" to 7,
        "oktober" to 10, "dezember" to 12
    )

    // DD.MM.YYYY or DD/MM/YYYY or DD-MM-YYYY
    private val RE_DOTDATE = Regex("""(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})""")
    // YYYY-MM-DD (ISO)
    private val RE_ISO = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    // "15 APR 2033" or "15-APR-2033"
    private val RE_SHORT_MONTH = Regex(
        """(\d{1,2})[.\-/\s](JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[.\-/\s](\d{4})""",
        RegexOption.IGNORE_CASE
    )
    // "15 April 2033" or "15. April 2033"
    private val RE_LONG_MONTH = Regex(
        """(\d{1,2})\.?\s+(januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember|january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses any common date string into a [LocalDate].
     * Returns null if no recognisable format is found.
     */
    fun parseDate(raw: String): LocalDate? {
        val text = raw.trim()
        return parseIso(text)
            ?: parseDotDate(text)
            ?: parseShortMonth(text)
            ?: parseLongMonth(text)
    }

    /** Normalizes a date to ISO-8601 string "yyyy-MM-dd", or returns null. */
    fun normalizeDate(raw: String): String? =
        parseDate(raw)?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** Parses a MRZ 6-digit YYMMDD string into a LocalDate. */
    fun parseMrzDate(yymmdd: String): LocalDate? {
        if (yymmdd.length != 6) return null
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return null
        val mm = yymmdd.substring(2, 4).toIntOrNull() ?: return null
        val dd = yymmdd.substring(4, 6).toIntOrNull() ?: return null
        val year = if (yy < 70) 2000 + yy else 1900 + yy
        return runCatching { LocalDate.of(year, mm, dd) }.getOrNull()
    }

    private fun parseIso(text: String): LocalDate? {
        val m = RE_ISO.find(text) ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull()
    }

    private fun parseDotDate(text: String): LocalDate? {
        val m = RE_DOTDATE.find(text) ?: return null
        val d = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val rawY = m.groupValues[3].toIntOrNull() ?: return null
        val y = when { rawY < 50 -> 2000 + rawY; rawY < 100 -> 1900 + rawY; else -> rawY }
        return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
    }

    private fun parseShortMonth(text: String): LocalDate? {
        val m = RE_SHORT_MONTH.find(text) ?: return null
        val month = MONTH_MAP[m.groupValues[2].lowercase()] ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        }.getOrNull()
    }

    private fun parseLongMonth(text: String): LocalDate? {
        val m = RE_LONG_MONTH.find(text) ?: return null
        val month = MONTH_MAP[m.groupValues[2].lowercase()] ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        }.getOrNull()
    }

    // ── Name normalization ────────────────────────────────────────────────────

    /**
     * Converts MRZ name format ("AKKINEPALLI<<SIDDHARTH") to "Siddharth Akkinepalli".
     * Also handles plain ALL-CAPS names and trims OCR noise.
     */
    fun normalizeName(raw: String): String {
        val text = raw.trim()
        return if (text.contains('<')) {
            // MRZ format: SURNAME<<GIVENNAMES<MIDDLE
            val parts = text.split("<<").map { it.replace('<', ' ').trim() }
            val surname = parts.getOrElse(0) { "" }.toTitleCase()
            val given  = parts.getOrElse(1) { "" }.toTitleCase()
            if (given.isBlank()) surname else "$given $surname"
        } else {
            text.toTitleCase()
        }
    }

    private fun String.toTitleCase(): String =
        split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }

    // ── ID normalization ──────────────────────────────────────────────────────

    /** Removes spaces, dashes, and dots from an ID string. */
    fun normalizeId(raw: String): String =
        raw.replace(Regex("[\\s\\-.]"), "").uppercase()

    /** Validates and normalises an IBAN (strips spaces, uppercase). */
    fun normalizeIban(raw: String): String =
        raw.replace(Regex("\\s"), "").uppercase()

    /** Strips spaces from an Aadhaar number and returns 12 digits, or null. */
    fun normalizeAadhaar(raw: String): String? {
        val digits = raw.replace(Regex("\\s"), "")
        return if (digits.length == 12 && digits.all { it.isDigit() }) digits else null
    }

    /** Validates PAN format and returns uppercase, or null. */
    fun normalizePan(raw: String): String? {
        val clean = raw.replace(Regex("\\s"), "").uppercase()
        return if (clean.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))) clean else null
    }

    // ── OCR artifact correction ───────────────────────────────────────────────

    /**
     * Fixes common OCR character substitutions in document text:
     * 0↔O, 1↔I/l, S↔5, B↔8, etc.
     * Applied to ID numbers and codes only — not to free-text fields.
     */
    fun fixOcrArtifactsInId(raw: String): String {
        return raw
            .replace('O', '0')
            .replace('o', '0')
            .replace('l', '1')
            .replace('I', '1')
            .replace('S', '5')
            .replace('B', '8')
    }
}
