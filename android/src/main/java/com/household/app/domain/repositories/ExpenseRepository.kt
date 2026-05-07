package com.household.app.domain.repositories

import com.household.app.domain.models.Expense
import java.time.LocalDate

interface ExpenseRepository {
    suspend fun getExpensesInRange(
        minAmount: Double,
        maxAmount: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Expense>

    suspend fun attachReceipt(expenseId: Int, vaultId: Long)
}
