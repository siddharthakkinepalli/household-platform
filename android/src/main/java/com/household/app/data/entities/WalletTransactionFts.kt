package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "wallet_transactions_fts")
@Fts4(contentEntity = WalletTransactionEntity::class)
data class WalletTransactionFts(
    val title: String,
    val category: String,
    val note: String,
    val bankName: String
)
