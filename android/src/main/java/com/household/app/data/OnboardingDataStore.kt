package com.household.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingStore: DataStore<Preferences> by preferencesDataStore("onboarding")

object OnboardingDataStore {
    private val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")

    suspend fun hasOnboarded(context: Context): Boolean =
        context.onboardingStore.data.map { it[HAS_ONBOARDED] ?: false }.first()

    suspend fun markOnboarded(context: Context) {
        context.onboardingStore.edit { it[HAS_ONBOARDED] = true }
    }
}
