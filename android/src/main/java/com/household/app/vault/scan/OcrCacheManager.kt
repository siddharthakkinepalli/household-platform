package com.household.app.vault.scan

import android.graphics.Bitmap
import android.graphics.Color
import com.household.app.data.dao.OcrCacheDao
import com.household.app.data.entities.OcrCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages OCR result caching keyed by a perceptual page hash.
 * Uses average-hash (aHash): resize to 8x8, grayscale, compare to mean.
 * Two renders of the same page produce the same hash even if JPEG2000 vs PNG.
 */
class OcrCacheManager(private val dao: OcrCacheDao) {

    companion object {
        const val ML_KIT_ENGINE_VERSION = 1
        private const val HASH_SIZE = 8  // 8x8 = 64-bit hash
    }

    /**
     * Computes the aHash of a Bitmap as a hex string.
     * Resize to 8x8 greyscale, compute mean, bits = pixel > mean.
     */
    fun computeHash(bitmap: Bitmap): String {
        // Resize to HASH_SIZE x HASH_SIZE
        val small = Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, true)
        val pixels = IntArray(HASH_SIZE * HASH_SIZE)
        small.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)
        if (small != bitmap) small.recycle()

        // Convert to grayscale luminance values
        val grey = pixels.map { px ->
            val r = Color.red(px); val g = Color.green(px); val b = Color.blue(px)
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Compute mean
        val mean = grey.average()

        // Build 64-bit fingerprint: 1 if pixel >= mean, else 0
        var hashLow = 0L
        var hashHigh = 0L
        for (i in 0 until 32) {
            if (grey[i] >= mean) hashLow = hashLow or (1L shl i)
        }
        for (i in 32 until 64) {
            if (grey[i] >= mean) hashHigh = hashHigh or (1L shl (i - 32))
        }

        return "%016x%016x".format(hashLow, hashHigh)
    }

    /** Returns cached OCR text if available for this hash + engine version, else null. */
    suspend fun getCached(hash: String, engineVersion: Int = ML_KIT_ENGINE_VERSION): String? =
        withContext(Dispatchers.IO) {
            dao.get(hash, engineVersion)?.ocrText
        }

    /** Stores OCR result in the cache. */
    suspend fun store(hash: String, ocrText: String, confidence: Float = 1.0f, engineVersion: Int = ML_KIT_ENGINE_VERSION) =
        withContext(Dispatchers.IO) {
            dao.put(OcrCacheEntity(
                pageHash      = hash,
                engineVersion = engineVersion,
                ocrText       = ocrText,
                confidence    = confidence
            ))
        }
}
