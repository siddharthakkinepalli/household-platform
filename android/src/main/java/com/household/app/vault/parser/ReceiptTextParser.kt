package com.household.app.vault.parser

import com.household.app.vault.parser.LocalReceiptScanner.fixOcrCharacterGlitch
import java.util.regex.Pattern

object ReceiptTextParser {

    data class ParsedReceipt(
        val merchant: String,
        val date: String?,
        val totalAmount: Double,
        val category: String,
        val isInvoice: Boolean,
        val lineItems: List<LineItem>
    )

    data class LineItem(
        val rawText: String,
        val name: String,
        val price: Double,
        val quantity: Int = 1
    )

    fun parse(rawOcrContent: String): ParsedReceipt {
        if (rawOcrContent.isBlank()) {
            return ParsedReceipt("UNBEKANNT", null, 0.0, "Uncategorized", false, emptyList())
        }

        val textLower = rawOcrContent.lowercase()
        val isInvoice = checkIsInvoice(textLower)
        val amount = extractGermanAmount(textLower, isInvoice)
        val date = extractGermanDate(rawOcrContent)
        val (merchant, category) = identifyMerchantAndCategory(textLower)
        val lineItems = if (!isInvoice) extractLineItems(rawOcrContent) else emptyList()

        return ParsedReceipt(merchant, date, amount, category, isInvoice, lineItems)
    }

    private fun checkIsInvoice(textLower: String): Boolean {
        val invoiceKeywords = listOf(
            "rechnung", "rechnungsnummer", "belegnr", "kundennummer",
            "vertragsnummer", "abschlag", "rechnungsbetrag", "fälligkeit"
        )
        return invoiceKeywords.count { textLower.contains(it) } >= 2
    }

    private fun extractGermanAmount(textLower: String, isInvoice: Boolean): Double {
        val patterns = mutableListOf<Pattern>()

        if (isInvoice) {
            patterns += Pattern.compile(
                "(?:rechnungsbetrag|zahlungsbetrag|gesamtbetrag|abschlagbetrag|fällig)" +
                "[\\s\\D]*(?:eur|€)?[\\s]*(\\d{1,5}(?:\\.\\d{3})*,\\d{2})\\b"
            )
            patterns += Pattern.compile(
                "(?:monatlicher abschlag|ihr guthaben)[\\s\\D]*(?:eur|€)?[\\s]*(\\d{1,5}(?:\\.\\d{3})*,\\d{2})\\b"
            )
        }

        patterns += Pattern.compile(
            "(?:gesamt|summe|endbetrag|zahlbetrag|zu zahlen|total)[\\s\\D]*(?:eur|€)?[\\s]*(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\b"
        )
        patterns += Pattern.compile(
            "(?:bar|gegeben|bezahlt)[\\s\\D]*(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\b"
        )

        val fixedText = fixOcrCharacterGlitch(textLower)
        for (p in patterns) {
            val m = p.matcher(fixedText)
            if (m.find()) m.group(1)?.let { return cleanGermanFloat(it) }
        }

        // Fallback: largest price in the document (robust for broken OCR layouts)
        val genericPrice = Pattern.compile("\\b(\\d{1,5}(?:\\.\\d{3})*,\\d{2})\\b")
        val fallback = genericPrice.matcher(fixedText)
        var max = 0.0
        while (fallback.find()) {
            fallback.group(1)?.let { v ->
                val d = cleanGermanFloat(v)
                if (d > max) max = d
            }
        }
        return max
    }

    private fun extractGermanDate(rawText: String): String? {
        val m = Pattern.compile("\\b(\\d{2}\\.\\d{2}\\.(?:20)?\\d{2})\\b").matcher(rawText)
        return if (m.find()) m.group(1) else null
    }

    // Extracts individual line items: "Produktname   1,49 B" or "2 x Milch   2,98 A"
    internal fun extractLineItems(rawText: String): List<LineItem> {
        val items = mutableListOf<LineItem>()
        val priceAtEnd = Pattern.compile("^(.+?)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*[A-Ba-b]?\\s*$")
        val quantityLine = Pattern.compile("^(\\d+)\\s*[xX×]\\s+(.+?)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})")
        val skipWords = setOf(
            "gesamt", "summe", "mwst", "ust", "steuer", "eur", "total",
            "gegeben", "rückgeld", "rabatt", "pfand", "bon", "vielen dank",
            "kassierer", "datum", "uhrzeit", "karte", "bar", "zahlbetrag"
        )

        for (line in rawText.lines()) {
            val trimmed = line.trim()
            if (trimmed.length < 4) continue
            val lower = trimmed.lowercase()
            if (skipWords.any { lower.contains(it) }) continue

            val qm = quantityLine.matcher(trimmed)
            if (qm.find()) {
                val qty = qm.group(1)?.toIntOrNull() ?: 1
                val name = qm.group(2)?.trim()?.replaceFirstChar { it.uppercase() } ?: continue
                val price = cleanGermanFloat(qm.group(3) ?: continue)
                if (price > 0.0 && price < 500.0)
                    items += LineItem(trimmed, name, price, qty)
                continue
            }

            val pm = priceAtEnd.matcher(trimmed)
            if (pm.find()) {
                val name = pm.group(1)?.trim()?.replaceFirstChar { it.uppercase() } ?: continue
                val price = cleanGermanFloat(pm.group(2) ?: continue)
                // Skip likely total/tax lines and implausible prices
                if (price > 0.0 && price < 200.0 && name.length >= 3)
                    items += LineItem(trimmed, name, price, 1)
            }
        }
        return items
    }

    private fun identifyMerchantAndCategory(textLower: String): Pair<String, String> {
        // Ulm-localized (checked first — most specific)
        if (textLower.contains("swu nahverkehr") || textLower.contains("ding ticket") || textLower.contains("donau-iller"))
            return "SWU / DING" to "Local Transit (SWU/DING)"
        if (textLower.contains("swu energie") || textLower.contains("swu stadtwerke") || textLower.contains("fernwaerme ulm") || textLower.contains(" fvu"))
            return "SWU ENERGIE" to "Utilities (SWU)"
        if (textLower.contains("stadt ulm") || textLower.contains("stadtkasse ulm") || textLower.contains("buergerbuero ulm"))
            return "STADT ULM" to "Municipal Fees & Public Kita"
        if (textLower.contains("studierendenwerk ulm") || textLower.contains("uni ulm mensa") || textLower.contains("besseresser"))
            return "MENSA ULM" to "School Lunch / Mensa"
        if (textLower.contains("ssv ulm") || textLower.contains("tsg soeflingen") || textLower.contains("tsv pfuhl"))
            return "SPORTVEREIN ULM" to "Local Activities & Sports"
        if (textLower.contains("uws") || textLower.contains("ulmer wohnungsbau"))
            return "UWS ULM" to "Housing & Rent"

        // National utilities & telecom
        if (textLower.contains("vattenfall") || textLower.contains("e.on") || textLower.contains("yello") || textLower.contains("enbw") || textLower.contains("stadtwerke"))
            return "ENERGIEANBIETER" to "Utilities"
        if (textLower.contains("telekom") || textLower.contains("vodafone") || textLower.contains(" o2 ") || textLower.contains("1&1") || textLower.contains("congstar"))
            return "TELEKOMMUNIKATION" to "Internet & Mobile"

        // Groceries
        val groceries = listOf("lidl", "rewe", "edeka", "aldi", "kaufland", "penny", "netto", "norma", "alnatura", "denns", "tegut", "globus", "famila")
        for (g in groceries) if (textLower.contains(g)) return g.uppercase() to "Groceries"

        // Drugstores
        val drugstores = listOf("dm-drogerie", "dm drogerie", "dm ", "rossmann", "müller drogerie", "mueller drogerie", "budni")
        for (d in drugstores) if (textLower.contains(d)) return d.trim().uppercase() to "Drugstore & Personal Care"

        // Discount/variety
        val discounters = listOf("woolworth", "action ", "tedi", "kik ", "tk maxx", "galeria")
        for (di in discounters) if (textLower.contains(di)) return di.trim().uppercase() to "Discount & Department Stores"

        // DIY
        val diy = listOf("obi", "bauhaus", "hornbach", "toom", "hagebau")
        for (h in diy) if (textLower.contains(h)) return h.uppercase() to "DIY & Hardware Store"

        // Electronics
        val electronics = listOf("mediamarkt", "saturn", "apple store", "cyberport")
        for (e in electronics) if (textLower.contains(e)) return e.uppercase() to "Electronics"

        // Dining
        val dining = listOf("restaurant", "cafe ", "pizza", "mcdonald", "burger", "sushi", "döner", "bäckerei", "backerei")
        for (r in dining) if (textLower.contains(r)) return r.trim().uppercase() to "Dining & Takeaway"

        // Pharmacy
        if (textLower.contains("apotheke")) return "APOTHEKE" to "Pharmacy & Health"

        // Fuel
        val fuel = listOf("aral", "shell", "totalenergies", "esso", "jet tankstelle", "omv")
        for (f in fuel) if (textLower.contains(f)) return f.uppercase() to "Gas & Fuel"

        // Try first non-blank line of OCR as merchant fallback
        val firstMeaningfulLine = textLower.lines()
            .map { it.trim() }
            .firstOrNull { it.length in 3..40 && !it.all { c -> c.isDigit() || c == '.' || c == ',' } }
            ?.uppercase()

        return (firstMeaningfulLine ?: "UNBEKANNT") to "Uncategorized"
    }

    private fun cleanGermanFloat(v: String): Double = try {
        v.replace(".", "").replace(",", ".").toDouble()
    } catch (_: Exception) { 0.0 }
}
