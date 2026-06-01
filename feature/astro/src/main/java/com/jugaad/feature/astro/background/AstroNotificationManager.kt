package com.jugaad.feature.astro.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jugaad.feature.astro.preferences.AstroPreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delivers Vedic astro notifications with strict debounce enforcement.
 *
 * Debounce rules (enforced via [AstroPreferencesManager]):
 *  - Maximum 3 planetary notifications in any rolling 24-hour window.
 *  - Adjacent transit updates (same planet changing sign within 2 hours) are coalesced
 *    into a single summary notification instead of firing individually.
 *  - Rahu Kaal window entry fires at most once per calendar day.
 */
@Singleton
class AstroNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AstroPreferencesManager
) {

    companion object {
        const val CHANNEL_ID    = "astro_vedic_alerts"
        const val CHANNEL_NAME  = "Vedic Astro Alerts"
        const val MAX_PER_24H   = 3

        private const val NOTIF_ID_TRANSIT  = 9001
        private const val NOTIF_ID_RAHU     = 9002
        private const val NOTIF_ID_SUMMARY  = 9003
        private const val NOTIF_ID_BRIEFING = 9004
    }

    init {
        createChannelIfNeeded()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Sends a transit-change notification if the debounce quota allows it. */
    suspend fun sendTransitNotification(planetName: String, fromSign: String, toSign: String) {
        if (!canSend()) return
        val title = "$planetName enters $toSign"
        val body  = "Transit shift: $planetName moves from $fromSign → $toSign"
        deliver(NOTIF_ID_TRANSIT, title, body)
    }

    /**
     * Coalesces multiple planet events into a summary notification.
     * Used when 2+ planets change sign within the same 2-hour window.
     *
     * @param changes List of "PlanetName: OldSign → NewSign" strings.
     */
    suspend fun sendCoalescedTransitSummary(changes: List<String>) {
        if (!canSend() || changes.isEmpty()) return
        val title = "${changes.size} planetary transits today"
        val body  = changes.take(3).joinToString(" • ")
        deliver(NOTIF_ID_SUMMARY, title, body)
    }

    /**
     * Sends the 8 AM actionable daily briefing.
     *
     * [peakWindow]  e.g. "9:00 AM–10:00 AM  Jupiter Hora (Expansion & Clarity)"
     * [rahuKaal]    e.g. "7:30 AM–9:00 AM"
     * [summary]     One-sentence day summary from the rule engine.
     */
    suspend fun sendDailyBriefing(peakWindow: String, rahuKaal: String, summary: String) {
        if (!canSend()) return
        val body = buildString {
            append(summary)
            append("\n🟢 Peak window: $peakWindow")
            append("\n🔴 Avoid: $rahuKaal (Rahu Kaal)")
        }
        deliver(NOTIF_ID_BRIEFING, "Good morning — today's alignment", body)
    }

    /** Sends a Rahu Kaal window-open notification (at most once per calendar day). */
    suspend fun sendRahuKaalAlert(startLabel: String, endLabel: String) {
        if (!canSend()) return
        deliver(
            id    = NOTIF_ID_RAHU,
            title = "Rahu Kaal Active",
            body  = "Inauspicious period: $startLabel – $endLabel. Avoid new beginnings."
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun canSend(): Boolean {
        val recent = preferences.getRecentNotificationTimestamps()
        return recent.size < MAX_PER_24H
    }

    private suspend fun deliver(id: Int, title: String, body: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            context, id, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(id, notification)
        preferences.recordNotificationSent()
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Planetary transit changes, Rahu Kaal windows, and daily astro insights"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
