package com.jugaad.feature.astro.processor

import com.jugaad.core.ephemeris.dto.HouseData
import com.jugaad.core.ephemeris.dto.PlanetPosition
import com.jugaad.feature.astro.domain.model.ContextPayload
import com.jugaad.feature.astro.domain.model.GrahaYuddha
import com.jugaad.feature.astro.domain.model.ShadbalaSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Stateless computation engine for the Vedic astrology domain layer.
 *
 * Responsibilities:
 *  1. Detect Graha Yuddha (planetary wars) — two major planets within < 1.0° longitude
 *  2. Compute simplified dignity-based Shadbala [0, 100] for all 9 Grahas
 *  3. Apply Graha Yuddha 50% reduction to the losing planet's score
 *  4. Map computed results into a token-compressed JSON payload for Phase 4 inference
 *
 * Thread safety: all methods are pure functions with no shared mutable state.
 * Safe to call from any coroutine context.
 */
@Singleton
class AstroDataProcessor @Inject constructor() {

    // ── Planet ID constants (mirrors sweph_bridge.h) ──────────────────────────
    private val SUN      = 0; private val MOON    = 1; private val MERCURY = 2
    private val VENUS    = 3; private val MARS    = 4; private val JUPITER = 5
    private val SATURN   = 6; private val RAHU    = 7; private val KETU    = 8

    /**
     * The five major Grahas eligible for Graha Yuddha.
     * Sun, Moon, Rahu, Ketu are excluded (luminaries and nodes do not fight).
     */
    private val YUDDHA_ELIGIBLE = setOf(MERCURY, VENUS, MARS, JUPITER, SATURN)

    // ── Dignity map: planetId → Triple(exaltSignId, debilSignId, ownSignIds) ──
    // Sign IDs: 0=Aries, 1=Taurus, 2=Gemini, 3=Cancer, 4=Leo, 5=Virgo,
    //           6=Libra, 7=Scorpio, 8=Sagittarius, 9=Capricorn, 10=Aquarius, 11=Pisces
    private data class DignityData(
        val exaltSign: Int,
        val debilSign: Int,
        val ownSigns: Set<Int>
    )

    private val DIGNITY: Map<Int, DignityData> = mapOf(
        SUN     to DignityData(exaltSign=0,  debilSign=6,  ownSigns=setOf(4)),
        MOON    to DignityData(exaltSign=1,  debilSign=7,  ownSigns=setOf(3)),
        MERCURY to DignityData(exaltSign=5,  debilSign=11, ownSigns=setOf(2, 5)),
        VENUS   to DignityData(exaltSign=11, debilSign=5,  ownSigns=setOf(1, 6)),
        MARS    to DignityData(exaltSign=9,  debilSign=3,  ownSigns=setOf(0, 7)),
        JUPITER to DignityData(exaltSign=3,  debilSign=9,  ownSigns=setOf(8, 11)),
        SATURN  to DignityData(exaltSign=6,  debilSign=0,  ownSigns=setOf(9, 10)),
        RAHU    to DignityData(exaltSign=2,  debilSign=8,  ownSigns=setOf(10)),
        KETU    to DignityData(exaltSign=7,  debilSign=1,  ownSigns=setOf(11))
    )

    // ── Sign lords: signId → owning planetId ─────────────────────────────────
    private val SIGN_LORD: Map<Int, Int> = mapOf(
        0 to MARS, 1 to VENUS, 2 to MERCURY, 3 to MOON, 4 to SUN, 5 to MERCURY,
        6 to VENUS, 7 to MARS, 8 to JUPITER, 9 to SATURN, 10 to SATURN, 11 to JUPITER
    )

    // ── Planetary friendship table (traditional Vedic) ────────────────────────
    private val FRIENDS: Map<Int, Set<Int>> = mapOf(
        SUN     to setOf(MOON, MARS, JUPITER),
        MOON    to setOf(SUN, MERCURY),
        MERCURY to setOf(SUN, VENUS),
        VENUS   to setOf(MERCURY, SATURN),
        MARS    to setOf(SUN, MOON, JUPITER),
        JUPITER to setOf(SUN, MOON, MARS),
        SATURN  to setOf(MERCURY, VENUS),
        RAHU    to setOf(MERCURY, VENUS, SATURN),
        KETU    to setOf(MARS, JUPITER, SATURN)
    )
    private val ENEMIES: Map<Int, Set<Int>> = mapOf(
        SUN     to setOf(VENUS, SATURN),
        MOON    to setOf(/* Moon has no traditional enemies */ ),
        MERCURY to setOf(MOON),
        VENUS   to setOf(SUN, MOON),
        MARS    to setOf(MERCURY),
        JUPITER to setOf(MERCURY, VENUS),
        SATURN  to setOf(SUN, MOON, MARS),
        RAHU    to setOf(SUN, MOON, MARS),
        KETU    to setOf(VENUS, MERCURY)
    )

    // ── Dignity score values ──────────────────────────────────────────────────
    private val SCORE_EXALT       = 100
    private val SCORE_OWN         =  75
    private val SCORE_GREAT_FRIEND=  65
    private val SCORE_FRIEND      =  55
    private val SCORE_NEUTRAL     =  45
    private val SCORE_ENEMY       =  30
    private val SCORE_GREAT_ENEMY =  20
    private val SCORE_DEBIL       =  10

    // ── Graha Yuddha detection ────────────────────────────────────────────────

    /**
     * Detects all active Graha Yuddha events in [planets].
     *
     * Algorithm:
     *   For each pair of Yuddha-eligible planets (Mercury, Venus, Mars, Jupiter, Saturn):
     *     1. Compute minimum longitudinal separation (accounting for 0°/360° wrap)
     *     2. If separation < 1.0°: mark as war
     *     3. Loser = the planet with lower |latitudeDeg|
     *
     * @param planets Full list of 9 PlanetPosition objects from EphemerisEngine
     * @return List of GrahaYuddha events, empty if none
     */
    fun detectGrahaYuddha(planets: List<PlanetPosition>): List<GrahaYuddha> {
        val eligible = planets.filter { it.planetId in YUDDHA_ELIGIBLE }
        val wars = mutableListOf<GrahaYuddha>()

        for (i in eligible.indices) {
            for (j in (i + 1) until eligible.size) {
                val p1 = eligible[i]
                val p2 = eligible[j]

                // Longitudinal separation with wrap-around
                val rawDiff = abs(p1.longitudeDeg - p2.longitudeDeg)
                val separation = if (rawDiff > 180.0) 360.0 - rawDiff else rawDiff

                if (separation < 1.0) {
                    // Loser has lower absolute ecliptic latitude
                    val loser  = if (abs(p1.latitudeDeg) <= abs(p2.latitudeDeg)) p1 else p2
                    val winner = if (loser.planetId == p1.planetId) p2 else p1

                    wars += GrahaYuddha(
                        planet1Id              = minOf(p1.planetId, p2.planetId),
                        planet2Id              = maxOf(p1.planetId, p2.planetId),
                        longitudeSeparationDeg = separation,
                        loserPlanetId          = loser.planetId,
                        winnerPlanetId         = winner.planetId,
                        loserLatitudeDeg       = loser.latitudeDeg
                    )
                }
            }
        }
        return wars
    }

    // ── Shadbala computation ─────────────────────────────────────────────────

    /**
     * Computes dignity-based Shadbala for all 9 planets, then applies
     * Graha Yuddha 50% reductions to losing planets.
     *
     * @param planets   Full 9-planet list from EphemerisEngine
     * @param wars      Active Graha Yuddha events (from [detectGrahaYuddha])
     */
    fun computeShadbala(
        planets: List<PlanetPosition>,
        wars: List<GrahaYuddha>
    ): ShadbalaSummary {
        val loserIds = wars.map { it.loserPlanetId }.toSet()

        val scores = planets.associate { planet ->
            val baseScore = dignityScore(planet)
            val finalScore = if (planet.planetId in loserIds) {
                (baseScore * 0.5).roundToInt().coerceAtLeast(1)
            } else {
                baseScore
            }
            planet.planetId to finalScore
        }

        return ShadbalaSummary(
            scores             = scores,
            activeWars         = wars,
            warReducedPlanets  = loserIds
        )
    }

    /**
     * Returns the dignity strength score [0, 100] for a single planet.
     *
     * Checks (in priority order): exaltation, own sign, debilitation,
     * then sign-lord friendship tier (great friend, friend, neutral, enemy, great enemy).
     */
    private fun dignityScore(planet: PlanetPosition): Int {
        val dignity = DIGNITY[planet.planetId] ?: return SCORE_NEUTRAL

        return when {
            planet.signId == dignity.exaltSign       -> SCORE_EXALT
            planet.signId in dignity.ownSigns        -> SCORE_OWN
            planet.signId == dignity.debilSign       -> SCORE_DEBIL
            else -> {
                val lord = SIGN_LORD[planet.signId] ?: return SCORE_NEUTRAL
                if (lord == planet.planetId) return SCORE_OWN  // moolatrikona fallback

                val friends = FRIENDS[planet.planetId] ?: emptySet()
                val enemies = ENEMIES[planet.planetId] ?: emptySet()

                // Check compound (mutual) relationship for great friend/enemy
                val lordFriends = FRIENDS[lord] ?: emptySet()
                val lordEnemies = ENEMIES[lord] ?: emptySet()

                when {
                    lord in friends && planet.planetId in lordFriends -> SCORE_GREAT_FRIEND
                    lord in enemies && planet.planetId in lordEnemies -> SCORE_GREAT_ENEMY
                    lord in friends -> SCORE_FRIEND
                    lord in enemies -> SCORE_ENEMY
                    else            -> SCORE_NEUTRAL
                }
            }
        }
    }

    // ── Context payload compilation ───────────────────────────────────────────

    /**
     * Compiles all computed data into a token-compressed JSON map for LLM injection.
     *
     * Output format (compact, no whitespace):
     *   {"0":[signId,lon,shadbala],"1":[...],...,"8":[...],"L":[lagnaSign,lagnaLon]}
     *
     * - signId    : integer [0–11]
     * - lon       : sidereal longitude rounded to 1 decimal place
     * - shadbala  : integer [0–100], Graha Yuddha-adjusted
     * - L[0]      : Lagna sign index [0–11]
     * - L[1]      : Lagna degree in sign rounded to 1 decimal place
     *
     * Estimated token cost: ~40–55 tokens for 9 planets + Lagna.
     *
     * @param planets       9-planet list (Phase 2 PlanetPosition — fields used: planetId, signId, longitudeDeg)
     * @param houseData     Phase 2 HouseData — fields used: lagnaSignId, lagnaInSign
     * @param shadbalaSummary Computed Shadbala scores
     * @param julianDayUt   Timestamp of computation
     * @param fromCache     True if data was reconstructed from stale Room cache
     */
    fun buildContextPayload(
        planets: List<PlanetPosition>,
        houseData: HouseData,
        shadbalaSummary: ShadbalaSummary,
        julianDayUt: Double,
        fromCache: Boolean = false
    ): ContextPayload {
        val sb = StringBuilder("{")

        // Planet entries
        planets.sortedBy { it.planetId }.forEachIndexed { idx, planet ->
            if (idx > 0) sb.append(',')
            val lon      = (planet.longitudeDeg * 10.0).roundToInt() / 10.0
            val shadbala = shadbalaSummary.scoreFor(planet.planetId)
            sb.append('"').append(planet.planetId).append('"')
            sb.append(":[").append(planet.signId).append(',')
              .append(lon).append(',').append(shadbala).append(']')
        }

        // Lagna entry
        val lagnaLon = (houseData.lagnaInSign * 10.0).roundToInt() / 10.0
        sb.append(",\"L\":[").append(houseData.lagnaSignId).append(',').append(lagnaLon).append("]}")

        val json = sb.toString()
        // Token estimate: 1 token ≈ 4 chars (conservative for numeric JSON)
        val tokenEst = (json.length / 4.0).roundToInt().coerceAtLeast(1)

        return ContextPayload(
            json          = json,
            tokenEstimate = tokenEst,
            julianDayUt   = julianDayUt,
            fromCache     = fromCache
        )
    }

    /**
     * Variant of [buildContextPayload] that omits the Lagna "L" key.
     *
     * Used by [GetDailyTransitUseCase] where transit positions are universal
     * (same for all observers) but lagna is location-specific and not cached.
     * The ONNX model in Phase 4 handles missing "L" gracefully.
     *
     * Output format: {"0":[signId,lon,shadbala],…,"8":[signId,lon,shadbala]}
     */
    fun buildContextPayloadNoLagna(
        planets: List<PlanetPosition>,
        shadbalaSummary: ShadbalaSummary,
        julianDayUt: Double,
        fromCache: Boolean = false
    ): ContextPayload {
        val sb = StringBuilder("{")
        planets.sortedBy { it.planetId }.forEachIndexed { idx, planet ->
            if (idx > 0) sb.append(',')
            val lon      = (planet.longitudeDeg * 10.0).roundToInt() / 10.0
            val shadbala = shadbalaSummary.scoreFor(planet.planetId)
            sb.append('"').append(planet.planetId).append('"')
            sb.append(":[").append(planet.signId).append(',')
              .append(lon).append(',').append(shadbala).append(']')
        }
        sb.append("}")
        val json     = sb.toString()
        val tokenEst = (json.length / 4.0).roundToInt().coerceAtLeast(1)
        return ContextPayload(json = json, tokenEstimate = tokenEst, julianDayUt = julianDayUt, fromCache = fromCache)
    }

    // ── Transit cache reconstruction ──────────────────────────────────────────

    /**
     * Reconstructs a [PlanetPosition] list from cached [DailyTransitCacheEntity] rows.
     *
     * Mapping is explicit — every field name matches exactly:
     *   entity.transitDateJd    → PlanetPosition.julianDayUt
     *   entity.planetId         → PlanetPosition.planetId
     *   entity.signId           → PlanetPosition.signId
     *   entity.housePosition    → PlanetPosition.degreeInSign
     *   entity.nakshatraId      → PlanetPosition.nakshatraId
     *   entity.nakshatraPada    → PlanetPosition.pada
     *   entity.retrograde       → PlanetPosition.retrograde
     *   entity.speedDegPerDay   → PlanetPosition.speedDegPerDay
     *
     * latitudeDeg is set to 0.0 — not stored in cache. This is acceptable for
     * transit analysis but means Graha Yuddha loser detection uses 0.0 latitude
     * (ties are broken by the lower planetId in this edge case).
     */
    fun reconstructFromCache(
        entities: List<com.jugaad.feature.astro.data.db.entity.DailyTransitCacheEntity>
    ): List<PlanetPosition> = entities.map { entity ->
        val longitudeDeg = entity.signId * 30.0 + entity.housePosition
        PlanetPosition(
            planetId       = entity.planetId,
            longitudeDeg   = longitudeDeg,
            latitudeDeg    = 0.0,           // not cached — see contract above
            speedDegPerDay = entity.speedDegPerDay,
            retrograde     = entity.retrograde,
            signId         = entity.signId,
            degreeInSign   = entity.housePosition,
            nakshatraId    = entity.nakshatraId,
            pada           = entity.nakshatraPada,
            julianDayUt    = entity.transitDateJd
        )
    }.sortedBy { it.planetId }

    /**
     * Converts a [PlanetPosition] list into [DailyTransitCacheEntity] rows
     * for persistence. Sets expiry to 24 hours from [computedAt].
     */
    fun toCacheEntities(
        planets: List<PlanetPosition>,
        computedAt: Long = System.currentTimeMillis()
    ): List<com.jugaad.feature.astro.data.db.entity.DailyTransitCacheEntity> {
        val expiresAt = computedAt + 24 * 60 * 60 * 1000L  // 24h TTL
        return planets.map { planet ->
            com.jugaad.feature.astro.data.db.entity.DailyTransitCacheEntity(
                transitDateJd  = planet.julianDayUt,
                planetId       = planet.planetId,
                signId         = planet.signId,
                housePosition  = planet.degreeInSign,
                nakshatraId    = planet.nakshatraId,
                nakshatraPada  = planet.pada,
                retrograde     = planet.retrograde,
                aspectData     = "[]",           // aspects computed separately in Phase 4
                speedDegPerDay = planet.speedDegPerDay,
                computedAt     = computedAt,
                expiresAt      = expiresAt
            )
        }
    }
}
