package com.household.app.domain.usecases

import com.household.app.domain.models.BudgetRunway
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class GetBudgetRunwayUseCase {

    fun execute(currentBalance: Double, anchorDay: Int = 25): BudgetRunway {
        val today = LocalDate.now()

        val targetDate = if (today.dayOfMonth >= anchorDay) {
            today.plusMonths(1).withDayOfMonth(anchorDay)
        } else {
            today.withDayOfMonth(anchorDay)
        }

        val daysRemaining = ChronoUnit.DAYS.between(today, targetDate).coerceAtLeast(1)
        val dailyBudget = currentBalance / daysRemaining

        return BudgetRunway(
            dailyBudget = dailyBudget,
            daysRemaining = daysRemaining.toInt(),
            totalRemaining = currentBalance
        )
    }
}
