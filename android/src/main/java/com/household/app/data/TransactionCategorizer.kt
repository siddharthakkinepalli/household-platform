package com.household.app.data

/**
 * Four-bucket household spending classifier.
 *
 * Grocery = supermarkets and grocery stores.
 * Eat out = restaurants, cafes, takeaway, delivery.
 * Travel = DB, Confificar/Confitech, fuel, taxi, transit, parking.
 * Shopping = other retail and online stores.
 */
class TransactionCategorizer {

    private val excludePatterns = listOf(
        "commerzbank", "ing-diba",
        "mittagstisch",
        "google payment ireland"
    )

    private val categoryKeywords = linkedMapOf(
        "Grocery" to listOf(
            "rewe", "aldi", "lidl", "edeka", "penny", "netto", "kaufland",
            "alnatura", "supermarket", "foodmarket", "grocery", "costco",
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
        "Shopping" to listOf(
            "amazon", "shopping", "mall", "store", "target", "h+m", "h&m",
            "zara", "fashion", "online shop", "ebay", "aliexpress", "ikea",
            "möbel", "moebel", "mueller", "muller", "woolworth", "tk maxx",
            "tkmaxx", "ostermeier", "paypal", "dm-drogerie", "rossmann",
            "decathlon"
        )
    )

    fun shouldExclude(description: String): Boolean {
        val lower = description.lowercase()
        return excludePatterns.any { lower.contains(it) }
    }

    fun classifyCategory(description: String, currentCategory: String): String {
        val exactCurrent = currentCategory.trim()
        if (exactCurrent in categoryKeywords.keys) return exactCurrent

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
            lowered == "shopping" || lowered.contains("shop") -> "Shopping"
            else -> null
        }
    }
}
