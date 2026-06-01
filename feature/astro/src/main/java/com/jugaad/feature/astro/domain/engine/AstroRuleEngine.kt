package com.jugaad.feature.astro.domain.engine

import com.jugaad.feature.astro.domain.model.DailyTransit
import com.jugaad.feature.astro.domain.model.EventAssessment
import com.jugaad.feature.astro.domain.model.LifeEventCategory
import com.jugaad.feature.astro.domain.model.NumerologyResult
import com.jugaad.feature.astro.domain.model.Verdict
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class RuleInsight(
    val score: Int,
    val summary: String,
    val auspiciousWindows: List<String>,
    val avoidWindows: List<String>
)

/**
 * Deterministic rule engine — pure on-device, zero network, always instant.
 *
 * Two entry points:
 *  - [generateDeterministicInsight]: daily momentum score + summary
 *  - [evaluateLifeEvent]: cross-domain astro × numerology score per decision category
 *
 * Timing helpers (shared by both):
 *  - Hora (planetary hour): Chaldean sequence from day's sunrise (~06:00)
 *  - Rahu Kaal: standard 90-min slot by day of week
 */
@Singleton
class AstroRuleEngine @Inject constructor() {

    // ── Hora / timing tables ──────────────────────────────────────────────────

    private val HORA_SEQUENCE = intArrayOf(0, 3, 2, 1, 6, 5, 4) // Sun,Venus,Mercury,Moon,Saturn,Jupiter,Mars
    private val DAY_RULERS    = intArrayOf(0, 1, 4, 2, 5, 3, 6)  // Sun=0…Sat=6
    private val HORA_NAME     = mapOf(0 to "Sun", 1 to "Moon", 2 to "Mercury", 3 to "Venus", 4 to "Mars", 5 to "Jupiter", 6 to "Saturn")
    private val HORA_TAG      = mapOf(2 to "Communication & Learning", 3 to "Finance & Relationships", 5 to "Expansion & Clarity")
    private val BENEFIC_IDS   = setOf(2, 3, 5)
    private val RAHU_SLOT     = intArrayOf(8, 2, 7, 5, 6, 4, 3)  // Sun=0…Sat=6

    // ── Daily Momentum ────────────────────────────────────────────────────────

    fun generateDeterministicInsight(
        transit: DailyTransit,
        date: LocalDate = LocalDate.now()
    ): RuleInsight {
        val scores          = transit.shadbalaSummary.scores
        val moonStrength    = scores[1] ?: 50
        val mercuryStrength = scores[2] ?: 50
        val jupiterStrength = scores[5] ?: 50
        val avgStrength     = scores.values.average().toInt()

        val momentumScore = ((avgStrength * 0.4) + (moonStrength * 0.4) + (mercuryStrength * 0.2))
            .toInt().coerceIn(0, 100)

        val insights = mutableListOf<String>()
        if (moonStrength > 70)             insights.add("Emotional clarity is high; trust your intuition.")
        if (mercuryStrength < 40)          insights.add("Expect minor communication delays; double-check emails.")
        if (jupiterStrength > 70)          insights.add("Expansion energy is strong — good for new plans and investments.")
        if (transit.grahaYuddhaList.isNotEmpty()) insights.add("Planetary tension detected; avoid confrontational meetings.")

        return RuleInsight(
            score             = momentumScore,
            summary           = insights.joinToString(" ").ifBlank { "A balanced day for routine tasks. Focus on steady progress." },
            auspiciousWindows = auspiciousHoras(date),
            avoidWindows      = rahuKaalWindow(date)
        )
    }

    // ── Life Event Planner ────────────────────────────────────────────────────

    /**
     * Evaluates today's astro + numerology alignment for a specific life decision.
     * Score 0–100: GO ≥ 70 · CAUTION 40–69 · NO_GO < 40.
     */
    fun evaluateLifeEvent(
        category: LifeEventCategory,
        transit: DailyTransit,
        numerology: NumerologyResult?,
        date: LocalDate = LocalDate.now()
    ): EventAssessment {
        val scores      = transit.shadbalaSummary.scores
        val mercury     = scores[2] ?: 50
        val jupiter     = scores[5] ?: 50
        val venus       = scores[3] ?: 50
        val sun         = scores[0] ?: 50
        val moon        = scores[1] ?: 50
        val saturn      = scores[6] ?: 50
        val mercuryRetro = transit.planets.firstOrNull { it.planetId == 2 }?.retrograde ?: false
        val saturnRetro  = transit.planets.firstOrNull { it.planetId == 6 }?.retrograde ?: false
        val marsInWar    = transit.grahaYuddhaList.any { it.planet1Id == 4 || it.planet2Id == 4 }
        val hasAnyWar    = transit.grahaYuddhaList.isNotEmpty()
        val pDay         = numerology?.personalDayNumber ?: 0

        var score = 50
        val astroIndicator: String
        val numIndicator: String
        val advice: String

        when (category) {
            LifeEventCategory.CONTRACT_SIGNING -> {
                astroIndicator = when {
                    mercuryRetro         -> { score -= 25; "Mercury Retrograde — read every clause, delays likely" }
                    mercury > 65         -> { score += 15; "Mercury direct & strong — communication is clear" }
                    else                 ->              "Mercury neutral — proceed with normal care"
                }
                if (jupiter > 65)  score += 10
                if (hasAnyWar)     score -= 10
                val (ns, ni) = numerologyScore(pDay,
                    boost = mapOf(1 to 15, 8 to 15),
                    drag  = mapOf(2 to -5, 5 to -15, 9 to -10),
                    defaultLabel = "Neutral for agreements"
                )
                score += ns; numIndicator = ni
                advice = when {
                    score < 40 -> "Postpone signing — too many flags today."
                    score < 70 -> "Review all terms carefully; ask for 24h to consider."
                    else       -> "Favourable alignment — confirm and sign."
                }
            }

            LifeEventCategory.NEW_HABIT -> {
                astroIndicator = when {
                    moon > 65   -> { score += 20; "Moon strong — emotional commitment flows easily" }
                    moon < 35   -> { score -= 15; "Moon weak — sustained routine harder to anchor" }
                    saturn > 60 -> { score += 10; "Saturn's discipline reinforces new routines" }
                    else        ->               "Neutral planetary alignment"
                }
                if (jupiter > 65)  score += 15
                if (mercuryRetro)  score -= 10
                val (ns, ni) = numerologyScore(pDay,
                    boost = mapOf(1 to 20, 4 to 15, 6 to 10),
                    drag  = mapOf(5 to -10, 9 to -15),
                    defaultLabel = "Neutral for beginnings"
                )
                score += ns; numIndicator = ni
                advice = when {
                    score < 40 -> "Wait for a more grounded day to start."
                    score < 70 -> "Good enough — start small and anchor the habit in the first 3 days."
                    else       -> "Strong alignment — start today and build momentum."
                }
            }

            LifeEventCategory.TRAVEL -> {
                astroIndicator = when {
                    mercuryRetro -> { score -= 15; "Mercury Retrograde — booking errors and miscommunications" }
                    marsInWar    -> { score -= 15; "Mars in Planetary War — travel friction and delays" }
                    venus > 65   -> { score += 20; "Venus strong — journeys are pleasant and fruitful" }
                    jupiter > 65 -> { score += 15; "Jupiter expands the horizon" }
                    else         ->               "Neutral alignment for travel"
                }
                val (ns, ni) = numerologyScore(pDay,
                    boost = mapOf(3 to 15, 5 to 10, 9 to 10),
                    drag  = mapOf(4 to -10, 7 to -10),
                    defaultLabel = "Neutral for travel"
                )
                score += ns; numIndicator = ni
                advice = when {
                    score < 40 -> "Reschedule if possible — conditions are unfavourable."
                    score < 70 -> "Travel is viable; double-check bookings and allow extra time."
                    else       -> "Great time to travel — smooth conditions ahead."
                }
            }

            LifeEventCategory.LARGE_INVESTMENT -> {
                astroIndicator = when {
                    mercuryRetro -> { score -= 20; "Mercury Retrograde — contracts may conceal hidden terms" }
                    saturnRetro  -> { score -= 15; "Saturn Retrograde — restructuring phase, capital deployment risky" }
                    jupiter > 70 -> { score += 25; "Jupiter strongly aligned — fortuitous for growth investments" }
                    venus > 65   -> { score += 15; "Venus favours material gains and returns" }
                    else         ->               "Neutral planetary alignment"
                }
                if (marsInWar)    score -= 10
                if (hasAnyWar)    score -= 5
                val (ns, ni) = numerologyScore(pDay,
                    boost = mapOf(8 to 20, 1 to 10, 22 to 20),
                    drag  = mapOf(5 to -20, 9 to -10, 2 to -5),
                    defaultLabel = "Neutral for financial decisions"
                )
                score += ns; numIndicator = ni
                advice = when {
                    score < 40 -> "Hold. Too many headwinds — wait for Saturn and Mercury to clear."
                    score < 70 -> "Acceptable for smaller positions; avoid committing full capital."
                    else       -> "Strong alignment — conditions support bold investment decisions."
                }
            }

            LifeEventCategory.CAREER_CHANGE -> {
                astroIndicator = when {
                    mercuryRetro -> { score -= 20; "Mercury Retrograde — offers may not be what they seem" }
                    saturnRetro  -> { score -= 15; "Saturn Retrograde — internal growth phase, not external leaps" }
                    sun > 65     -> { score += 15; "Sun's vitality supports leadership and identity shift" }
                    jupiter > 65 -> { score += 20; "Jupiter's expansion amplifies career growth" }
                    else         ->               "Neutral alignment for career decisions"
                }
                val (ns, ni) = numerologyScore(pDay,
                    boost = mapOf(1 to 20, 9 to 10, 8 to 10),
                    drag  = mapOf(2 to -10, 7 to -10, 4 to -5),
                    defaultLabel = "Neutral for career shifts"
                )
                score += ns; numIndicator = ni
                advice = when {
                    score < 40 -> "Not the right moment — a better window is forming."
                    score < 70 -> "Proceed with caution; negotiate terms before committing."
                    else       -> "Aligned for a bold career move — act with confidence."
                }
            }
        }

        score = score.coerceIn(0, 100)
        val verdict = when {
            score >= 70 -> Verdict.GO
            score >= 40 -> Verdict.CAUTION
            else        -> Verdict.NO_GO
        }

        val bestWindow = auspiciousHoras(if (score < 40) date.plusDays(1) else date).firstOrNull()?.let {
            val prefix = if (score < 40) "Tomorrow — " else ""
            "$prefix$it"
        }

        return EventAssessment(
            category            = category,
            score               = score,
            verdict             = verdict,
            astroIndicator      = astroIndicator,
            numerologyIndicator = numIndicator,
            advice              = advice,
            bestWindowHint      = bestWindow
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns (scoreDelta, label) for a Personal Day number.
     * [boost] and [drag] map day numbers to score adjustments.
     */
    private fun numerologyScore(
        personalDay: Int,
        boost: Map<Int, Int>,
        drag: Map<Int, Int>,
        defaultLabel: String
    ): Pair<Int, String> {
        if (personalDay == 0) return Pair(0, "No profile — numerology unavailable")
        val delta = boost[personalDay] ?: drag[personalDay] ?: 0
        val label = when {
            boost.containsKey(personalDay) -> "Personal Day $personalDay — ${dayMini(personalDay)}"
            drag.containsKey(personalDay)  -> "Personal Day $personalDay — ${dayMini(personalDay)}"
            else                           -> "Personal Day $personalDay — $defaultLabel"
        }
        return Pair(delta, label)
    }

    private fun dayMini(day: Int): String = when (day) {
        1  -> "initiative and new beginnings"
        2  -> "patience, review over action"
        3  -> "creative and expressive energy"
        4  -> "structure and discipline"
        5  -> "instability and change"
        6  -> "care and responsibility"
        7  -> "introspection and rest"
        8  -> "ambition and financial power"
        9  -> "completion and transition"
        11 -> "heightened intuition"
        22 -> "master builder energy"
        33 -> "service and teaching"
        else -> "balanced energy"
    }

    private fun auspiciousHoras(date: LocalDate): List<String> {
        val dow        = date.dayOfWeek.value % 7
        val startIdx   = HORA_SEQUENCE.indexOf(DAY_RULERS[dow])
        val windows    = mutableListOf<String>()
        for (h in 0..11) {
            val rulerId = HORA_SEQUENCE[(startIdx + h) % 7]
            if (rulerId in BENEFIC_IDS) {
                val startH = 6 + h
                val tag    = HORA_TAG[rulerId] ?: ""
                windows.add("${fmtHour(startH)}–${fmtHour(startH + 1)}  ${HORA_NAME[rulerId]} Hora ($tag)")
                if (windows.size == 2) break
            }
        }
        return windows.ifEmpty { listOf("${fmtHour(9)}–${fmtHour(10)}  Balanced window") }
    }

    private fun rahuKaalWindow(date: LocalDate): List<String> {
        val dow       = date.dayOfWeek.value % 7
        val startMins = 6 * 60 + (RAHU_SLOT[dow] - 1) * 90
        return listOf("${fmtMins(startMins)}–${fmtMins(startMins + 90)}  Rahu Kaal (avoid new beginnings)")
    }

    private fun fmtHour(hour: Int): String {
        val h12  = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        return "$h12:00 ${if (hour < 12) "AM" else "PM"}"
    }

    private fun fmtMins(totalMins: Int): String {
        val h = totalMins / 60; val m = totalMins % 60
        val h12 = if (h > 12) h - 12 else if (h == 0) 12 else h
        return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }
}
