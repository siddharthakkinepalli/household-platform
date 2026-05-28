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

                // Load actual Elektrobit salary landing dates.
                // These drive cycle boundaries precisely — no more fixed-day approximation.
                val salarySource = db.salarySourceDao().getSalarySource()
                val actualSalaryDates = if (salarySource != null) {
                    db.walletTransactionDao()
                        .getSalaryTransactionsByPattern(salarySource.merchantPattern.take(8))
                        .map { it.date }
                        .distinct()
                        .sorted()
                } else {
                    emptyList()
                }

                val service = RecurringDetectionService(
                    walletTransactionDao = db.walletTransactionDao(),
                    recurringBillDao = db.recurringBillDao(),
                    salaryAnchorDay = salaryAnchorDay,
                    actualSalaryDates = actualSalaryDates
                )
                service.detectAndStore()
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
