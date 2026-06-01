package com.jugaad.feature.astro.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jugaad.core.time.JulianDayConverter
import com.jugaad.feature.astro.domain.engine.AstroRuleEngine
import com.jugaad.feature.astro.domain.usecase.GetDailyTransitUseCase
import com.jugaad.feature.astro.preferences.AstroPreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

/**
 * WorkManager worker that refreshes the planetary transit cache nightly.
 *
 * ══ STRICT CONSTRAINT ══
 * This worker MUST NOT call [AstroInferenceModel]. Token inference is banned from
 * background workers — it exceeds the 64MB background memory ceiling and blocks the CPU.
 * Only ephemeris data (planet positions + cache writes) is computed here.
 *
 * What this worker does:
 *  1. Computes today's Julian Day.
 *  2. Calls [GetDailyTransitUseCase] — this may call EphemerisEngine JNI if cache is stale.
 *     EphemerisEngine JNI IS permitted in workers (< 10MB footprint).
 *  3. Stores the Rahu Kaal window for widget display.
 *  4. Triggers widget refresh via [AstroWidgetUpdater].
 *  5. Sends transit change notification if quota allows.
 *
 * Scheduled by [AstroBackgroundScheduler] as a PeriodicWorkRequest (24h interval, DEVICE_IDLE).
 */
@HiltWorker
class TransitRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getDailyTransitUseCase: GetDailyTransitUseCase,
    private val ruleEngine: AstroRuleEngine,
    private val preferences: AstroPreferencesManager,
    private val notificationManager: AstroNotificationManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val today = LocalDate.now()
            val jd    = JulianDayConverter.toJulianDay(today)

            // Refresh transit cache — EphemerisEngine JNI is OK in a Worker
            val transit = getDailyTransitUseCase.execute(jd)

            // Store Rahu Kaal window for widget + ViewModel
            val rahuKaal = computeRahuKaal(today, jd)
            preferences.saveRahuKaal(rahuKaal.startMs, rahuKaal.endMs, jd)

            // Trigger widget refresh via broadcast to decouple feature:astro from widget:astro-home
            val updateIntent = android.content.Intent("com.jugaad.astro.action.UPDATE_WIDGET")
            updateIntent.setPackage(applicationContext.packageName)
            applicationContext.sendBroadcast(updateIntent)

            // Compute rule insight for smart briefing (pure Kotlin, < 1ms, safe in worker)
            val rules          = ruleEngine.generateDeterministicInsight(transit, today)
            val peakWindow     = rules.auspiciousWindows.firstOrNull() ?: "morning"
            val rahuKaalLabel  = rules.avoidWindows.firstOrNull()
                ?.substringBefore("  Rahu Kaal")
                ?: "check app"

            notificationManager.sendDailyBriefing(
                peakWindow = peakWindow,
                rahuKaal   = rahuKaalLabel,
                summary    = rules.summary
            )

            Result.success()
        }.getOrElse { Result.retry() }
    }

    private data class RahuWindow(val startMs: Long, val endMs: Long)

    private fun computeRahuKaal(today: LocalDate, julianDayUt: Double): RahuWindow {
        val slotByDay = intArrayOf(8, 2, 7, 5, 6, 4, 3)   // Sun=7, Mon=1, …
        val dayOfWeek = today.dayOfWeek.value % 7
        val slot      = slotByDay[dayOfWeek]

        // Approximate day window: 6am → 6pm (12h), 8 equal parts of 90 min
        val sunriseMs  = today.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000 + 6 * 3_600_000L
        val partMs     = 90L * 60_000L
        return RahuWindow(
            startMs = sunriseMs + (slot - 1) * partMs,
            endMs   = sunriseMs + slot * partMs
        )
    }

    companion object {
        const val WORK_TAG = "astro_transit_refresh"

        private val PLANET_NAMES = arrayOf(
            "Sun","Moon","Mercury","Venus","Mars","Jupiter","Saturn","Rahu","Ketu"
        )
    }
}
