package com.household.app.feature.assistant.di

import com.household.app.feature.assistant.HouseholdContextProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    fun provideContextProvider(@ApplicationContext context: Context): HouseholdContextProvider {
        return HouseholdContextProvider(context)
    }
}
