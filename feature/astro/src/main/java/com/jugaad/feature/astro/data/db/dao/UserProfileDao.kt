package com.jugaad.feature.astro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jugaad.feature.astro.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: UserProfileEntity): Long

    @Update
    suspend fun update(profile: UserProfileEntity): Int

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    fun observeById(id: Long): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: Long): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE name_hash = :nameHash")
    suspend fun getByNameHash(nameHash: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles ORDER BY created_at DESC")
    fun observeAll(): Flow<List<UserProfileEntity>>

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun count(): Int

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE user_profiles SET updated_at = :ts, veda_rashi = :rashi, veda_ascendant = :ascendant, veda_nakshatra = :nakshatra WHERE id = :id")
    suspend fun updateComputedFields(id: Long, rashi: String, ascendant: String, nakshatra: String, ts: Long): Int
}
