package com.jugaad.feature.astro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jugaad.feature.astro.data.db.entity.DailyTransitCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTransitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transits: List<DailyTransitCacheEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transit: DailyTransitCacheEntity): Long

    @Query("SELECT * FROM daily_transit_cache WHERE transit_date_jd = :jd ORDER BY planet_id ASC")
    suspend fun getForDay(jd: Double): List<DailyTransitCacheEntity>

    @Query("SELECT * FROM daily_transit_cache WHERE transit_date_jd = :jd ORDER BY planet_id ASC")
    fun observeForDay(jd: Double): Flow<List<DailyTransitCacheEntity>>

    @Query("SELECT * FROM daily_transit_cache WHERE transit_date_jd = :jd AND planet_id = :planetId")
    suspend fun getPlanetForDay(jd: Double, planetId: Int): DailyTransitCacheEntity?

    @Query("SELECT * FROM daily_transit_cache WHERE transit_date_jd BETWEEN :fromJd AND :toJd AND planet_id = :planetId ORDER BY transit_date_jd ASC")
    suspend fun getPlanetRange(fromJd: Double, toJd: Double, planetId: Int): List<DailyTransitCacheEntity>

    @Query("SELECT * FROM daily_transit_cache WHERE retrograde = 1 AND transit_date_jd BETWEEN :fromJd AND :toJd")
    suspend fun getRetrogradePeriods(fromJd: Double, toJd: Double): List<DailyTransitCacheEntity>

    /** Purge stale cache entries past their expiry. Call periodically from a WorkManager job. */
    @Query("DELETE FROM daily_transit_cache WHERE expires_at < :nowEpochMs")
    suspend fun purgeExpired(nowEpochMs: Long): Int

    @Query("SELECT COUNT(*) FROM daily_transit_cache WHERE transit_date_jd = :jd AND expires_at > :nowEpochMs")
    suspend fun isValidCachePresent(jd: Double, nowEpochMs: Long): Int
}
