package com.jugaad.core.documentai.di

import com.jugaad.core.documentai.DocumentInferenceModel
import com.jugaad.core.llmruntime.LlamaEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DocumentAiModule {

    @Provides
    @Singleton
    fun provideDocumentInferenceModel(engine: LlamaEngine): DocumentInferenceModel {
        return DocumentInferenceModel(engine)
    }
}
