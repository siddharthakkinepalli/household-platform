package com.household.app.vault.workers

import android.content.Context
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.household.app.R
import com.household.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fires notifications for documents expiring within 30/7/1 days.
 *
 * Enqueued by [PipelineManager.onDocumentUploaded] after a vault upload completes.
 *
 * Logic:
 * 1. Queries [document_alerts] for unacknowledged rows where daysUntil <= 30.
 * 2. Posts one Android notification per alert (🔴 urgent ≤7 days, 🟡 upcoming 8–30 days).
 * 3. Marks every notified alert as acknowledged so it will not re-fire on the next run.
 * 4. Always returns [Result.success()] — notification failures are swallowed so WorkManager
 *    does not schedule unnecessary retries.
 */
class DocumentExpiryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val alertDao = db.documentAlertDao()

        // getAlertsDueWithinDaysList already filters daysUntil <= 30 in SQL;
        // we additionally guard against acknowledged alerts that may have slipped through.
        val alerts = alertDao.getAlertsDueWithinDaysList(30)
            .filter { !it.isAcknowledged }

        if (alerts.isEmpty()) return@withContext Result.success()

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure the shared expiry channel exists (idempotent — no-ops if already created).
        ExpiryNotificationWorker.createChannel(applicationContext)

        val notifManager = NotificationManagerCompat.from(applicationContext)
        if (!notifManager.areNotificationsEnabled()) {
            // Notifications are disabled system-wide; skip posting but still acknowledge
            // so the worker doesn't accumulate a back-log when the user re-enables them.
            alerts.forEach { alert ->
                runCatching { alertDao.acknowledgeAlert(alert.id, "NOTIFICATIONS_DISABLED") }
            }
            return@withContext Result.success()
        }

        alerts.forEach { alert ->
            runCatching {
                val (emoji, urgencyText) = when {
                    alert.daysUntil <= 0 -> "🔴" to "expires TODAY"
                    alert.daysUntil == 1 -> "🔴" to "expires tomorrow"
                    alert.daysUntil <= 7 -> "🔴" to "expires in ${alert.daysUntil} days"
                    else                 -> "🟡" to "expires in ${alert.daysUntil} days"
                }

                val priority = if (alert.daysUntil <= 7)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT

                val notification = NotificationCompat.Builder(
                    applicationContext,
                    ExpiryNotificationWorker.CHANNEL_ID
                )
                    .setSmallIcon(R.drawable.ic_tab_documents)
                    .setContentTitle("$emoji Document expiry")
                    .setContentText("${alert.message} — $urgencyText")
                    .setPriority(priority)
                    .setAutoCancel(true)
                    .build()

                notifManager.notify(NOTIF_BASE_ID + alert.id.toInt(), notification)
            }
        }

        // Mark every alert as acknowledged regardless of whether posting succeeded,
        // to prevent re-firing on the next worker run.
        alerts.forEach { alert ->
            runCatching { alertDao.acknowledgeAlert(alert.id, "NOTIFIED") }
        }

        Result.success()
    }

    companion object {
        /**
         * Notification ID base offset.  Added to [DocumentAlertEntity.id] to produce a stable,
         * per-alert notification ID in the 3000-range, avoiding collisions with other workers.
         */
        const val NOTIF_BASE_ID = 3000
    }
}
