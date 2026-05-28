package com.household.app.data.service

import com.household.app.domain.models.vault.VaultCategory
import com.household.app.domain.models.vault.VaultSubFolder
import java.time.LocalDate

/**
 * Pure Kotlin service — no Android deps.
 * Classifies a vault document and extracts structured metadata from OCR text.
 */
object VaultDocumentParser {

    data class ParsedDocumentMeta(
        val category: VaultCategory,
        val subFolder: VaultSubFolder,
        val suggestedTitle: String?,
        val expiryDate: LocalDate?,
        val monthlyCost: Double?
    )

    private val EXPIRY_KEYWORDS = setOf(
        "gültig bis", "gueltig bis", "gültig:", "gültigkeit",
        "gültigkeitsdatum", "gültig bis:", "gültig bis ",
        "valid until", "valid through", "valid to", "date of expiry",
        "expires", "expiry date", "expiry:", "expiration", "date of expiration",
        "ablauf", "ablaufdatum", "vertragsende", "vertragsablauf",
        "laufzeit bis", "vertragslaufzeit bis", "end of contract",
        "end date", "contract ends", "kündigungsfrist bis",
        "date d'expiration", "expiry", "gültig"
    )

    // MRZ TD3 line 2: exactly 44 chars, position 21-26 = expiry date YYMMDD.
    // Pattern captures the 9-char doc number zone, 3-char nationality, 6-char DOB, check,
    // sex (M/F/<), then the 6-char expiry we want.
    private val MRZ_LINE2 = Regex("""[A-Z0-9<]{9}[0-9][A-Z<]{3}[0-9]{6}[0-9][MF<](\d{6})""")

    // YYMMDD appearing after a <<< sequence (fallback MRZ path)
    private val DATE_MRZ = Regex("""[<]{2,}[A-Z0-9<]*?(\d{2})(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[0-1])""")

    private val DATE_GERMAN   = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""")
    private val DATE_ISO      = Regex("""(\d{4})-(\d{2})-(\d{2})""")

    // Full month names — German and English
    private val DATE_MONTH_LONG = Regex(
        """(\d{1,2})\.?\s+(januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember|january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    // Abbreviated month names — English (Indian/British passports, UK docs)
    // Matches: "15 APR 2033", "15-APR-2033", "15/APR/2033"
    private val DATE_MONTH_SHORT = Regex(
        """(\d{1,2})[.\-/\s](JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[.\-/\s](\d{4})""",
        RegexOption.IGNORE_CASE
    )

    private val MONTHLY_COST  = Regex(
        """(\d+[,.]?\d*)\s*€?\s*/\s*(monat|month|mo\b)""",
        RegexOption.IGNORE_CASE
    )

    private val MONTH_MAP = mapOf(
        "januar" to 1, "january" to 1, "jan" to 1,
        "februar" to 2, "february" to 2, "feb" to 2,
        "märz" to 3, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "mai" to 5, "may" to 5,
        "juni" to 6, "june" to 6, "jun" to 6,
        "juli" to 7, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9,
        "oktober" to 10, "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "dezember" to 12, "december" to 12, "dec" to 12
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun parse(
        ocrText: String,
        existingCategory: VaultCategory = VaultCategory.OTHER
    ): ParsedDocumentMeta {
        val lower = ocrText.lowercase()
        val lines = lower.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val isGeneric = existingCategory == VaultCategory.RECEIPT || existingCategory == VaultCategory.OTHER
        val (rawCategory, rawSubFolder) = if (isGeneric) classify(lower) else {
            existingCategory to refineSubFolder(existingCategory, lower)
        }

        // Keep actual receipts as receipts when parser finds nothing more specific
        val (category, subFolder) = if (existingCategory == VaultCategory.RECEIPT && rawCategory == VaultCategory.OTHER) {
            VaultCategory.RECEIPT to VaultSubFolder.RECEIPTS_GENERAL
        } else {
            rawCategory to rawSubFolder
        }

        return ParsedDocumentMeta(
            category    = category,
            subFolder   = subFolder,
            suggestedTitle = suggestTitle(subFolder, ocrText),
            expiryDate  = extractExpiryDate(lines, category),
            monthlyCost = extractMonthlyCost(lower)
        )
    }

    // ── Classification ────────────────────────────────────────────────────────

    private fun classify(lower: String): Pair<VaultCategory, VaultSubFolder> = when {
        // Identity
        lower.has("reisepass", "passport")
            -> VaultCategory.IDENTITY to VaultSubFolder.PASSPORT
        lower.has("personalausweis", "national id", "identity card", "ausweis") && !lower.has("kranken")
            -> VaultCategory.IDENTITY to VaultSubFolder.NATIONAL_ID
        lower.has("führerschein", "fahrerlaubnis", "driving licence", "driving license", "driver's license")
            -> VaultCategory.IDENTITY to VaultSubFolder.DRIVING_LICENCE
        lower.has("blaue karte", "blue card", "eu blue card")
            -> VaultCategory.IDENTITY to VaultSubFolder.EU_BLUE_CARD
        lower.has("niederlassungserlaubnis", "aufenthaltstitel", "residence permit", "aufenthaltserlaubnis")
            -> VaultCategory.IDENTITY to VaultSubFolder.EU_RESIDENCE
        lower.has("visum", "einreisegenehmigung") || (lower.has("visa") && !lower.has("girokonto", "kreditkarte"))
            -> VaultCategory.IDENTITY to VaultSubFolder.VISA
        lower.has("meldebescheinigung", "wohnungsgeberbestätigung", "einwohnermeldeamt") ||
            (lower.has("anmeldung") && lower.has("behörde", "amt", "bürger"))
            -> VaultCategory.IDENTITY to VaultSubFolder.REGISTRATION
        lower.has("geburtsurkunde", "birth certificate")
            -> VaultCategory.IDENTITY to VaultSubFolder.BIRTH_CERTIFICATE
        lower.has("heiratsurkunde", "marriage certificate", "eheurkunde")
            -> VaultCategory.IDENTITY to VaultSubFolder.MARRIAGE_CERTIFICATE

        // Insurance — specific before generic
        lower.has("zahnversicherung", "zahnzusatz", "dental insurance")
            -> VaultCategory.INSURANCE to VaultSubFolder.DENTAL_INSURANCE
        (lower.has("krankenversicherung", "krankenkasse", "gesundheitskarte") && !lower.has("zahn")) ||
            lower.has(" gkv", " pkv")
            -> VaultCategory.INSURANCE to VaultSubFolder.HEALTH_INSURANCE
        (lower.has("kfz", "kraftfahrzeug") && lower.has("versicherung")) ||
            lower.has("kfz-versicherung", "autoversicherung", "kfz-haftpflicht")
            -> VaultCategory.INSURANCE to VaultSubFolder.CAR_INSURANCE
        lower.has("hausratversicherung", "home contents insurance")
            -> VaultCategory.INSURANCE to VaultSubFolder.HOUSEHOLD_CONTENTS
        lower.has("lebensversicherung", "life insurance")
            -> VaultCategory.INSURANCE to VaultSubFolder.LIFE_INSURANCE
        lower.has("reiseversicherung", "reisekranken", "travel insurance")
            -> VaultCategory.INSURANCE to VaultSubFolder.TRAVEL_INSURANCE
        lower.has("haftpflichtversicherung", "haftpflicht") && !lower.has("kfz")
            -> VaultCategory.INSURANCE to VaultSubFolder.LIABILITY
        lower.has("versicherung", "insurance") && lower.has("police", "policennummer", "vertrag", "nummer")
            -> VaultCategory.INSURANCE to VaultSubFolder.OTHER_INSURANCE

        // Contracts — streaming services before generic subscription
        lower.has("netflix", "amazon prime", "disney+", "spotify", "apple tv", "hbo", "dazn", "paramount+", "sky abo")
            -> VaultCategory.CONTRACT to VaultSubFolder.STREAMING
        lower.has("fitnessstudio", "mcfit", "fitness first", "john reed", "clever fit") ||
            (lower.has("gym", "fitness") && lower.has("mitgliedschaft", "membership"))
            -> VaultCategory.CONTRACT to VaultSubFolder.GYM
        lower.has("adobe", "microsoft 365", "google workspace", "dropbox", "softwarelizenz", "software lizenz")
            -> VaultCategory.CONTRACT to VaultSubFolder.SOFTWARE_CLOUD
        lower.has("mobilfunkvertrag", "handyvertrag", "mobilvertrag") ||
            (lower.has("telekom", "vodafone", "o2", "1&1") && lower.has("vertrag", "kündigung"))
            -> VaultCategory.CONTRACT to VaultSubFolder.MOBILE_INTERNET
        lower.has("mietvertrag", "mieter", "vermieter", "rental agreement")
            -> VaultCategory.CONTRACT to VaultSubFolder.RENT_HOUSING

        // Utility bills
        lower.has("strom", "electricity", "stromrechnung", "stromanbieter")
            -> VaultCategory.UTILITY_BILL to VaultSubFolder.UTILITY_ELECTRICITY
        lower.has("erdgas", "gasrechnung") ||
            (lower.has("gas") && lower.has("rechnung", "abrechnung", "invoice") && !lower.has("auto", "kfz"))
            -> VaultCategory.UTILITY_BILL to VaultSubFolder.UTILITY_GAS
        lower.has("wasserwerk", "wasserbetriebe") ||
            (lower.has("wasser") && lower.has("rechnung", "abrechnung"))
            -> VaultCategory.UTILITY_BILL to VaultSubFolder.UTILITY_WATER
        (lower.has("telekom", "vodafone", "o2", "1&1", "dsl", "glasfaser") && lower.has("rechnung", "invoice"))
            -> VaultCategory.UTILITY_BILL to VaultSubFolder.INTERNET

        // Medical
        lower.has("impfausweis", "impfpass", "impfbescheinigung", "vaccination certificate")
            -> VaultCategory.MEDICAL to VaultSubFolder.VACCINATION
        lower.has("rezept", "prescription") && lower.has("medikament", "medication", "arzt", "apotheke")
            -> VaultCategory.MEDICAL to VaultSubFolder.PRESCRIPTIONS
        lower.has("laborergebnis", "blutbild", "laborbefund", "laborwerte", "lab result")
            -> VaultCategory.MEDICAL to VaultSubFolder.LAB_RESULTS
        lower.has("zahnarzt", "zahnklinik") && lower.has("befund", "arzt", "praxis")
            -> VaultCategory.MEDICAL to VaultSubFolder.MEDICAL_DENTAL
        lower.has("arzt", "klinik", "krankenhaus", "patient", "diagnose", "attest", "befund")
            -> VaultCategory.MEDICAL to VaultSubFolder.OTHER_MEDICAL

        // Property
        lower.has("kaution", "mietkaution", "deposit") && lower.has("miete", "wohnung", "wohnen")
            -> VaultCategory.PROPERTY to VaultSubFolder.DEPOSIT
        lower.has("übergabeprotokoll", "wohnungsübergabe")
            -> VaultCategory.PROPERTY to VaultSubFolder.HANDOVER
        lower.has("mietvertrag", "lease agreement", "mietverhältnis")
            -> VaultCategory.PROPERTY to VaultSubFolder.LEASE

        else -> VaultCategory.OTHER to VaultSubFolder.OTHER
    }

    private fun refineSubFolder(category: VaultCategory, lower: String): VaultSubFolder {
        val (detectedCategory, detectedSub) = classify(lower)
        return if (detectedCategory == category) detectedSub else VaultSubFolder.UNFILED
    }

    // ── Expiry date extraction ────────────────────────────────────────────────

    private fun extractExpiryDate(lines: List<String>, category: VaultCategory): LocalDate? {
        val today = LocalDate.now()
        val maxFuture = if (category == VaultCategory.IDENTITY) today.plusYears(15) else today.plusYears(5)
        val oneYearAgo = today.minusYears(1)

        // Primary: expiry keyword on same line or next line
        // Use ORIGINAL case text for short-month matching (APR not apr)
        val originalLines = lines  // already trimmed, lowercased for keyword check
        for (i in originalLines.indices) {
            val line = originalLines[i]
            if (EXPIRY_KEYWORDS.any { line.contains(it) }) {
                val window = if (i + 1 < originalLines.size) "$line ${originalLines[i + 1]}" else line
                parseFirstDate(window)
                    ?.takeIf { it.isAfter(oneYearAgo) && it.isBefore(maxFuture) }
                    ?.let { return it }
            }
        }

        // For identity documents: try every date format across entire text
        if (category == VaultCategory.IDENTITY) {
            val fullText = lines.joinToString("\n")

            // 1. Structured MRZ TD3 line 2 — most reliable for passports
            //    Expiry date is at a fixed offset; extract it directly
            MRZ_LINE2.findAll(fullText).mapNotNull { m ->
                val yymmdd = m.groupValues[1]
                if (yymmdd.length == 6)
                    parseMrzYYMMDD(yymmdd.substring(0, 2), yymmdd.substring(2, 4), yymmdd.substring(4, 6))
                else null
            }.filter { it.isAfter(today) && it.isBefore(maxFuture) }
                .maxOrNull()?.let { return it }

            // 2. Fallback MRZ pattern — YYMMDD after <<< sequences
            DATE_MRZ.findAll(fullText).mapNotNull { m ->
                parseMrzYYMMDD(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            }.filter { it.isAfter(today) && it.isBefore(maxFuture) }
                .maxOrNull()?.let { return it }

            // 3. All conventional dates in the doc — pick the latest future date
            //    (on a passport the expiry date is always later than the issue date)
            lines.flatMap { allDatesIn(it) }
                .filter { it.isAfter(today) && it.isBefore(maxFuture) }
                .maxOrNull()?.let { return it }
        }

        return null
    }

    private fun parseMrzYYMMDD(yy: String, mm: String, dd: String): LocalDate? {
        val year2 = yy.toIntOrNull() ?: return null
        val month = mm.toIntOrNull() ?: return null
        val day   = dd.toIntOrNull() ?: return null
        val year  = if (year2 < 70) 2000 + year2 else 1900 + year2
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun extractMonthlyCost(lower: String): Double? =
        MONTHLY_COST.find(lower)
            ?.groupValues?.get(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()

    private fun suggestTitle(subFolder: VaultSubFolder, ocrText: String): String? {
        if (subFolder != VaultSubFolder.UNFILED && subFolder != VaultSubFolder.OTHER) {
            return subFolder.label
        }
        // Fall back to first non-trivial OCR line
        return ocrText.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.length in 4..60
                    && line.any { it.isLetter() }
                    && !DATE_GERMAN.containsMatchIn(line)
                    && !DATE_ISO.containsMatchIn(line)
            }
    }

    // ── Date parsing ──────────────────────────────────────────────────────────

    private fun parseFirstDate(text: String): LocalDate? =
        parseGerman(text) ?: parseIso(text) ?: parseMonthShort(text) ?: parseMonthLong(text)

    private fun allDatesIn(text: String): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        DATE_GERMAN.findAll(text).forEach { m ->
            parseGermanParts(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { out.add(it) }
        }
        DATE_ISO.findAll(text).forEach { m ->
            runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()?.let { out.add(it) }
        }
        // "15 APR 2033" style — common on Indian/British passports
        DATE_MONTH_SHORT.findAll(text).forEach { m ->
            val month = MONTH_MAP[m.groupValues[2].lowercase()] ?: return@forEach
            runCatching {
                LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
            }.getOrNull()?.let { out.add(it) }
        }
        DATE_MONTH_LONG.findAll(text).forEach { m ->
            val month = MONTH_MAP[m.groupValues[2].lowercase()] ?: return@forEach
            runCatching {
                LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
            }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun parseGerman(text: String): LocalDate? {
        val m = DATE_GERMAN.find(text) ?: return null
        return parseGermanParts(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    private fun parseIso(text: String): LocalDate? {
        val m = DATE_ISO.find(text) ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull()
    }

    // "15 APR 2033" — Indian/British passport, abbreviated month
    private fun parseMonthShort(text: String): LocalDate? {
        val m = DATE_MONTH_SHORT.find(text) ?: return null
        val month = MONTH_MAP[m.groupValues[2].lowercase()] ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        }.getOrNull()
    }

    // "15 April 2033" — full month name, German or English
    private fun parseMonthLong(text: String): LocalDate? {
        val m = DATE_MONTH_LONG.find(text) ?: return null
        val month = MONTH_MAP[m.groupValues[2].lowercase()] ?: return null
        return runCatching {
            LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        }.getOrNull()
    }

    private fun parseGermanParts(d: String, mo: String, y: String): LocalDate? {
        val day   = d.toIntOrNull() ?: return null
        val month = mo.toIntOrNull() ?: return null
        val raw   = y.toIntOrNull() ?: return null
        val year  = when { raw < 50 -> 2000 + raw; raw < 100 -> 1900 + raw; else -> raw }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun String.has(vararg keywords: String) = keywords.any { this.contains(it) }
}
