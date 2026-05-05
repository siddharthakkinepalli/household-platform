package com.household.expenses.repository

import androidx.lifecycle.LiveData
import com.household.expenses.categorization.ExpensesCategories
import com.household.expenses.db.ExpensesDatabase
import com.household.expenses.db.dao.CategorySpending
import com.household.expenses.db.entity.BudgetCategory
import com.household.expenses.db.entity.Transaction
import com.household.expenses.db.entity.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ImportResult(val inserted: Int, val skipped: Int)

data class TripSummary(
    val trip: Trip,
    val spending: List<CategorySpending>,
    val totalSpent: Double
)

/**
 * Repository for managing household expenses.
 * Handles all local transaction, category, and budget operations.
 *
 * All operations are scoped to a specific household via householdId.
 */
class ExpensesRepository(
    private val database: ExpensesDatabase,
    private val householdId: String
) {

    private val txDao = database.transactionDao()
    private val budgetDao = database.budgetCategoryDao()
    private val tripDao = database.tripDao()
    private val categorizer = ExpensesCategories()

    // ------------------------------------------------------------------
    // Live data (for observing in ViewModel)
    // ------------------------------------------------------------------
    fun getAllTransactionsLive(): LiveData<List<Transaction>> =
        txDao.getAllLive(householdId)

    fun getAllBudgetCategoriesLive(): LiveData<List<BudgetCategory>> =
        budgetDao.getAllLive(householdId)

    fun getAllTripsLive(): LiveData<List<Trip>> =
        tripDao.getAllLive(householdId)

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    /**
     * Insert a single transaction with automatic categorization.
     */
    suspend fun insertTransaction(
        date: String,
        description: String,
        amount: Double,
        bank: String = "",
        tripId: Long? = null,
        skipCategorization: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val (category, budgetCategory) = if (skipCategorization) {
            Pair("", "")
        } else {
            categorizer.categorizeTransaction(description, amount)
        }

        // Skip excluded transactions
        if (category == "__exclude__") return@withContext -1L

        val hash = md5Hash("$date|$description|$amount|$bank")
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val transaction = Transaction(
            householdId = householdId,
            date = date,
            description = description,
            amount = amount,
            bank = bank,
            category = category,
            budgetCategory = budgetCategory,
            tripId = tripId,
            importedAt = now,
            hash = hash
        )
        txDao.insert(transaction)
    }

    /**
     * Import multiple transactions at once.
     */
    suspend fun importTransactions(
        transactions: List<Map<String, Any>>,
        skipCategorization: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        var inserted = 0
        var skipped = 0

        for (tx in transactions) {
            val date = tx["date"] as? String ?: continue
            val description = tx["description"] as? String ?: ""
            val amount = (tx["amount"] as? Number)?.toDouble() ?: 0.0
            val bank = tx["bank"] as? String ?: ""

            val result = insertTransaction(
                date = date,
                description = description,
                amount = amount,
                bank = bank,
                skipCategorization = skipCategorization
            )

            if (result == -1L) skipped++ else inserted++
        }

        ImportResult(inserted, skipped)
    }

    /**
     * Retrieve filtered transactions.
     */
    suspend fun getFilteredTransactions(
        startDate: String? = null,
        endDate: String? = null,
        budgetCategory: String? = null,
        bank: String? = null,
        tripId: Long? = null,
        search: String? = null,
        excludeIncome: Boolean = false
    ): List<Transaction> = withContext(Dispatchers.IO) {
        txDao.getFiltered(
            householdId = householdId,
            startDate = startDate,
            endDate = endDate,
            budgetCategory = budgetCategory,
            bank = bank,
            tripId = tripId,
            search = search,
            excludeIncome = if (excludeIncome) 1 else 0
        )
    }

    /**
     * Get spending breakdown by category for a date range.
     */
    suspend fun getSpendingByCategory(
        startDate: String? = null,
        endDate: String? = null
    ): List<CategorySpending> = withContext(Dispatchers.IO) {
        txDao.getSpendingByCategory(
            householdId = householdId,
            startDate = startDate,
            endDate = endDate
        )
    }

    // ------------------------------------------------------------------
    // Budget Categories
    // ------------------------------------------------------------------

    /**
     * Get all budget categories for this household.
     */
    suspend fun getBudgetCategories(): List<BudgetCategory> = withContext(Dispatchers.IO) {
        budgetDao.getAll(householdId)
    }

    /**
     * Insert or update a budget category.
     */
    suspend fun upsertBudgetCategory(
        name: String,
        monthlyLimit: Double,
        allowedBanks: String = "[]",
        priority: String = "",
        description: String = ""
    ) = withContext(Dispatchers.IO) {
        val category = BudgetCategory(
            householdId = householdId,
            name = name,
            monthlyLimit = monthlyLimit,
            allowedBanks = allowedBanks,
            priority = priority,
            description = description
        )
        budgetDao.upsert(category)
    }

    /**
     * Delete a budget category.
     */
    suspend fun deleteBudgetCategory(category: BudgetCategory) = withContext(Dispatchers.IO) {
        budgetDao.delete(category)
    }

    // ------------------------------------------------------------------
    // Trips
    // ------------------------------------------------------------------

    /**
     * Create a new trip and auto-tag transactions in that date range.
     */
    suspend fun createTrip(name: String, startDate: String, endDate: String, notes: String = ""): Long =
        withContext(Dispatchers.IO) {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val trip = Trip(
                householdId = householdId,
                name = name,
                startDate = startDate,
                endDate = endDate,
                notes = notes,
                createdAt = now
            )
            val id = tripDao.insert(trip)
            // Auto-tag transactions in the date range
            txDao.assignTripToRange(householdId, id, startDate, endDate)
            id
        }

    /**
     * Get all trips for this household.
     */
    suspend fun getTrips(): List<Trip> = withContext(Dispatchers.IO) {
        tripDao.getAll(householdId)
    }

    /**
     * Delete a trip by ID.
     */
    suspend fun deleteTrip(id: Long) = withContext(Dispatchers.IO) {
        tripDao.deleteById(householdId, id)
    }

    /**
     * Get a summary of trip spending: transactions, categories, total.
     */
    suspend fun getTripSummary(tripId: Long): TripSummary? = withContext(Dispatchers.IO) {
        val trip = tripDao.getById(householdId, tripId) ?: return@withContext null
        val spending = txDao.getSpendingByCategory(
            householdId = householdId,
            startDate = trip.startDate,
            endDate = trip.endDate
        )
        val total = spending.sumOf { it.total_abs }
        TripSummary(trip, spending, total)
    }

    /**
     * Get total number of transactions for this household.
     */
    suspend fun getTransactionCount(): Int = withContext(Dispatchers.IO) {
        txDao.count(householdId)
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    /**
     * Compute MD5 hash for transaction deduplication.
     */
    private fun md5Hash(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
