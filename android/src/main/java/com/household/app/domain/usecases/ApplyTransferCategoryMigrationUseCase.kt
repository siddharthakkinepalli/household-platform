package com.household.app.domain.usecases

import android.content.Context
import com.household.app.data.AppDatabase
import com.household.app.data.TransactionCategorizer
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.domain.utils.MerchantNameCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time migration to apply Transfers category to existing transactions.
 * This re-categorizes inter-bank transfers (ING, N26, Commerz, SEPA, IBAN, etc.)
 * from their original category (likely Shopping or Utilities) to Transfers.
 *
 * Run once per app lifecycle; uses SharedPreferences flag to track completion.
 */
object ApplyTransferCategoryMigrationUseCase {
    private const val PREF_KEY = "com.household.app.transfer_migration_applied_v4"
    private const val PREF_FILE = "com.household.app.migrations"

    suspend fun execute(context: Context) {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_KEY, false)) {
            return // Already migrated
        }

        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val walletDataLoader = WalletDataLoader(context)
            val categorizer = TransactionCategorizer()

            // Load all transactions from merged wallet
            val mergedTransactions = WalletUserDataStore.loadMergedTransactions(
                context,
                walletDataLoader.loadTransactions()
            )

            // Re-categorize each transaction using updated categorizer
            val updatedEntities = mergedTransactions.mapNotNull { transaction ->
                val cleanDescription = MerchantNameCleaner.clean(transaction.title)
                val newCategory = categorizer.classifyCategory(
                    cleanDescription,
                    transaction.category
                )
                
                // Only update if category changed
                if (newCategory != transaction.category) {
                    WalletTransactionEntity(
                        id = transaction.id,
                        title = transaction.title,
                        category = newCategory,
                        amount = transaction.amount,
                        date = transaction.date,
                        paymentType = transaction.paymentType,
                        trip = transaction.trip,
                        note = transaction.note,
                        bankName = transaction.bankName,
                        excluded = transaction.excluded,
                        syncedFromJson = false
                    )
                } else {
                    null
                }
            }

            // Persist updates to wallet database
            updatedEntities.forEach { entity ->
                db.walletTransactionDao().updateTransaction(entity)
            }
        }

        // Mark migration as complete
        prefs.edit().putBoolean(PREF_KEY, true).apply()
    }
}
