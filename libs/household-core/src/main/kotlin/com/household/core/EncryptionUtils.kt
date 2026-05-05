package com.household.core

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Utility class for AES encryption/decryption using Android Keystore.
 * All encryption is done using AES/GCM for authenticated encryption.
 */
@OptIn(ExperimentalEncodingApi::class)
object EncryptionUtils {
    
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "household_backup_key"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256 // bits
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12 // bytes
    
    /**
     * Initialize or retrieve the encryption key from Android Keystore.
     * On API 23+, uses AndroidKeyStore with hardware backing if available.
     * Creates a new key if one doesn't exist.
     */
    fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        
        val existingKey = keyStore.getKey(KEY_ALIAS, null)
        if (existingKey is SecretKey) {
            return existingKey
        }
        
        // Create new key
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            createKeyWithAndroidKeystore()
        } else {
            createFallbackKey()
        }
    }
    
    /**
     * Encrypts data using AES/GCM.
     * Returns Base64-encoded ciphertext with IV prepended.
     *
     * @param plaintext The data to encrypt
     * @return Base64-encoded encrypted data (IV + ciphertext)
     */
    fun encrypt(plaintext: ByteArray): String {
        try {
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            // Get IV from cipher initialization
            val iv = cipher.iv // GCM IV
            val ciphertext = cipher.doFinal(plaintext)
            
            // Combine IV + ciphertext and encode as Base64
            val combined = iv + ciphertext
            return Base64.encode(combined)
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt data", e)
        }
    }
    
    /**
     * Decrypts Base64-encoded data encrypted with [encrypt].
     *
     * @param encryptedData Base64-encoded encrypted data (IV + ciphertext)
     * @return Decrypted plaintext as ByteArray
     */
    fun decrypt(encryptedData: String): ByteArray {
        try {
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            
            // Decode Base64
            val combined = Base64.decode(encryptedData)
            
            // Extract IV and ciphertext
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val ciphertext = combined.sliceArray(GCM_IV_LENGTH until combined.size)
            
            // Initialize cipher with IV
            val gcmSpec: AlgorithmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw EncryptionException("Failed to decrypt data", e)
        }
    }
    
    /**
     * Encrypts a string to Base64-encoded format.
     */
    fun encryptString(plaintext: String): String {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Decrypts Base64-encoded string back to plaintext.
     */
    fun decryptString(encryptedData: String): String {
        val decrypted = decrypt(encryptedData)
        return String(decrypted, Charsets.UTF_8)
    }
    
    /**
     * Create encryption key using Android Keystore (API 23+).
     * This provides hardware-backed security where available.
     */
    private fun createKeyWithAndroidKeystore(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true) // Use StrongBox if available
                }
            }
            .build()
        
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Fallback key generation for API < 23.
     * Generates a random AES key for symmetric encryption.
     */
    private fun createFallbackKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(KEY_SIZE)
        return keyGenerator.generateKey()
    }
}

/**
 * Exception thrown during encryption/decryption operations.
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
