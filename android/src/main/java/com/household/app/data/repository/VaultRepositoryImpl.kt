package com.household.app.data.repository

import android.net.Uri
import com.household.app.data.dao.VaultDao
import com.household.app.data.entities.VaultEntity
import com.household.app.data.service.FileStorageService
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.vault.VisionTextPayload
import com.household.app.domain.repositories.VaultRepository
import com.household.app.domain.services.ReceiptRefiner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class VaultRepositoryImpl(
    private val vaultDao: VaultDao,
    private val storageService: FileStorageService,
    private val refiner: ReceiptRefiner
) : VaultRepository {

    override fun getVaultEntries(): Flow<List<VaultEntity>> = vaultDao.getAllEntries()

    override fun getUnlinkedVaultEntries(): Flow<List<VaultEntity>> = vaultDao.getUnlinkedEntries()

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
        return vaultDao.insertVaultEntry(entity)
    }

    override suspend fun linkReceiptToExpense(vaultId: Long, expenseId: Long) {
        vaultDao.linkToExpense(vaultId, expenseId.toLong())
    }
}
