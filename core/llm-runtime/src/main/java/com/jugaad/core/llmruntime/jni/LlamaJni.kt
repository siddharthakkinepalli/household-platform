package com.jugaad.core.llmruntime.jni

internal object LlamaJni {
    init {
        System.loadLibrary("llama_jni")
    }

    external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Long
    external fun nativeCreateContext(modelPtr: Long, nCtx: Int): Long
    external fun nativeGenerate(
        ctxPtr: Long,
        modelPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repPenalty: Float
    ): String

    external fun nativeGenerateStream(
        ctxPtr: Long,
        modelPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repPenalty: Float,
        callback: (String) -> Unit
    )
    external fun nativeReleaseContext(ctxPtr: Long)
    external fun nativeFreeModel(modelPtr: Long)
    external fun nativeStopGeneration()
}
