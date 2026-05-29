package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.household.app.data.entities.SalarySourceEntity

@Dao
interface SalarySourceDao {
    @Query("SELECT * FROM salary_sources WHERE id = 1")
    suspend fun getSalarySource(): SalarySourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SalarySourceEntity): Long

    @Query("DELETE FROM salary_sources")
    suspend fun clear(): Int
}
