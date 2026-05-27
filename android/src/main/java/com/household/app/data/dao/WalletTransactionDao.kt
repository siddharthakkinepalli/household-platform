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
    suspend fun insertTransaction(transaction: WalletTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<WalletTransactionEntity>)

    @Query("SELECT * FROM wallet_transactions ORDER BY date DESC")
    suspend fun getAllTransactions(): List<WalletTransactionEntity>

    @Query("SELECT * FROM wallet_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): WalletTransactionEntity?

    @Query("UPDATE wallet_transactions SET linkedVaultEntryId = :vaultId WHERE id = :expenseId")
    suspend fun attachVaultEntry(expenseId: Int, vaultId: Long)

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
    suspend fun updateTransaction(transaction: WalletTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: WalletTransactionEntity)

    @Query("DELETE FROM wallet_transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT COUNT(*) FROM wallet_transactions")
    suspend fun getTransactionCount(): Int

    @Query("UPDATE wallet_transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Int, category: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionsIgnore(transactions: List<WalletTransactionEntity>)

    @Query("""
        SELECT wallet_transactions.* FROM wallet_transactions_fts
        JOIN wallet_transactions ON wallet_transactions.id = wallet_transactions_fts.rowid
        WHERE wallet_transactions_fts MATCH :query
        ORDER BY wallet_transactions.date DESC
        LIMIT 100
    """)
    suspend fun searchTransactionsFts(query: String): List<WalletTransactionEntity>
}
