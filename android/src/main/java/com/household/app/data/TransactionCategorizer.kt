package com.household.app.data

/**
 * Category classification logic mirrored from Expenses web app.
 * Provides rule-based categorization of transactions by keyword matching.
 */
class TransactionCategorizer {

    // Patterns to fully exclude from tracking
    private val excludePatterns = listOf(
        // Inter-bank self-transfers
        "commerzbank", "ing-diba",
        // School food deduction
        "mittagstisch",
        // Google payments
        "google payment ireland"
    )

    // Category keyword mappings
    private val categoryKeywords = mapOf(
        "Food & Dining" to listOf(
            "restaurant", "cafe", "coffee", "pizza", "burger", "starbucks",
            "mcdonald", "kfc", "rewe", "aldi", "lidl", "edeka", "penny", "netto",
            "kaufland", "grocery", "bakery", "sushi", "doener", "nordsee", "alnatura",
            "foodmarket", "supermarket", "costco", "trader joe", "whole foods",
            "takeaway", "sodexo", "baeckerei", "backerei", "bäckerei", "staib", "konditorei"
        ),
        "Transportation" to listOf(
            "gas", "fuel", "shell", "bp", "aral", "esso", "taxi", "uber",
            "lyft", "public transport", "train", "bus", "parking", "toll",
            "car rental", "deutsche bahn", "db", "mvg", "confitech"
        ),
        "Utilities" to listOf(
            "electricity", "water", "gas provider", "internet", "phone bill",
            "lekker energie", "rundfunk", "strom", "wasser", "telekom"
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "cinema", "movie", "theater", "concert",
            "gaming", "steam", "playstation", "xbox", "hbo", "disney",
            "music", "entertainment"
        ),
        "Shopping" to listOf(
            "amazon", "shopping", "mall", "store", "target", "h+m", "h&m", "zara",
            "fashion", "online shop", "ebay", "aliexpress", "ikea", "möbel",
            "mueller", "muller", "woolworth", "tk maxx", "tkmaxx", "ostermeier", "paypal"
        ),
        "Health & Fitness" to listOf(
            "gym", "fitness", "yoga", "doctor", "pharmacy", "cvs", "walgreens",
            "health", "medical", "hospital", "clinic"
        ),
        "Salary/Income" to listOf(
            "salary", "payroll", "income", "payment from", "transfer to",
            "bonus", "refund", "lohn", "gehalt", "abrechnun", "elektrobit"
        ),
        "Housing & Property" to listOf(
            "lindner", "immobilien", "grundstueck", "miete", "rent", "apartment"
        ),
        "Transfers" to listOf(
            "transfer", "sent", "payment", "p2p", "revolut", "wise", "topup",
            "end-to-end-ref", "mandatsref", "revpoints"
        ),
        "Cash" to listOf(
            "cash withdrawal", "geldautomat", "atm", "abhebung"
        ),
        "Government & Benefits" to listOf(
            "bundesagentur", "stadt ulm", "stadt", "government", "tax", "benefit",
            "school lunch", "mss -"
        ),
        "Banking & Fees" to listOf(
            "n26", "bank", "fee", "gebuehr", "provision"
        )
    )

    fun shouldExclude(description: String): Boolean {
        val lower = description.lowercase()
        return excludePatterns.any { lower.contains(it) }
    }

    fun classifyCategory(description: String, currentCategory: String): String {
        // If already classified by bank, try to refine
        if (currentCategory.isNotBlank() && currentCategory != "Other") {
            return currentCategory
        }

        val lower = description.lowercase()

        // Try keyword matching
        for ((category, keywords) in categoryKeywords) {
            if (keywords.any { lower.contains(it) }) {
                return category
            }
        }

        return currentCategory.ifBlank { "Other" }
    }

    fun getAllCategories(): List<String> {
        return (listOf("All", "Other", "Excluded") + categoryKeywords.keys).distinct()
    }
}
