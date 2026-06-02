package com.household.app.di

import com.jugaad.core.documentai.DocumentInferenceModel
import com.jugaad.core.llmruntime.ModelDownloadManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentAiEntryPoint {
    fun documentInferenceModel(): DocumentInferenceModel
    fun modelDownloadManager(): ModelDownloadManager
}
