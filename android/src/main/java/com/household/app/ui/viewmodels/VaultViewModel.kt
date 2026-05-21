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
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.vault.DriveDataStore
import com.household.app.vault.DriveSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _allEntries = MutableStateFlow<List<VaultEntity>>(emptyList())
    private val _selectedCategory = MutableStateFlow<VaultCategory?>(null)
    val selectedCategory: StateFlow<VaultCategory?> = _selectedCategory.asStateFlow()

    val vaultEntries: StateFlow<List<VaultEntity>> = combine(_allEntries, _selectedCategory) { entries, cat ->
        if (cat == null) entries else entries.filter { it.category == cat.name }
    }.let { flow ->
        val state = MutableStateFlow<List<VaultEntity>>(emptyList())
        viewModelScope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    init {
        viewModelScope.launch {
            vaultRepository.getVaultEntries().collect { _allEntries.value = it }
        }
    }

    fun selectCategory(category: VaultCategory?) {
        _selectedCategory.value = category
    }

    fun saveDocument(uri: android.net.Uri, mimeType: String, category: VaultCategory, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = vaultRepository.saveDocument(uri, mimeType, category, title)
            if (DriveDataStore.isDriveEnabled(appContext)) enqueueDriveSync(id)
            _uiState.value = VaultUiState.Idle
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

            val storeName = extractStoreName(payload.fullText)

            // Log raw OCR lines so we can see exactly what the camera captured
            val rawLines = payload.fullText.lines().filter { it.isNotBlank() }
            android.util.Log.d("ItemParser", "=== RAW OCR (${rawLines.size} lines) ===")
            rawLines.forEachIndexed { i, l -> android.util.Log.d("ItemParser", "  [$i] '$l'") }

            // Filter to actual grocery items only before passing to the resolver
            val parsedItems = ReceiptItemParser.parse(payload.fullText)
            android.util.Log.d("ItemParser", "=== FILTERED items (${parsedItems.size}) ===")
            parsedItems.forEachIndexed { i, item ->
                android.util.Log.d("ItemParser", "  [$i] '${item.name}' qty=${item.quantity} cat=${item.category}")
            }

            val resolvedItems = receiptResolutionService.resolveReceiptLines(
                parsedItems.map { it.name }, id, storeName
            )

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

    fun deleteEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultRepository.deleteEntry(id)
        }
    }

    fun deleteEntries(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) { vaultRepository.deleteEntries(ids) }
    }

    fun moveEntries(ids: List<Long>, category: VaultCategory) {
        viewModelScope.launch(Dispatchers.IO) { vaultRepository.moveEntries(ids, category) }
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
