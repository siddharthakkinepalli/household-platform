package com.jugaad.core.ephemeris

import android.content.Context
import com.jugaad.core.ephemeris.dto.HouseData
import com.jugaad.core.ephemeris.dto.PlanetPosition
import com.jugaad.core.ephemeris.dto.SunriseSunset
import com.jugaad.core.ephemeris.jni.SwephJni
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.sqrt

private const val NAKSHATRA_COUNT      = 27
private const val DEGREES_PER_SIGN     = 30.0
private const val DEGREES_PER_NAKSHATRA = 360.0 / NAKSHATRA_COUNT   // 13.333...°
private const val DEGREES_PER_PADA     = DEGREES_PER_NAKSHATRA / 4.0 // 3.333...°

/**
 * High-level Kotlin interface to the Swiss Ephemeris JNI bridge.
 *
 * Lifecycle contract:
 *   1. [initialize] — call once at application start (or on first chart request)
 *   2. [computePlanetPositions], [computeLagna], [computeSunriseSunset] — call as needed
 *   3. [release] — call when the engine is no longer needed (typically never in production,
 *                  but required in tests and on low-memory teardown)
 *
 * Thread safety: all public methods dispatch to [EphemerisDispatcher] internally.
 * Callers do NOT need to wrap calls in withContext — this class handles it.
 *
 * The Swiss Ephemeris data files (seas_18.se1, semo_18.se1, sepl_18.se1) must be
 * copied from assets to internal storage on first run. Pass the target directory
 * to [initialize].
 */
@Singleton
class EphemerisEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile private var initialized = false

    /**
     * Initialises the engine. Copies ephemeris data files from assets if not
     * already present, then calls into the native layer.
     *
     * Must be called before any compute method. Safe to call multiple times —
     * subsequent calls are no-ops if already initialised.
     */
    suspend fun initialize() = withContext(EphemerisDispatcher.dispatcher) {
        if (initialized) return@withContext

        val epheDir = File(context.filesDir, "ephe").also { it.mkdirs() }
        copyEpheAssetsIfNeeded(epheDir)

        SwephJni.initialize(epheDir.absolutePath)
        initialized = true
    }

    /**
     * Releases native resources. After calling this, [initialize] must be
     * called again before any compute operation.
     */
    suspend fun release() = withContext(EphemerisDispatcher.dispatcher) {
        if (!initialized) return@withContext
        SwephJni.release()
        initialized = false
    }

    /**
     * Computes sidereal positions for all 9 Vedic planets (Sun → Ketu) at [julianDayUt].
     *
     * @param julianDayUt Julian Day in Universal Time
     * @return List of [PlanetPosition] in planetId order (0–8). Throws if engine not initialised.
     */
    suspend fun computePlanetPositions(julianDayUt: Double): List<PlanetPosition> =
        withContext(EphemerisDispatcher.dispatcher) {
            checkInitialized()
            (0..8).mapNotNull { planetId ->
                val raw = SwephJni.computePlanet(julianDayUt, planetId)
                    ?: error("Planet $planetId computation returned null at JD=$julianDayUt")
                raw.toPlanetPosition(planetId, julianDayUt)
            }
        }

    /**
     * Computes the Lagna (Ascendant) and Whole Sign house assignments for all planets.
     *
     * @param julianDayUt  Julian Day in Universal Time
     * @param latitudeDeg  Birth latitude in decimal degrees (+N / -S)
     * @param longitudeDeg Birth longitude in decimal degrees (+E / -W)
     * @param planets      Pre-computed planet positions for this chart (for house mapping)
     */
    suspend fun computeLagna(
        julianDayUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        planets: List<PlanetPosition>
    ): HouseData = withContext(EphemerisDispatcher.dispatcher) {
        checkInitialized()

        val raw = SwephJni.computeLagna(julianDayUt, latitudeDeg, longitudeDeg)
            ?: error("Lagna computation returned null at JD=$julianDayUt")

        val lagnaLon    = raw[0]
        val lagnaSignId = floor(lagnaLon / DEGREES_PER_SIGN).toInt()
        val lagnaInSign = lagnaLon % DEGREES_PER_SIGN
        val lagnaNak    = floor(lagnaLon / DEGREES_PER_NAKSHATRA).toInt()
        val lagnaPada   = (floor((lagnaLon % DEGREES_PER_NAKSHATRA) / DEGREES_PER_PADA) + 1).toInt()

        val planetHouses = planets.associate { planet ->
            val house = ((planet.signId - lagnaSignId + 12) % 12) + 1
            planet.planetId to house
        }

        HouseData(
            lagnaLongitudeDeg = lagnaLon,
            lagnaSignId       = lagnaSignId,
            lagnaInSign       = lagnaInSign,
            lagnaNakshatraId  = lagnaNak,
            lagnaPada         = lagnaPada,
            planetHouses      = planetHouses,
            julianDayUt       = julianDayUt
        )
    }

    /**
     * Computes topographic sunrise and sunset with atmospheric refraction for elevation.
     *
     * Applied formula: h = -0.8333° - (0.0347° × √elevationMeters)
     * Accuracy: < 30 seconds of arc variance against reference tables.
     *
     * @param julianDayUt      Reference Julian Day (search begins here)
     * @param latitudeDeg      Observer latitude in decimal degrees
     * @param longitudeDeg     Observer longitude in decimal degrees
     * @param elevationMeters  Observer elevation above sea level in metres
     */
    suspend fun computeSunriseSunset(
        julianDayUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        elevationMeters: Double
    ): SunriseSunset = withContext(EphemerisDispatcher.dispatcher) {
        checkInitialized()

        val raw = SwephJni.computeSunriseSunset(
            julianDayUt, latitudeDeg, longitudeDeg, elevationMeters
        ) ?: error("Sunrise computation returned null at JD=$julianDayUt")

        SunriseSunset(
            sunriseJd       = raw[0],
            sunsetJd        = raw[1],
            observerLatDeg  = latitudeDeg,
            observerLonDeg  = longitudeDeg,
            elevationMeters = elevationMeters
        )
    }

    /**
     * Returns the Lahiri ayanamsha value for verification against reference tables.
     */
    suspend fun getAyanamsha(julianDayUt: Double): Double =
        withContext(EphemerisDispatcher.dispatcher) {
            checkInitialized()
            SwephJni.getAyanamsha(julianDayUt)
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun checkInitialized() {
        check(initialized) {
            "EphemerisEngine.initialize() must be called before any compute method"
        }
    }

    private fun copyEpheAssetsIfNeeded(destDir: File) {
        val assetManager = context.assets
        val epheAssets   = listOf("seas_18.se1", "semo_18.se1", "sepl_18.se1")
        for (assetName in epheAssets) {
            val destFile = File(destDir, assetName)
            if (!destFile.exists()) {
                runCatching {
                    assetManager.open("ephe/$assetName").use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }.onFailure {
                    android.util.Log.w("EphemerisEngine", "Could not copy $assetName - expected in stub mode")
                }
            }
        }
    }

    /** Converts the raw JNI double[4] result into a typed [PlanetPosition]. */
    private fun DoubleArray.toPlanetPosition(planetId: Int, julianDayUt: Double): PlanetPosition {
        val lon       = this[0]                              // sidereal longitude [0, 360)
        val lat       = this[1]                              // ecliptic latitude
        val speed     = this[2]                              // deg/day
        val retro     = this[3] > 0.5                        // 1.0 = retrograde

        val signId    = floor(lon / DEGREES_PER_SIGN).toInt()
        val degInSign = lon % DEGREES_PER_SIGN
        val nakId     = floor(lon / DEGREES_PER_NAKSHATRA).toInt()
        val pada      = (floor((lon % DEGREES_PER_NAKSHATRA) / DEGREES_PER_PADA) + 1).toInt()

        return PlanetPosition(
            planetId       = planetId,
            longitudeDeg   = lon,
            latitudeDeg    = lat,
            speedDegPerDay = speed,
            retrograde     = retro,
            signId         = signId,
            degreeInSign   = degInSign,
            nakshatraId    = nakId,
            pada           = pada,
            julianDayUt    = julianDayUt
        )
    }
}
