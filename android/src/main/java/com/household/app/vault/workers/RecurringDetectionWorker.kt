package com.household.app.vault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.household.app.data.AppDatabase
import com.household.app.data.DashboardPrefs
import com.household.app.data.service.RecurringDetectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecurringDetectionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val salaryAnchorDay = DashboardPrefs.getSalaryAnchorDay(applicationContext)
                val service = RecurringDetectionService(
                    walletTransactionDao = db.walletTransactionDao(),
                    recurringBillDao = db.recurringBillDao(),
                    salaryAnchorDay = salaryAnchorDay
                )
                service.detectAndStore()
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
