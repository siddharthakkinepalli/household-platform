package com.jugaad.feature.astro.domain.usecase

import com.jugaad.core.ephemeris.EphemerisEngine
import com.jugaad.core.security.log.AstroLogger
import com.jugaad.feature.astro.domain.model.DailyTransit
import com.jugaad.feature.astro.domain.repository.AstroRepository
import com.jugaad.feature.astro.processor.AstroDataProcessor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns today's planetary transit snapshot as a [DailyTransit].
 *
 * Cache strategy (cache-first):
 *  1. Check Room transit cache for [julianDayUt].
 *  2. If a non-expired cache entry exists → reconstruct from cache (avoids JNI overhead).
 *  3. Otherwise → call EphemerisEngine JNI, compute all 9 planet positions,
 *     write the result back to the transit cache, and return it.
 *
 * Lagna is NOT computed here — it requires observer location which is user-specific.
 * Use [ComputeBirthChartUseCase] for natal lagna, or call [EphemerisEngine.computeLagna]
 * directly when the observer location is available.
 */
@Singleton
class GetDailyTransitUseCase @Inject constructor(
    private val repository: AstroRepository,
    private val engine: EphemerisEngine,
    private val processor: AstroDataProcessor,
    private val logger: AstroLogger
) {

    suspend fun execute(julianDayUt: Double): DailyTransit {
        val now = System.currentTimeMillis()

        if (repository.isTransitCacheValid(julianDayUt, now)) {
            logger.log(AstroLogger.Level.DEBUG, TAG, "TRANSIT_CACHE_HIT")

            val entities = repository.getTransitCache(julianDayUt)
            val planets  = processor.reconstructFromCache(entities)
            val wars     = processor.detectGrahaYuddha(planets)
            val shadbala = processor.computeShadbala(planets, wars)
            val context  = processor.buildContextPayloadNoLagna(
                planets          = planets,
                shadbalaSummary  = shadbala,
                julianDayUt      = julianDayUt,
                fromCache        = true
            )

            return DailyTransit(
                julianDayUt      = julianDayUt,
                planets          = planets,
                grahaYuddhaList  = wars,
                shadbalaSummary  = shadbala,
                contextPayload   = context,
                fromCache        = true
            )
        }

        logger.log(AstroLogger.Level.DEBUG, TAG, "TRANSIT_LIVE_COMPUTE")

        engine.initialize()
        val planets  = engine.computePlanetPositions(julianDayUt)
        val wars     = processor.detectGrahaYuddha(planets)
        val shadbala = processor.computeShadbala(planets, wars)
        val context  = processor.buildContextPayloadNoLagna(
            planets         = planets,
            shadbalaSummary = shadbala,
            julianDayUt     = julianDayUt,
            fromCache       = false
        )

        // Write back to cache — 24-hour TTL set inside toCacheEntities()
        val cacheEntities = processor.toCacheEntities(planets, now)
        repository.upsertTransitCache(cacheEntities)

        logger.log(AstroLogger.Level.INFO, TAG, "TRANSIT_CACHED",
            mapOf("planets" to planets.size.toString()))

        return DailyTransit(
            julianDayUt     = julianDayUt,
            planets         = planets,
            grahaYuddhaList = wars,
            shadbalaSummary = shadbala,
            contextPayload  = context,
            fromCache       = false
        )
    }

    companion object {
        private const val TAG = "AstroTransit"
    }
}
