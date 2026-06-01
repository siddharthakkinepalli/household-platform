package com.jugaad.widget.astrohome

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Glance home screen widget displaying today's key Vedic transit data.
 *
 * ══ STRICT CONSTRAINTS ══
 * - Cache-read-only: reads exclusively from Glance preference state, populated by
 *   [AstroWidgetUpdater] (called from [TransitRefreshWorker]).
 * - NO EphemerisEngine JNI calls — all NDK computation is banned from AppWidgetProvider lifecycles.
 * - NO AI inference — prediction text is read from pre-computed cache.
 * - Rendering MUST complete under 100ms — no blocking I/O in [Content].
 *
 * State keys match exactly what [AstroWidgetUpdater] writes. If a key is absent,
 * the widget renders a sensible placeholder without crashing.
 */
class AstroHomeWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, id)
        provideContent {
            GlanceTheme {
                Content(prefs)
            }
        }
    }

    @Composable
    fun Content(prefs: Preferences) {
        val predictionText = prefs[KEY_PREDICTION_TEXT] ?: "Tap to load today's insight"
        val moonSign       = prefs[KEY_MOON_SIGN]       ?: "—"
        val nakshatra      = prefs[KEY_NAKSHATRA]       ?: ""
        val rahuStartMs    = prefs[KEY_RAHU_START_MS]   ?: 0L
        val rahuEndMs      = prefs[KEY_RAHU_END_MS]     ?: 0L
        val retrogrades    = prefs[KEY_RETROGRADE_PLANETS] ?: ""

        val nowMs          = System.currentTimeMillis()
        val rahuProgress   = computeRahuProgress(nowMs, rahuStartMs, rahuEndMs)
        val rahuActive     = nowMs in rahuStartMs..rahuEndMs
        val startLabel     = epochToTimeLabel(rahuStartMs)
        val endLabel       = epochToTimeLabel(rahuEndMs)

        WidgetBody(
            moonSign       = moonSign,
            nakshatra      = nakshatra,
            predictionText = predictionText,
            rahuProgress   = rahuProgress,
            rahuActive     = rahuActive,
            rahuLabel      = "$startLabel–$endLabel",
            retrogrades    = retrogrades
        )
    }

    companion object {
        // Preference keys — must match AstroWidgetUpdater.update()
        val KEY_PREDICTION_TEXT    = stringPreferencesKey("w_prediction_text")
        val KEY_MOON_SIGN          = stringPreferencesKey("w_moon_sign")
        val KEY_NAKSHATRA          = stringPreferencesKey("w_nakshatra")
        val KEY_RAHU_START_MS      = longPreferencesKey("w_rahu_start_ms")
        val KEY_RAHU_END_MS        = longPreferencesKey("w_rahu_end_ms")
        val KEY_RETROGRADE_PLANETS = stringPreferencesKey("w_retrogrades")
    }
}

// ── Widget layout (Glance composables only — NOT standard Compose) ────────────

@Composable
private fun WidgetBody(
    moonSign:       String,
    nakshatra:      String,
    predictionText: String,
    rahuProgress:   Float,
    rahuActive:     Boolean,
    rahuLabel:      String,
    retrogrades:    String
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ── Header row ────────────────────────────────────────────────────
        Row(
            modifier              = GlanceModifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalAlignment   = Alignment.Start
        ) {
            Text(
                text  = "☽ $moonSign",
                style = TextStyle(
                    color      = GlanceTheme.colors.primary,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.width(6.dp))
            if (nakshatra.isNotBlank()) {
                Text(
                    text  = "· $nakshatra",
                    style = TextStyle(
                        color    = GlanceTheme.colors.secondary,
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        // ── AI insight snippet ────────────────────────────────────────────
        Text(
            text     = predictionText.take(120).let { if (predictionText.length > 120) "$it…" else it },
            style    = TextStyle(
                color    = GlanceTheme.colors.onSurface,
                fontSize = 12.sp
            ),
            maxLines = 3
        )

        Spacer(GlanceModifier.height(8.dp))

        // ── Rahu Kaal progress bar ────────────────────────────────────────
        Row(
            modifier          = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = if (rahuActive) "⚡ Rahu Kaal" else "Rahu $rahuLabel",
                style = TextStyle(
                    color    = if (rahuActive) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = rahuProgress,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color    = if (rahuActive) GlanceTheme.colors.error else GlanceTheme.colors.primary
        )

        // ── Retrograde indicator ──────────────────────────────────────────
        if (retrogrades.isNotBlank()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text  = "℞ $retrogrades",
                style = TextStyle(
                    color    = GlanceTheme.colors.secondary,
                    fontSize = 10.sp
                )
            )
        }
    }
}

// ── Pure helpers (no coroutines, no I/O — safe for <100ms render) ─────────────

private fun computeRahuProgress(nowMs: Long, startMs: Long, endMs: Long): Float {
    if (startMs <= 0 || endMs <= startMs) return 0f
    return when {
        nowMs <= startMs -> 0f
        nowMs >= endMs   -> 1f
        else             -> (nowMs - startMs).toFloat() / (endMs - startMs)
    }
}

private fun epochToTimeLabel(epochMs: Long): String {
    if (epochMs <= 0L) return "--:--"
    return try {
        val ldt = java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
        String.format("%02d:%02d", ldt.hour, ldt.minute)
    } catch (_: Exception) { "--:--" }
}
