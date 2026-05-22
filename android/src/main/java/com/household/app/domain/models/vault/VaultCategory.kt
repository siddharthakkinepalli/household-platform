package com.household.app.domain.models.vault

enum class VaultCategory(val label: String, val emoji: String) {
    RECEIPT("Receipts", "🧾"),
    IDENTITY("Identity", "🪪"),
    CONTRACT("Contracts", "📄"),
    PROPERTY("Property & housing", "🏠"),
    UTILITY_BILL("Utility Bills", "⚡"),
    INSURANCE("Insurance", "🛡"),
    MEDICAL("Medical", "🏥"),
    OTHER("Other", "📁")
}
