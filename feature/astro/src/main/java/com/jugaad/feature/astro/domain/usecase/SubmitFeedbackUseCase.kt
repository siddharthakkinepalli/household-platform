package com.jugaad.feature.astro.domain.usecase

import com.jugaad.core.security.log.AstroLogger
import com.jugaad.feature.astro.data.db.entity.ClosedLoopFeedbackEntity
import com.jugaad.feature.astro.domain.repository.AstroRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Input for a single prediction feedback submission.
 *
 * [predictionContent] and [actualOutcome] are PII-sanitized by [SubmitFeedbackUseCase]
 * before persistence — callers do not need to pre-sanitize.
 */
data class FeedbackInput(
    val profileId: Long,
    /** UUID string identifying the specific prediction. */
    val predictionId: String,
    /** One of: "DAILY_TRANSIT", "NATAL_STRENGTH", "MUHURTA", "DASHA_PERIOD", "COMPATIBILITY". */
    val predictionType: String,
    /** Prediction text as shown to the user. Will be sanitized before storage. */
    val predictionContent: String,
    /** User accuracy rating: 1 (completely wrong) → 5 (exactly accurate). */
    val userRating: Int,
    /** What actually happened — user's description. Nullable, sanitized before storage. */
    val actualOutcome: String? = null,
    /** Inference model confidence at prediction time (0.0–1.0). */
    val modelConfidence: Float,
    val modelVersion: String,
    val predictionTimestamp: Long
)

/**
 * Validates, sanitizes, and persists a user feedback record for closed-loop model improvement.
 *
 * Security contract:
 *  - [FeedbackInput.predictionContent] and [FeedbackInput.actualOutcome] are run through
 *    [AstroLogger.sanitize] to strip any PII patterns before the entity is written to Room.
 *  - [FeedbackInput.userRating] is validated to [1, 5]; out-of-range values throw [IllegalArgumentException].
 */
@Singleton
class SubmitFeedbackUseCase @Inject constructor(
    private val repository: AstroRepository,
    private val logger: AstroLogger
) {

    suspend fun execute(input: FeedbackInput): Long {
        require(input.userRating in 1..5) {
            "userRating must be in [1, 5], got ${input.userRating}"
        }
        require(input.predictionId.isNotBlank()) { "predictionId must not be blank" }
        require(input.predictionType.isNotBlank()) { "predictionType must not be blank" }

        val sanitizedContent = logger.sanitize(input.predictionContent)
        val sanitizedOutcome = input.actualOutcome?.let { logger.sanitize(it) }

        val entity = ClosedLoopFeedbackEntity(
            userProfileId      = input.profileId,
            predictionId       = input.predictionId,
            predictionType     = input.predictionType,
            predictionContent  = sanitizedContent,
            userRating         = input.userRating,
            actualOutcome      = sanitizedOutcome,
            modelConfidence    = input.modelConfidence,
            modelVersion       = input.modelVersion,
            predictionTimestamp = input.predictionTimestamp
        )

        return repository.insertFeedback(entity).also { rowId ->
            logger.log(AstroLogger.Level.INFO, TAG, "FEEDBACK_SUBMITTED",
                mapOf(
                    "rowId"  to rowId.toString(),
                    "type"   to input.predictionType,
                    "rating" to input.userRating.toString()
                )
            )
        }
    }

    companion object {
        private const val TAG = "AstroFeedback"
    }
}
