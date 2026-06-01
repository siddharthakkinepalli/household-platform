package com.jugaad.feature.astro.domain.model

import com.jugaad.core.ephemeris.dto.PlanetPosition

/**
 * Domain model for a complete daily transit snapshot.
 *
 * Returned by [GetDailyTransitUseCase] regardless of whether the data came from a live
 * EphemerisEngine JNI call or was reconstructed from the Room transit cache.
 *
 * NOTE: Lagna (Ascendant) is intentionally absent — it is observer-location-specific and
 * not stored in the shared transit cache. Use [BirthChart.houseData] for natal lagna,
 * or compute it on demand from [EphemerisEngine.computeLagna] when location is known.
 *
 * Field names mirror [PlanetPosition] exactly (no drift from Phase 2 canonical shape).
 */
data class DailyTransit(
    /** Julian Day (UT) this transit snapshot was computed for. */
    val julianDayUt: Double,

    /**
     * Sidereal positions of all 9 Vedic Grahas at [julianDayUt].
     * Ordered by planetId ascending: 0=Sun … 8=Ketu.
     * Fields: planetId, longitudeDeg, latitudeDeg, speedDegPerDay, retrograde,
     *         signId, degreeInSign, nakshatraId, pada, julianDayUt.
     */
    val planets: List<PlanetPosition>,

    /**
     * Active Graha Yuddha (planetary war) events at this moment.
     * Empty list when no eligible pair (Mercury/Venus/Mars/Jupiter/Saturn) is within 1°.
     */
    val grahaYuddhaList: List<GrahaYuddha>,

    /**
     * Dignity-based Shadbala scores [0, 100] for all 9 planets.
     * Losing planets in Graha Yuddha have their scores reduced by 50%.
     */
    val shadbalaSummary: ShadbalaSummary,

    /**
     * Token-compressed JSON context for Phase 4 local ONNX inference.
     * Format: {"0":[signId,lon,shadbala],…,"8":[signId,lon,shadbala]}
     * No "L" key — lagna is user/location-specific and not included in transit snapshots.
     */
    val contextPayload: ContextPayload,

    /**
     * True when this transit was reconstructed from stale Room cache.
     * Phase 4 inference should apply a lower confidence weight when true.
     */
    val fromCache: Boolean = false
)
