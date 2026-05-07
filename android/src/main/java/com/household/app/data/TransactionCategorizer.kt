package com.household.app.data

/**
 * Six-bucket household spending classifier.
 *
 * Grocery = supermarkets and grocery stores.
 * Eat out = restaurants, cafes, takeaway, delivery.
 * Travel = DB, Confificar/Confitech, fuel, taxi, transit, parking.
 * Utilities = rent, electricity, internet, phone, paypal, wise, etc.
 * Transfers = inter-bank transfers, ING, N26, Commerz, SEPA, IBAN, etc.
 * Shopping = other retail and online stores.
 */
class TransactionCategorizer {

    private val excludePatterns = listOf(
        "commerzbank", "ing-diba",
        "mittagstisch",
        "google payment ireland"
    )

    private val categoryKeywords = linkedMapOf(
        "Transfers" to listOf(
            "ing", "n26", "commerz", "chithra", "iban", "sepa", "transfer",
            "wire", "ucc", "trwi", "wise", "wise europe", "interbk",
            "interbank", "inter-bank", "bank to bank", "main account",
            "siddharth", "akkinepalli", "ntbs"
        ),
        "Grocery" to listOf(
            "rewe", "aldi", "lidl", "edeka", "penny", "netto", "kaufland",
            "alnatura", "supermarket", "foodmarket", "grocery", "costco",
            "norma", "asia store", "asiamarkt", "asia markt",
            "trader joe", "whole foods"
        ),
        "Eat out" to listOf(
            "restaurant", "cafe", "coffee", "pizza", "burger", "starbucks",
            "mcdonald", "kfc", "sushi", "doener", "döner", "nordsee",
            "takeaway", "lieferando", "wolt", "uber eats", "sodexo",
            "bakery", "baeckerei", "backerei", "bäckerei", "staib", "konditorei"
        ),
        "Travel" to listOf(
            "conficar", "confitech", "deutsche bahn", "deutschlandticket",
            "db vertri", " db ", "bahn", "train", "bus", "mvg",
            "fuel", "gas", "shell", "bp", "aral", "esso", "taxi", "uber",
            "parking", "toll", "car rental"
        ),
        "Utilities" to listOf(
            "miete", "rent", "landlord", "electricity", "strom", "gas",
            "wasser", "water", "internet", "telekom", "vodafone", "telefonica",
            "telefon", "telephone", "lyca", "elektrobit", "current", "stadt ulm",
            "menza", "mensa", "school mess"
        ),
        "Shopping" to listOf(
            "amazon", "shopping", "mall", "store", "target", "h+m", "h&m",
            "zara", "fashion", "online shop", "ebay", "aliexpress", "ikea",
            "möbel", "moebel", "mueller", "muller", "woolworth", "tk maxx",
            "tkmaxx", "ostermeier", "dm-drogerie", "rossmann",
            "decathlon"
        )
    )

    fun shouldExclude(description: String): Boolean {
        val lower = description.lowercase()
        return excludePatterns.any { lower.contains(it) }
    }

    fun classifyCategory(description: String, currentCategory: String): String {
        val lower = description.lowercase()
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { lower.contains(it) }) {
                return category
            }
        }

        normalizeCategory(currentCategory)?.let { return it }
        return "Shopping"
    }

    fun getAllCategories(): List<String> = categoryKeywords.keys.toList()

    fun normalizeCategory(raw: String): String? {
        val lowered = raw.trim().lowercase()
        if (lowered.isBlank() || lowered == "other") return null
        return when {
            lowered == "grocery" || lowered.contains("supermarket") || lowered.contains("grocer") -> "Grocery"
            lowered == "eat out" || lowered.contains("restaurant") || lowered.contains("dining") || lowered.contains("food") -> "Eat out"
            lowered == "travel" || lowered.contains("transport") || lowered.contains("bahn") -> "Travel"
            lowered == "utilities" || lowered.contains("rent") || lowered.contains("utility") || lowered.contains("bill") -> "Utilities"
            lowered == "transfers" || lowered.contains("transfer") || lowered.contains("bank") -> "Transfers"
            lowered == "shopping" || lowered.contains("shop") -> "Shopping"
            else -> null
        }
    }
}
