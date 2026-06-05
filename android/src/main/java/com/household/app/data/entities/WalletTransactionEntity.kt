package com.household.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "wallet_transactions",
    indices = [Index(value = ["contentHash"], unique = true, name = "idx_wallet_transactions_contentHash")]
)
data class WalletTransactionEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val category: String,
    val amount: Double,
    val date: LocalDate,
    val paymentType: String,
    val trip: String? = null,
    val linkedVaultEntryId: Long? = null,
    val note: String = "",
    val bankName: String = "",
    val excluded: Boolean = false,
    val syncedFromJson: Boolean = true,
    @ColumnInfo(name = "contentHash") val contentHash: String? = null
)
