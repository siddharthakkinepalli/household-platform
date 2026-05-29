package com.household.app.data.entities

import androidx.room.ColumnInfo
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
        Index(value = ["category", "ownerMemberId", "subFolder"], name = "index_vault_entries_folder"),
        Index("fileHash", name = "idx_vault_entries_fileHash")
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
    @ColumnInfo(defaultValue = "RECEIPT")
    val category: String = VaultCategory.RECEIPT.name,
    /** null = shared household folder */
    val ownerMemberId: Long? = null,
    @ColumnInfo(defaultValue = "unfiled")
    val subFolder: String = VaultSubFolder.UNFILED.id,
    val documentTitle: String? = null,
    @ColumnInfo(defaultValue = "image/jpeg")
    val mimeType: String = "image/jpeg",
    val fileHash: String? = null
)
