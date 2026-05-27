package com.household.app.data.dao

import androidx.room.*
import com.household.app.data.entities.TaxCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxCheckDao {
    @Query("SELECT * FROM tax_checks WHERE year = :year LIMIT 1")
    suspend fun getForYear(year: Int): TaxCheckEntity?

    @Query("SELECT * FROM tax_checks ORDER BY year DESC")
    fun observeAll(): Flow<List<TaxCheckEntity>>

    @Query("SELECT DISTINCT year FROM tax_checks ORDER BY year DESC")
    suspend fun getAvailableYears(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaxCheckEntity)

    @Query("UPDATE tax_checks SET driveIncomeTaxFolderId = :folderId WHERE year = :year")
    suspend fun setDriveFolderId(year: Int, folderId: String)

    @Query("UPDATE tax_checks SET isComplete = :complete WHERE year = :year")
    suspend fun setComplete(year: Int, complete: Boolean)
}
