package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_trips")
data class WalletTripEntity(
    @PrimaryKey
    val name: String,
    val budget: Double,
    val createdAt: String = System.currentTimeMillis().toString()
)
