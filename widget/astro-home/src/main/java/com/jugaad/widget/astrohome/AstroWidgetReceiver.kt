package com.jugaad.widget.astrohome

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidgetProvider for [AstroHomeWidget].
 *
 * Glance handles all widget lifecycle callbacks (onUpdate, onEnabled, onDisabled)
 * via this receiver — no custom onUpdate() override needed.
 *
 * The actual data refresh is driven by [TransitRefreshWorker] via [AstroWidgetUpdater],
 * which calls [GlanceAppWidget.update] after writing to the widget's preference state.
 *
 * Registered in the host app's AndroidManifest.xml with:
 *   <meta-data android:name="android.appwidget.provider"
 *              android:resource="@xml/astro_widget_info" />
 */
class AstroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AstroHomeWidget()
}
