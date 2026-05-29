package com.household.app.domain.models.vault

import com.household.app.data.entities.VaultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultFolderTreeTest {

    @Test
    fun `test categoryUsesMemberLevel`() {
        assertTrue(VaultFolderTree.categoryUsesMemberLevel(VaultCategory.IDENTITY))
        assertTrue(VaultFolderTree.categoryUsesMemberLevel(VaultCategory.CONTRACT))
        assertFalse(VaultFolderTree.categoryUsesMemberLevel(VaultCategory.RECEIPT))
    }

    @Test
    fun `test filterEntries for Root`() {
        val entries = listOf(
            VaultEntity(id = 1, imagePath = "", merchantName = null, totalAmount = null, dateEpoch = 0, rawOcrContent = "", category = "RECEIPT")
        )
        val filtered = VaultFolderTree.filterEntries(entries, VaultBrowseState.Root)
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `test filterEntries for RECEIPT category`() {
        val receipt = VaultEntity(id = 1, imagePath = "", merchantName = null, totalAmount = null, dateEpoch = 0, rawOcrContent = "", category = "RECEIPT")
        val contract = VaultEntity(id = 2, imagePath = "", merchantName = null, totalAmount = null, dateEpoch = 0, rawOcrContent = "", category = "CONTRACT")
        val entries = listOf(receipt, contract)

        val filtered = VaultFolderTree.filterEntries(entries, VaultBrowseState.Category(VaultCategory.RECEIPT))
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
    }

    @Test
    fun `test filterEntries for IDENTITY category (uses member level)`() {
        val identity = VaultEntity(id = 1, imagePath = "", merchantName = null, totalAmount = null, dateEpoch = 0, rawOcrContent = "", category = "IDENTITY")
        val entries = listOf(identity)

        // IDENTITY uses member level, so filtering at Category level should return empty
        val filtered = VaultFolderTree.filterEntries(entries, VaultBrowseState.Category(VaultCategory.IDENTITY))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `test rowsAt Root`() {
        val entries = listOf(
            VaultEntity(id = 1, imagePath = "", merchantName = null, totalAmount = null, dateEpoch = 0, rawOcrContent = "", category = "RECEIPT"),
            VaultEntity(id = 2, imagePath = "", merchantName = null, totalAmount = null, dateEpoch = 0, rawOcrContent = "", category = "RECEIPT")
        )
        val rows = VaultFolderTree.rowsAt(VaultBrowseState.Root, entries, emptyList())

        val receiptRow = rows.find { it.target is VaultBrowseState.Category && it.target.category == VaultCategory.RECEIPT }
        assertEquals(2, receiptRow?.itemCount)
    }

    @Test
    fun `test breadcrumb for Root`() {
        val breadcrumb = VaultFolderTree.breadcrumb(VaultBrowseState.Root, emptyList())
        assertEquals(1, breadcrumb.size)
        assertEquals("Documents", breadcrumb[0].first)
    }

    private fun assertFalse(condition: Boolean) = org.junit.Assert.assertFalse(condition)
}
