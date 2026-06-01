package com.jugaad.feature.astro.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.astroDataStore: DataStore<Preferences> by preferencesDataStore("jugaad_astro_prefs")

/**
 * Lightweight preference store for the astro sub-system.
 *
 * Stores:
 *  1. Daily prediction cache — written by AstroDashboardViewModel (foreground), read by widget.
 *  2. Rahu Kaal window times — written by TransitRefreshWorker, read by widget + ViewModel.
 *  3. Notification timestamps — written by AstroNotificationManager for debounce enforcement.
 *
 * NEVER stores birth data (lat/lon/DOB) — those live only in the AES-GCM encrypted Room entity.
 */
@Singleton
class AstroPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Prediction cache keys ─────────────────────────────────────────────────
    private val PREDICTION_TEXT        = stringPreferencesKey("astro_prediction_text")
    private val PREDICTION_JD          = floatPreferencesKey("astro_prediction_jd")
    private val PREDICTION_CONFIDENCE  = floatPreferencesKey("astro_prediction_confidence")
    private val PREDICTION_FROM_NPU    = booleanPreferencesKey("astro_prediction_from_npu")
    private val PREDICTION_INFERENCE_MS= longPreferencesKey("astro_prediction_inference_ms")

    // ── Rahu Kaal cache keys ──────────────────────────────────────────────────
    private val RAHU_KAAL_START_MS = longPreferencesKey("rahu_kaal_start_ms")
    private val RAHU_KAAL_END_MS   = longPreferencesKey("rahu_kaal_end_ms")
    private val RAHU_KAAL_JD       = floatPreferencesKey("rahu_kaal_jd")

    // ── Notification debounce key ─────────────────────────────────────────────
    private val NOTIFICATION_TIMESTAMPS = stringPreferencesKey("astro_notif_timestamps")

    // ── Prediction cache ──────────────────────────────────────────────────────

    data class CachedPrediction(
        val text: String,
        val julianDayUt: Double,
        val confidence: Float,
        val fromNpu: Boolean,
        val inferenceMs: Long
    )

    suspend fun savePrediction(
        text: String,
        julianDayUt: Double,
        confidence: Float,
        fromNpu: Boolean,
        inferenceMs: Long
    ) {
        context.astroDataStore.edit { prefs ->
            prefs[PREDICTION_TEXT]         = text
            prefs[PREDICTION_JD]           = julianDayUt.toFloat()
            prefs[PREDICTION_CONFIDENCE]   = confidence
            prefs[PREDICTION_FROM_NPU]     = fromNpu
            prefs[PREDICTION_INFERENCE_MS] = inferenceMs
        }
    }

    fun observePrediction(): Flow<CachedPrediction?> =
        context.astroDataStore.data.map { prefs ->
            val text = prefs[PREDICTION_TEXT] ?: return@map null
            CachedPrediction(
                text        = text,
                julianDayUt = (prefs[PREDICTION_JD] ?: 0f).toDouble(),
                confidence  = prefs[PREDICTION_CONFIDENCE] ?: 0f,
                fromNpu     = prefs[PREDICTION_FROM_NPU] ?: false,
                inferenceMs = prefs[PREDICTION_INFERENCE_MS] ?: 0L
            )
        }

    suspend fun getCachedPrediction(): CachedPrediction? =
        observePrediction().first()

    // ── Rahu Kaal cache ───────────────────────────────────────────────────────

    data class RahuKaalCache(
        val startMs: Long,
        val endMs: Long,
        val julianDayUt: Double
    )

    suspend fun saveRahuKaal(startMs: Long, endMs: Long, julianDayUt: Double) {
        context.astroDataStore.edit { prefs ->
            prefs[RAHU_KAAL_START_MS] = startMs
            prefs[RAHU_KAAL_END_MS]   = endMs
            prefs[RAHU_KAAL_JD]       = julianDayUt.toFloat()
        }
    }

    suspend fun getRahuKaal(): RahuKaalCache? =
        context.astroDataStore.data.map { prefs ->
            val start = prefs[RAHU_KAAL_START_MS] ?: return@map null
            val end   = prefs[RAHU_KAAL_END_MS]   ?: return@map null
            RahuKaalCache(
                startMs     = start,
                endMs       = end,
                julianDayUt = (prefs[RAHU_KAAL_JD] ?: 0f).toDouble()
            )
        }.first()

    // ── Notification debounce ─────────────────────────────────────────────────

    /** Returns epoch-ms timestamps of notifications sent in the last 24 hours. */
    suspend fun getRecentNotificationTimestamps(): List<Long> {
        val raw = context.astroDataStore.data.map { it[NOTIFICATION_TIMESTAMPS] ?: "" }.first()
        if (raw.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val windowStart = now - 24 * 60 * 60 * 1000L
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it >= windowStart }
    }

    suspend fun recordNotificationSent() {
        val existing = getRecentNotificationTimestamps().toMutableList()
        existing.add(System.currentTimeMillis())
        context.astroDataStore.edit { prefs ->
            prefs[NOTIFICATION_TIMESTAMPS] = existing.joinToString(",")
        }
    }
}
