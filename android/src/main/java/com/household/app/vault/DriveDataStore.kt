package com.household.app.vault

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.driveDataStore: DataStore<Preferences> by preferencesDataStore(name = "drive_prefs")

object DriveDataStore {
    private val KEY_DRIVE_ENABLED = booleanPreferencesKey("drive_enabled")
    private val KEY_JUGAAD_FOLDER_ID = stringPreferencesKey("jugaad_folder_id")

    fun observeDriveEnabled(context: Context): Flow<Boolean> =
        context.driveDataStore.data.map { it[KEY_DRIVE_ENABLED] ?: false }

    suspend fun isDriveEnabled(context: Context): Boolean =
        context.driveDataStore.data.map { it[KEY_DRIVE_ENABLED] ?: false }.first()

    suspend fun setDriveEnabled(context: Context, enabled: Boolean) {
        context.driveDataStore.edit { it[KEY_DRIVE_ENABLED] = enabled }
    }

    suspend fun getJugaadFolderId(context: Context): String? =
        context.driveDataStore.data.map { it[KEY_JUGAAD_FOLDER_ID] }.first()

    suspend fun setJugaadFolderId(context: Context, folderId: String) {
        context.driveDataStore.edit { it[KEY_JUGAAD_FOLDER_ID] = folderId }
    }

    suspend fun clearOnSignOut(context: Context) {
        context.driveDataStore.edit {
            it[KEY_DRIVE_ENABLED] = false
            it.remove(KEY_JUGAAD_FOLDER_ID)
        }
    }
}
