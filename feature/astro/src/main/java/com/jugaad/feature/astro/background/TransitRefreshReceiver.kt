package com.jugaad.feature.astro.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * BroadcastReceiver that handles AlarmManager triggers.
 *
 * On [ACTION_DAILY_REFRESH]: enqueues a one-time [TransitRefreshWorker] immediately.
 * On [ACTION_RAHU_KAAL]: also enqueues the worker (for notification delivery) and
 *   re-arms the next Rahu Kaal alarm via [AstroBackgroundScheduler].
 *
 * The receiver is also registered for [Intent.ACTION_BOOT_COMPLETED] so alarms
 * are re-scheduled after device reboot (alarms are cleared on reboot by Android).
 */
class TransitRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            AstroBackgroundScheduler.ACTION_DAILY_REFRESH,
            AstroBackgroundScheduler.ACTION_RAHU_KAAL,
            Intent.ACTION_BOOT_COMPLETED -> enqueueImmediateRefresh(context)
        }
    }

    private fun enqueueImmediateRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<TransitRefreshWorker>()
            .addTag(TransitRefreshWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
