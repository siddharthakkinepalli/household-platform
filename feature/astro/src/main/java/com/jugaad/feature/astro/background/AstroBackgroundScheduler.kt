package com.jugaad.feature.astro.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all background execution triggers for the astro sub-system.
 *
 * Scheduling model:
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │ 1. AlarmManager EXACT (API 31+)                                     │
 *  │    Fires at 05:00 local time → TransitRefreshReceiver               │
 *  │    Rahu Kaal window start → RahuKaalReceiver                        │
 *  │    Falls back to setWindow() if SCHEDULE_EXACT_ALARM not granted.   │
 *  ├─────────────────────────────────────────────────────────────────────│
 *  │ 2. WorkManager PeriodicWork (24h, DEVICE_IDLE)                      │
 *  │    Battery-safe background net for when exact alarm fires missed.   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * No inference is scheduled here — WorkManager workers are cache-refresh only.
 * The nightly AlarmManager trigger wakes the app (even if killed) for a cache refresh.
 */
@Singleton
class AstroBackgroundScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Schedules both the exact AlarmManager trigger and the WorkManager fallback.
     * Call once from the host Application.onCreate() or from the first ViewModel init.
     */
    fun scheduleAll() {
        scheduleDailyAlarm()
        scheduleWorkManagerFallback()
        Log.i(TAG, "Astro background schedule established")
    }

    /**
     * Schedules an exact alarm for the upcoming Rahu Kaal window.
     *
     * @param rahuKaalStartMs Epoch millis for the start of tomorrow's Rahu Kaal window.
     *                        Compute from [AstroPreferencesManager.getRahuKaal].
     */
    fun scheduleRahuKaalAlarm(rahuKaalStartMs: Long) {
        val intent = rahuKaalPendingIntent()
        setExactOrWindow(
            triggerMs  = rahuKaalStartMs,
            intent     = intent,
            windowMs   = 5 * 60_000L   // ±5 min acceptable for Rahu Kaal notification
        )
        Log.i(TAG, "Rahu Kaal alarm set at $rahuKaalStartMs")
    }

    /** Cancels all scheduled astro alarms (call on user opt-out). */
    fun cancelAll() {
        alarmManager.cancel(dailyPendingIntent())
        alarmManager.cancel(rahuKaalPendingIntent())
        WorkManager.getInstance(context).cancelUniqueWork(TransitRefreshWorker.WORK_TAG)
    }

    // ── Private scheduling helpers ────────────────────────────────────────────

    /** Schedules the daily 05:00 exact alarm. Re-arms itself via receiver. */
    private fun scheduleDailyAlarm() {
        val triggerMs = nextOccurrenceMs(hour = 5, minute = 0)
        setExactOrWindow(
            triggerMs = triggerMs,
            intent    = dailyPendingIntent(),
            windowMs  = 10 * 60_000L
        )
    }

    /** WorkManager 24h periodic job as a battery-safe fallback. */
    private fun scheduleWorkManagerFallback() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresDeviceIdle(true)
            .build()

        val request = PeriodicWorkRequestBuilder<TransitRefreshWorker>(
            repeatInterval     = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval   = 2,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TransitRefreshWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TransitRefreshWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Uses [AlarmManager.setExactAndAllowWhileIdle] on API 23+, with permission check on API 31+.
     * Falls back to [AlarmManager.setWindow] if exact alarms are not permitted.
     */
    private fun setExactOrWindow(triggerMs: Long, intent: PendingIntent, windowMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fallback: inexact window (battery-friendly, within ±windowMs)
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerMs,
                windowMs,
                intent
            )
            Log.d(TAG, "Exact alarm not permitted — using setWindow fallback")
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMs,
                intent
            )
        }
    }

    private fun dailyPendingIntent(): PendingIntent {
        val intent = Intent(context, TransitRefreshReceiver::class.java)
            .setAction(ACTION_DAILY_REFRESH)
        return PendingIntent.getBroadcast(
            context, RC_DAILY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rahuKaalPendingIntent(): PendingIntent {
        val intent = Intent(context, TransitRefreshReceiver::class.java)
            .setAction(ACTION_RAHU_KAAL)
        return PendingIntent.getBroadcast(
            context, RC_RAHU, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Returns epoch millis for the next occurrence of [hour]:[minute] local time. */
    private fun nextOccurrenceMs(hour: Int, minute: Int): Long {
        val zone    = ZoneId.systemDefault()
        val now     = java.time.ZonedDateTime.now(zone)
        var target  = now.with(LocalTime.of(hour, minute)).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toEpochSecond() * 1000L
    }

    companion object {
        private const val TAG           = "AstroScheduler"
        const val ACTION_DAILY_REFRESH  = "com.jugaad.astro.ACTION_DAILY_REFRESH"
        const val ACTION_RAHU_KAAL      = "com.jugaad.astro.ACTION_RAHU_KAAL"
        private const val RC_DAILY      = 8800
        private const val RC_RAHU       = 8801
    }
}
