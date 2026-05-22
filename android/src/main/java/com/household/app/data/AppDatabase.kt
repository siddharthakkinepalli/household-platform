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
import com.household.app.data.dao.PantryDao
import com.household.app.data.dao.VaultDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.dao.WalletTripDao
import com.household.app.data.dao.WeightSnapshotDao
import com.household.app.data.dao.ProductDao
import com.household.app.data.dao.ProductAliasDao
import com.household.app.data.dao.ReceiptLineDao
import com.household.app.data.dao.DocumentAlertDao
import com.household.app.data.dao.DocumentDao
import com.household.app.data.dao.FamilyMemberDao
import com.household.app.data.dao.InventoryEventDao
import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.DashboardPrefsEntity
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.data.entities.ExcludedTransactionEntity
import com.household.app.data.entities.ImportAuditEntity
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.data.entities.MealsSummaryEntity
import com.household.app.data.entities.TransactionOverrideEntity
import com.household.app.data.entities.PantryEntity
import com.household.app.data.entities.VaultEntity
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.data.entities.WalletTripEntity
import com.household.app.data.entities.WeightSnapshotEntity
import com.household.app.data.entities.ProductEntity
import com.household.app.data.entities.ProductAliasEntity
import com.household.app.data.entities.ReceiptLineEntity
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.InventoryEventEntity

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
        ImportAuditEntity::class,
        VaultEntity::class,
        PantryEntity::class,
        ProductEntity::class,
        ProductAliasEntity::class,
        ReceiptLineEntity::class,
        InventoryEventEntity::class,
        FamilyMemberEntity::class,
        DocumentEntity::class,
        DocumentAlertEntity::class
    ],
    version = 10,
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
    abstract fun vaultDao(): VaultDao
    abstract fun pantryDao(): PantryDao
    abstract fun productDao(): ProductDao
    abstract fun productAliasDao(): ProductAliasDao
    abstract fun receiptLineDao(): ReceiptLineDao
    abstract fun inventoryEventDao(): InventoryEventDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun documentDao(): DocumentDao
    abstract fun documentAlertDao(): DocumentAlertDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vault_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        imagePath TEXT NOT NULL,
                        merchantName TEXT,
                        totalAmount REAL,
                        currency TEXT NOT NULL,
                        dateEpoch INTEGER NOT NULL,
                        rawOcrContent TEXT NOT NULL,
                        isLinkedToExpense INTEGER NOT NULL,
                        linkedExpenseId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vault_entries_dateEpoch ON vault_entries(dateEpoch)"
                )
                db.execSQL(
                    "ALTER TABLE wallet_transactions ADD COLUMN linkedVaultEntryId INTEGER"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pantry_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        quantity REAL NOT NULL DEFAULT 1.0,
                        expiryEstimate TEXT,
                        vaultId INTEGER,
                        isConfirmed INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pantry_items_vaultId ON pantry_items(vaultId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pantry_items_isConfirmed ON pantry_items(isConfirmed)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Products table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        canonicalName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Product Aliases table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_aliases (
                        rawOcrString TEXT NOT NULL,
                        productId INTEGER NOT NULL,
                        storeName TEXT NOT NULL,
                        frequency INTEGER NOT NULL DEFAULT 1,
                        lastSeenAt INTEGER NOT NULL,
                        PRIMARY KEY (rawOcrString, storeName),
                        FOREIGN KEY (productId) REFERENCES products(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_aliases_productId ON product_aliases(productId)")

                // Receipt Lines table (Observation Layer)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receipt_lines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scanId INTEGER NOT NULL,
                        rawText TEXT NOT NULL,
                        parsedPrice REAL,
                        matchedProductId INTEGER,
                        pantryItemId INTEGER,
                        confidence REAL NOT NULL DEFAULT 0,
                        resolutionMethod TEXT NOT NULL DEFAULT 'FAILED',
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_lines_matchedProductId ON receipt_lines(matchedProductId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_lines_pantryItemId ON receipt_lines(pantryItemId)")

                // Inventory Events table (State Projection)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pantryItemId INTEGER NOT NULL DEFAULT -1,
                        delta REAL NOT NULL DEFAULT 0,
                        eventType TEXT NOT NULL DEFAULT 'ADD',
                        sourceReceiptLineId INTEGER,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_events_pantryItemId ON inventory_events(pantryItemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_events_sourceReceiptLineId ON inventory_events(sourceReceiptLineId)")

                // Seed common products for learning
                val commonProducts = listOf(
                    "Milch", "Butter", "Käse", "Joghurt", "Eier",
                    "Brot", "Brötchen", "Nudeln", "Reis",
                    "Äpfel", "Bananen", "Tomaten", "Gurken", "Kartoffeln",
                    "Hähnchen", "Rinderhack", "Schweinefleisch", "Fisch",
                    "Wasser", "Saft", "Kaffee", "Tee"
                )
                val now = System.currentTimeMillis()
                commonProducts.forEachIndexed { index, name ->
                    db.execSQL("INSERT INTO products (id, canonicalName, createdAt) VALUES (${index + 1}, '$name', $now)")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Family Members table (no DEFAULT - Room handles via Kotlin code)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS family_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        avatarPath TEXT,
                        colorCode TEXT NOT NULL,
                        role TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_family_members_role ON family_members(role)")

                // Documents table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS documents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerId INTEGER,
                        title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        expiryDate INTEGER,
                        noticePeriodDays INTEGER NOT NULL,
                        monthlyCost REAL,
                        localUri TEXT,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (ownerId) REFERENCES family_members(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_ownerId ON documents(ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_expiryDate ON documents(expiryDate)")

                // Document Alerts table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS document_alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        documentId INTEGER NOT NULL,
                        alertType TEXT NOT NULL,
                        message TEXT NOT NULL,
                        daysUntil INTEGER NOT NULL,
                        isAcknowledged INTEGER NOT NULL,
                        actionTaken TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_alerts_documentId ON document_alerts(documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_alerts_isAcknowledged ON document_alerts(isAcknowledged)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN category TEXT NOT NULL DEFAULT 'RECEIPT'")
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN documentTitle TEXT")
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN mimeType TEXT NOT NULL DEFAULT 'image/jpeg'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_entries_category ON vault_entries(category)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN ownerMemberId INTEGER")
                db.execSQL("ALTER TABLE vault_entries ADD COLUMN subFolder TEXT NOT NULL DEFAULT 'unfiled'")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vault_entries_folder " +
                        "ON vault_entries(category, ownerMemberId, subFolder)"
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
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
