package com.household.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.household.app.data.dao.DashboardPrefsDao
import com.household.app.data.dao.ExcludedTransactionDao
import com.household.app.data.dao.MealsSummaryDao
import com.household.app.data.dao.TransactionOverrideDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.dao.WalletTripDao
import com.household.app.data.dao.WeightSnapshotDao
import com.household.app.data.entities.DashboardPrefsEntity
import com.household.app.data.entities.ExcludedTransactionEntity
import com.household.app.data.entities.MealsSummaryEntity
import com.household.app.data.entities.TransactionOverrideEntity
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.data.entities.WalletTripEntity
import com.household.app.data.entities.WeightSnapshotEntity

@Database(
    entities = [
        WalletTransactionEntity::class,
        WalletTripEntity::class,
        TransactionOverrideEntity::class,
        ExcludedTransactionEntity::class,
        WeightSnapshotEntity::class,
        MealsSummaryEntity::class,
        DashboardPrefsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletTransactionDao(): WalletTransactionDao
    abstract fun walletTripDao(): WalletTripDao
    abstract fun transactionOverrideDao(): TransactionOverrideDao
    abstract fun excludedTransactionDao(): ExcludedTransactionDao
    abstract fun weightSnapshotDao(): WeightSnapshotDao
    abstract fun mealsSummaryDao(): MealsSummaryDao
    abstract fun dashboardPrefsDao(): DashboardPrefsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "household_app.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
