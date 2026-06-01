package com.jugaad.feature.astro.domain.model

import com.jugaad.core.ephemeris.dto.HouseData
import com.jugaad.core.ephemeris.dto.PlanetPosition
import com.jugaad.core.ephemeris.dto.SunriseSunset
import com.jugaad.core.time.VedicCalendar

/**
 * Complete Drik Panchang output for a given observer location and Julian Day.
 *
 * All planet positions strictly use [PlanetPosition] from Phase 2 — no field renames.
 * All Panchanga fields strictly use [VedicCalendar.Panchanga] — no remapping.
 */
data class DailyPanchang(
    /** Julian Day (UT) this Panchang is computed for. */
    val julianDayUt: Double,

    /**
     * Sidereal positions of all 9 Vedic Grahas at this Julian Day.
     * Ordered by planetId: 0=Sun … 8=Ketu.
     * Field names are immutable — sourced directly from [PlanetPosition].
     */
    val planets: List<PlanetPosition>,

    /** Five-limb Panchanga: tithi, paksha, nakshatra, yoga, karana, vara. */
    val panchanga: VedicCalendar.Panchanga,

    /** Topographic sunrise/sunset for the observer's location. */
    val sunriseSunset: SunriseSunset,

    /**
     * Whole Sign house assignments based on the observer's Lagna for this time.
     * Contains lagnaLongitudeDeg, lagnaSignId, lagnaInSign, planetHouses.
     */
    val houseData: HouseData,

    /**
     * Active Graha Yuddha events at this moment.
     * Empty list when no major planets are within < 1.0° longitude.
     */
    val grahaYuddhaList: List<GrahaYuddha>,

    /** Shadbala scores for all 9 planets, Graha Yuddha reductions applied. */
    val shadbalaSummary: ShadbalaSummary,

    /**
     * Token-compressed JSON context payload ready for Phase 4 local inference.
     * Format: {"0":[signId,lon,shadbala],…,"L":[lagnaSign,lagnaLon]}
     */
    val contextPayload: ContextPayload,

    /**
     * True when this Panchang was assembled from cached Room data because the
     * live EphemerisEngine JNI call failed. Phase 4 inference must treat
     * stale results with lower confidence.
     */
    val isStale: Boolean = false
)
