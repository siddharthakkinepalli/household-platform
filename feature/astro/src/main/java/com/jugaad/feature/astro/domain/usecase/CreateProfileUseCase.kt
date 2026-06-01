package com.jugaad.feature.astro.domain.usecase

import com.jugaad.core.security.SecurityUtils
import com.jugaad.core.security.keystore.AstroKeyProvider
import com.jugaad.core.security.log.AstroLogger
import com.jugaad.feature.astro.data.db.entity.UserProfileEntity
import com.jugaad.feature.astro.domain.repository.AstroRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileInput(
    val name: String,
    val dob: String,  // YYYY-MM-DD
    val tob: String,  // HH:mm
    val city: String,
    val lat: Double,
    val lon: Double
)

/**
 * Creates a new [UserProfileEntity] with encrypted birth details.
 */
@Singleton
class CreateProfileUseCase @Inject constructor(
    private val repository: AstroRepository,
    private val keyProvider: AstroKeyProvider,
    private val logger: AstroLogger
) {

    suspend fun execute(input: ProfileInput): Long {
        val payload = buildJsonObject {
            put("dob", input.dob)
            put("tob", input.tob)
            put("city", input.city)
            put("lat", input.lat)
            put("lon", input.lon)
        }.toString().toByteArray()

        val wrapped = keyProvider.encryptBirthPayload(payload)
        val nameHash = SecurityUtils.sha256(input.name)

        val entity = UserProfileEntity(
            nameHash               = nameHash,
            encryptedBirthPayload  = wrapped.ciphertext,
            birthPayloadIv         = wrapped.iv,
            vedaRashi              = "", // Computed later
            vedaAscendant          = "", // Computed later
            vedaNakshatra          = ""  // Computed later
        )

        val id = repository.saveUserProfile(entity)
        logger.log(AstroLogger.Level.INFO, "AstroProfile", "PROFILE_CREATED", mapOf("id" to id.toString()))

        return id
    }
}
