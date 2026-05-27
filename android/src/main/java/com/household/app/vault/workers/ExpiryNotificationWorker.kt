package com.household.app.vault.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.household.app.R
import com.household.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExpiryNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "expiry_alerts"
        const val CHANNEL_NAME = "Document Expiry Alerts"

        fun createChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alerts for upcoming document expiries" }
            manager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val alerts = db.documentAlertDao().getAlertsDueWithinDaysList(30)

        if (alerts.isEmpty()) return@withContext Result.success()

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) return@withContext Result.success()

        alerts.forEachIndexed { index, alert ->
            val urgency = when {
                alert.daysUntil <= 7 -> "Urgent: "
                alert.daysUntil <= 14 -> "Soon: "
                else -> ""
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tab_documents)
                .setContentTitle("${urgency}Document Expiry")
                .setContentText(alert.message)
                .setPriority(if (alert.daysUntil <= 7) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(CHANNEL_ID.hashCode() + index, notification)
        }

        Result.success()
    }
}
