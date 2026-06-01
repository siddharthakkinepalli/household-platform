package com.jugaad.feature.astro.di

import android.content.Context
import com.jugaad.core.security.keystore.AstroKeyProvider
import com.jugaad.feature.astro.data.db.AstroDatabase
import com.jugaad.feature.astro.data.db.dao.DailyTransitDao
import com.jugaad.feature.astro.data.db.dao.FeedbackDao
import com.jugaad.feature.astro.data.db.dao.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AstroDatabaseModule {

    @Provides
    @Singleton
    fun provideAstroDatabase(
        @ApplicationContext context: Context,
        keyProvider: AstroKeyProvider
    ): AstroDatabase = AstroDatabase.build(context, keyProvider)

    @Provides
    @Singleton
    fun provideUserProfileDao(db: AstroDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    @Singleton
    fun provideDailyTransitDao(db: AstroDatabase): DailyTransitDao = db.dailyTransitDao()

    @Provides
    @Singleton
    fun provideFeedbackDao(db: AstroDatabase): FeedbackDao = db.feedbackDao()
}
