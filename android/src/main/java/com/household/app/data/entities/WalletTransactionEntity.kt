package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val category: String,
    val amount: Double,
    val date: LocalDate,
    val paymentType: String,
    val trip: String? = null,
    val note: String = "",
    val bankName: String = "",
    val excluded: Boolean = false,
    val syncedFromJson: Boolean = true  // Tracks if imported from wallet_data.json
)
