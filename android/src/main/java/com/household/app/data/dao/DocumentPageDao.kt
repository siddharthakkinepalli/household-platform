package com.household.app.data.dao

import androidx.room.*
import com.household.app.data.entities.DocumentPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: DocumentPageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<DocumentPageEntity>)

    @Query("SELECT * FROM vault_document_pages WHERE vaultEntryId = :vaultId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocument(vaultId: Long): List<DocumentPageEntity>

    @Query("SELECT * FROM vault_document_pages WHERE pageHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): DocumentPageEntity?

    @Query("UPDATE vault_document_pages SET processingState = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)

    @Query("DELETE FROM vault_document_pages WHERE vaultEntryId = :vaultId")
    suspend fun deleteForDocument(vaultId: Long)
}
