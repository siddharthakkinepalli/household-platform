package com.household.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for the Household Platform Core.
 * Contains all core entities shared across household modules.
 * 
 * Supports migrations from version 1 onwards.
 */
@Database(
    entities = [HouseholdProfile::class],
    version = 1,
    exportSchema = false
)
abstract class HouseholdDatabase : RoomDatabase() {
    
    /**
     * Data Access Object for HouseholdProfile operations
     */
    abstract fun householdProfileDao(): HouseholdProfileDao
    
    companion object {
        private const val DATABASE_NAME = "household_core.db"
        
        @Volatile
        private var INSTANCE: HouseholdDatabase? = null
        
        /**
         * Get singleton instance of HouseholdDatabase
         */
        fun getInstance(context: Context): HouseholdDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        
        /**
         * Build database with migrations
         */
        private fun buildDatabase(context: Context): HouseholdDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                HouseholdDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*getMigrations())
                .fallbackToDestructiveMigration()
                .build()
        }
        
        /**
         * Define all migrations here
         * Add new migrations as the schema evolves
         */
        private fun getMigrations(): Array<Migration> {
            return arrayOf(
                // Migration from version 0 to 1 (if needed in future)
                // MIGRATION_0_1
            )
        }
        
        /**
         * Reset database (useful for testing)
         */
        fun resetDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

/**
 * Data Access Object for HouseholdProfile operations
 */
@androidx.room.Dao
interface HouseholdProfileDao {
    
    /**
     * Insert a household profile
     */
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertHousehold(household: HouseholdProfile): Long
    
    /**
     * Get a household by its Room database ID
     */
    @androidx.room.Query("SELECT * FROM households WHERE id = :id")
    suspend fun getHouseholdById(id: Long): HouseholdProfile?
    
    /**
     * Get a household by its householdId (unique identifier)
     */
    @androidx.room.Query("SELECT * FROM households WHERE householdId = :householdId")
    suspend fun getHouseholdByHouseholdId(householdId: String): HouseholdProfile?
    
    /**
     * Get all households
     */
    @androidx.room.Query("SELECT * FROM households")
    suspend fun getAllHouseholds(): List<HouseholdProfile>
    
    /**
     * Update a household profile
     */
    @androidx.room.Update
    suspend fun updateHousehold(household: HouseholdProfile): Int
    
    /**
     * Delete a household by its Room database ID
     */
    @androidx.room.Delete
    suspend fun deleteHousehold(household: HouseholdProfile): Int
    
    /**
     * Delete a household by its householdId
     */
    @androidx.room.Query("DELETE FROM households WHERE householdId = :householdId")
    suspend fun deleteHouseholdByHouseholdId(householdId: String): Int
    
    /**
     * Count total households
     */
    @androidx.room.Query("SELECT COUNT(*) FROM households")
    suspend fun getHouseholdCount(): Int
    
    /**
     * Check if a household exists by householdId
     */
    @androidx.room.Query("SELECT EXISTS(SELECT 1 FROM households WHERE householdId = :householdId)")
    suspend fun householdExists(householdId: String): Boolean
}

/**
 * Migration template for future schema changes.
 * Uncomment and modify as needed when adding new entities or modifying existing ones.
 */
/*
val MIGRATION_0_1 = object : Migration(0, 1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Example: Add a new table or column
        // database.execSQL("""
        //     ALTER TABLE households ADD COLUMN newColumn TEXT DEFAULT ''
        // """)
    }
}
*/
