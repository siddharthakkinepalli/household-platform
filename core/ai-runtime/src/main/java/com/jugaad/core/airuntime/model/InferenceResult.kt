package com.jugaad.core.airuntime.model

/**
 * Output from a single local inference run.
 *
 * [confidence] is derived from the mean top-token probability across all sampled positions
 * using nucleus (top-p) sampling. Range: 0.0 (random) → 1.0 (fully deterministic).
 *
 * [inferenceMs] measures wall-clock time from session.run() entry to last decoded token.
 * Used for latency monitoring — alert if > 3000ms on NPU or > 8000ms on CPU fallback.
 *
 * [fromNpu] is true when the NNAPI execution provider was active for this result.
 * False indicates CPU fallback was used (NPU unavailable or allocation failed).
 */
data class InferenceResult(
    /** The generated prediction text, decoded from sampled token IDs. */
    val predictionText: String,

    /** Mean confidence score across sampled positions [0.0, 1.0]. */
    val confidence: Float,

    /** Semantic version string of the ONNX model that produced this result. */
    val modelVersion: String,

    /** Wall-clock inference time in milliseconds. */
    val inferenceMs: Long,

    /** True when NNAPI NPU execution provider was used. False = CPU fallback. */
    val fromNpu: Boolean = false
)
