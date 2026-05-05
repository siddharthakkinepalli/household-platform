package com.household.expenses.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budget_categories",
    indices = [
        Index(value = ["household_id", "name"], unique = true),
        Index(value = ["household_id"])
    ]
)
data class BudgetCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "household_id") val householdId: String,
    @ColumnInfo(name = "name") val name: String,               // e.g. Grocery
    @ColumnInfo(name = "monthly_limit") val monthlyLimit: Double,
    @ColumnInfo(name = "allowed_banks") val allowedBanks: String = "[]",  // JSON array
    @ColumnInfo(name = "priority") val priority: String = "",
    @ColumnInfo(name = "description") val description: String = ""
)
