package com.jugaad.core.ephemeris.jni

import android.util.Log

/**
 * Raw JNI declarations for the Swiss Ephemeris C bridge.
 */
internal object SwephJni {

    private var libraryLoaded = false

    init {
        runCatching {
            System.loadLibrary("sweph_bridge")
            libraryLoaded = true
        }.onFailure {
            Log.w("SwephJni", "Native library sweph_bridge not found, using stubs")
        }
    }

    /**
     * Initialises the Swiss Ephemeris engine and sets the sidereal mode to Lahiri.
     */
    fun initialize(ephePath: String) {
        if (libraryLoaded) {
            try {
                nativeInitialize(ephePath)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SwephJni", "JNI call failed", e)
            }
        }
    }

    /**
     * Releases Swiss Ephemeris resources.
     */
    fun release() {
        if (libraryLoaded) {
            try {
                nativeRelease()
            } catch (e: UnsatisfiedLinkError) {}
        }
    }

    /**
     * Computes the sidereal position of a single planet at the given Julian Day (UT).
     */
    fun computePlanet(julianDayUt: Double, planetId: Int): DoubleArray? {
        if (libraryLoaded) {
            try {
                return nativeComputePlanet(julianDayUt, planetId)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SwephJni", "JNI call failed", e)
            }
        }
        // Stub fallback
        val angle = (julianDayUt % 360.0)
        return doubleArrayOf(angle, 0.0, 1.0, 0.0)
    }

    /**
     * Computes the Lagna (Ascendant) and Whole Sign house cusps for a location.
     */
    fun computeLagna(
        julianDayUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double
    ): DoubleArray? {
        if (libraryLoaded) {
            try {
                return nativeComputeLagna(julianDayUt, latitudeDeg, longitudeDeg)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SwephJni", "JNI call failed", e)
            }
        }
        return DoubleArray(13) { if (it == 0) 0.0 else (it - 1) * 30.0 }
    }

    /**
     * Computes topographic sunrise and sunset Julian Days with atmospheric refraction.
     */
    fun computeSunriseSunset(
        julianDayUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        elevationMeters: Double
    ): DoubleArray? {
        if (libraryLoaded) {
            try {
                return nativeComputeSunriseSunset(julianDayUt, latitudeDeg, longitudeDeg, elevationMeters)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SwephJni", "JNI call failed", e)
            }
        }
        val base = julianDayUt.toLong().toDouble()
        return doubleArrayOf(base + 0.25, base + 0.75)
    }

    /**
     * Returns the Lahiri ayanamsha value (in degrees) for the given Julian Day.
     */
    fun getAyanamsha(julianDayUt: Double): Double {
        if (libraryLoaded) {
            try {
                return nativeGetAyanamsha(julianDayUt)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("SwephJni", "JNI call failed", e)
            }
        }
        return 24.0
    }

    // ── External JNI declarations (mapping to sweph_bridge.cpp) ────────────────

    @JvmStatic private external fun nativeInitialize(ephePath: String)
    @JvmStatic private external fun nativeRelease()
    @JvmStatic private external fun nativeComputePlanet(julianDayUt: Double, planetId: Int): DoubleArray?
    @JvmStatic private external fun nativeComputeLagna(julianDayUt: Double, latitudeDeg: Double, longitudeDeg: Double): DoubleArray?
    @JvmStatic private external fun nativeComputeSunriseSunset(julianDayUt: Double, latitudeDeg: Double, longitudeDeg: Double, elevationMeters: Double): DoubleArray?
    @JvmStatic private external fun nativeGetAyanamsha(julianDayUt: Double): Double
}
