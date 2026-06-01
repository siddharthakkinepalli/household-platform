package com.jugaad.core.airuntime.runtime

import com.jugaad.core.airuntime.model.ModelConfig
import java.util.concurrent.ArrayBlockingQueue

/**
 * Thread-safe pool of reusable [FloatArray] logit buffers.
 *
 * ONNX Runtime produces a new allocation per forward pass by default. Pooling these
 * avoids GC pressure during autoregressive generation where the loop runs
 * up to [ModelConfig.MAX_OUTPUT_TOKENS] times.
 *
 * Contract:
 *  - [acquire] returns a zeroed buffer (either freshly allocated or recycled + zeroed).
 *  - [release] zeros the buffer before returning it to the queue. Callers MUST release
 *    from a `finally` block to prevent pool exhaustion on exceptions.
 *  - Buffers that cannot fit back into the pool (pool full) are discarded — GC handles them.
 *
 * @param bufferSize  Number of floats per buffer. Default = [ModelConfig.LOGIT_BUFFER_SIZE].
 * @param poolCapacity Max pooled buffers. Default = [ModelConfig.TENSOR_POOL_SIZE].
 */
class TensorBufferPool(
    private val bufferSize: Int   = ModelConfig.LOGIT_BUFFER_SIZE,
    private val poolCapacity: Int = ModelConfig.TENSOR_POOL_SIZE
) {
    private val pool = ArrayBlockingQueue<FloatArray>(poolCapacity)

    /**
     * Returns a zeroed [FloatArray] of [bufferSize] elements.
     * Non-blocking — allocates a new array if the pool is empty.
     */
    fun acquire(): FloatArray = pool.poll()?.also { it.fill(0f) } ?: FloatArray(bufferSize)

    /**
     * Returns [buffer] to the pool after zeroing it.
     * If the pool is at capacity, the buffer is discarded (not blocked).
     */
    fun release(buffer: FloatArray) {
        buffer.fill(0f)
        pool.offer(buffer)  // non-blocking; drops if full
    }
}
