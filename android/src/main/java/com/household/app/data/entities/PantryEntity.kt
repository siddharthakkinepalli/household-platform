package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "pantry_items",
    indices = [Index("vaultId"), Index("isConfirmed")]
)
data class PantryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String,
    val quantity: Float = 1f,
    val expiryEstimate: LocalDate? = null,
    val vaultId: Long? = null,
    val isConfirmed: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
