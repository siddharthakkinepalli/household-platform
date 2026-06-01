package com.jugaad.feature.astro.di

import com.jugaad.feature.astro.data.repository.AstroRepositoryImpl
import com.jugaad.feature.astro.domain.repository.AstroRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Top-level Hilt module for the astro feature.
 *
 * Binds [AstroRepository] → [AstroRepositoryImpl].
 * Database and DAO bindings live in [AstroDatabaseModule].
 * Security bindings live in com.jugaad.core.security.di.SecurityModule.
 * EphemerisEngine binding lives in com.jugaad.core.ephemeris.di.EphemerisModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AstroModule {

    @Binds
    @Singleton
    abstract fun bindAstroRepository(impl: AstroRepositoryImpl): AstroRepository
}
