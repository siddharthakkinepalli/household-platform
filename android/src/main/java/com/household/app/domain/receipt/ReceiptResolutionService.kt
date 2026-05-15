package com.household.app.domain.receipt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory-First Receipt Ingestion Pipeline
 *
 * Implements the "Probabilistic Household Ledger" architecture:
 *
 * 1. OCR as Sensor: Raw text is evidence, not final truth.
 * 2. Canonical Identity: Many-to-one mapping (Aliases -> Product).
 * 3. Observation Layer: Every scan logs ReceiptLine for audit.
 * 4. State Projection: InventoryEvents update pantry stock.
 *
 * The Resolution Engine (3 Steps):
 * - Step 1: Exact Alias Match (rawOcrString + storeName)
 * - Step 2: Fuzzy Fallback (Levenshtein distance)
 * - Step 3: Commit (Transaction - saves ReceiptLine, updates Alias frequency, emits InventoryEvent)
 */
class ReceiptResolutionService(
    private val productDao: ProductDao,
    private val productAliasDao: ProductAliasDao,
    private val receiptLineDao: ReceiptLineDao,
    private val inventoryEventDao: InventoryEventDao
) {
    companion object {
        private const val FUZZY_THRESHOLD = 0.7f      // 70% similarity for fuzzy match
        private const val PRICE_ANCHOR_REGEX = """(\d+[,.]\d{2})\s*[AB*]?\s*$"""
    }

    /**
     * Resolution Result - outcome of processing a single receipt line
     */
    data class ResolutionResult(
        val receiptLine: ReceiptLine,
        val product: Product?,
        val inventoryEvent: InventoryEvent?
    )

    /**
     * Process a single OCR line through the resolution pipeline.
     * This is the core "Memory-First" logic.
     */
    suspend fun resolveLine(
        rawText: String,
        scanId: Long,
        storeName: String,
        defaultQuantity: Float = 1f
    ): ResolutionResult = withContext(Dispatchers.IO) {

        // Step 0: Extract price from line (Right-to-Left anchor)
        val parsedPrice = extractPrice(rawText)

        // Step 1: Exact Alias Match
        val exactMatch = productAliasDao.findExactMatch(rawText, storeName)
        if (exactMatch != null) {
            val product = productDao.getById(exactMatch.productId)
            if (product != null) {
                // Update alias frequency (learning!)
                productAliasDao.incrementFrequency(rawText, storeName, System.currentTimeMillis())

                // Create ReceiptLine with EXACT resolution
                val receiptLine = ReceiptLine(
                    scanId = scanId,
                    rawText = rawText,
                    parsedPrice = parsedPrice,
                    matchedProductId = product.id,
                    confidence = 1.0f,
                    resolutionMethod = ResolutionMethod.EXACT
                )
                val lineId = receiptLineDao.insert(receiptLine)

                // Emit InventoryEvent (pantryItemId set by caller after pantry creation)
                val event = InventoryEvent(
                    pantryItemId = -1L,
                    delta = defaultQuantity,
                    eventType = EventType.ADD,
                    sourceReceiptLineId = lineId
                )

                return@withContext ResolutionResult(receiptLine.copy(id = lineId), product, event)
            }
        }

        // Step 2: Fuzzy Fallback - search Product table with Levenshtein
        val productName = extractProductName(rawText)
        if (productName.isNotEmpty()) {
            val allProducts = productDao.getAll()
            val productNames = allProducts.map { it.canonicalName }

            val bestMatch = Levenshtein.findBestMatch(productName, productNames, FUZZY_THRESHOLD)
            if (bestMatch != null) {
                val matchedProduct = allProducts.first { it.canonicalName == bestMatch.first }

                // Create new alias (learns from fuzzy match)
                productAliasDao.insert(
                    ProductAlias(
                        rawOcrString = rawText,
                        productId = matchedProduct.id,
                        storeName = storeName,
                        frequency = 1
                    )
                )

                // Create ReceiptLine with FUZZY resolution
                val receiptLine = ReceiptLine(
                    scanId = scanId,
                    rawText = rawText,
                    parsedPrice = parsedPrice,
                    matchedProductId = matchedProduct.id,
                    confidence = bestMatch.second,
                    resolutionMethod = ResolutionMethod.FUZZY
                )
                val lineId = receiptLineDao.insert(receiptLine)

                val event = InventoryEvent(
                    pantryItemId = -1L,
                    delta = defaultQuantity,
                    eventType = EventType.ADD,
                    sourceReceiptLineId = lineId
                )

                return@withContext ResolutionResult(receiptLine.copy(id = lineId), matchedProduct, event)
            }
        }

        // Step 3: Failed - save as evidence even if unresolved
        val failedLine = ReceiptLine(
            scanId = scanId,
            rawText = rawText,
            parsedPrice = parsedPrice,
            matchedProductId = null,
            confidence = 0f,
            resolutionMethod = ResolutionMethod.FAILED
        )
        val lineId = receiptLineDao.insert(failedLine)

        return@withContext ResolutionResult(failedLine.copy(id = lineId), null, null)
    }

    /**
     * Process multiple lines from a receipt.
     * Uses transaction to ensure atomicity.
     */
    suspend fun processReceipt(
        ocrLines: List<String>,
        scanId: Long,
        storeName: String
    ): List<ResolutionResult> = withContext(Dispatchers.IO) {

        val results = mutableListOf<ResolutionResult>()

        for (line in ocrLines) {
            val result = resolveLine(line.trim(), scanId, storeName)
            results.add(result)
        }

        results
    }

    /**
     * Extract price from line using "Right-to-Left" anchor
     */
    private fun extractPrice(text: String): Double? {
        val regex = Regex(PRICE_ANCHOR_REGEX, RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
    }

    /**
     * Extract product name by removing price anchor from right
     */
    private fun extractProductName(text: String): String {
        val regex = Regex(PRICE_ANCHOR_REGEX, RegexOption.IGNORE_CASE)
        val match = regex.replaceFirst(text, "").trim()
        // Also remove leading product codes (e.g., "230401 ")
        return Regex("""^\d{6}\s+""", RegexOption.IGNORE_CASE).replace(match, "").trim()
    }

    /**
     * Get learning statistics - which aliases are most reliable
     */
    suspend fun getLearnedPatterns(storeName: String): List<ProductAlias> {
        return productAliasDao.getLearnedAliases()
    }

    /**
     * Get audit of unresolved lines
     */
    suspend fun getUnresolvedLines(): List<ReceiptLine> {
        return receiptLineDao.getLowConfidenceLines()
    }
}

/**
 * Database access wrapper (to be provided by Room Database)
 */
class ReceiptDatabase(
    context: Context
) {
    // Placeholder - would be actual Room database
    // In production, inject via Hilt/DI

    val productDao: ProductDao = TODO("Inject via Room")
    val productAliasDao: ProductAliasDao = TODO("Inject via Room")
    val receiptLineDao: ReceiptLineDao = TODO("Inject via Room")
    val inventoryEventDao: InventoryEventDao = TODO("Inject via Room")

    val resolutionService: ReceiptResolutionService by lazy {
        ReceiptResolutionService(productDao, productAliasDao, receiptLineDao, inventoryEventDao)
    }
}