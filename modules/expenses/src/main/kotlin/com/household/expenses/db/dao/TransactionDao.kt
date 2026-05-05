package com.household.expenses.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.expenses.db.entity.Transaction

data class CategorySpending(
    val budget_category: String,
    val tx_count: Int,
    val total: Double,
    val total_abs: Double
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE household_id = :householdId ORDER BY date DESC")
    fun getAllLive(householdId: String): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE household_id = :householdId ORDER BY date DESC")
    suspend fun getAll(householdId: String): List<Transaction>

    @Query("""
        SELECT * FROM transactions
        WHERE household_id = :householdId
          AND (:startDate IS NULL OR date >= :startDate)
          AND (:endDate IS NULL OR date <= :endDate)
          AND (:budgetCategory IS NULL OR budget_category = :budgetCategory)
          AND (:bank IS NULL OR bank = :bank)
          AND (:tripId IS NULL OR trip_id = :tripId)
          AND (:search IS NULL OR description LIKE '%' || :search || '%')
          AND (:excludeIncome = 0 OR amount < 0)
        ORDER BY date DESC
    """)
    suspend fun getFiltered(
        householdId: String,
        startDate: String? = null,
        endDate: String? = null,
        budgetCategory: String? = null,
        bank: String? = null,
        tripId: Long? = null,
        search: String? = null,
        excludeIncome: Int = 0
    ): List<Transaction>

    @Query("""
        SELECT budget_category,
               COUNT(*) AS tx_count,
               SUM(amount) AS total,
               ABS(SUM(amount)) AS total_abs
        FROM transactions
        WHERE household_id = :householdId
          AND amount < 0
          AND budget_category != ''
          AND (:startDate IS NULL OR date >= :startDate)
          AND (:endDate IS NULL OR date <= :endDate)
        GROUP BY budget_category
        ORDER BY total ASC
    """)
    suspend fun getSpendingByCategory(
        householdId: String,
        startDate: String? = null,
        endDate: String? = null
    ): List<CategorySpending>

    @Query("""
        UPDATE transactions SET trip_id = :tripId
        WHERE household_id = :householdId AND date BETWEEN :startDate AND :endDate
    """)
    suspend fun assignTripToRange(householdId: String, tripId: Long, startDate: String, endDate: String)

    @Query("SELECT * FROM transactions WHERE household_id = :householdId AND trip_id = :tripId AND amount < 0")
    suspend fun getByTrip(householdId: String, tripId: Long): List<Transaction>

    @Query("SELECT COUNT(*) FROM transactions WHERE household_id = :householdId")
    suspend fun count(householdId: String): Int
}
