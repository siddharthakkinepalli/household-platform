package com.household.app.domain.services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.domain.models.HouseholdBackup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.time.LocalDate

/**
 * Unit tests for [HouseholdImportService].
 *
 * Strategy: mock [Context] + [ContentResolver] so that [Uri] resolution returns a
 * [ByteArrayInputStream] wrapping a hand-crafted JSON string.  All DAO calls are
 * also mocked, so no Room or Android runtime is required.
 */
class HouseholdImportServiceTest {

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

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var uri: Uri

    private lateinit var importService: HouseholdImportService

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
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

        context         = mock()
        contentResolver = mock()
        uri             = mock()
        whenever(context.contentResolver).thenReturn(contentResolver)

        importService = HouseholdImportService(db)
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun sampleTx(id: Int) = WalletTransactionEntity(
        id          = id,
        title       = "REWE Markt",
        category    = "Groceries",
        amount      = -42.0,
        date        = LocalDate.of(2026, 1, 15),
        paymentType = "card"
    )

    /** Serialises [backup] and wires [contentResolver] to return it as an InputStream. */
    private fun givenBackupStream(backup: HouseholdBackup) {
        val json   = gson.toJson(backup)
        val stream = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        whenever(contentResolver.openInputStream(uri)).thenReturn(stream)
    }

    /** Wires [contentResolver] to return a raw string directly. */
    private fun givenRawJsonStream(json: String) {
        val stream = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        whenever(contentResolver.openInputStream(uri)).thenReturn(stream)
    }

    // ─────────────────────────────────────────────────────────────
    // import — parses known JSON and verifies insert counts
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `import returns success with correct total import count`() = runTest {
        val backup = HouseholdBackup(
            walletTransactions = listOf(sampleTx(1), sampleTx(2))
        )
        givenBackupStream(backup)

        val result = importService.import(context, uri)

        assertTrue(result.success)
        // 2 wallet transactions counted.
        assertEquals(2, result.imported)
    }

    @Test
    fun `import inserts wallet transactions via DAO`() = runTest {
        val txList = listOf(sampleTx(10), sampleTx(11), sampleTx(12))
        val backup = HouseholdBackup(walletTransactions = txList)
        givenBackupStream(backup)

        importService.import(context, uri)

        verify(walletTransactionDao).insertTransactionsIgnore(txList)
    }

    @Test
    fun `import counts all entity types correctly`() = runTest {
        val backup = HouseholdBackup(
            walletTransactions = listOf(sampleTx(1)),
            merchantRules = listOf(
                com.household.app.data.entities.MerchantRuleEntity(
                    merchantPattern  = "rewe",
                    targetCategoryId = "Groceries"
                )
            )
        )
        givenBackupStream(backup)

        val result = importService.import(context, uri)

        assertTrue(result.success)
        // 1 wallet tx + 1 rule = 2
        assertEquals(2, result.imported)
    }

    @Test
    fun `import returns success message for version 1 backup`() = runTest {
        val backup = HouseholdBackup(version = 1, walletTransactions = listOf(sampleTx(5)))
        givenBackupStream(backup)

        val result = importService.import(context, uri)

        assertTrue(result.success)
        assertEquals("Import complete", result.message)
    }

    @Test
    fun `import warns but succeeds for newer backup version`() = runTest {
        val backup = HouseholdBackup(version = 99, walletTransactions = listOf(sampleTx(7)))
        givenBackupStream(backup)

        val result = importService.import(context, uri)

        assertTrue(result.success)
        assertTrue(
            "Expected newer-format warning in message",
            result.message.contains("newer format")
        )
    }

    // ─────────────────────────────────────────────────────────────
    // import — duplicate handling (IGNORE conflict strategy)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `import does not crash on re-import of the same backup`() = runTest {
        val backup = HouseholdBackup(walletTransactions = listOf(sampleTx(1), sampleTx(2)))

        // First import.
        givenBackupStream(backup)
        val first = importService.import(context, uri)
        assertTrue(first.success)

        // Re-import the identical data — DAO uses IGNORE conflict, should not throw.
        givenBackupStream(backup)
        val second = importService.import(context, uri)
        assertTrue(second.success)
    }

    @Test
    fun `import calls insertTransactionsIgnore allowing silent duplicates`() = runTest {
        val txList = listOf(sampleTx(1))
        val backup = HouseholdBackup(walletTransactions = txList)

        // First import.
        givenBackupStream(backup)
        importService.import(context, uri)

        // Second import of the same list — DAO is called again; Room's IGNORE strategy
        // handles duplicates at the DB level without throwing.
        givenBackupStream(backup)
        importService.import(context, uri)

        // Verify insertTransactionsIgnore (IGNORE conflict) was called twice without error.
        verify(walletTransactionDao, org.mockito.kotlin.times(2)).insertTransactionsIgnore(txList)
    }

    // ─────────────────────────────────────────────────────────────
    // import — malformed JSON returns error ImportResult, no crash
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `import returns failure result for malformed JSON`() = runTest {
        givenRawJsonStream("{ this is not valid json !!!")

        val result = importService.import(context, uri)

        assertFalse(result.success)
        assertTrue(
            "Error message should mention 'Import failed'",
            result.message.startsWith("Import failed")
        )
        assertEquals(0, result.imported)
    }

    @Test
    fun `import returns failure result for empty string`() = runTest {
        givenRawJsonStream("")

        val result = importService.import(context, uri)

        assertFalse(result.success)
        assertEquals(0, result.imported)
    }

    @Test
    fun `import returns failure result for JSON array instead of object`() = runTest {
        givenRawJsonStream("[1, 2, 3]")

        val result = importService.import(context, uri)

        // Gson may return null when types don't match; ImportService must handle that.
        assertFalse(result.success)
        assertEquals(0, result.imported)
    }

    @Test
    fun `import returns failure result for null stream`() = runTest {
        whenever(contentResolver.openInputStream(uri)).thenReturn(null)

        val result = importService.import(context, uri)

        assertFalse(result.success)
        assertTrue(result.message.contains("Could not open file"))
        assertEquals(0, result.imported)
    }

    // ─────────────────────────────────────────────────────────────
    // import — empty entity lists are not inserted (no DAO calls)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `import does not call DAO for empty entity lists`() = runTest {
        // Backup with no data at all.
        val backup = HouseholdBackup()
        givenBackupStream(backup)

        importService.import(context, uri)

        verify(walletTransactionDao, never()).insertTransactionsIgnore(any())
        verify(vaultDao,             never()).insertEntries(any())
        verify(pantryDao,            never()).insertItemsIgnore(any())
        verify(inventoryEventDao,    never()).insertEvents(any())
        verify(merchantRuleDao,      never()).insertRulesIgnore(any())
        verify(categoryThresholdDao, never()).insertThresholdsIgnore(any())
        verify(familyMemberDao,      never()).insertMembers(any())
        verify(documentDao,          never()).insertDocuments(any())
        verify(documentAlertDao,     never()).insertAlertsIgnore(any())
    }

    // ─────────────────────────────────────────────────────────────
    // ImportResult data class contract
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `ImportResult success flag and message are accessible`() {
        val ok  = ImportResult(success = true,  message = "Import complete", imported = 5)
        val err = ImportResult(success = false, message = "Import failed: oops", imported = 0)

        assertTrue(ok.success)
        assertEquals(5, ok.imported)

        assertFalse(err.success)
        assertEquals(0, err.imported)
        assertTrue(err.message.contains("oops"))
    }
}
