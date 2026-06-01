package com.jugaad.core.airuntime.di

import android.content.Context
import com.jugaad.core.airuntime.AstroInferenceModel
import com.jugaad.core.airuntime.runtime.LocalInferenceEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the `core:ai-runtime` module.
 *
 * Both [LocalInferenceEngine] and [AstroInferenceModel] are @Singleton — the ONNX
 * session is expensive to create (~200–500ms NNAPI bind + model load) and must be
 * shared across all callers rather than re-initialized per ViewModel.
 *
 * [LocalInferenceEngine] is also exposed as a binding so that Phase 5 ViewModels
 * can inject the engine directly if they need raw [GenerationResult] access.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiRuntimeModule {

    @Provides
    @Singleton
    fun provideLocalInferenceEngine(
        @ApplicationContext context: Context
    ): LocalInferenceEngine = LocalInferenceEngine(context)

    @Provides
    @Singleton
    fun provideAstroInferenceModel(
        @ApplicationContext context: Context,
        engine: LocalInferenceEngine
    ): AstroInferenceModel = AstroInferenceModel(context, engine)
}
