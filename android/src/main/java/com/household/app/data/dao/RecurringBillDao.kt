package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.household.app.data.entities.RecurringBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringBillDao {
    @Query("SELECT * FROM recurring_bills WHERE isActive = 1 ORDER BY normalizedAmount DESC")
    fun observeActiveBills(): Flow<List<RecurringBillEntity>>

    @Query("SELECT * FROM recurring_bills WHERE source = 'AUTO' ORDER BY normalizedAmount DESC")
    fun observeAutoBills(): Flow<List<RecurringBillEntity>>

    @Query("SELECT * FROM recurring_bills WHERE source IN ('CONFIRMED', 'MANUAL') AND isActive = 1 ORDER BY normalizedAmount DESC")
    fun observeConfirmedBills(): Flow<List<RecurringBillEntity>>

    @Query("SELECT * FROM recurring_bills WHERE isActive = 1 ORDER BY normalizedAmount DESC")
    suspend fun getActiveBills(): List<RecurringBillEntity>

    @Query("SELECT * FROM recurring_bills WHERE merchantPattern = :pattern LIMIT 1")
    suspend fun getByMerchantPattern(pattern: String): RecurringBillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bills: List<RecurringBillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: RecurringBillEntity)

    @Query("DELETE FROM recurring_bills")
    suspend fun clearAll()

    @Query("DELETE FROM recurring_bills WHERE source = 'AUTO'")
    suspend fun clearAutoBills()

    @Query("SELECT merchantPattern FROM recurring_bills WHERE source IN ('CONFIRMED', 'MANUAL', 'DISMISSED')")
    suspend fun getUserManagedPatterns(): List<String>

    @Query("UPDATE recurring_bills SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE recurring_bills SET source = :source WHERE id = :id")
    suspend fun updateSource(id: Long, source: String)

    @Query("UPDATE recurring_bills SET merchantPattern = :pattern, normalizedAmount = :amount, minAmount = :amount * 0.85, maxAmount = :amount * 1.15, category = :category WHERE id = :id")
    suspend fun updateBill(id: Long, pattern: String, amount: Double, category: String)

    @Query("SELECT COALESCE(SUM(normalizedAmount), 0.0) FROM recurring_bills WHERE isActive = 1")
    suspend fun getTotalMonthlyFixed(): Double
}
