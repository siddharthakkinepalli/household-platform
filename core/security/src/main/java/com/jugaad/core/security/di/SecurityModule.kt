package com.jugaad.core.security.di

import com.jugaad.core.security.log.AstroLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideAstroLogger(): AstroLogger = AstroLogger()
}
