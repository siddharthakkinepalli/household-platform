package com.jugaad.widget.astrohome.background

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.jugaad.feature.astro.preferences.AstroPreferencesManager
import com.jugaad.widget.astrohome.AstroHomeWidget
import com.jugaad.widget.astrohome.AstroHomeWidget.Companion.KEY_PREDICTION_TEXT
import com.jugaad.widget.astrohome.AstroHomeWidget.Companion.KEY_RAHU_END_MS
import com.jugaad.widget.astrohome.AstroHomeWidget.Companion.KEY_RAHU_START_MS

/**
 * Utility object that pushes the latest cached data into the Glance widget state.
 *
 * The widget renderer only reads from its own Glance preference state — it never
 * touches Room or DataStore directly.
 */
object AstroWidgetUpdater {

    suspend fun update(context: Context) {
        val prefs    = AstroPreferencesManager(context)
        val cached   = prefs.getCachedPrediction()
        val rahuKaal = prefs.getRahuKaal()

        val glanceManager = GlanceAppWidgetManager(context)
        val ids = glanceManager.getGlanceIds(AstroHomeWidget::class.java)

        ids.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
                state.toMutablePreferences().apply {
                    set(KEY_PREDICTION_TEXT,    cached?.text ?: "")
                    set(KEY_RAHU_START_MS,      rahuKaal?.startMs ?: 0L)
                    set(KEY_RAHU_END_MS,        rahuKaal?.endMs ?: 0L)
                }
            }
            AstroHomeWidget().update(context, id)
        }
    }
}
