package com.jugaad.feature.astro.domain.model

/**
 * Records a Graha Yuddha (Planetary War) event between two major planets.
 *
 * A planetary war occurs when two of the five major Grahas (Mars, Mercury,
 * Jupiter, Venus, Saturn — planet IDs 4, 2, 5, 3, 6) are within < 1.0° of
 * sidereal longitude of each other.
 *
 * Resolution rule (Drik Panchang standard):
 *   The planet with the lower ecliptic latitude (|latitudeDeg|) is the loser.
 *   The loser's Shadbala score is reduced by exactly 50%.
 */
data class GrahaYuddha(
    /** Planet ID of the first combatant (always the lower ID). */
    val planet1Id: Int,

    /** Planet ID of the second combatant. */
    val planet2Id: Int,

    /** Absolute sidereal longitude separation in degrees. Always < 1.0. */
    val longitudeSeparationDeg: Double,

    /** Planet ID of the loser — the one with lower |latitudeDeg|. */
    val loserPlanetId: Int,

    /** Planet ID of the winner. */
    val winnerPlanetId: Int,

    /** Latitude of the loser at the time of conflict (decimal degrees). */
    val loserLatitudeDeg: Double,

    /** Shadbala reduction applied to the loser. Always 50.0 per Drik standard. */
    val shadabalaReductionPct: Double = 50.0
)
