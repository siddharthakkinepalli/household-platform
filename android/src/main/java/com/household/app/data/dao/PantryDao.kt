package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.household.app.data.entities.PantryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PantryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PantryEntity): Long

    @Delete
    suspend fun deleteItem(item: PantryEntity): Int

    @Query("SELECT * FROM pantry_items WHERE vaultId = :vaultId AND isConfirmed = 0 ORDER BY addedAt ASC")
    fun getStagedItemsByVault(vaultId: Long): Flow<List<PantryEntity>>

    @Query("SELECT * FROM pantry_items WHERE isConfirmed = 1 ORDER BY addedAt DESC")
    fun getConfirmedItems(): Flow<List<PantryEntity>>

    @Query("DELETE FROM pantry_items WHERE vaultId = :vaultId AND isConfirmed = 0")
    suspend fun deleteStagedForVault(vaultId: Long): Int

    @Query("SELECT * FROM pantry_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): PantryEntity?

    @Query("SELECT * FROM pantry_items ORDER BY addedAt DESC")
    suspend fun getAllPantry(): List<PantryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemsIgnore(items: List<PantryEntity>): List<Long>
}
