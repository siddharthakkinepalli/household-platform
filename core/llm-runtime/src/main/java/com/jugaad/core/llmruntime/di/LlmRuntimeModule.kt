package com.jugaad.core.llmruntime.di

import android.content.Context
import com.jugaad.core.llmruntime.LlamaEngine
import com.jugaad.core.llmruntime.ModelDownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmRuntimeModule {

    @Provides
    @Singleton
    fun provideLlamaEngine(@ApplicationContext ctx: Context): LlamaEngine {
        return LlamaEngine(ctx)
    }

    @Provides
    @Singleton
    fun provideModelDownloadManager(@ApplicationContext ctx: Context): ModelDownloadManager {
        return ModelDownloadManager(ctx)
    }
}
