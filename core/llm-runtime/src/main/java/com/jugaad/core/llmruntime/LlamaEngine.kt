package com.jugaad.core.llmruntime

import android.content.Context
import android.util.Log
import com.jugaad.core.llmruntime.jni.LlamaJni
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class ModelTier { DEEP }

    object DeepParams {       // Gemma 4 E2B Q4_K_M
        const val MAX_TOKENS  = 96   // ~3–4 sentences; 256 was generating needless padding
        const val TEMPERATURE = 0.1f
        const val TOP_P       = 0.9f
        const val TOP_K       = 40
        const val REP_PENALTY = 1.0f
    }

    companion object {
        const val N_CTX     = 768  // 300 system + 200 context + 50 history + 96 gen + headroom
        const val N_THREADS = 4    // matches C++ n_threads — Prime + 3 Gold perf cores
    }

    private var modelPtr = 0L
    private var ctxPtr = 0L
    private val mutex = Mutex()
    var currentTier: ModelTier = ModelTier.DEEP
        private set

    val isLoaded: Boolean get() = modelPtr != 0L && ctxPtr != 0L

    suspend fun loadModel(modelPath: String, tier: ModelTier = ModelTier.DEEP): Boolean = withContext(Dispatchers.Default) {
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

            if (isLoaded) currentTier = tier
            isLoaded
        }
    }

    suspend fun swap(newTier: ModelTier, modelPath: String): Boolean =
        withContext(Dispatchers.Default) {
            val tQueued = System.currentTimeMillis()
            Log.d("LlamaEngine", "swap queued → $newTier  mutexLocked=${mutex.isLocked}")
            if (mutex.isLocked) LlamaJni.nativeStopGeneration()
            mutex.withLock {
                Log.d("LlamaEngine", "swap acquired mutex after ${System.currentTimeMillis() - tQueued}ms")
                if (currentTier == newTier && isLoaded) return@withLock true
                val t0 = System.currentTimeMillis()
                Log.d("LlamaEngine", "swap → $newTier  path=$modelPath")

                if (ctxPtr != 0L) {
                    LlamaJni.nativeReleaseContext(ctxPtr); ctxPtr = 0L
                    Log.d("LlamaEngine", "  releaseContext: ${System.currentTimeMillis() - t0}ms")
                }
                if (modelPtr != 0L) {
                    LlamaJni.nativeFreeModel(modelPtr); modelPtr = 0L
                    Log.d("LlamaEngine", "  freeModel: ${System.currentTimeMillis() - t0}ms")
                }

                val tLoad = System.currentTimeMillis()
                modelPtr = LlamaJni.nativeLoadModel(modelPath, N_CTX, N_THREADS)
                Log.d("LlamaEngine", "  nativeLoadModel: ${System.currentTimeMillis() - tLoad}ms  ptr=$modelPtr")

                if (modelPtr != 0L) {
                    val tCtx = System.currentTimeMillis()
                    ctxPtr = LlamaJni.nativeCreateContext(modelPtr, N_CTX)
                    Log.d("LlamaEngine", "  nativeCreateContext: ${System.currentTimeMillis() - tCtx}ms  ptr=$ctxPtr")
                }
                if (ctxPtr == 0L && modelPtr != 0L) {
                    LlamaJni.nativeFreeModel(modelPtr); modelPtr = 0L
                }
                Log.d("LlamaEngine", "swap done  ok=$isLoaded  total=${System.currentTimeMillis() - t0}ms")
                if (isLoaded) { currentTier = newTier; true } else false
            }
        }

    suspend fun generate(prompt: String, tier: ModelTier = ModelTier.DEEP): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (!isLoaded) return@withLock ""
            val t0 = System.currentTimeMillis()
            val (max, temp, topP, topK, rep) = getGenerationParams(tier)
            val result = LlamaJni.nativeGenerate(ctxPtr, modelPtr, prompt, max, temp, topP, topK, rep)
            Log.d("LlamaEngine", "generate: ${System.currentTimeMillis() - t0}ms")
            result
        }
    }

    fun generateStream(prompt: String, tier: ModelTier = ModelTier.DEEP): Flow<String> = callbackFlow {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                if (!isLoaded) {
                    return@withLock
                }
                val t0 = System.currentTimeMillis()
                val (max, temp, topP, topK, rep) = getGenerationParams(tier)
                LlamaJni.nativeGenerateStream(ctxPtr, modelPtr, prompt, max, temp, topP, topK, rep) { token ->
                    trySend(token)
                }
                Log.d("LlamaEngine", "generateStream: ${System.currentTimeMillis() - t0}ms")
            }
        }
        close()
    }

    private fun getGenerationParams(tier: ModelTier): GenerationParams = when (tier) {
        ModelTier.DEEP -> GenerationParams(
            DeepParams.MAX_TOKENS, DeepParams.TEMPERATURE, DeepParams.TOP_P, DeepParams.TOP_K, DeepParams.REP_PENALTY
        )
    }

    private data class GenerationParams(val max: Int, val temp: Float, val topP: Float, val topK: Int, val rep: Float)

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
