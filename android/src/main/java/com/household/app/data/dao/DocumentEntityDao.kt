package com.household.app.data.dao

import androidx.room.*
import com.household.app.data.entities.VaultDocumentEntityRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VaultDocumentEntityRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VaultDocumentEntityRecord): Long

    @Query("SELECT * FROM vault_extracted_entities WHERE vaultEntryId = :vaultId ORDER BY confidence DESC")
    suspend fun getForDocument(vaultId: Long): List<VaultDocumentEntityRecord>

    @Query("SELECT * FROM vault_extracted_entities WHERE vaultEntryId = :vaultId AND entityType = :type ORDER BY confidence DESC")
    suspend fun getByType(vaultId: Long, type: String): List<VaultDocumentEntityRecord>

    @Query("SELECT * FROM vault_extracted_entities WHERE entityType = 'EXPIRY_DATE' ORDER BY normalizedValue ASC")
    fun observeAllExpiries(): Flow<List<VaultDocumentEntityRecord>>

    @Query("SELECT * FROM vault_extracted_entities WHERE normalizedValue LIKE '%' || :query || '%' OR rawValue LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<VaultDocumentEntityRecord>

    @Query("DELETE FROM vault_extracted_entities WHERE vaultEntryId = :vaultId")
    suspend fun deleteForDocument(vaultId: Long)

    @Query("UPDATE vault_extracted_entities SET isVerified = 1 WHERE id = :id")
    suspend fun markVerified(id: Long)
}
