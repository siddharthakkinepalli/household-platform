package com.household.app.data.service

import android.content.Context
import com.household.app.data.AppDatabase
import com.household.app.data.dao.ProductDao
import com.household.app.data.dao.ProductAliasDao
import com.household.app.data.dao.ReceiptLineDao
import com.household.app.data.dao.InventoryEventDao
import com.household.app.data.entities.ProductAliasEntity
import com.household.app.data.entities.ReceiptLineEntity
import com.household.app.data.entities.InventoryEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory-First Receipt Resolution Service
 *
 * Integrates into the Vault scan flow:
 * 1. Capture image → ML Kit OCR → raw text
 * 2. Pass raw lines to resolveReceiptLines()
 * 3. Service handles EXACT → FUZZY → COMMIT
 * 4. Returns parsed items ready for pantry
 */
class ReceiptResolutionService(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val productDao: ProductDao = db.productDao()
    private val productAliasDao: ProductAliasDao = db.productAliasDao()
    private val receiptLineDao: ReceiptLineDao = db.receiptLineDao()
    private val inventoryEventDao: InventoryEventDao = db.inventoryEventDao()

    companion object {
        private const val FUZZY_THRESHOLD = 0.7f
        private val PRICE_ANCHOR_REGEX = Regex("""(\d+[,.]\d{2})\s*[AB*]?\s*$""", RegexOption.IGNORE_CASE)
    }

    /**
     * Resolution Result - ready to add to pantry
     */
    data class ResolvedItem(
        val name: String,
        val confidence: Float,
        val resolutionMethod: String,
        val rawText: String
    )

    /**
     * Process OCR lines through the resolution pipeline.
     *
     * @param ocrLines Raw text lines from ML Kit
     * @param scanId Reference to the vault scan
     * @param storeName e.g., "ALDI", "REWE" (context matters!)
     * @return List of resolved items ready for pantry
     */
    suspend fun resolveReceiptLines(
        ocrLines: List<String>,
        scanId: Long,
        storeName: String
    ): List<ResolvedItem> = withContext(Dispatchers.IO) {

        val results = mutableListOf<ResolvedItem>()

        for (line in ocrLines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Step 1: Exact Alias Match
            val exactMatch = productAliasDao.findExactMatch(trimmed, storeName)
            if (exactMatch != null) {
                val product = productDao.getById(exactMatch.productId)
                if (product != null) {
                    productAliasDao.incrementFrequency(trimmed, storeName, System.currentTimeMillis())
                    saveReceiptLine(scanId, trimmed, product.id, 1.0f, "EXACT")

                    results.add(ResolvedItem(
                        name = product.canonicalName,
                        confidence = 1.0f,
                        resolutionMethod = "EXACT",
                        rawText = trimmed
                    ))
                    continue
                }
            }

            // Step 2: Fuzzy Match (Levenshtein)
            val productName = extractProductName(trimmed)
            if (productName.isNotEmpty()) {
                val allProducts = productDao.getAll()
                val bestMatch = findBestMatch(productName, allProducts.map { it.canonicalName })

                if (bestMatch != null) {
                    val matchedProduct = allProducts.first { it.canonicalName == bestMatch.first }

                    // Learn: Create new alias from fuzzy match
                    productAliasDao.insert(ProductAliasEntity(
                        rawOcrString = trimmed,
                        productId = matchedProduct.id,
                        storeName = storeName,
                        frequency = 1
                    ))

                    saveReceiptLine(scanId, trimmed, matchedProduct.id, bestMatch.second, "FUZZY")

                    results.add(ResolvedItem(
                        name = matchedProduct.canonicalName,
                        confidence = bestMatch.second,
                        resolutionMethod = "FUZZY",
                        rawText = trimmed
                    ))
                    continue
                }
            }

            // Step 3: Failed - save as evidence
            saveReceiptLine(scanId, trimmed, null, 0f, "FAILED")
        }

        results
    }

    /**
     * Save receipt line to observation layer
     */
    private suspend fun saveReceiptLine(
        scanId: Long,
        rawText: String,
        matchedProductId: Long?,
        confidence: Float,
        method: String
    ) {
        val price = extractPrice(rawText)
        receiptLineDao.insert(ReceiptLineEntity(
            scanId = scanId,
            rawText = rawText,
            parsedPrice = price,
            matchedProductId = matchedProductId,
            confidence = confidence,
            resolutionMethod = method
        ))
    }

    /**
     * Emit inventory event after pantry item is created
     */
    suspend fun emitInventoryEvent(pantryItemId: Long, delta: Float, sourceReceiptLineId: Long?) {
        inventoryEventDao.insert(InventoryEventEntity(
            pantryItemId = pantryItemId,
            delta = delta,
            eventType = "ADD",
            sourceReceiptLineId = sourceReceiptLineId
        ))
    }

    /**
     * Extract price using "Right-to-Left" anchor
     */
    private fun extractPrice(text: String): Double? {
        val match = PRICE_ANCHOR_REGEX.find(text)
        return match?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
    }

    /**
     * Extract product name by removing price anchor
     */
    private fun extractProductName(text: String): String {
        val withoutPrice = PRICE_ANCHOR_REGEX.replaceFirst(text, "").trim()
        // Remove product codes (6 digits)
        return Regex("""^\d{6}\s+""", RegexOption.IGNORE_CASE).replace(withoutPrice, "").trim()
    }

    /**
     * Levenshtein-based fuzzy matching
     */
    private fun findBestMatch(query: String, candidates: List<String>): Pair<String, Float>? {
        if (candidates.isEmpty()) return null

        var bestCandidate: String? = null
        var bestScore = 0f

        for (candidate in candidates) {
            val score = similarity(query.lowercase(), candidate.lowercase())
            if (score > bestScore && score >= FUZZY_THRESHOLD) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        return bestCandidate?.let { Pair(it, bestScore) }
    }

    /**
     * Levenshtein similarity (0.0 to 1.0)
     */
    private fun similarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val maxLen = maxOf(s1.length, s2.length)
        val dist = levenshtein(s1, s2)
        return 1.0f - (dist.toFloat() / maxLen)
    }

    /**
     * Pure Kotlin Levenshtein distance
     */
    private fun levenshtein(s1: String, s2: String): Int {
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val len1 = s1.length
        val len2 = s2.length

        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i-1] == s2[j-1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j-1] + 1, prev[j-1] + cost)
            }
            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[len2]
    }

    /**
     * Get learning statistics
     */
    suspend fun getLearnedPatterns(): List<ProductAliasEntity> {
        return productAliasDao.getLearnedAliases()
    }

    /**
     * Get unresolved lines for debugging
     */
    suspend fun getUnresolvedLines(): List<ReceiptLineEntity> {
        return receiptLineDao.getLowConfidenceLines()
    }
}