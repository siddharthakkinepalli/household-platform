package com.jugaad.feature.astro.domain.engine

import com.jugaad.feature.astro.domain.model.NumerologyResult
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pythagorean numerology engine — pure Kotlin, no network, no external deps.
 *
 * Reduction rules:
 *  - Sum digits repeatedly until a single digit.
 *  - Master numbers 11, 22, 33 are NOT reduced further.
 *
 * Day hierarchy:
 *  Personal Year  = reduce(birthMonth + birthDay + currentYear)
 *  Personal Month = reduce(personalYear + currentMonth)
 *  Personal Day   = reduce(personalMonth + currentDayOfMonth)
 */
@Singleton
class NumerologyEngine @Inject constructor() {

    fun compute(
        birthDay: Int,
        birthMonth: Int,
        birthYear: Int,
        date: LocalDate = LocalDate.now()
    ): NumerologyResult {
        val lifePath       = reduce(reduce(birthDay) + reduce(birthMonth) + reduce(birthYear))
        val personalYear   = reduce(reduce(birthDay) + reduce(birthMonth) + reduce(date.year))
        val personalMonth  = reduce(personalYear + date.monthValue)
        val personalDay    = reduce(personalMonth + date.dayOfMonth)

        return NumerologyResult(
            lifePathNumber      = lifePath,
            personalYearNumber  = personalYear,
            personalMonthNumber = personalMonth,
            personalDayNumber   = personalDay,
            personalDayLabel    = dayLabel(personalDay),
            personalDayBrief    = dayBrief(personalDay)
        )
    }

    /** Pythagorean digit reduction — preserves master numbers 11, 22, 33. */
    fun reduce(n: Int): Int {
        var v = kotlin.math.abs(n)
        while (v > 9 && v != 11 && v != 22 && v != 33) {
            v = v.toString().sumOf { it.digitToInt() }
        }
        return v
    }

    private fun dayLabel(day: Int): String = when (day) {
        1  -> "New Beginnings"
        2  -> "Cooperation & Patience"
        3  -> "Creativity & Expression"
        4  -> "Structure & Hard Work"
        5  -> "Change & Freedom"
        6  -> "Responsibility & Harmony"
        7  -> "Introspection & Analysis"
        8  -> "Ambition & Achievement"
        9  -> "Completion & Release"
        11 -> "Intuition & Inspiration"
        22 -> "Master Builder"
        33 -> "Master Teacher"
        else -> "Balanced Energy"
    }

    private fun dayBrief(day: Int): String = when (day) {
        1  -> "Drive to initiate — ideal for first steps."
        2  -> "Best for reviewing and patience, not pushing."
        3  -> "Creative flow is strong today."
        4  -> "Focus on steady, methodical work."
        5  -> "Energy is restless — adapt, don't anchor."
        6  -> "Day for care, commitments, and relationships."
        7  -> "Quiet introspection yields better than action."
        8  -> "Power and ambition peak — execute strategically."
        9  -> "A day of endings and transitions."
        11 -> "Heightened sensitivity — intuition over logic."
        22 -> "Large-scale projects gain momentum today."
        33 -> "Service and teaching lead to results."
        else -> "Neutral energy — steady progress."
    }
}
