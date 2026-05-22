package com.household.app.domain.repositories

import android.net.Uri
import com.household.app.data.entities.VaultEntity
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.domain.models.vault.VaultFolderPath
import com.household.app.domain.models.vault.VisionTextPayload
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun getVaultEntries(): Flow<List<VaultEntity>>
    fun getUnlinkedVaultEntries(): Flow<List<VaultEntity>>
    fun getEntriesByCategory(category: VaultCategory): Flow<List<VaultEntity>>

    suspend fun processAndSaveScan(tempUri: Uri, payload: VisionTextPayload): Long
    suspend fun processNewScan(imageUri: String, payload: VisionTextPayload): RefinedScan
    suspend fun saveScanEntry(
        imageUri: String,
        payload: VisionTextPayload,
        refinedOverride: RefinedScan? = null
    ): Long
    suspend fun saveDocument(uri: Uri, mimeType: String, folder: VaultFolderPath, title: String): Long
    suspend fun linkReceiptToExpense(vaultId: Long, expenseId: Long)
    suspend fun deleteEntry(id: Long)
    suspend fun deleteEntries(ids: List<Long>)
    suspend fun moveEntries(ids: List<Long>, category: VaultCategory)
    suspend fun moveEntriesToFolder(ids: List<Long>, folder: VaultFolderPath)
}
