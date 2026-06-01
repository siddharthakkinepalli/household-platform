package com.jugaad.core.ephemeris.dto

/**
 * Immutable transfer object for a single planet's sidereal position.
 *
 * THIS IS THE CANONICAL DATA SHAPE for Phase 2 → Phase 3 handoff.
 * Phase 3 domain models MUST map from these exact field names.
 * Do not rename fields without updating AstroRepositoryImpl and BirthChart.
 *
 * Planet ID constants:
 *   0=Sun  1=Moon  2=Mercury  3=Venus  4=Mars
 *   5=Jupiter  6=Saturn  7=Rahu(TrueNode)  8=Ketu
 */
data class PlanetPosition(
    /** Planet identifier (0–8). See constants above. */
    val planetId: Int,

    /** Sidereal longitude in decimal degrees [0, 360). Lahiri ayanamsha applied. */
    val longitudeDeg: Double,

    /** Ecliptic latitude in decimal degrees. Near zero for most planets. */
    val latitudeDeg: Double,

    /** Daily motion in degrees/day. Negative indicates retrograde. */
    val speedDegPerDay: Double,

    /** True when the planet appears to move backward (speedDegPerDay < 0). */
    val retrograde: Boolean,

    /**
     * Vedic zodiac sign index: 0=Aries, 1=Taurus, … 11=Pisces.
     * Derived from longitudeDeg: signId = floor(longitudeDeg / 30).
     */
    val signId: Int,

    /**
     * Degree within the sign [0.0, 30.0).
     * Derived from longitudeDeg: degreeInSign = longitudeDeg % 30.
     */
    val degreeInSign: Double,

    /**
     * Nakshatra index [0, 26]. 27 Nakshatras × 13°20' each.
     * Derived: nakshatraId = floor(longitudeDeg / (360.0 / 27)).
     */
    val nakshatraId: Int,

    /**
     * Nakshatra Pada (quarter) [1, 4].
     * Each Nakshatra = 4 padas × 3°20'. Derived from position within nakshatra.
     */
    val pada: Int,

    /** Julian Day (UT) at which this position was computed. */
    val julianDayUt: Double
)
