package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.domain.models.vault.VaultSubFolder

@Entity(
    tableName = "vault_entries",
    indices = [
        Index("dateEpoch"),
        Index("category"),
        Index(value = ["category", "ownerMemberId", "subFolder"])
    ]
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
    val linkedExpenseId: Long? = null,
    val category: String = VaultCategory.RECEIPT.name,
    /** null = shared household folder */
    val ownerMemberId: Long? = null,
    val subFolder: String = VaultSubFolder.UNFILED.id,
    val documentTitle: String? = null,
    val mimeType: String = "image/jpeg",
    val fileHash: String? = null
)
