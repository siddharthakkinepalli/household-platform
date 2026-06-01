package com.jugaad.core.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages a device-bound AES-256-GCM master key in the Android Keystore.
 *
 * The key is non-exportable by design. It is used exclusively to wrap/unwrap the
 * SQLCipher database passphrase. All key material remains within the Keystore boundary.
 */
internal object KeystoreManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS  = "jugaad_astro_master_key_v1"
    private const val KEY_ALGORITHM     = KeyProperties.KEY_ALGORITHM_AES
    private const val KEY_SIZE_BITS     = 256
    private const val BLOCK_MODE        = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING           = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val GCM_TAG_LENGTH    = 128
    private const val CIPHER_TRANSFORM  = "AES/GCM/NoPadding"

    /**
     * Returns the master key, generating it on first call. Thread-safe via Keystore lock.
     */
    @Synchronized
    fun getMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }

        keyStore.getKey(MASTER_KEY_ALIAS, null)?.let { return it as SecretKey }

        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)   // allow background DB access
            .build()

        return KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER).run {
            init(spec)
            generateKey()
        }
    }

    /**
     * Encrypts [plaintext] using the master key.
     * Returns a [WrappedSecret] containing the GCM IV and ciphertext.
     */
    fun wrap(plaintext: ByteArray): WrappedSecret {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM).also {
            it.init(Cipher.ENCRYPT_MODE, getMasterKey())
        }
        val ciphertext = cipher.doFinal(plaintext)
        return WrappedSecret(iv = cipher.iv, ciphertext = ciphertext)
    }

    /**
     * Decrypts a [WrappedSecret] using the master key.
     * Returns the plaintext bytes. Caller is responsible for zeroing the result after use.
     */
    fun unwrap(wrapped: WrappedSecret): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM).also {
            it.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(GCM_TAG_LENGTH, wrapped.iv))
        }
        return cipher.doFinal(wrapped.ciphertext)
    }
}

data class WrappedSecret(val iv: ByteArray, val ciphertext: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is WrappedSecret && iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
}
