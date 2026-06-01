package com.jugaad.feature.astro.domain.model

import com.jugaad.core.ephemeris.dto.HouseData
import com.jugaad.core.ephemeris.dto.PlanetPosition

/**
 * Complete natal (birth) chart for a user profile.
 *
 * All planet positions use [PlanetPosition] from Phase 2 with exact field names.
 * All house data uses [HouseData] from Phase 2 — lagnaLongitudeDeg, lagnaSignId,
 * lagnaInSign, planetHouses, etc. — no renaming.
 */
data class BirthChart(
    /** FK to [com.jugaad.feature.astro.data.db.entity.UserProfileEntity.id]. */
    val profileId: Long,

    /**
     * Sidereal positions of all 9 Grahas at the moment of birth.
     * Ordered by planetId ascending (0=Sun … 8=Ketu).
     */
    val planets: List<PlanetPosition>,

    /**
     * Whole Sign house data: Lagna, house assignments for all planets.
     * planetHouses: Map<Int, Int> where key=planetId, value=house[1,12].
     */
    val houseData: HouseData,

    /** Lahiri ayanamsha value in degrees at the birth Julian Day. */
    val ayanamshaDeg: Double,

    /** Julian Day (UT) of birth. */
    val julianDayUt: Double,

    /** Shadbala scores computed at birth moment, Graha Yuddha-adjusted. */
    val shadbalaSummary: ShadbalaSummary,

    /**
     * Planetary wars active at the exact moment of birth.
     * These are permanent natal conditions, distinct from transit wars.
     */
    val natalGrahaYuddhaList: List<GrahaYuddha>,

    /**
     * Token-compressed context payload for Phase 4 natal inference.
     * Identical format to DailyPanchang.contextPayload but represents natal positions.
     */
    val contextPayload: ContextPayload
)
