package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.app.data.entities.ProductEntity
import com.household.app.data.entities.ProductAliasEntity
import com.household.app.data.entities.ReceiptLineEntity
import com.household.app.data.entities.InventoryEventEntity

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(canonicalName) LIKE LOWER(:name)")
    suspend fun findByName(name: String): List<ProductEntity>

    @Query("SELECT * FROM products ORDER BY canonicalName ASC")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE canonicalName = :name LIMIT 1")
    suspend fun findExactByName(name: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)
}

@Dao
interface ProductAliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: ProductAliasEntity)

    @Query("""
        SELECT * FROM product_aliases
        WHERE rawOcrString = :rawOcrString
        AND storeName = :storeName
    """)
    suspend fun findExactMatch(rawOcrString: String, storeName: String): ProductAliasEntity?

    @Query("SELECT * FROM product_aliases WHERE productId = :productId")
    suspend fun getAliasesForProduct(productId: Long): List<ProductAliasEntity>

    @Query("""
        UPDATE product_aliases
        SET frequency = frequency + 1, lastSeenAt = :timestamp
        WHERE rawOcrString = :rawOcrString AND storeName = :storeName
    """)
    suspend fun incrementFrequency(rawOcrString: String, storeName: String, timestamp: Long)

    @Query("SELECT * FROM product_aliases WHERE frequency >= 3 ORDER BY frequency DESC")
    suspend fun getLearnedAliases(): List<ProductAliasEntity>

    @Query("SELECT * FROM product_aliases WHERE storeName = :storeName")
    suspend fun getAliasesForStore(storeName: String): List<ProductAliasEntity>
}

@Dao
interface ReceiptLineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: ReceiptLineEntity): Long

    @Query("SELECT * FROM receipt_lines WHERE scanId = :scanId")
    suspend fun getLinesForScan(scanId: Long): List<ReceiptLineEntity>

    @Query("SELECT * FROM receipt_lines WHERE id = :id")
    suspend fun getById(id: Long): ReceiptLineEntity?

    @Query("SELECT * FROM receipt_lines WHERE matchedProductId IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getResolvedLines(): List<ReceiptLineEntity>

    @Query("SELECT COUNT(*) FROM receipt_lines WHERE matchedProductId IS NULL")
    suspend fun getFailedResolutionCount(): Int

    @Query("SELECT * FROM receipt_lines WHERE confidence < 0.5 ORDER BY timestamp DESC")
    suspend fun getLowConfidenceLines(): List<ReceiptLineEntity>
}

@Dao
interface InventoryEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: InventoryEventEntity): Long

    @Query("SELECT * FROM inventory_events WHERE pantryItemId = :pantryItemId ORDER BY timestamp DESC")
    suspend fun getEventsForPantryItem(pantryItemId: Long): List<InventoryEventEntity>

    @Query("SELECT * FROM inventory_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 50): List<InventoryEventEntity>

    @Query("SELECT SUM(delta) FROM inventory_events WHERE pantryItemId = :pantryItemId")
    suspend fun getCurrentStock(pantryItemId: Long): Float?
}