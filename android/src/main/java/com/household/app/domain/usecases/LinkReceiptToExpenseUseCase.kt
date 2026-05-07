package com.household.app.domain.usecases

import com.household.app.domain.repositories.ExpenseRepository
import com.household.app.domain.repositories.VaultRepository

class LinkReceiptToExpenseUseCase(
    private val vaultRepository: VaultRepository,
    private val expenseRepository: ExpenseRepository
) {
    suspend fun execute(vaultId: Long, expenseId: Int) {
        vaultRepository.linkReceiptToExpense(vaultId, expenseId.toLong())
        expenseRepository.attachReceipt(expenseId, vaultId)
    }
}
