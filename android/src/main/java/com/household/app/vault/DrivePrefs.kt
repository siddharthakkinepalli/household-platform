package com.household.app.vault

import android.content.Context

object DrivePrefs {
    private const val PREFS_NAME = "drive_prefs"
    private const val KEY_DRIVE_ENABLED = "drive_enabled"

    fun isDriveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DRIVE_ENABLED, false)

    fun setDriveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DRIVE_ENABLED, enabled)
            .apply()
    }
}
