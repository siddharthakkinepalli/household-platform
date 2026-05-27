package com.household.app.vault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.household.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

class ReceiptMatchingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val vaultId = inputData.getLong("vault_id", -1L)
        if (vaultId < 0) return Result.failure()

        return try {
            val db = AppDatabase.getInstance(applicationContext)

            val vaultEntry = withContext(Dispatchers.IO) {
                db.vaultDao().getEntryById(vaultId)
            } ?: return Result.success()

            val amount = vaultEntry.totalAmount ?: return Result.success()
            val date = LocalDate.ofEpochDay(vaultEntry.dateEpoch)

            val absAmount = abs(amount.toDouble())
            val minAmount = absAmount * 0.90
            val maxAmount = absAmount * 1.10
            val startDate = date.minusDays(3)
            val endDate = date.plusDays(3)

            val candidates = withContext(Dispatchers.IO) {
                db.walletTransactionDao().getTransactionsInRange(minAmount, maxAmount, startDate, endDate)
            }

            if (candidates.size == 1) {
                withContext(Dispatchers.IO) {
                    db.walletTransactionDao().attachVaultEntry(candidates[0].id, vaultId)
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
