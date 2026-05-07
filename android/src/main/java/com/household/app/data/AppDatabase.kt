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
import com.household.app.data.dao.ImportAuditDao
import com.household.app.data.dao.MerchantRuleDao
import com.household.app.data.dao.MealsSummaryDao
import com.household.app.data.dao.CategoryThresholdDao
import com.household.app.data.dao.TransactionOverrideDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.dao.WalletTripDao
import com.household.app.data.dao.WeightSnapshotDao
import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.DashboardPrefsEntity
import com.household.app.data.entities.ExcludedTransactionEntity
import com.household.app.data.entities.ImportAuditEntity
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
        MerchantRuleEntity::class,
        CategoryThresholdEntity::class,
        ImportAuditEntity::class
    ],
    version = 4,
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
    abstract fun categoryThresholdDao(): CategoryThresholdDao
    abstract fun importAuditDao(): ImportAuditDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE merchant_rules ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE merchant_rules ADD COLUMN priority INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_thresholds (
                        categoryId TEXT NOT NULL,
                        limitAmount REAL NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(categoryId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        fileHash TEXT NOT NULL,
                        rowCount INTEGER NOT NULL,
                        skippedCount INTEGER NOT NULL,
                        detectedBank TEXT NOT NULL,
                        delimiter TEXT NOT NULL,
                        warningCount INTEGER NOT NULL,
                        salaryAnchorDate INTEGER NOT NULL DEFAULT 25,
                        parserVersion INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_import_audits_fileHash ON import_audits(fileHash)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dashboard_prefs ADD COLUMN salaryAnchorDay INTEGER NOT NULL DEFAULT 25"
                )
                db.execSQL(
                    "ALTER TABLE import_audits ADD COLUMN salaryAnchorDate INTEGER NOT NULL DEFAULT 25"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
