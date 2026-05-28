package com.household.app.domain.utils

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object FiscalDateUtils {

    fun getCycleRange(anchorDay: Int): Pair<LocalDate, LocalDate> =
        getFiscalCycleRange(LocalDate.now(), anchorDay)

    fun getFiscalCycleStart(date: LocalDate, anchorDay: Int): LocalDate {
        val thisYM = YearMonth.from(date)
        val candidate = thisYM.atDay(anchorDay.coerceIn(1, thisYM.lengthOfMonth()))
        return if (!date.isBefore(candidate)) {
            candidate
        } else {
            val prevYM = thisYM.minusMonths(1)
            prevYM.atDay(anchorDay.coerceIn(1, prevYM.lengthOfMonth()))
        }
    }

    fun getFiscalCycleRange(date: LocalDate, anchorDay: Int): Pair<LocalDate, LocalDate> {
        val start = getFiscalCycleStart(date, anchorDay)
        val nextYM = YearMonth.from(start).plusMonths(1)
        val nextStart = nextYM.atDay(anchorDay.coerceIn(1, nextYM.lengthOfMonth()))
        return start to nextStart.minusDays(1)
    }

    fun getPreviousFiscalCycleRange(date: LocalDate, anchorDay: Int): Pair<LocalDate, LocalDate> {
        val currentStart = getFiscalCycleStart(date, anchorDay)
        return getFiscalCycleRange(currentStart.minusDays(1), anchorDay)
    }

    fun getFiscalCycleId(date: LocalDate, anchorDay: Int): String {
        val start = getFiscalCycleStart(date, anchorDay)
        return "%04d_%02d".format(start.year, start.monthValue)
    }

    fun formatRangeLabel(range: Pair<LocalDate, LocalDate>): String {
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return "${range.first.format(fmt)} - ${range.second.format(fmt)}"
    }

    // ── Dynamic salary-date cycle (preferred over fixed anchorDay) ────────────

    /**
     * Given a list of actual salary landing dates (sorted ascending), returns the
     * cycle start date that contains [date] — i.e. the most recent salary date
     * that is on or before [date].
     *
     * Returns null if [salaryDates] is empty or all salary dates are after [date]
     * (caller should fall back to the fixed-anchorDay path).
     */
    fun getCycleStartFromActualDates(date: LocalDate, salaryDates: List<LocalDate>): LocalDate? =
        salaryDates.filter { !it.isAfter(date) }.maxOrNull()

    /**
     * Returns a sort key string "YYYY_MM" for the cycle that contains [date],
     * using actual salary landing dates as boundaries.
     * Falls back to [anchorDay]-based logic if [salaryDates] is empty.
     */
    fun getFiscalCycleIdDynamic(date: LocalDate, salaryDates: List<LocalDate>, anchorDay: Int): String {
        val start = getCycleStartFromActualDates(date, salaryDates)
            ?: return getFiscalCycleId(date, anchorDay)
        return "%04d_%02d".format(start.year, start.monthValue)
    }

    /**
     * Returns the [start, end) pair for the cycle that contains [date].
     * end = the day before the next salary landing, or today if no next salary exists.
     */
    fun getCycleRangeFromActualDates(date: LocalDate, salaryDates: List<LocalDate>): Pair<LocalDate, LocalDate>? {
        val start = getCycleStartFromActualDates(date, salaryDates) ?: return null
        val next = salaryDates.firstOrNull { it.isAfter(start) }
        val end = next?.minusDays(1) ?: LocalDate.now()
        return start to end
    }
}
