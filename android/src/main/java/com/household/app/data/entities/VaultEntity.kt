package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_entries",
    indices = [Index("dateEpoch")]
)
data class VaultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val merchantName: String?,
    val totalAmount: Double?,
    val currency: String = "EUR",
    val dateEpoch: Long,
    val rawOcrContent: String,
    val isLinkedToExpense: Boolean = false,
    val linkedExpenseId: Long? = null
)
