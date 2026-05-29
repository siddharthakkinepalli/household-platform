package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.ImportAuditEntity
import com.household.app.data.entities.TransactionOverrideEntity
import com.household.app.data.entities.ExcludedTransactionEntity
import com.household.app.data.entities.MerchantRuleEntity

@Dao
interface TransactionOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: TransactionOverrideEntity): Long

    @Query("SELECT * FROM transaction_overrides WHERE transactionId = :transactionId")
    suspend fun getOverride(transactionId: Int): TransactionOverrideEntity?

    @Update
    suspend fun updateOverride(override: TransactionOverrideEntity): Int

    @Delete
    suspend fun deleteOverride(override: TransactionOverrideEntity): Int

    @Query("DELETE FROM transaction_overrides")
    suspend fun deleteAllOverrides(): Int

    @Query("SELECT * FROM transaction_overrides")
    suspend fun getAllOverrides(): List<TransactionOverrideEntity>
}

@Dao
interface ExcludedTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcluded(excluded: ExcludedTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcludedBatch(excluded: List<ExcludedTransactionEntity>): List<Long>

    @Query("SELECT transactionId FROM excluded_transactions")
    suspend fun getExcludedIds(): List<Int>

    @Query("SELECT * FROM excluded_transactions WHERE transactionId = :transactionId")
    suspend fun getExcludedTransaction(transactionId: Int): ExcludedTransactionEntity?

    @Delete
    suspend fun deleteExcluded(excluded: ExcludedTransactionEntity): Int

    @Query("DELETE FROM excluded_transactions")
    suspend fun deleteAllExcluded(): Int

    @Query("SELECT COUNT(*) FROM excluded_transactions")
    suspend fun getExcludedCount(): Int
}

@Dao
interface MerchantRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: MerchantRuleEntity): Long

    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC, updatedAt DESC")
    suspend fun getAllRules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE isEnabled = 1 ORDER BY priority DESC, updatedAt DESC")
    suspend fun getEnabledRules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC, updatedAt DESC")
    fun observeAllRules(): kotlinx.coroutines.flow.Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules WHERE merchantPattern = :merchantPattern")
    suspend fun getRule(merchantPattern: String): MerchantRuleEntity?

    @Query("DELETE FROM merchant_rules WHERE merchantPattern = :merchantPattern")
    suspend fun deleteRule(merchantPattern: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRulesIgnore(rules: List<MerchantRuleEntity>): List<Long>
}

@Dao
interface CategoryThresholdDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThreshold(threshold: CategoryThresholdEntity): Long

    @Query("SELECT * FROM category_thresholds ORDER BY categoryId")
    suspend fun getAllThresholds(): List<CategoryThresholdEntity>

    @Query("SELECT * FROM category_thresholds WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getThreshold(categoryId: String): CategoryThresholdEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertThresholdsIgnore(thresholds: List<CategoryThresholdEntity>): List<Long>
}

@Dao
interface ImportAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: ImportAuditEntity): Long

    @Query("SELECT * FROM import_audits WHERE fileHash = :fileHash ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByHash(fileHash: String): ImportAuditEntity?

    @Query("SELECT * FROM import_audits ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAudits(limit: Int = 10): List<ImportAuditEntity>

    @Query("DELETE FROM import_audits")
    suspend fun clearAll(): Int
}
