package com.household.app.domain.services

import com.household.app.data.dao.MerchantRuleDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.config.RuleEngineService

/**
 * Applies the current set of enabled merchant rules to all wallet transactions
 * and updates the category for any transaction whose category has changed.
 *
 * Inject via constructor — works with plain DAOs so it can be used both from
 * the UI ViewModel and from the import pipeline (ConfigViewModel.commitImport).
 */
class RetroactiveCategorizer(
    private val merchantRuleDao: MerchantRuleDao,
    private val walletTransactionDao: WalletTransactionDao
) {
    /**
     * Re-categorizes every transaction in the database that matches a rule
     * whose targetCategoryId differs from the current category.
     *
     * @return number of transactions updated
     */
    suspend fun recategorizeAll(): Int {
        val rules = merchantRuleDao.getEnabledRules()
        if (rules.isEmpty()) return 0

        val transactions = walletTransactionDao.getAllTransactions()
        var updatedCount = 0

        for (tx in transactions) {
            val match = RuleEngineService.pickRule(tx.title, rules) ?: continue
            if (match.isExclusion) {
                // Exclusion rules mark the transaction as "Exclude" category
                if (tx.category != "Exclude") {
                    walletTransactionDao.updateCategory(tx.id, "Exclude")
                    updatedCount++
                }
            } else if (match.targetCategoryId != tx.category) {
                walletTransactionDao.updateCategory(tx.id, match.targetCategoryId)
                updatedCount++
            }
        }

        return updatedCount
    }
}
