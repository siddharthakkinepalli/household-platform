package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.app.data.entities.WalletTransactionEntity
import java.time.LocalDate

@Dao
interface WalletTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: WalletTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<WalletTransactionEntity>): List<Long>

    @Query("SELECT * FROM wallet_transactions ORDER BY date DESC")
    suspend fun getAllTransactions(): List<WalletTransactionEntity>

    @Query("SELECT * FROM wallet_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): WalletTransactionEntity?

    @Query("UPDATE wallet_transactions SET linkedVaultEntryId = :vaultId WHERE id = :expenseId")
    suspend fun attachVaultEntry(expenseId: Int, vaultId: Long): Int

    @Query(
        """
        SELECT * FROM wallet_transactions
        WHERE ABS(amount) BETWEEN :minAmount AND :maxAmount
          AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC
        """
    )
    suspend fun getTransactionsInRange(
        minAmount: Double,
        maxAmount: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WalletTransactionEntity>

    @Update
    suspend fun updateTransaction(transaction: WalletTransactionEntity): Int

    @Delete
    suspend fun deleteTransaction(transaction: WalletTransactionEntity): Int

    @Query("DELETE FROM wallet_transactions")
    suspend fun deleteAllTransactions(): Int

    @Query("SELECT COUNT(*) FROM wallet_transactions")
    suspend fun getTransactionCount(): Int

    /**
     * Returns all income transactions whose title contains [patternFragment] (case-insensitive),
     * sorted oldest-first. Used to derive actual salary landing dates for fiscal cycle boundaries.
     */
    @Query("SELECT * FROM wallet_transactions WHERE trip = :tripName AND amount < 0 ORDER BY date DESC")
    suspend fun getTransactionsByTrip(tripName: String): List<WalletTransactionEntity>

    @Query("UPDATE wallet_transactions SET trip = :tripName WHERE id = :id")
    suspend fun updateTransactionTrip(id: Int, tripName: String?): Int

    @Query("SELECT * FROM wallet_transactions WHERE amount > 0 AND LOWER(title) LIKE '%' || LOWER(:patternFragment) || '%' ORDER BY date ASC")
    suspend fun getSalaryTransactionsByPattern(patternFragment: String): List<WalletTransactionEntity>

    @Query("UPDATE wallet_transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Int, category: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionsIgnore(transactions: List<WalletTransactionEntity>): List<Long>

    @Query("""
        SELECT wallet_transactions.* FROM wallet_transactions_fts
        JOIN wallet_transactions ON wallet_transactions.id = wallet_transactions_fts.rowid
        WHERE wallet_transactions_fts MATCH :query
        ORDER BY wallet_transactions.date DESC
        LIMIT 100
    """)
    suspend fun searchTransactionsFts(query: String): List<WalletTransactionEntity>
}
