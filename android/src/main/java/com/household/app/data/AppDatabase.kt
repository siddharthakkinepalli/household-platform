package com.household.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.household.app.data.dao.DashboardPrefsDao
import com.household.app.data.dao.ExcludedTransactionDao
import com.household.app.data.dao.MerchantRuleDao
import com.household.app.data.dao.MealsSummaryDao
import com.household.app.data.dao.TransactionOverrideDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.dao.WalletTripDao
import com.household.app.data.dao.WeightSnapshotDao
import com.household.app.data.entities.DashboardPrefsEntity
import com.household.app.data.entities.ExcludedTransactionEntity
import com.household.app.data.entities.MerchantRuleEntity
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
        DashboardPrefsEntity::class,
        MerchantRuleEntity::class
    ],
    version = 2,
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
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merchant_rules (
                        merchantPattern TEXT NOT NULL,
                        targetCategoryId TEXT NOT NULL,
                        isExclusion INTEGER NOT NULL DEFAULT 0,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(merchantPattern)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "household_app.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
