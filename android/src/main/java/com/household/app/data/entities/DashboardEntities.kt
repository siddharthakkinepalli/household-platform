package com.household.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_overrides")
data class TransactionOverrideEntity(
    @PrimaryKey
    val transactionId: Int,
    val categoryOverride: String? = null,
    val tripOverride: String? = null
)

@Entity(tableName = "excluded_transactions")
data class ExcludedTransactionEntity(
    @PrimaryKey
    val transactionId: Int,
    val excludedAt: String = System.currentTimeMillis().toString()
)

@Entity(tableName = "weight_snapshots")
data class WeightSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val currentKg: Double,
    val previousKg: Double? = null,
    val date: String  // ISO date string
)

@Entity(tableName = "meals_summary")
data class MealsSummaryEntity(
    @PrimaryKey
    val id: Int = 1,  // Singleton table
    val mealsToday: Int,
    val nextMeal: String,
    val updatedOn: String  // ISO date string
)

@Entity(tableName = "dashboard_prefs")
data class DashboardPrefsEntity(
    @PrimaryKey
    val id: Int = 1,  // Singleton table
    val walletQuickCategory: String = "",
    val walletQuickQuery: String = "",
    val monthlyBudget: Int = 3000,
    val salaryAnchorDay: Int = 25
)

@Entity(tableName = "category_thresholds")
data class CategoryThresholdEntity(
    @PrimaryKey
    val categoryId: String,
    val limitAmount: Float,
    val updatedAt: String = System.currentTimeMillis().toString()
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey
    val merchantPattern: String,
    val targetCategoryId: String,
    val isExclusion: Boolean = false,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val updatedAt: String = System.currentTimeMillis().toString()
)

@Entity(tableName = "salary_sources")
data class SalarySourceEntity(
    @PrimaryKey val id: Int = 1,
    val merchantPattern: String,
    val lastAmount: Double,
    val minAmount: Double,
    val maxAmount: Double,
    val confirmedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "import_audits")
data class ImportAuditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val fileName: String,
    val fileHash: String,
    val rowCount: Int,
    val skippedCount: Int,
    val detectedBank: String,
    val delimiter: String,
    val warningCount: Int,
    val salaryAnchorDate: Int,
    val parserVersion: Int = 1
)

@Entity(tableName = "recurring_bills", indices = [Index("isActive")])
data class RecurringBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantPattern: String,
    val normalizedAmount: Double,
    val minAmount: Double,
    val maxAmount: Double,
    val category: String,
    val lastSeenDate: Long,
    val cycleCount: Int,
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true,
    // "AUTO" = pipeline-detected, "CONFIRMED" = user-validated, "MANUAL" = user-created, "DISMISSED" = user-rejected
    @ColumnInfo(defaultValue = "'AUTO'") val source: String = "AUTO"
)

@Entity(tableName = "tax_checks", indices = [Index("year")])
data class TaxCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val driveIncomeTaxFolderId: String? = null,
    @ColumnInfo(defaultValue = "0") val payslipsFound: Int = 0,
    @ColumnInfo(defaultValue = "0") val lohnsteuerFound: Boolean = false,
    @ColumnInfo(defaultValue = "0") val lohnsteuerChitraFound: Boolean = false,
    @ColumnInfo(defaultValue = "0") val kitaDocsFound: Int = 0,
    @ColumnInfo(defaultValue = "0") val taxExcelFound: Boolean = false,
    @ColumnInfo(defaultValue = "0") val bankStatementsFound: Int = 0,
    @ColumnInfo(defaultValue = "0") val donationDocsFound: Int = 0,
    @ColumnInfo(defaultValue = "0") val utilityBillsFound: Int = 0,
    @ColumnInfo(defaultValue = "0") val isComplete: Boolean = false,
    @ColumnInfo(defaultValue = "0") val lastSyncedAt: Long = 0L,
    @ColumnInfo(defaultValue = "'OFFLINE'") val syncStatus: String = "OFFLINE"
)

@Entity(
    tableName = "tax_tags",
    indices = [Index("entityType", "entityId"), Index("year")]
)
data class TaxTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,   // "transaction" or "vault_doc"
    val entityId: Long,
    val taxCategory: String,  // e.g. "Work Equipment", "Home Office", "Medical Expenses", "Donations", "Work Commute", "Other"
    val year: Int,
    val note: String? = null
)
