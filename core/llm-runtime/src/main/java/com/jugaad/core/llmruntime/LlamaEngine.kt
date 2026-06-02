package com.jugaad.core.llmruntime

import android.content.Context
import com.jugaad.core.llmruntime.jni.LlamaJni
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val N_CTX = 4096
        const val N_THREADS = 4
        const val TEMPERATURE = 0.1f
        const val TOP_P = 0.9f
        const val MAX_TOKENS = 512
    }

    private var modelPtr = 0L
    private var ctxPtr = 0L
    private val mutex = Mutex()

    val isLoaded: Boolean get() = modelPtr != 0L && ctxPtr != 0L

    suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (isLoaded) return@withLock true

            modelPtr = LlamaJni.nativeLoadModel(modelPath, N_CTX, N_THREADS)
            if (modelPtr != 0L) {
                ctxPtr = LlamaJni.nativeCreateContext(modelPtr, N_CTX)
            }

            if (ctxPtr == 0L && modelPtr != 0L) {
                LlamaJni.nativeFreeModel(modelPtr)
                modelPtr = 0L
            }

            isLoaded
        }
    }

    suspend fun generate(prompt: String, maxTokens: Int = MAX_TOKENS): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (!isLoaded) return@withLock ""
            LlamaJni.nativeGenerate(ctxPtr, modelPtr, prompt, maxTokens, TEMPERATURE, TOP_P)
        }
    }

    fun release() {
        if (ctxPtr != 0L) {
            LlamaJni.nativeReleaseContext(ctxPtr)
            ctxPtr = 0L
        }
        if (modelPtr != 0L) {
            LlamaJni.nativeFreeModel(modelPtr)
            modelPtr = 0L
        }
    }
}
