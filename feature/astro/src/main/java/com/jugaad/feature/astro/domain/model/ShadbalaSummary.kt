package com.jugaad.feature.astro.domain.model

/**
 * Simplified Shadbala (six-fold strength) summary for all 9 Vedic Grahas.
 *
 * Implementation uses dignity-based strength (Sthana Bala component only),
 * scaled to [0, 100] and adjusted for Graha Yuddha reductions. Full
 * multi-pillar Shadbala (Kala, Dig, Chesta, Naisargika, Drik) is deferred
 * to Phase 4 AI inference.
 *
 * Dignity strength tiers:
 *   Exaltation (Uccha)        → 100
 *   Own sign (Swakshetra)     →  75
 *   Great friend sign         →  65
 *   Friend sign               →  55
 *   Neutral sign              →  45
 *   Enemy sign                →  30
 *   Great enemy sign          →  20
 *   Debilitation (Neecha)     →  10
 *
 * Graha Yuddha reduces the loser's score by 50% (floor applied).
 */
data class ShadbalaSummary(
    /** Maps planetId (0–8) → strength score [0, 100]. */
    val scores: Map<Int, Int>,

    /**
     * List of planetary wars active at this chart moment.
     * Empty when no planets are within < 1.0° of each other.
     */
    val activeWars: List<GrahaYuddha>,

    /**
     * Set of planet IDs whose scores were reduced due to Graha Yuddha.
     * Useful for UI highlighting.
     */
    val warReducedPlanets: Set<Int>
) {
    /** Convenience: returns the strength score for a given planet ID. */
    fun scoreFor(planetId: Int): Int = scores[planetId] ?: 45
}
