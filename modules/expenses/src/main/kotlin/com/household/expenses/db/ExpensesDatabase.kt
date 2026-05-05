package com.household.expenses.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.household.core.HouseholdDatabase
import com.household.expenses.db.dao.BudgetCategoryDao
import com.household.expenses.db.dao.TransactionDao
import com.household.expenses.db.dao.TripDao
import com.household.expenses.db.entity.BudgetCategory
import com.household.expenses.db.entity.Transaction
import com.household.expenses.db.entity.Trip

/**
 * Expenses Database extending the core HouseholdDatabase.
 * Includes all expenses-related entities: Transaction, Trip, BudgetCategory.
 *
 * Usage:
 * - Access via: val dao = database.transactionDao()
 * - All data is scoped to householdId for multi-household support
 */
@Database(
    entities = [Transaction::class, BudgetCategory::class, Trip::class],
    version = 1,
    exportSchema = false
)
abstract class ExpensesDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun budgetCategoryDao(): BudgetCategoryDao
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: ExpensesDatabase? = null

        /**
         * Get singleton instance of ExpensesDatabase.
         * This database is created via the parent HouseholdDatabase factory.
         *
         * Note: In a real app, this would be accessed through HouseholdDatabase.
         * This companion is for testing/standalone usage only.
         */
        fun getInstance(): ExpensesDatabase? = INSTANCE

        internal fun setInstance(db: ExpensesDatabase) {
            INSTANCE = db
        }
    }
}
