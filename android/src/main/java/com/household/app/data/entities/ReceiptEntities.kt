package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Product - Canonical product reference
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalName: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ProductAlias - Maps raw OCR strings to canonical Products
 */
@Entity(
    tableName = "product_aliases",
    primaryKeys = ["rawOcrString", "storeName"],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
data class ProductAliasEntity(
    val rawOcrString: String,
    val productId: Long,
    val storeName: String,
    val frequency: Int = 1,
    val lastSeenAt: Long = System.currentTimeMillis()
)

/**
 * ReceiptLine - Observation layer (audit trail)
 */
@Entity(
    tableName = "receipt_lines",
    indices = [Index("matchedProductId"), Index("pantryItemId")]
)
data class ReceiptLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scanId: Long = 0,
    val rawText: String = "",
    val parsedPrice: Double? = null,
    val matchedProductId: Long? = null,
    val pantryItemId: Long? = null,
    val confidence: Float = 0f,
    val resolutionMethod: String = "FAILED",  // EXACT, FUZZY, FAILED
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * InventoryEvent - State projection
 */
@Entity(
    tableName = "inventory_events",
    indices = [Index("pantryItemId"), Index("sourceReceiptLineId")]
)
data class InventoryEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pantryItemId: Long = -1L,
    val delta: Float = 0f,
    val eventType: String = "ADD",  // ADD, UPDATE, ADJUST
    val sourceReceiptLineId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)