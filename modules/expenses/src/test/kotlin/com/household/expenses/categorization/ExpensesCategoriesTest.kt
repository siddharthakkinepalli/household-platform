package com.household.expenses.categorization

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpensesCategoriesTest {

    private val categorizer = ExpensesCategories()

    @Test
    fun `test basic categorization`() {
        val (category, _) = categorizer.categorizeTransaction("REWE SAGT DANKE", 45.20)
        assertEquals("Food & Dining", category)
    }

    @Test
    fun `test exclusion patterns`() {
        val (category, _) = categorizer.categorizeTransaction("ING-DiBa Direct Debit", 100.0)
        assertEquals("__exclude__", category)
    }

    @Test
    fun `test bakery override`() {
        val (category, budget) = categorizer.categorizeTransaction("Bäckerei Staib", 5.50)
        assertEquals("Food & Dining", category)
        assertEquals("Bakery", budget)
    }

    @Test
    fun `test india travel override`() {
        val (category, budget) = categorizer.categorizeTransaction("ETIHAD AIRWAYS ABU DHABI", 850.0)
        assertEquals("India Travel", category)
        assertEquals("Flights", budget)
    }

    @Test
    fun `test normalization with umlauts`() {
        // 'muller' is a keyword, 'Müller' should match
        val (category, _) = categorizer.categorizeTransaction("Müller Drogerie", 12.0)
        assertEquals("Shopping", category)
    }

    @Test
    fun `test other category`() {
        val (category, _) = categorizer.categorizeTransaction("Unknown Merchant XYZ", 10.0)
        assertEquals("Other", category)
    }

    @Test
    fun `test empty description`() {
        val (category, _) = categorizer.categorizeTransaction("", 10.0)
        assertEquals("Other", category)
    }

    @Test
    fun `test categorizeTransactions list`() {
        val transactions = listOf(
            mapOf("description" to "REWE", "amount" to 10.0),
            mapOf("description" to "ING-DiBa", "amount" to 50.0) // Excluded
        )
        val result = categorizer.categorizeTransactions(transactions)
        assertEquals(1, result.size)
        assertEquals("Food & Dining", result[0]["category"])
    }
}
