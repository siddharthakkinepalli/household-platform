package com.jugaad.feature.astro.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the user's Vedic astrological profile.
 *
 * SECURITY CONTRACT:
 * - [encryptedBirthPayload] holds AES-GCM ciphertext of a JSON object containing
 *   latitude, longitude, and date-of-birth. Plaintext MUST NOT exist outside the
 *   encryption boundary in com.jugaad.core.security.
 * - [birthPayloadIv] is the GCM initialization vector for [encryptedBirthPayload].
 * - [nameHash] is a one-way SHA-256 hash of the display name — used for lookup only,
 *   never for reconstruction of the original name.
 * - Computed astrological outputs (rashi, ascendant, nakshatra) are NOT PII and
 *   may be stored in plaintext.
 */
@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["name_hash"], unique = true)]
)
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** SHA-256(displayName) — non-reversible identifier. */
    @ColumnInfo(name = "name_hash")
    val nameHash: String,

    /** AES-GCM ciphertext of { "lat": Double, "lon": Double, "dob": "YYYY-MM-DD" }. */
    @ColumnInfo(name = "encrypted_birth_payload", typeAffinity = ColumnInfo.BLOB)
    val encryptedBirthPayload: ByteArray,

    /** GCM IV (12 bytes) for [encryptedBirthPayload]. */
    @ColumnInfo(name = "birth_payload_iv", typeAffinity = ColumnInfo.BLOB)
    val birthPayloadIv: ByteArray,

    /** Vedic Moon sign (Rashi) — plaintext, not PII. */
    @ColumnInfo(name = "veda_rashi")
    val vedaRashi: String,

    /** Vedic Rising sign (Lagna/Ascendant) — plaintext, not PII. */
    @ColumnInfo(name = "veda_ascendant")
    val vedaAscendant: String,

    /** Vedic Birth star (Nakshatra) — plaintext, not PII. */
    @ColumnInfo(name = "veda_nakshatra")
    val vedaNakshatra: String,

    /** Vedic Ayanamsha used for calculations (e.g. "LAHIRI", "RAMAN"). */
    @ColumnInfo(name = "ayanamsha")
    val ayanamsha: String = "LAHIRI",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserProfileEntity) return false
        return id == other.id &&
            nameHash == other.nameHash &&
            encryptedBirthPayload.contentEquals(other.encryptedBirthPayload) &&
            birthPayloadIv.contentEquals(other.birthPayloadIv) &&
            vedaRashi == other.vedaRashi &&
            vedaAscendant == other.vedaAscendant &&
            vedaNakshatra == other.vedaNakshatra &&
            ayanamsha == other.ayanamsha
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nameHash.hashCode()
        result = 31 * result + encryptedBirthPayload.contentHashCode()
        result = 31 * result + birthPayloadIv.contentHashCode()
        return result
    }
}
