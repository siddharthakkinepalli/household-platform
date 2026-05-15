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
}
