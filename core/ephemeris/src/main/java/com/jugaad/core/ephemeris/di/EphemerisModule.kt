package com.jugaad.core.ephemeris.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object EphemerisModule {
    // EphemerisEngine is provided via @Inject constructor and @Singleton annotation on the class itself.
    // No explicit @Provides method is needed here unless we want to provide an interface.
}
