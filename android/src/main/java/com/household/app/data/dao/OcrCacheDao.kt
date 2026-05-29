package com.household.app.data.dao

import androidx.room.*
import com.household.app.data.entities.OcrCacheEntity

@Dao
interface OcrCacheDao {
    @Query("SELECT * FROM ocr_cache WHERE pageHash = :hash AND engineVersion = :version LIMIT 1")
    suspend fun get(hash: String, version: Int): OcrCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: OcrCacheEntity): Long

    @Query("DELETE FROM ocr_cache WHERE processedAt < :olderThanMs")
    suspend fun evictOlderThan(olderThanMs: Long): Int

    @Query("SELECT COUNT(*) FROM ocr_cache")
    suspend fun size(): Int
}
