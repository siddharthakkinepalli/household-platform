package com.household.app.data.repository

import com.household.app.data.dao.WalletTransactionDao
import com.household.app.domain.models.Expense
import com.household.app.domain.repositories.ExpenseRepository
import java.time.LocalDate

class ExpenseRepositoryImpl(
    private val walletTransactionDao: WalletTransactionDao
) : ExpenseRepository {
    override suspend fun getExpensesInRange(
        minAmount: Double,
        maxAmount: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Expense> {
        return walletTransactionDao.getTransactionsInRange(
            minAmount = minAmount,
            maxAmount = maxAmount,
            startDate = startDate,
            endDate = endDate
        ).map { entity ->
            Expense(
                id = entity.id,
                description = entity.title,
                amount = entity.amount,
                category = entity.category,
                date = entity.date
            )
        }
    }

    override suspend fun attachReceipt(expenseId: Int, vaultId: Long) {
        walletTransactionDao.attachVaultEntry(expenseId = expenseId, vaultId = vaultId)
    }
}
