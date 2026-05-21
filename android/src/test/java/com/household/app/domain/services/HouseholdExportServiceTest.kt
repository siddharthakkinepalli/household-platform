package com.household.app.domain.services

import com.google.gson.GsonBuilder
import com.household.app.data.AppDatabase
import com.household.app.data.dao.CategoryThresholdDao
import com.household.app.data.dao.DocumentAlertDao
import com.household.app.data.dao.DocumentDao
import com.household.app.data.dao.FamilyMemberDao
import com.household.app.data.dao.InventoryEventDao
import com.household.app.data.dao.MerchantRuleDao
import com.household.app.data.dao.PantryDao
import com.household.app.data.dao.VaultDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.InventoryEventEntity
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.data.entities.PantryEntity
import com.household.app.data.entities.VaultEntity
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.domain.models.HouseholdBackup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.time.LocalDate

/**
 * Unit tests for [HouseholdExportService].
 *
 * Because [HouseholdExportService.export] writes to an Android [android.content.Context]
 * external-files directory, these tests exercise the serialisation logic directly:
 * we stub every DAO, build the [HouseholdBackup] object, and write it to a [TemporaryFolder]
 * to assert JSON structure without needing the Android runtime.
 */
class HouseholdExportServiceTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var walletTransactionDao: WalletTransactionDao
    private lateinit var vaultDao: VaultDao
    private lateinit var pantryDao: PantryDao
    private lateinit var inventoryEventDao: InventoryEventDao
    private lateinit var merchantRuleDao: MerchantRuleDao
    private lateinit var categoryThresholdDao: CategoryThresholdDao
    private lateinit var familyMemberDao: FamilyMemberDao
    private lateinit var documentDao: DocumentDao
    private lateinit var documentAlertDao: DocumentAlertDao

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .setPrettyPrinting()
        .create()

    @Before
    fun setUp() {
        db = mock()

        walletTransactionDao  = mock()
        vaultDao              = mock()
        pantryDao             = mock()
        inventoryEventDao     = mock()
        merchantRuleDao       = mock()
        categoryThresholdDao  = mock()
        familyMemberDao       = mock()
        documentDao           = mock()
        documentAlertDao      = mock()

        whenever(db.walletTransactionDao()).thenReturn(walletTransactionDao)
        whenever(db.vaultDao()).thenReturn(vaultDao)
        whenever(db.pantryDao()).thenReturn(pantryDao)
        whenever(db.inventoryEventDao()).thenReturn(inventoryEventDao)
        whenever(db.merchantRuleDao()).thenReturn(merchantRuleDao)
        whenever(db.categoryThresholdDao()).thenReturn(categoryThresholdDao)
        whenever(db.familyMemberDao()).thenReturn(familyMemberDao)
        whenever(db.documentDao()).thenReturn(documentDao)
        whenever(db.documentAlertDao()).thenReturn(documentAlertDao)
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun sampleWalletTx(id: Int) = WalletTransactionEntity(
        id = id,
        title = "REWE Markt",
        category = "Groceries",
        amount = -42.0,
        date = LocalDate.of(2026, 1, 15),
        paymentType = "card"
    )

    /** Builds a [HouseholdBackup] by calling each DAO stub directly. */
    private suspend fun buildBackupFromStubs(): HouseholdBackup = HouseholdBackup(
        walletTransactions = db.walletTransactionDao().getAllTransactions(),
        vaultEntries       = db.vaultDao().getAllEntriesList(),
        pantryItems        = db.pantryDao().getAllPantry(),
        inventoryEvents    = db.inventoryEventDao().getAllEvents(),
        merchantRules      = db.merchantRuleDao().getAllRules(),
        categoryThresholds = db.categoryThresholdDao().getAllThresholds(),
        familyMembers      = db.familyMemberDao().getAllMembersList(),
        documents          = db.documentDao().getAllDocumentsList(),
        documentAlerts     = db.documentAlertDao().getAllAlertsList()
    )

    /** Writes backup JSON to a temp file and returns the file. */
    private fun writeToTempFile(backup: HouseholdBackup): File {
        val file = tmpFolder.newFile("test_backup.json")
        file.writer().use { gson.toJson(backup, it) }
        return file
    }

    // ─────────────────────────────────────────────────────────────
    // Test: resulting JSON contains all entity-type keys
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `export JSON contains all entity type keys`() = runTest {
        stubAllDaosEmpty()

        val backup = buildBackupFromStubs()
        val file   = writeToTempFile(backup)
        val json   = file.readText()

        assertTrue("walletTransactions missing", json.contains("walletTransactions"))
        assertTrue("vaultEntries missing",       json.contains("vaultEntries"))
        assertTrue("pantryItems missing",        json.contains("pantryItems"))
        assertTrue("inventoryEvents missing",    json.contains("inventoryEvents"))
        assertTrue("merchantRules missing",      json.contains("merchantRules"))
        assertTrue("categoryThresholds missing", json.contains("categoryThresholds"))
        assertTrue("familyMembers missing",      json.contains("familyMembers"))
        assertTrue("documents missing",          json.contains("documents"))
        assertTrue("documentAlerts missing",     json.contains("documentAlerts"))
    }

    // ─────────────────────────────────────────────────────────────
    // Test: JSON structure matches HouseholdBackup model
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `export JSON contains version 1 and exportedAt timestamp`() = runTest {
        stubAllDaosEmpty()

        val backup = buildBackupFromStubs()
        val file   = writeToTempFile(backup)
        val json   = file.readText()

        assertTrue("version field missing", json.contains("\"version\": 1"))
        assertTrue("exportedAt field missing", json.contains("exportedAt"))

        // Re-parse and verify field values.
        val parsed = gson.fromJson(file.reader(), HouseholdBackup::class.java)
        assertEquals(1, parsed.version)
        assertTrue("exportedAt should be a positive epoch millis", parsed.exportedAt > 0L)
    }

    @Test
    fun `exported backup round-trips correctly for wallet transactions`() = runTest {
        val txList = listOf(sampleWalletTx(1), sampleWalletTx(2))
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(txList)
        stubAllDaosEmptyExcept(walletTransactions = txList)

        val backup = buildBackupFromStubs()
        val file   = writeToTempFile(backup)

        val parsed = gson.fromJson(file.reader(), HouseholdBackup::class.java)
        assertEquals(2, parsed.walletTransactions.size)
        assertEquals(1, parsed.walletTransactions[0].id)
        assertEquals("REWE Markt", parsed.walletTransactions[0].title)
        assertEquals("Groceries",  parsed.walletTransactions[0].category)
    }

    @Test
    fun `exported backup round-trips LocalDate correctly`() = runTest {
        val txWithDate = sampleWalletTx(99)  // date = 2026-01-15
        stubAllDaosEmptyExcept(walletTransactions = listOf(txWithDate))

        val backup = buildBackupFromStubs()
        val file   = writeToTempFile(backup)

        val parsed = gson.fromJson(file.reader(), HouseholdBackup::class.java)
        assertEquals(LocalDate.of(2026, 1, 15), parsed.walletTransactions[0].date)
    }

    @Test
    fun `export populates all entity lists when DAOs return data`() = runTest {
        val txList = listOf(sampleWalletTx(1))
        val ruleList = listOf(
            MerchantRuleEntity(merchantPattern = "rewe", targetCategoryId = "Groceries")
        )
        stubAllDaosEmptyExcept(walletTransactions = txList, merchantRules = ruleList)

        val backup = buildBackupFromStubs()

        assertEquals(1, backup.walletTransactions.size)
        assertEquals(1, backup.merchantRules.size)
        assertEquals(0, backup.vaultEntries.size)
        assertEquals(0, backup.pantryItems.size)
        assertEquals(0, backup.inventoryEvents.size)
        assertEquals(0, backup.categoryThresholds.size)
        assertEquals(0, backup.familyMembers.size)
        assertEquals(0, backup.documents.size)
        assertEquals(0, backup.documentAlerts.size)
    }

    // ─────────────────────────────────────────────────────────────
    // Test: HouseholdBackup default values
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `HouseholdBackup default version is 1`() {
        val backup = HouseholdBackup()
        assertEquals(1, backup.version)
    }

    @Test
    fun `HouseholdBackup exportedAt is non-zero by default`() {
        val before = System.currentTimeMillis()
        val backup = HouseholdBackup()
        val after  = System.currentTimeMillis()
        assertTrue(backup.exportedAt in before..after)
    }

    @Test
    fun `HouseholdBackup all entity lists default to empty`() {
        val backup = HouseholdBackup()
        assertNotNull(backup.walletTransactions)
        assertTrue(backup.walletTransactions.isEmpty())
        assertTrue(backup.vaultEntries.isEmpty())
        assertTrue(backup.pantryItems.isEmpty())
        assertTrue(backup.inventoryEvents.isEmpty())
        assertTrue(backup.merchantRules.isEmpty())
        assertTrue(backup.categoryThresholds.isEmpty())
        assertTrue(backup.familyMembers.isEmpty())
        assertTrue(backup.documents.isEmpty())
        assertTrue(backup.documentAlerts.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // Private stub helpers
    // ─────────────────────────────────────────────────────────────

    private suspend fun stubAllDaosEmpty() = stubAllDaosEmptyExcept()

    private suspend fun stubAllDaosEmptyExcept(
        walletTransactions: List<WalletTransactionEntity> = emptyList(),
        vaultEntries: List<VaultEntity>                  = emptyList(),
        pantryItems: List<PantryEntity>                  = emptyList(),
        inventoryEvents: List<InventoryEventEntity>      = emptyList(),
        merchantRules: List<MerchantRuleEntity>          = emptyList(),
        categoryThresholds: List<CategoryThresholdEntity>= emptyList(),
        familyMembers: List<FamilyMemberEntity>          = emptyList(),
        documents: List<DocumentEntity>                  = emptyList(),
        documentAlerts: List<DocumentAlertEntity>        = emptyList()
    ) {
        whenever(walletTransactionDao.getAllTransactions()).thenReturn(walletTransactions)
        whenever(vaultDao.getAllEntriesList()).thenReturn(vaultEntries)
        whenever(pantryDao.getAllPantry()).thenReturn(pantryItems)
        whenever(inventoryEventDao.getAllEvents()).thenReturn(inventoryEvents)
        whenever(merchantRuleDao.getAllRules()).thenReturn(merchantRules)
        whenever(categoryThresholdDao.getAllThresholds()).thenReturn(categoryThresholds)
        whenever(familyMemberDao.getAllMembersList()).thenReturn(familyMembers)
        whenever(documentDao.getAllDocumentsList()).thenReturn(documents)
        whenever(documentAlertDao.getAllAlertsList()).thenReturn(documentAlerts)
    }
}
