package com.jugaad.feature.astro.domain.repository

import com.jugaad.feature.astro.data.db.entity.ClosedLoopFeedbackEntity
import com.jugaad.feature.astro.data.db.entity.DailyTransitCacheEntity
import com.jugaad.feature.astro.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for the astro feature.
 *
 * Deterministic single-shot queries → suspend functions.
 * Reactive cache state tracking → Flow channels.
 * All output is immutable (Room entities are data classes — already immutable).
 */
interface AstroRepository {

    // ── User profiles ─────────────────────────────────────────────────────────

    suspend fun getUserProfile(profileId: Long): UserProfileEntity?

    fun observeUserProfile(profileId: Long): Flow<UserProfileEntity?>

    fun observeAllUserProfiles(): Flow<List<UserProfileEntity>>

    suspend fun getUserProfileByNameHash(nameHash: String): UserProfileEntity?

    suspend fun saveUserProfile(profile: UserProfileEntity): Long

    suspend fun deleteUserProfile(profileId: Long)

    /**
     * Updates the plaintext computed astrological fields on a stored user profile.
     *
     * These fields (rashi, ascendant, nakshatra) are astronomical output — NOT PII —
     * and are safe to store as plaintext. Called by [ComputeBirthChartUseCase] after
     * computing the natal chart.
     */
    suspend fun updateUserProfileComputedFields(
        profileId: Long,
        rashi: String,
        ascendant: String,
        nakshatra: String
    )

    // ── Transit cache ─────────────────────────────────────────────────────────

    /**
     * Atomically replaces all transit cache entries for a given Julian Day.
     * Uses Room's REPLACE conflict strategy.
     */
    suspend fun upsertTransitCache(entities: List<DailyTransitCacheEntity>)

    /**
     * Returns the cached transit entities for the given Julian Day,
     * or an empty list if no valid cache exists.
     */
    suspend fun getTransitCache(julianDayUt: Double): List<DailyTransitCacheEntity>

    /**
     * Reactive stream of transit cache entries for [julianDayUt].
     * Emits a new list whenever the cache is updated or expires.
     */
    fun observeTransitCache(julianDayUt: Double): Flow<List<DailyTransitCacheEntity>>

    /**
     * Returns true if a non-expired cache exists for the given Julian Day.
     * @param nowEpochMs Current wall clock in epoch millis.
     */
    suspend fun isTransitCacheValid(julianDayUt: Double, nowEpochMs: Long): Boolean

    /**
     * Deletes all transit cache rows whose [DailyTransitCacheEntity.expiresAt]
     * is before [nowEpochMs]. Call periodically from a WorkManager job.
     */
    suspend fun purgeExpiredTransitCache(nowEpochMs: Long)

    // ── Closed-loop feedback ──────────────────────────────────────────────────

    suspend fun insertFeedback(entity: ClosedLoopFeedbackEntity): Long

    fun observeFeedbackForProfile(profileId: Long): Flow<List<ClosedLoopFeedbackEntity>>

    suspend fun getAverageRatingByType(profileId: Long, predictionType: String): Float?

    suspend fun updateFeedbackOutcome(predictionId: String, outcome: String)
}
