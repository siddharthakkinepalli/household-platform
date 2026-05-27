package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.household.app.data.entities.VaultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultEntry(entry: VaultEntity): Long

    @Query("SELECT * FROM vault_entries ORDER BY dateEpoch DESC")
    fun getAllEntries(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_entries WHERE isLinkedToExpense = 0 ORDER BY dateEpoch DESC")
    fun getUnlinkedEntries(): Flow<List<VaultEntity>>

    @Query("UPDATE vault_entries SET isLinkedToExpense = 1, linkedExpenseId = :expenseId WHERE id = :vaultId")
    suspend fun linkToExpense(vaultId: Long, expenseId: Long)

    @Delete
    suspend fun deleteEntry(entry: VaultEntity)

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): VaultEntity?

    @Query("SELECT COUNT(*) FROM vault_entries WHERE isLinkedToExpense = 0")
    suspend fun getUnlinkedCount(): Int

    @Query("SELECT * FROM vault_entries ORDER BY dateEpoch DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<VaultEntity>

    @Query("SELECT * FROM vault_entries WHERE category = :category ORDER BY dateEpoch DESC")
    fun getEntriesByCategory(category: String): Flow<List<VaultEntity>>

    @Query("UPDATE vault_entries SET category = :category, documentTitle = :title WHERE id = :id")
    suspend fun updateDocumentMeta(id: Long, category: String, title: String?)

    @Query("DELETE FROM vault_entries WHERE id IN (:ids)")
    suspend fun deleteEntries(ids: List<Long>)

    @Query("UPDATE vault_entries SET category = :category WHERE id IN (:ids)")
    suspend fun moveEntries(ids: List<Long>, category: String)

    @Query(
        """
        UPDATE vault_entries
        SET category = :category, ownerMemberId = :ownerMemberId, subFolder = :subFolder
        WHERE id IN (:ids)
        """
    )
    suspend fun moveEntriesToFolder(
        ids: List<Long>,
        category: String,
        ownerMemberId: Long?,
        subFolder: String
    )

    @Query("SELECT * FROM vault_entries ORDER BY dateEpoch DESC")
    suspend fun getAllEntriesList(): List<VaultEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<VaultEntity>)

    @Query("UPDATE vault_entries SET ownerMemberId = NULL WHERE ownerMemberId = :memberId")
    suspend fun clearOwnerFromEntries(memberId: Long)

    @Query("UPDATE vault_entries SET category = :category, subFolder = :subFolder, documentTitle = :title WHERE id = :id")
    suspend fun updateParsedMeta(id: Long, category: String, subFolder: String, title: String?)

    @Query("UPDATE vault_entries SET rawOcrContent = :content WHERE id = :id")
    suspend fun updateRawOcr(id: Long, content: String)

    @Query("UPDATE vault_entries SET merchantName = :merchant, totalAmount = :amount, dateEpoch = :dateEpoch WHERE id = :id")
    suspend fun updateReceiptMeta(id: Long, merchant: String, amount: Double, dateEpoch: Long)
}
