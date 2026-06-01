package com.jugaad.feature.astro.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jugaad.feature.astro.data.db.entity.ClosedLoopFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feedback: ClosedLoopFeedbackEntity): Long

    @Query("SELECT * FROM closed_loop_feedback WHERE user_profile_id = :profileId ORDER BY feedback_timestamp DESC")
    fun observeForProfile(profileId: Long): Flow<List<ClosedLoopFeedbackEntity>>

    @Query("SELECT * FROM closed_loop_feedback WHERE prediction_id = :predictionId")
    suspend fun getByPredictionId(predictionId: String): ClosedLoopFeedbackEntity?

    @Query("SELECT * FROM closed_loop_feedback WHERE prediction_type = :type AND user_profile_id = :profileId ORDER BY feedback_timestamp DESC LIMIT :limit")
    suspend fun getRecentByType(profileId: Long, type: String, limit: Int): List<ClosedLoopFeedbackEntity>

    @Query("SELECT AVG(CAST(user_rating AS REAL)) FROM closed_loop_feedback WHERE user_profile_id = :profileId AND prediction_type = :type")
    suspend fun averageRatingByType(profileId: Long, type: String): Float?

    @Query("SELECT * FROM closed_loop_feedback WHERE model_version = :version ORDER BY feedback_timestamp DESC")
    suspend fun getForModelVersion(version: String): List<ClosedLoopFeedbackEntity>

    @Query("UPDATE closed_loop_feedback SET actual_outcome = :outcome, feedback_timestamp = :ts WHERE prediction_id = :predictionId")
    suspend fun updateOutcome(predictionId: String, outcome: String, ts: Long): Int

    @Query("DELETE FROM closed_loop_feedback WHERE user_profile_id = :profileId")
    suspend fun deleteAllForProfile(profileId: Long): Int
}
