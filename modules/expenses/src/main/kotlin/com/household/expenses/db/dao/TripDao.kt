package com.household.expenses.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.household.expenses.db.entity.Trip

@Dao
interface TripDao {

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip): Int

    @Delete
    suspend fun delete(trip: Trip): Int

    @Query("SELECT * FROM trips WHERE household_id = :householdId ORDER BY start_date DESC")
    fun getAllLive(householdId: String): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE household_id = :householdId ORDER BY start_date DESC")
    suspend fun getAll(householdId: String): List<Trip>

    @Query("SELECT * FROM trips WHERE household_id = :householdId AND id = :id LIMIT 1")
    suspend fun getById(householdId: String, id: Long): Trip?

    @Query("DELETE FROM trips WHERE household_id = :householdId AND id = :id")
    suspend fun deleteById(householdId: String, id: Long): Int
}
