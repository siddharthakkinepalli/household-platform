package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.app.data.entities.WeightSnapshotEntity
import com.household.app.data.entities.MealsSummaryEntity
import com.household.app.data.entities.DashboardPrefsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: WeightSnapshotEntity): Long

    @Query("SELECT * FROM weight_snapshots ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSnapshot(): WeightSnapshotEntity?

    @Query("SELECT * FROM weight_snapshots ORDER BY date DESC")
    suspend fun getAllSnapshots(): List<WeightSnapshotEntity>

    @Delete
    suspend fun deleteSnapshot(snapshot: WeightSnapshotEntity): Int

    @Query("DELETE FROM weight_snapshots")
    suspend fun deleteAllSnapshots(): Int
}

@Dao
interface MealsSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: MealsSummaryEntity): Long

    @Query("SELECT * FROM meals_summary WHERE id = 1")
    suspend fun getSummary(): MealsSummaryEntity?

    @Update
    suspend fun updateSummary(summary: MealsSummaryEntity): Int

    @Delete
    suspend fun deleteSummary(summary: MealsSummaryEntity): Int

    @Query("DELETE FROM meals_summary")
    suspend fun deleteAllSummaries(): Int
}

@Dao
interface DashboardPrefsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrefs(prefs: DashboardPrefsEntity): Long

    @Query("SELECT * FROM dashboard_prefs WHERE id = 1")
    suspend fun getPrefs(): DashboardPrefsEntity?

    @Update
    suspend fun updatePrefs(prefs: DashboardPrefsEntity): Int

    @Delete
    suspend fun deletePrefs(prefs: DashboardPrefsEntity): Int

    @Query("DELETE FROM dashboard_prefs")
    suspend fun deleteAllPrefs(): Int

    @Query("SELECT salaryAnchorDay FROM dashboard_prefs WHERE id = 1")
    fun observeAnchorDay(): Flow<Int?>
}
