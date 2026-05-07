package com.household.app.domain.utils

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object FiscalDateUtils {
    fun getFiscalCycleStart(date: LocalDate, anchorDay: Int): LocalDate {
        val normalizedAnchor = anchorDay.coerceIn(1, 28)
        val candidateMonth = if (date.dayOfMonth >= normalizedAnchor) {
            YearMonth.from(date)
        } else {
            YearMonth.from(date.minusMonths(1))
        }
        return candidateMonth.atDay(normalizedAnchor.coerceAtMost(candidateMonth.lengthOfMonth()))
    }

    fun getFiscalCycleRange(date: LocalDate, anchorDay: Int): Pair<LocalDate, LocalDate> {
        val start = getFiscalCycleStart(date, anchorDay)
        val nextMonth = YearMonth.from(start).plusMonths(1)
        val normalizedAnchor = anchorDay.coerceIn(1, 28)
        val nextStart = nextMonth.atDay(normalizedAnchor.coerceAtMost(nextMonth.lengthOfMonth()))
        return start to nextStart.minusDays(1)
    }

    fun getPreviousFiscalCycleRange(date: LocalDate, anchorDay: Int): Pair<LocalDate, LocalDate> {
        val currentStart = getFiscalCycleStart(date, anchorDay)
        val previousReference = currentStart.minusDays(1)
        return getFiscalCycleRange(previousReference, anchorDay)
    }

    fun getFiscalCycleId(date: LocalDate, anchorDay: Int): String {
        val start = getFiscalCycleStart(date, anchorDay)
        return "%04d_%02d".format(start.year, start.monthValue)
    }

    fun formatRangeLabel(range: Pair<LocalDate, LocalDate>): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return "${range.first.format(formatter)} - ${range.second.format(formatter)}"
    }
}
