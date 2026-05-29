package com.household.app.data.dao

import androidx.room.*
import com.household.app.data.entities.TaxTagEntity

@Dao
interface TaxTagDao {
    @Query("SELECT * FROM tax_tags WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun getTagsFor(entityType: String, entityId: Long): List<TaxTagEntity>

    @Query("SELECT * FROM tax_tags WHERE year = :year ORDER BY taxCategory ASC")
    suspend fun getAllForYear(year: Int): List<TaxTagEntity>

    @Query("SELECT DISTINCT year FROM tax_tags ORDER BY year DESC")
    suspend fun getAvailableYears(): List<Int>

    @Upsert
    suspend fun upsert(tag: TaxTagEntity): Long

    @Query("DELETE FROM tax_tags WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun removeTagFor(entityType: String, entityId: Long): Int

    @Query("DELETE FROM tax_tags WHERE id = :id")
    suspend fun delete(id: Long): Int
}
