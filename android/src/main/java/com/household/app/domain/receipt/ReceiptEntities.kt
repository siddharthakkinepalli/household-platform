package com.household.app.domain.receipt

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Product - The canonical product reference.
 * Many aliases (ProductAlias) can map to one Product.
 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalName: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ProductAlias - Maps raw OCR strings to canonical Products.
 * Includes store context to prevent "Bio" bakery confusion.
 * frequency tracks how often this alias has been successfully resolved.
 */
@Entity(
    tableName = "product_aliases",
    primaryKeys = ["rawOcrString", "storeName"],
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
data class ProductAlias(
    val rawOcrString: String,      // e.g., "230401 Milch" from ALDI
    val productId: Long,          // FK to Product
    val storeName: String,        // e.g., "ALDI", "REWE" - context matters!
    val frequency: Int = 1,       // How often successfully resolved
    val lastSeenAt: Long = System.currentTimeMillis()
)

/**
 * ReceiptLine - The "Observation Layer".
 * Every scan logs raw evidence, preserving noise for debugging.
 * This is NOT yet the pantry item - it's the raw scan result.
 */
@Entity(
    tableName = "receipt_lines",
    indices = [Index("matchedProductId"), Index("pantryItemId")]
)
data class ReceiptLine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scanId: Long = 0,              // Reference to the VaultScan
    val rawText: String = "",           // The exact OCR line (preserves noise)
    val parsedPrice: Double? = null,     // Extracted price (if found)
    val matchedProductId: Long? = null,   // Resolved Product ID (null if failed)
    val pantryItemId: Long? = null,      // Reference to pantry item if linked
    val confidence: Float = 0f,        // 0.0-1.0 resolution confidence
    val resolutionMethod: ResolutionMethod = ResolutionMethod.FAILED,  // EXACT, FUZZY, or FAILED
    val timestamp: Long = System.currentTimeMillis()
)

enum class ResolutionMethod {
    EXACT,    // Exact alias match
    FUZZY,    // Levenshtein match
    FAILED    // Could not resolve
}

/**
 * InventoryEvent - State projection from ReceiptLine.
 * Emitted when a ReceiptLine successfully resolves to a pantry item.
 */
@Entity(
    tableName = "inventory_events",
    indices = [Index("pantryItemId"), Index("sourceReceiptLineId")]
)
data class InventoryEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pantryItemId: Long = -1L,      // Which pantry item was affected (-1 = not yet linked)
    val delta: Float = 0f,            // Quantity change (positive for add)
    val eventType: EventType = EventType.ADD,     // ADD, UPDATE, ADJUST
    val sourceReceiptLineId: Long? = null, // Reference to ReceiptLine
    val timestamp: Long = System.currentTimeMillis()
)

enum class EventType {
    ADD,       // New item added from scan
    UPDATE,    // Existing item updated (e.g., different quantity)
    ADJUST     // Manual adjustment
}