package com.household.app.domain.models

import java.time.LocalDate

data class PantryItem(
    val id: Long = 0L,
    val name: String,
    val category: PantryCategory,
    val quantity: Float = 1f,
    val expiryEstimate: LocalDate? = null,
    val vaultId: Long? = null
)
