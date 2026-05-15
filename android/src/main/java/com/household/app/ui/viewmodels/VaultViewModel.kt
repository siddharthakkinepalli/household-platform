package com.household.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.Text
import com.household.app.data.AppDatabase
import com.household.app.data.entities.VaultEntity
import com.household.app.data.refiner.toVisionTextPayload
import com.household.app.data.refiner.WeightedReceiptRefiner
import com.household.app.data.repository.ExpenseRepositoryImpl
import com.household.app.data.repository.VaultRepositoryImpl
import com.household.app.data.service.FileStorageService
import com.household.app.data.repository.PantryRepositoryImpl
import com.household.app.data.service.ReceiptResolutionService
import com.household.app.domain.models.Expense
import com.household.app.domain.models.PantryItem
import com.household.app.domain.models.PantryCategory
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.vault.VisionTextPayload
import com.household.app.domain.services.ReceiptItemParser
import com.household.app.domain.usecases.GetPaperclipCandidatesUseCase
import com.household.app.domain.usecases.LinkReceiptToExpenseUseCase
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.household.app.vault.DriveDataStore
import com.household.app.vault.DriveSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface VaultUiState {
    data object Idle    : VaultUiState
    data object Loading : VaultUiState
    data class ConfirmScan(
        val refinedScan: RefinedScan,
        val candidates: List<Expense>
    ) : VaultUiState
    data class ScanSaved(val vaultId: Long) : VaultUiState
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val db by lazy { AppDatabase.getInstance(appContext) }
    private val expenseRepository by lazy { ExpenseRepositoryImpl(db.walletTransactionDao()) }
    private val paperclipUseCase by lazy { GetPaperclipCandidatesUseCase(expenseRepository) }
    private val vaultRepository by lazy {
        VaultRepositoryImpl(
            vaultDao = db.vaultDao(),
            pantryDao = db.pantryDao(),
            storageService = FileStorageService(appContext),
            refiner = WeightedReceiptRefiner()
        )
    }
    private val linkReceiptUseCase by lazy {
        LinkReceiptToExpenseUseCase(
            vaultRepository = vaultRepository,
            expenseRepository = expenseRepository
        )
    }
    private val receiptRefiner = WeightedReceiptRefiner()
    private val receiptResolutionService by lazy { ReceiptResolutionService(appContext) }
    private var pendingVisionText: VisionTextPayload? = null
    private var pendingImagePath: String = ""

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Idle)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _vaultEntries = MutableStateFlow<List<VaultEntity>>(emptyList())
    val vaultEntries: StateFlow<List<VaultEntity>> = _vaultEntries.asStateFlow()

    init {
        viewModelScope.launch {
            vaultRepository.getVaultEntries().collect { entries ->
                _vaultEntries.value = entries
            }
        }
    }

    fun processScanResult(visionText: Text) {
        processScanResult(visionText.toVisionTextPayload())
    }

    fun processScanResult(visionText: VisionTextPayload, imagePath: String = "") {
        viewModelScope.launch {
            _uiState.value = VaultUiState.Loading
            pendingVisionText = visionText
            pendingImagePath = imagePath

            val refinedScan = withContext(Dispatchers.Default) {
                receiptRefiner.refine(visionText)
            }

            val amount = refinedScan.amount.value ?: 0.0
            val date = refinedScan.date.value ?: LocalDate.now()

            val matches = withContext(Dispatchers.IO) {
                paperclipUseCase.execute(amount = amount, date = date)
            }

            _uiState.value = VaultUiState.ConfirmScan(refinedScan, matches)
        }
    }

    fun savePendingScanToVault(confirmedScan: RefinedScan? = null) {
        val payload = pendingVisionText ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val id = vaultRepository.saveScanEntry(
                imageUri = pendingImagePath,
                payload = payload,
                refinedOverride = confirmedScan
            )

            // Memory-First Resolution: Pass lines through the learning pipeline
            val lines = payload.fullText.lines().filter { it.isNotBlank() }
            val storeName = extractStoreName(payload.fullText) // Try to detect store

            val resolvedItems = receiptResolutionService.resolveReceiptLines(lines, id, storeName)

            if (resolvedItems.isNotEmpty()) {
                // Map resolved items to pantry (use canonical names from resolution)
                val pantryItems = resolvedItems.map { resolved ->
                    PantryItem(
                        name = resolved.name,
                        category = PantryCategory.OTHER,  // Will be enhanced with ML
                        quantity = 1f,
                        vaultId = id
                    )
                }
                val pantryRepo = PantryRepositoryImpl(AppDatabase.getInstance(appContext).pantryDao())
                pantryRepo.stageParsedItems(pantryItems)
            }

            if (DriveDataStore.isDriveEnabled(appContext)) {
                enqueueDriveSync(id)
            }
            clearPending()
            _uiState.value = VaultUiState.ScanSaved(id)
        }
    }

    /**
     * Extract store name from OCR text for context-aware matching
     */
    private fun extractStoreName(text: String): String {
        val upper = text.uppercase()
        return when {
            upper.contains("ALDI SÜD") || upper.contains("ALDI SUED") -> "ALDI"
            upper.contains("ALDI NORD") -> "ALDI"
            upper.contains("LIDL") -> "LIDL"
            upper.contains("REWE") -> "REWE"
            upper.contains("EDEKA") -> "EDEKA"
            upper.contains("KAUFLAND") -> "KAUFLAND"
            upper.contains("PENNY") -> "PENNY"
            upper.contains("NETTO") -> "NETTO"
            else -> "UNKNOWN"
        }
    }

    fun linkPendingScanToExpense(expense: Expense, confirmedScan: RefinedScan? = null) {
        val payload = pendingVisionText ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val vaultId = vaultRepository.saveScanEntry(
                imageUri = pendingImagePath,
                payload = payload,
                refinedOverride = confirmedScan
            )
            linkReceiptUseCase.execute(vaultId = vaultId, expenseId = expense.id)
            if (DriveDataStore.isDriveEnabled(appContext)) {
                enqueueDriveSync(vaultId)
            }
            _uiState.value = VaultUiState.Idle
            clearPending()
        }
    }

    fun dismissConfirmation() {
        _uiState.value = VaultUiState.Idle
        clearPending()
    }

    fun acknowledgeScanned() {
        _uiState.value = VaultUiState.Idle
    }

    private fun clearPending() {
        pendingVisionText = null
        pendingImagePath = ""
    }

    private fun enqueueDriveSync(vaultId: Long) {
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setInputData(workDataOf("vault_id" to vaultId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
    }
}
