package com.household.expenses.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["budget_category"]),
        Index(value = ["trip_id"]),
        Index(value = ["household_id"]),
        Index(value = ["hash"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "household_id") val householdId: String,
    @ColumnInfo(name = "date") val date: String,               // ISO YYYY-MM-DD
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "amount") val amount: Double,           // negative=expense, positive=income
    @ColumnInfo(name = "bank") val bank: String = "",          // Commerzbank | ING | N26 | Revolut
    @ColumnInfo(name = "category") val category: String = "",
    @ColumnInfo(name = "budget_category") val budgetCategory: String = "",
    @ColumnInfo(name = "trip_id") val tripId: Long? = null,
    @ColumnInfo(name = "imported_at") val importedAt: String = "",
    @ColumnInfo(name = "hash") val hash: String                // MD5 for deduplication
)
