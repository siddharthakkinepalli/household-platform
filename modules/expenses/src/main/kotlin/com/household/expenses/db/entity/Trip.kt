package com.household.expenses.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [Index(value = ["household_id"])]
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "household_id") val householdId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "start_date") val startDate: String,    // ISO YYYY-MM-DD
    @ColumnInfo(name = "end_date") val endDate: String,        // ISO YYYY-MM-DD
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: String = ""
)
