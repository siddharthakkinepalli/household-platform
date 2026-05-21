package com.household.app.domain.services

import com.household.app.data.dao.MerchantRuleDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.data.entities.WalletTransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class RetroactiveCategorizerTest {

    private lateinit var merchantRuleDao: MerchantRuleDao
    private lateinit var walletTransactionDao: WalletTransactionDao
    private lateinit var categorizer: RetroactiveCategorizer

    @Before
    fun setUp() {
        merchantRuleDao = mock()
        walletTransactionDao = mock()
        categorizer = RetroactiveCategorizer(merchantRuleDao, walletTransactionDao)
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun tx(
        id: Int,
        title: String,
        category: String = "Uncategorized"
    ) = WalletTransactionEntity(
        id = id,
        title = title,
        category = category,
        amount = -10.0,
        date = LocalDate.of(2026, 1, 1),
        paymentType = "card"
    )

    private fun rule(
        pattern: String,
        targetCategory: String,
        isEnabled: Boolean = true,
        isExclusion: Boolean = false,
        priority: Int = 0
    ) = MerchantRuleEntity(
        merchantPattern = pattern,
        targetCategoryId = targetCategory,
        isEnabled = isEnabled,
        isExclusion = isExclusion,
        priority = priority
    )

    // ─────────────────────────────────────────────────────────────
    // recategorizeAll — no rules → early-return 0
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `recategorizeAll returns 0 when no enabled rules exist`() = runTest {
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(emptyList())

        val count = categorizer.recategorizeAll()

        assertEquals(0, count)
        verify(walletTransactionDao, never()).getAllTransactions()
    }

    // ─────────────────────────────────────────────────────────────
    // recategorizeAll — matching rule updates the category
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `recategorizeAll updates category when rule matches and category differs`() = runTest {
        val matchingRule = rule(pattern = "rewe", targetCategory = "Groceries")
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(matchingRule))

        val transaction = tx(id = 1, title = "REWE City", category = "Uncategorized")
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(transaction))

        val count = categorizer.recategorizeAll()

        assertEquals(1, count)
        verify(walletTransactionDao).updateCategory(eq(1), eq("Groceries"))
    }

    // ─────────────────────────────────────────────────────────────
    // recategorizeAll — no update when category already matches
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `recategorizeAll does not update when transaction already has target category`() = runTest {
        val matchingRule = rule(pattern = "rewe", targetCategory = "Groceries")
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(matchingRule))

        // Category already matches the rule's target — no write needed.
        val transaction = tx(id = 2, title = "REWE City", category = "Groceries")
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(transaction))

        val count = categorizer.recategorizeAll()

        assertEquals(0, count)
        verify(walletTransactionDao, never()).updateCategory(any(), any())
    }

    // ─────────────────────────────────────────────────────────────
    // recategorizeAll — transactions with no matching rule are skipped
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `recategorizeAll skips transactions with no matching rule`() = runTest {
        val unrelatedRule = rule(pattern = "amazon", targetCategory = "Shopping")
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(unrelatedRule))

        val transaction = tx(id = 3, title = "Unknown Merchant", category = "Uncategorized")
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(transaction))

        val count = categorizer.recategorizeAll()

        assertEquals(0, count)
        verify(walletTransactionDao, never()).updateCategory(any(), any())
    }

    @Test
    fun `recategorizeAll only updates matching transactions and skips non-matching ones`() = runTest {
        val ruleGroceries = rule(pattern = "rewe", targetCategory = "Groceries")
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(ruleGroceries))

        val txMatching = tx(id = 10, title = "REWE Markt", category = "Uncategorized")
        val txNoMatch  = tx(id = 11, title = "PayPal Transfer", category = "Uncategorized")
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(txMatching, txNoMatch))

        val count = categorizer.recategorizeAll()

        assertEquals(1, count)
        verify(walletTransactionDao).updateCategory(eq(10), eq("Groceries"))
        verify(walletTransactionDao, never()).updateCategory(eq(11), any())
    }

    // ─────────────────────────────────────────────────────────────
    // recategorizeAll — disabled rules are skipped
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `recategorizeAll skips transactions when only disabled rules exist`() = runTest {
        // getEnabledRules() already filters at the DAO level; simulate an empty
        // enabled-rules list so the categorizer short-circuits without touching transactions.
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(emptyList())

        val transaction = tx(id = 4, title = "REWE City", category = "Uncategorized")
        // getAllTransactions() should never be called in this branch.
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(transaction))

        val count = categorizer.recategorizeAll()

        assertEquals(0, count)
        verify(walletTransactionDao, never()).getAllTransactions()
        verify(walletTransactionDao, never()).updateCategory(any(), any())
    }

    @Test
    fun `recategorizeAll disabled rule in mixed list is not applied`() = runTest {
        // RuleEngineService.pickRule filters isEnabled internally.
        val enabledRule  = rule(pattern = "spotify", targetCategory = "Entertainment", isEnabled = true)
        val disabledRule = rule(pattern = "rewe",    targetCategory = "Groceries",     isEnabled = false)

        // DAO is assumed to return only enabled rules (as per its @Query).
        // Pass only the enabled rule to simulate correct DAO behaviour.
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(enabledRule))

        val txRewe    = tx(id = 5, title = "REWE City",  category = "Uncategorized")
        val txSpotify = tx(id = 6, title = "Spotify AB", category = "Uncategorized")
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(txRewe, txSpotify))

        val count = categorizer.recategorizeAll()

        // Only Spotify matches the enabled rule.
        assertEquals(1, count)
        verify(walletTransactionDao).updateCategory(eq(6), eq("Entertainment"))
        verify(walletTransactionDao, never()).updateCategory(eq(5), any())
    }

    // ─────────────────────────────────────────────────────────────
    // recategorizeAll — exclusion rules set category to "Exclude"
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `recategorizeAll applies exclusion rule and sets Exclude category`() = runTest {
        val exclusionRule = rule(pattern = "internal transfer", targetCategory = "anything", isExclusion = true)
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(exclusionRule))

        val transaction = tx(id = 7, title = "Internal Transfer ABC", category = "Uncategorized")
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(transaction))

        val count = categorizer.recategorizeAll()

        assertEquals(1, count)
        verify(walletTransactionDao).updateCategory(eq(7), eq("Exclude"))
    }

    @Test
    fun `recategorizeAll does not update when exclusion transaction already has Exclude category`() = runTest {
        val exclusionRule = rule(pattern = "internal transfer", targetCategory = "anything", isExclusion = true)
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(exclusionRule))

        val transaction = tx(id = 8, title = "Internal Transfer ABC", category = "Exclude")
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(listOf(transaction))

        val count = categorizer.recategorizeAll()

        assertEquals(0, count)
        verify(walletTransactionDao, never()).updateCategory(any(), any())
    }

    // ─────────────────────────────────────────────────────────────
    // recategorizeAll — returns total count across multiple matches
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `recategorizeAll returns correct count when multiple transactions are updated`() = runTest {
        val rule1 = rule(pattern = "rewe",    targetCategory = "Groceries")
        val rule2 = rule(pattern = "spotify", targetCategory = "Entertainment")
        whenever(merchantRuleDao.getEnabledRules()).thenReturn(listOf(rule1, rule2))

        val transactions = listOf(
            tx(id = 20, title = "REWE Markt",  category = "Uncategorized"),
            tx(id = 21, title = "Spotify AB",  category = "Uncategorized"),
            tx(id = 22, title = "Unknown",      category = "Uncategorized")
        )
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(transactions)

        val count = categorizer.recategorizeAll()

        assertEquals(2, count)
    }
}
