package com.household.expenses.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.expenses.db.entity.BudgetCategory

@Dao
interface BudgetCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: BudgetCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<BudgetCategory>)

    @Delete
    suspend fun delete(category: BudgetCategory)

    @Query("SELECT * FROM budget_categories WHERE household_id = :householdId ORDER BY name")
    fun getAllLive(householdId: String): LiveData<List<BudgetCategory>>

    @Query("SELECT * FROM budget_categories WHERE household_id = :householdId ORDER BY name")
    suspend fun getAll(householdId: String): List<BudgetCategory>

    @Query("SELECT * FROM budget_categories WHERE household_id = :householdId AND name = :name LIMIT 1")
    suspend fun getByName(householdId: String, name: String): BudgetCategory?
}
