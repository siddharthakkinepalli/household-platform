package com.jugaad.core.ephemeris.dto

/**
 * Whole Sign house system output for a given birth location and time.
 *
 * In Whole Sign houses, the Lagna determines the 1st house sign,
 * and each subsequent sign = next house. House cusps are simply
 * 0° of each sign starting from the Lagna sign.
 */
data class HouseData(
    /**
     * Lagna (Ascendant) sidereal longitude in decimal degrees [0, 360).
     * This is the true eastern horizon intersection with the ecliptic.
     */
    val lagnaLongitudeDeg: Double,

    /**
     * Lagna sign index (0=Aries … 11=Pisces).
     * Derived: lagnaSignId = floor(lagnaLongitudeDeg / 30).
     */
    val lagnaSignId: Int,

    /**
     * Degree of Lagna within its sign [0.0, 30.0).
     */
    val lagnaInSign: Double,

    /**
     * Nakshatra index of the Lagna [0, 26].
     */
    val lagnaNakshatraId: Int,

    /**
     * Pada (quarter) of Lagna nakshatra [1, 4].
     */
    val lagnaPada: Int,

    /**
     * Whole Sign house assignments for each planet.
     * Maps planetId → house number [1, 12].
     * House number = ((planet.signId - lagnaSignId + 12) % 12) + 1
     */
    val planetHouses: Map<Int, Int>,

    /** Julian Day (UT) at which this computation was made. */
    val julianDayUt: Double
)
