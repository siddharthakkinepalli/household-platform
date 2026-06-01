package com.jugaad.feature.astro.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores user feedback on astrological predictions for closed-loop model improvement.
 *
 * This table drives the feedback cycle:
 *   Prediction generated → user observes outcome → user rates prediction → stored here
 *   → aggregated to improve future inference model weights (core:ai-runtime).
 *
 * SECURITY CONTRACT: [actualOutcome] and [predictionContent] must be scrubbed of any
 * PII before insertion. The repository layer enforces this via AstroLogger.sanitize().
 */
@Entity(
    tableName = "closed_loop_feedback",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_profile_id"]),
        Index(value = ["prediction_id"], unique = true),
        Index(value = ["feedback_timestamp"])
    ]
)
data class ClosedLoopFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** FK → UserProfileEntity.id */
    @ColumnInfo(name = "user_profile_id")
    val userProfileId: Long,

    /** UUID string identifying the specific prediction this feedback refers to. */
    @ColumnInfo(name = "prediction_id")
    val predictionId: String,

    /**
     * Prediction domain: "DAILY_TRANSIT", "NATAL_STRENGTH", "MUHURTA",
     * "DASHA_PERIOD", "COMPATIBILITY".
     */
    @ColumnInfo(name = "prediction_type")
    val predictionType: String,

    /** The prediction text (PII-scrubbed). Must not contain birth data. */
    @ColumnInfo(name = "prediction_content")
    val predictionContent: String,

    /**
     * User rating: 1 (completely wrong) → 5 (exactly accurate).
     * Stored as-is; range validation enforced at repository layer.
     */
    @ColumnInfo(name = "user_rating")
    val userRating: Int,

    /** User's description of what actually happened (PII-scrubbed). Nullable. */
    @ColumnInfo(name = "actual_outcome")
    val actualOutcome: String?,

    /**
     * Confidence score from the inference model at prediction time (0.0–1.0).
     * Used for calibration analysis.
     */
    @ColumnInfo(name = "model_confidence")
    val modelConfidence: Float,

    /** Version string of the inference model that generated this prediction. */
    @ColumnInfo(name = "model_version")
    val modelVersion: String,

    @ColumnInfo(name = "prediction_timestamp")
    val predictionTimestamp: Long,

    @ColumnInfo(name = "feedback_timestamp")
    val feedbackTimestamp: Long = System.currentTimeMillis()
)
