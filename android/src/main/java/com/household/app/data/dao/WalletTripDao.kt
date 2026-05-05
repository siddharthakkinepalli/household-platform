package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.app.data.entities.WalletTripEntity

@Dao
interface WalletTripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: WalletTripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<WalletTripEntity>)

    @Query("SELECT * FROM wallet_trips ORDER BY name ASC")
    suspend fun getAllTrips(): List<WalletTripEntity>

    @Query("SELECT * FROM wallet_trips WHERE name = :name")
    suspend fun getTripByName(name: String): WalletTripEntity?

    @Update
    suspend fun updateTrip(trip: WalletTripEntity)

    @Delete
    suspend fun deleteTrip(trip: WalletTripEntity)

    @Query("DELETE FROM wallet_trips")
    suspend fun deleteAllTrips()

    @Query("SELECT COUNT(*) FROM wallet_trips")
    suspend fun getTripCount(): Int
}
