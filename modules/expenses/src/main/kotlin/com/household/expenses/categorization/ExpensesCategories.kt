package com.household.expenses.categorization

/**
 * Rule-based expense categorizer using keyword matching (no AI required).
 * Ported from simple_categorizer.py with India travel + Etihad overrides.
 */
class ExpensesCategories {

    private val excludePatterns = listOf(
        // Inter-bank self-transfers
        "commerzbank",
        "ing-diba",
        // School food deduction via Stadt Ulm
        "mittagstisch",
        // Google payments
        "google payment ireland"
    )

    private val categories = mapOf(
        "Food & Dining" to listOf(
            "restaurant", "cafe", "coffee", "pizza", "burger", "starbucks",
            "mcdonald", "kfc", "rewe", "aldi", "lidl", "edeka", "penny", "netto",
            "kaufland", "grocery", "bakery", "sushi", "doener", "nordsee", "alnatura",
            "foodmarket", "supermarket", "costco", "trader joe", "whole foods",
            "choclet", "takeaway", "sodexo",
            "baeckerei", "backerei", "bäckerei", "staib", "konditorei"
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
            "fashion", "online shop", "ebay", "aliexpress", "shop", "ikea",
            "moebel", "moebeln", "mueller", "muller", "woolworth", "tk maxx", "tkmaxx",
            "ostermeier", "paypal", "willbold"
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
            "siddharth", "chithra", "akkinepalli", "madhusudhanan",
            "end-to-end-ref", "mandatsref", "revpoints"
        ),
        "Cash" to listOf(
            "cash withdrawal", "geldautomat", "atm", "abhebung"
        ),
        "Government & Benefits" to listOf(
            "bundesagentur", "stadt ulm", "stadt ", "government", "tax", "benefit",
            "school lunch", "mss -"
        ),
        "Banking & Fees" to listOf(
            "n26", "bank", "fee", "gebuehr", "provision"
        )
    )

    /**
     * Categorize a transaction based on description and amount.
     * Returns pair of (category, budgetCategory).
     * Returns ("__exclude__", "") for excluded transactions.
     */
    fun categorizeTransaction(description: String, amount: Double): Pair<String, String> {
        if (description.isEmpty()) {
            return Pair("Other", "")
        }

        // Normalize: lowercase and handle umlaut variations
        val descNorm = description.lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")

        // Check exclude list first
        for (pattern in excludePatterns) {
            if (descNorm.contains(pattern)) {
                return Pair("__exclude__", "")
            }
        }

        // Bakeries/cafes are Dining Out, not groceries — check before grocery categorizer
        val bakeryKeywords = listOf("baeckerei", "backerei", "bäckerei", "konditorei", "staib")
        if (bakeryKeywords.any { descNorm.contains(it) }) {
            return Pair("Food & Dining", "Bakery")
        }

        // Force airline ticket bookings into India Travel (override shop category)
        if (descNorm.contains("etihad") && descNorm.contains("airway")) {
            return Pair("India Travel", "Flights")
        }

        // Check each category for keyword matches
        for ((category, keywords) in categories) {
            for (keyword in keywords) {
                if (descNorm.contains(keyword)) {
                    return Pair(category, "")
                }
            }
        }

        return Pair("Other", "")
    }

    /**
     * Categorize a list of transactions.
     * Returns list with added "Category" and "BudgetCategory" fields.
     */
    fun categorizeTransactions(transactions: List<Map<String, Any>>): List<Map<String, Any>> {
        return transactions.mapNotNull { transaction ->
            val description = (transaction["description"] as? String) ?: return@mapNotNull null
            val amount = (transaction["amount"] as? Number)?.toDouble() ?: 0.0

            val (category, budgetCategory) = categorizeTransaction(description, amount)

            // Skip excluded transactions
            if (category == "__exclude__") return@mapNotNull null

            transaction + mapOf(
                "category" to category,
                "budgetCategory" to budgetCategory
            )
        }
    }
}
