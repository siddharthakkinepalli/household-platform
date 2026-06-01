package com.jugaad.core.ephemeris

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Thread-confined dispatcher for all Swiss Ephemeris JNI calls.
 *
 * Swiss Ephemeris (libswe) is NOT thread-safe — it uses process-global state
 * for the ephemeris path, ayanamsha mode, and file handles. All calls to
 * [SwephJni] must execute on this single-thread executor to prevent data races.
 *
 * Usage:
 * ```kotlin
 * withContext(EphemerisDispatcher.dispatcher) {
 *     SwephJni.nativeComputePlanet(jd, planetId)
 * }
 * ```
 *
 * The underlying thread is named "swe-compute-0" for profiler identification.
 * Do NOT pass JNI pointers or native memory references across coroutine context
 * boundaries — all computation must begin and end within the same dispatcher block.
 */
object EphemerisDispatcher {

    val dispatcher: CoroutineDispatcher = Executors
        .newSingleThreadExecutor { runnable ->
            Thread(runnable, "swe-compute-0").also { it.isDaemon = true }
        }
        .asCoroutineDispatcher()
}
