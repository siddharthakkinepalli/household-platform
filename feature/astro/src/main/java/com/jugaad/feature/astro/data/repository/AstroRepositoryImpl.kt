package com.jugaad.feature.astro.data.repository

import com.jugaad.feature.astro.data.db.dao.DailyTransitDao
import com.jugaad.feature.astro.data.db.dao.FeedbackDao
import com.jugaad.feature.astro.data.db.dao.UserProfileDao
import com.jugaad.feature.astro.data.db.entity.ClosedLoopFeedbackEntity
import com.jugaad.feature.astro.data.db.entity.DailyTransitCacheEntity
import com.jugaad.feature.astro.data.db.entity.UserProfileEntity
import com.jugaad.feature.astro.domain.repository.AstroRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AstroRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val dailyTransitDao: DailyTransitDao,
    private val feedbackDao: FeedbackDao
) : AstroRepository {

    // ── User profiles ─────────────────────────────────────────────────────────

    override suspend fun getUserProfile(profileId: Long): UserProfileEntity? =
        userProfileDao.getById(profileId)

    override fun observeUserProfile(profileId: Long): Flow<UserProfileEntity?> =
        userProfileDao.observeById(profileId)

    override fun observeAllUserProfiles(): Flow<List<UserProfileEntity>> =
        userProfileDao.observeAll()

    override suspend fun getUserProfileByNameHash(nameHash: String): UserProfileEntity? =
        userProfileDao.getByNameHash(nameHash)

    override suspend fun saveUserProfile(profile: UserProfileEntity): Long =
        userProfileDao.insert(profile)

    override suspend fun deleteUserProfile(profileId: Long) {
        userProfileDao.deleteById(profileId)
    }

    override suspend fun updateUserProfileComputedFields(
        profileId: Long,
        rashi: String,
        ascendant: String,
        nakshatra: String
    ) {
        userProfileDao.updateComputedFields(
            id         = profileId,
            rashi      = rashi,
            ascendant  = ascendant,
            nakshatra  = nakshatra,
            ts         = System.currentTimeMillis()
        )
    }

    // ── Transit cache ─────────────────────────────────────────────────────────

    override suspend fun upsertTransitCache(entities: List<DailyTransitCacheEntity>) {
        dailyTransitDao.upsertAll(entities)
    }

    override suspend fun getTransitCache(julianDayUt: Double): List<DailyTransitCacheEntity> =
        dailyTransitDao.getForDay(julianDayUt)

    override fun observeTransitCache(julianDayUt: Double): Flow<List<DailyTransitCacheEntity>> =
        dailyTransitDao.observeForDay(julianDayUt)

    override suspend fun isTransitCacheValid(julianDayUt: Double, nowEpochMs: Long): Boolean =
        dailyTransitDao.isValidCachePresent(julianDayUt, nowEpochMs) > 0

    override suspend fun purgeExpiredTransitCache(nowEpochMs: Long) {
        dailyTransitDao.purgeExpired(nowEpochMs)
    }

    // ── Closed-loop feedback ──────────────────────────────────────────────────

    override suspend fun insertFeedback(entity: ClosedLoopFeedbackEntity): Long =
        feedbackDao.insert(entity)

    override fun observeFeedbackForProfile(profileId: Long): Flow<List<ClosedLoopFeedbackEntity>> =
        feedbackDao.observeForProfile(profileId)

    override suspend fun getAverageRatingByType(profileId: Long, predictionType: String): Float? =
        feedbackDao.averageRatingByType(profileId, predictionType)

    override suspend fun updateFeedbackOutcome(predictionId: String, outcome: String) {
        feedbackDao.updateOutcome(predictionId, outcome, System.currentTimeMillis())
    }
}
