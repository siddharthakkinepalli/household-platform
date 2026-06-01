package com.jugaad.core.security.keystore

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE     = "jugaad_astro_key_store"
private const val KEY_IV         = "wrapped_iv"
private const val KEY_CIPHERTEXT = "wrapped_ct"
private const val PASSPHRASE_LEN = 32  // 256-bit SQLCipher passphrase

/**
 * Provides the SQLCipher database passphrase by wrapping/unwrapping it through
 * the Android Keystore. The passphrase bytes are derived once, encrypted with the
 * Keystore master key, and stored as ciphertext in private SharedPreferences.
 *
 * CRITICAL: Callers MUST zero-fill the returned [ByteArray] immediately after
 * passing it to the SQLCipher SupportFactory to prevent passphrase lingering in heap.
 */
@Singleton
class AstroKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    /**
     * Returns the database passphrase as raw bytes.
     * Call this ONCE per database open, then immediately zero the result.
     */
    @Synchronized
    fun acquirePassphrase(): ByteArray {
        val storedIv = prefs.getString(KEY_IV, null)
        val storedCt = prefs.getString(KEY_CIPHERTEXT, null)

        return if (storedIv != null && storedCt != null) {
            val wrapped = WrappedSecret(
                iv         = Base64.decode(storedIv, Base64.NO_WRAP),
                ciphertext = Base64.decode(storedCt, Base64.NO_WRAP)
            )
            KeystoreManager.unwrap(wrapped)
        } else {
            generateAndStorePassphrase()
        }
    }

    /**
     * Decrypts an AES-GCM ciphertext encrypted with the Android Keystore master key.
     *
     * Used by [ComputeBirthChartUseCase] to decrypt the birth payload stored in
     * [UserProfileEntity.encryptedBirthPayload]. Caller MUST zero-fill the returned
     * ByteArray immediately after use.
     *
     * @param ciphertext The AES-GCM ciphertext (matches [UserProfileEntity.encryptedBirthPayload]).
     * @param iv         The 12-byte GCM initialization vector (matches [UserProfileEntity.birthPayloadIv]).
     */
    fun decryptBirthPayload(ciphertext: ByteArray, iv: ByteArray): ByteArray =
        KeystoreManager.unwrap(WrappedSecret(iv = iv, ciphertext = ciphertext))

    /**
     * Encrypts a birth payload using the Android Keystore master key.
     *
     * @param plaintext The raw bytes of the birth payload JSON.
     * @return [WrappedSecret] with the IV and ciphertext.
     */
    fun encryptBirthPayload(plaintext: ByteArray): WrappedSecret =
        KeystoreManager.wrap(plaintext)

    private fun generateAndStorePassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_LEN).also { SecureRandom().nextBytes(it) }
        val wrapped = KeystoreManager.wrap(passphrase)
        prefs.edit()
            .putString(KEY_IV,         Base64.encodeToString(wrapped.iv,         Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(wrapped.ciphertext, Base64.NO_WRAP))
            .apply()
        return passphrase
    }
}
