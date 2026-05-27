package com.household.app.vault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.household.app.data.AppDatabase
import com.household.app.domain.services.RetroactiveCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategorizationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val categorizer = RetroactiveCategorizer(
                    merchantRuleDao = db.merchantRuleDao(),
                    walletTransactionDao = db.walletTransactionDao()
                )
                categorizer.recategorizeAll()
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
