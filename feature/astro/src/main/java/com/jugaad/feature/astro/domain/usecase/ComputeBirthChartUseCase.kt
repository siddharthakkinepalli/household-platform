package com.jugaad.feature.astro.domain.usecase

import com.jugaad.core.ephemeris.EphemerisEngine
import com.jugaad.core.security.keystore.AstroKeyProvider
import com.jugaad.core.security.log.AstroLogger
import com.jugaad.core.time.JulianDayConverter
import com.jugaad.feature.astro.domain.model.BirthChart
import com.jugaad.feature.astro.domain.repository.AstroRepository
import com.jugaad.feature.astro.processor.AstroDataProcessor
import org.json.JSONObject
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes a complete natal [BirthChart] for a stored user profile.
 *
 * Security contract:
 *  - Birth payload bytes are decrypted, parsed, and immediately zeroed — they never
 *    linger in heap beyond the scope of [execute].
 *  - Lat/lon/DOB values are never passed to any log sink; only semantic event codes are logged.
 *  - Computed plaintext fields (rashi, ascendant, nakshatra) are NOT PII and are safe to store.
 */
@Singleton
class ComputeBirthChartUseCase @Inject constructor(
    private val repository: AstroRepository,
    private val engine: EphemerisEngine,
    private val processor: AstroDataProcessor,
    private val keyProvider: AstroKeyProvider,
    private val logger: AstroLogger
) {

    suspend fun execute(profileId: Long): BirthChart {
        val profile = repository.getUserProfile(profileId)
            ?: error("UserProfile $profileId not found in AstroDatabase")

        // Decrypt birth payload — zero-fill immediately after parsing to avoid heap lingering
        val plainBytes = keyProvider.decryptBirthPayload(
            ciphertext = profile.encryptedBirthPayload,
            iv = profile.birthPayloadIv
        )
        val coords = parseBirthPayload(plainBytes)
        plainBytes.fill(0)

        logger.log(AstroLogger.Level.DEBUG, TAG, "BIRTH_CHART_COMPUTE_START",
            mapOf("profileId" to profileId.toString()))

        engine.initialize()

        val planets  = engine.computePlanetPositions(coords.julianDayUt)
        val houseData = engine.computeLagna(coords.julianDayUt, coords.latDeg, coords.lonDeg, planets)
        val ayanamsha = engine.getAyanamsha(coords.julianDayUt)

        val wars         = processor.detectGrahaYuddha(planets)
        val shadbala     = processor.computeShadbala(planets, wars)
        val contextPayload = processor.buildContextPayload(planets, houseData, shadbala, coords.julianDayUt)

        // Update plaintext computed fields in DB (these are astronomical output — not PII)
        val moon = planets.first { it.planetId == MOON_ID }
        repository.updateUserProfileComputedFields(
            profileId  = profileId,
            rashi      = SIGN_NAMES[moon.signId],
            ascendant  = SIGN_NAMES[houseData.lagnaSignId],
            nakshatra  = NAKSHATRA_NAMES[moon.nakshatraId]
        )

        logger.log(AstroLogger.Level.INFO, TAG, "BIRTH_CHART_COMPUTED",
            mapOf("wars" to wars.size.toString(), "planets" to planets.size.toString()))

        return BirthChart(
            profileId              = profileId,
            planets                = planets,
            houseData              = houseData,
            ayanamshaDeg           = ayanamsha,
            julianDayUt            = coords.julianDayUt,
            shadbalaSummary        = shadbala,
            natalGrahaYuddhaList   = wars,
            contextPayload         = contextPayload
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private data class BirthCoords(val julianDayUt: Double, val latDeg: Double, val lonDeg: Double)

    /**
     * Parses the decrypted birth payload JSON.
     * Expected format: {"lat": Double, "lon": Double, "dob": "ISO-8601 ZonedDateTime string"}
     * Example: {"lat": 48.1351, "lon": 11.5820, "dob": "1983-01-01T06:30:00+05:30"}
     */
    private fun parseBirthPayload(plainBytes: ByteArray): BirthCoords {
        val json = JSONObject(String(plainBytes, Charsets.UTF_8))
        val lat  = json.getDouble("lat")
        val lon  = json.getDouble("lon")
        val dob  = ZonedDateTime.parse(json.getString("dob"))
        return BirthCoords(
            julianDayUt = JulianDayConverter.toJulianDay(dob),
            latDeg      = lat,
            lonDeg      = lon
        )
    }

    companion object {
        private const val TAG     = "AstroBirthChart"
        private const val MOON_ID = 1

        private val SIGN_NAMES = arrayOf(
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
        )

        private val NAKSHATRA_NAMES = arrayOf(
            "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
            "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni",
            "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
            "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishtha", "Shatabhisha",
            "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"
        )
    }
}
