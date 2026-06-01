package com.jugaad.feature.astro.domain.model

/**
 * Token-compressed numerical JSON map for local LLM inference.
 *
 * Format (compact, no whitespace):
 *   {"0":[signId,lon,shadbala],"1":[...],...,"8":[...],"L":[lagnaSign,lagnaLon]}
 *
 * Key legend:
 *   "0"–"8" = planet IDs (Sun → Ketu), matching PlanetPosition.planetId
 *   "L"     = Lagna (Ascendant)
 *
 * Value array per planet:
 *   [0] signId     : Int [0–11]         — zodiac sign
 *   [1] lon        : Double (1dp)       — sidereal longitude rounded to 1 decimal
 *   [2] shadbala   : Int [0–100]        — Shadbala score (Graha Yuddha-adjusted)
 *
 * Value array for Lagna:
 *   [0] lagnaSign  : Int [0–11]
 *   [1] lagnaLon   : Double (1dp)       — degree within sign
 *
 * Estimated token count: ~40–60 tokens (well under the 150-token budget).
 * No planet names, sign names, or prose — purely numeric for minimal prompt overhead.
 */
data class ContextPayload(
    /** Compact JSON string ready for LLM context injection. */
    val json: String,

    /** Estimated token count (chars / 4 approximation). */
    val tokenEstimate: Int,

    /** Julian Day at which this payload was generated. */
    val julianDayUt: Double,

    /** True if this payload was reconstructed from stale cache data. */
    val fromCache: Boolean = false
)
