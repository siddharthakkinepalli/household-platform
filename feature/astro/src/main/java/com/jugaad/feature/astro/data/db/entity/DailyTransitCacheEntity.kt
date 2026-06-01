package com.jugaad.feature.astro.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache of ephemeris-computed planetary transit positions.
 *
 * Transit data is astronomical output — NOT PII. Planet positions for a given
 * Julian Day are the same for all users. This table acts as a computed cache
 * to avoid redundant Swiss Ephemeris JNI calls.
 *
 * Indexed on (transit_date_jd, planet_id) for O(log n) daily lookups.
 */
@Entity(
    tableName = "daily_transit_cache",
    indices = [
        Index(value = ["transit_date_jd", "planet_id"], unique = true),
        Index(value = ["expires_at"])
    ]
)
data class DailyTransitCacheEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Julian Day Number for the transit date (e.g. 2460500.5 for 2024-07-01 00:00 UTC). */
    @ColumnInfo(name = "transit_date_jd")
    val transitDateJd: Double,

    /**
     * Vedic planet code:
     * 0=Sun, 1=Moon, 2=Mercury, 3=Venus, 4=Mars,
     * 5=Jupiter, 6=Saturn, 7=Rahu (N.Node), 8=Ketu (S.Node)
     */
    @ColumnInfo(name = "planet_id")
    val planetId: Int,

    /** Zodiac sign index (0=Aries … 11=Pisces) in the Vedic (sidereal) system. */
    @ColumnInfo(name = "sign_id")
    val signId: Int,

    /** Degree within the sign (0.0–29.999). */
    @ColumnInfo(name = "house_position")
    val housePosition: Double,

    /** Nakshatra index (0–26). */
    @ColumnInfo(name = "nakshatra_id")
    val nakshatraId: Int,

    /** Pada (quarter) within the Nakshatra (1–4). */
    @ColumnInfo(name = "nakshatra_pada")
    val nakshatraPada: Int,

    /** True when the planet is in apparent retrograde motion. */
    @ColumnInfo(name = "retrograde")
    val retrograde: Boolean,

    /**
     * JSON array of aspect objects: [{"planet":Int,"aspect":String,"orb":Double}].
     * Aspects are Vedic (conjunction, trine, square, opposition, sextile).
     */
    @ColumnInfo(name = "aspect_data")
    val aspectData: String,

    /** Speed of the planet in degrees per day (negative = retrograde). */
    @ColumnInfo(name = "speed_deg_per_day")
    val speedDegPerDay: Double,

    @ColumnInfo(name = "computed_at")
    val computedAt: Long = System.currentTimeMillis(),

    /** Cache expiry epoch millis — recompute after this timestamp. */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)
