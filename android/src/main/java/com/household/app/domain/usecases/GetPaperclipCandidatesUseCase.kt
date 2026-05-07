package com.household.app.domain.usecases

import com.household.app.domain.models.Expense
import com.household.app.domain.repositories.ExpenseRepository
import java.time.LocalDate

class GetPaperclipCandidatesUseCase(
    private val repository: ExpenseRepository
) {
    suspend fun execute(amount: Double, date: LocalDate): List<Expense> {
        return repository.getExpensesInRange(
            minAmount = amount - 0.05,
            maxAmount = amount + 0.05,
            startDate = date.minusDays(2),
            endDate = date.plusDays(2)
        )
    }
}
