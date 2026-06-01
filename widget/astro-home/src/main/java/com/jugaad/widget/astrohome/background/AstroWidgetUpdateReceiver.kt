package com.jugaad.widget.astrohome.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver that listens for transit data refresh completion and updates
 * all active Glance widgets.
 */
class AstroWidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    AstroWidgetUpdater.update(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.jugaad.astro.action.UPDATE_WIDGET"
    }
}
