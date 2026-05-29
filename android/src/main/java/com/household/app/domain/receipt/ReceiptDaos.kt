package com.household.app.domain.receipt

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product): Long

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Query("SELECT * FROM products WHERE LOWER(canonicalName) LIKE LOWER(:name)")
    suspend fun findByName(name: String): List<Product>

    @Query("SELECT * FROM products ORDER BY canonicalName ASC")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE canonicalName = :name LIMIT 1")
    suspend fun findExactByName(name: String): Product?
}

@Dao
interface ProductAliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: ProductAlias): Long

    /**
     * Step 1: Exact Alias Match
     * Check if rawOcrString + storeName combination exists
     */
    @Query("""
        SELECT pa.* FROM product_aliases pa
        WHERE pa.rawOcrString = :rawOcrString
        AND pa.storeName = :storeName
    """)
    suspend fun findExactMatch(rawOcrString: String, storeName: String): ProductAlias?

    /**
     * Find all aliases for a product
     */
    @Query("SELECT * FROM product_aliases WHERE productId = :productId")
    suspend fun getAliasesForProduct(productId: Long): List<ProductAlias>

    /**
     * Update frequency after successful resolution
     */
    @Query("""
        UPDATE product_aliases
        SET frequency = frequency + 1, lastSeenAt = :timestamp
        WHERE rawOcrString = :rawOcrString AND storeName = :storeName
    """)
    suspend fun incrementFrequency(rawOcrString: String, storeName: String, timestamp: Long): Int

    /**
     * Get high-frequency aliases (learned patterns)
     */
    @Query("SELECT * FROM product_aliases WHERE frequency >= 3 ORDER BY frequency DESC")
    suspend fun getLearnedAliases(): List<ProductAlias>

    /**
     * Get all aliases for a store
     */
    @Query("SELECT * FROM product_aliases WHERE storeName = :storeName")
    suspend fun getAliasesForStore(storeName: String): List<ProductAlias>
}

@Dao
interface ReceiptLineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: ReceiptLine): Long

    @Query("SELECT * FROM receipt_lines WHERE scanId = :scanId")
    suspend fun getLinesForScan(scanId: Long): List<ReceiptLine>

    @Query("SELECT * FROM receipt_lines WHERE id = :id")
    suspend fun getById(id: Long): ReceiptLine?

    @Query("SELECT * FROM receipt_lines WHERE matchedProductId IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getResolvedLines(): List<ReceiptLine>

    @Query("SELECT COUNT(*) FROM receipt_lines WHERE matchedProductId IS NULL")
    suspend fun getFailedResolutionCount(): Int

    @Query("SELECT * FROM receipt_lines WHERE confidence < 0.5 ORDER BY timestamp DESC")
    suspend fun getLowConfidenceLines(): List<ReceiptLine>
}

@Dao
interface InventoryEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: InventoryEvent): Long

    @Query("SELECT * FROM inventory_events WHERE pantryItemId = :pantryItemId ORDER BY timestamp DESC")
    suspend fun getEventsForPantryItem(pantryItemId: Long): List<InventoryEvent>

    @Query("SELECT * FROM inventory_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 50): List<InventoryEvent>

    @Query("SELECT SUM(delta) FROM inventory_events WHERE pantryItemId = :pantryItemId")
    suspend fun getCurrentStock(pantryItemId: Long): Float?
}