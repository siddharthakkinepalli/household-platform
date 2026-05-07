package com.household.app.data.entities

import androidx.room.Entity
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
    val monthlyBudget: Int = 3000
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey
    val merchantPattern: String,
    val targetCategoryId: String,
    val isExclusion: Boolean = false,
    val updatedAt: String = System.currentTimeMillis().toString()
)
