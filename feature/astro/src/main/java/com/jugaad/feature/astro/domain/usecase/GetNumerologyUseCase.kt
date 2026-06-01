package com.jugaad.feature.astro.domain.usecase

import com.jugaad.core.security.keystore.AstroKeyProvider
import com.jugaad.feature.astro.domain.engine.NumerologyEngine
import com.jugaad.feature.astro.domain.model.NumerologyResult
import com.jugaad.feature.astro.domain.repository.AstroRepository
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decrypts the birth payload and returns [NumerologyResult] for [profileId].
 *
 * Security contract (same as [ComputeBirthChartUseCase]):
 *  - plainBytes are zeroed immediately after DOB is extracted.
 *  - Only day/month/year integers are used — full DOB string is never stored.
 */
@Singleton
class GetNumerologyUseCase @Inject constructor(
    private val repository: AstroRepository,
    private val keyProvider: AstroKeyProvider,
    private val engine: NumerologyEngine
) {
    suspend fun execute(profileId: Long, date: LocalDate = LocalDate.now()): NumerologyResult? {
        val profile = repository.getUserProfile(profileId) ?: return null

        val plainBytes = keyProvider.decryptBirthPayload(
            ciphertext = profile.encryptedBirthPayload,
            iv         = profile.birthPayloadIv
        )
        return try {
            val dob = ZonedDateTime.parse(JSONObject(String(plainBytes, Charsets.UTF_8)).getString("dob"))
            engine.compute(dob.dayOfMonth, dob.monthValue, dob.year, date)
        } finally {
            plainBytes.fill(0)
        }
    }
}
