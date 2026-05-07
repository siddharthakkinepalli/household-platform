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
}
