package com.household.app.data.repository

import android.content.Context
import android.net.Uri
import com.household.app.data.dao.PantryDao
import com.household.app.data.dao.VaultDao
import com.household.app.data.entities.PantryEntity
import com.household.app.data.entities.VaultEntity
import com.household.app.data.service.FileStorageService
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.domain.models.vault.VaultFolderPath
import com.household.app.domain.models.vault.VaultSubFolder
import com.household.app.domain.models.vault.VisionTextPayload
import com.household.app.domain.repositories.VaultRepository
import com.household.app.domain.services.ReceiptItemParser
import com.household.app.domain.services.ReceiptRefiner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class VaultRepositoryImpl(
    private val vaultDao: VaultDao,
    private val pantryDao: PantryDao,
    private val storageService: FileStorageService,
    private val refiner: ReceiptRefiner,
    private val context: Context
) : VaultRepository {

    override fun getVaultEntries(): Flow<List<VaultEntity>> = vaultDao.getAllEntries()

    override fun getUnlinkedVaultEntries(): Flow<List<VaultEntity>> = vaultDao.getUnlinkedEntries()

    override fun getEntriesByCategory(category: VaultCategory): Flow<List<VaultEntity>> =
        vaultDao.getEntriesByCategory(category.name)

    override suspend fun processAndSaveScan(tempUri: Uri, payload: VisionTextPayload): Long {
        val permanentPath = storageService.saveReceiptImage(tempUri)
        val refined = refiner.refine(payload)

        val entity = VaultEntity(
            imagePath = permanentPath,
            merchantName = refined.merchant.value,
            totalAmount = refined.amount.value,
            dateEpoch = refined.date.value?.toEpochDay() ?: LocalDate.now().toEpochDay(),
            rawOcrContent = payload.fullText,
            isLinkedToExpense = false
        )

        return vaultDao.insertVaultEntry(entity)
    }

    override suspend fun processNewScan(imageUri: String, payload: VisionTextPayload): RefinedScan {
        val refined = refiner.refine(payload)
        saveScanEntry(imageUri = imageUri, payload = payload)
        return refined
    }

    override suspend fun saveScanEntry(
        imageUri: String,
        payload: VisionTextPayload,
        refinedOverride: RefinedScan?
    ): Long {
        val refined = refinedOverride ?: refiner.refine(payload)
        val entity = VaultEntity(
            imagePath = imageUri,
            merchantName = refined.merchant.value,
            totalAmount = refined.amount.value,
            dateEpoch = refined.date.value?.toEpochDay() ?: LocalDate.now().toEpochDay(),
            rawOcrContent = payload.fullText,
            isLinkedToExpense = false
        )
        val vaultId = vaultDao.insertVaultEntry(entity)
        stageReceiptItems(vaultId, payload.fullText)
        return vaultId
    }

    override suspend fun saveDocument(
        uri: Uri,
        mimeType: String,
        folder: VaultFolderPath,
        title: String
    ): Long {
        // SHA-256 dedup: compute hash first, return existing entry id if duplicate
        val fileHash = computeFileHash(uri)
        if (fileHash != null) {
            val existing = vaultDao.getByFileHash(fileHash)
            if (existing != null) return existing.id  // duplicate — return existing entry
        }

        val path = storageService.saveDocument(uri, mimeType)
        val entity = VaultEntity(
            imagePath = path,
            merchantName = null,
            totalAmount = null,
            dateEpoch = LocalDate.now().toEpochDay(),
            rawOcrContent = "",
            category = folder.category.name,
            ownerMemberId = folder.ownerMemberId,
            subFolder = VaultSubFolder.normalizeId(folder.subFolder),
            documentTitle = title.trim().ifBlank { null },
            mimeType = mimeType,
            fileHash = fileHash
        )
        return vaultDao.insertVaultEntry(entity)
    }

    private fun computeFileHash(uri: Uri): String? = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    override suspend fun linkReceiptToExpense(vaultId: Long, expenseId: Long) {
        vaultDao.linkToExpense(vaultId, expenseId.toLong())
    }

    override suspend fun deleteEntry(id: Long) {
        vaultDao.getEntryById(id)?.let { vaultDao.deleteEntry(it) }
    }

    override suspend fun deleteEntries(ids: List<Long>) {
        vaultDao.deleteEntries(ids)
    }

    override suspend fun moveEntries(ids: List<Long>, category: VaultCategory) {
        vaultDao.moveEntries(ids, category.name)
    }

    override suspend fun moveEntriesToFolder(ids: List<Long>, folder: VaultFolderPath) {
        vaultDao.moveEntriesToFolder(
            ids = ids,
            category = folder.category.name,
            ownerMemberId = folder.ownerMemberId,
            subFolder = VaultSubFolder.normalizeId(folder.subFolder)
        )
    }

    private suspend fun stageReceiptItems(vaultId: Long, receiptText: String) {
        val parsed = ReceiptItemParser.parse(receiptText)
        if (parsed.isEmpty()) return
        pantryDao.insertItems(
            parsed.map { item ->
                PantryEntity(
                    name = item.name,
                    category = item.category.name,
                    quantity = item.quantity,
                    vaultId = vaultId,
                    isConfirmed = false
                )
            }
        )
    }
}
