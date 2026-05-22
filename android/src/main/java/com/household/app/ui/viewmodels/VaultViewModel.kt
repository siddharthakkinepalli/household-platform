package com.household.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.Text
import com.household.app.data.AppDatabase
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.PantryEntity
import com.household.app.data.entities.VaultEntity
import com.household.app.data.refiner.toVisionTextPayload
import com.household.app.data.refiner.WeightedReceiptRefiner
import com.household.app.data.repository.ExpenseRepositoryImpl
import com.household.app.data.repository.PantryRepositoryImpl
import com.household.app.data.repository.VaultRepositoryImpl
import com.household.app.data.service.FileStorageService
import com.household.app.data.service.ReceiptResolutionService
import com.household.app.domain.models.Expense
import com.household.app.domain.models.PantryCategory
import com.household.app.domain.models.PantryItem
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.vault.VaultBrowseState
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.domain.models.vault.VaultFolderPath
import com.household.app.domain.models.vault.VaultFolderRow
import com.household.app.domain.models.vault.VaultFolderTree
import com.household.app.domain.models.vault.VaultSubFolder
import com.household.app.domain.models.vault.VisionTextPayload
import com.household.app.domain.services.ReceiptItemParser
import com.household.app.domain.usecases.GetPaperclipCandidatesUseCase
import com.household.app.domain.usecases.LinkReceiptToExpenseUseCase
import com.household.app.vault.DriveDataStore
import com.household.app.vault.DriveSyncWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface VaultUiState {
    data object Idle : VaultUiState
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
    private val _browseState = MutableStateFlow<VaultBrowseState>(VaultBrowseState.Root)

    val browseState: StateFlow<VaultBrowseState> = _browseState.asStateFlow()

    val familyMembers: StateFlow<List<FamilyMemberEntity>> = db.familyMemberDao()
        .getAllMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vaultEntries: StateFlow<List<VaultEntity>> = combine(
        _allEntries,
        _browseState
    ) { entries, browse ->
        VaultFolderTree.filterEntries(entries, browse)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folderRows: StateFlow<List<VaultFolderRow>> = combine(
        _allEntries,
        _browseState,
        familyMembers
    ) { entries, browse, members ->
        VaultFolderTree.rowsAt(browse, entries, members)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showFolderBrowser: StateFlow<Boolean> = _browseState.map { browse ->
        when (browse) {
            is VaultBrowseState.Root -> true
            is VaultBrowseState.Category ->
                VaultFolderTree.categoryUsesMemberLevel(browse.category)
            is VaultBrowseState.MemberScope -> true
            is VaultBrowseState.Folder -> false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        viewModelScope.launch {
            vaultRepository.getVaultEntries().collect { _allEntries.value = it }
        }
    }

    fun navigateTo(target: VaultBrowseState) {
        _browseState.value = target
    }

    /** Opens Identity → member folder browser (from Family detail). */
    fun openMemberIdentityFolder(memberId: Long, memberName: String) {
        _browseState.value = VaultBrowseState.MemberScope(
            category = VaultCategory.IDENTITY,
            ownerMemberId = memberId,
            memberLabel = memberName
        )
    }

    fun navigateBack(): Boolean {
        val current = _browseState.value
        _browseState.value = when (current) {
            is VaultBrowseState.Root -> return false
            is VaultBrowseState.Category -> VaultBrowseState.Root
            is VaultBrowseState.MemberScope -> VaultBrowseState.Category(current.category)
            is VaultBrowseState.Folder -> VaultBrowseState.MemberScope(
                current.path.category,
                current.path.ownerMemberId,
                memberLabelFor(current.path.ownerMemberId)
            )
        }
        return true
    }

    fun defaultSaveFolder(): VaultFolderPath {
        val browse = _browseState.value
        return when (browse) {
            is VaultBrowseState.Folder -> browse.path
            is VaultBrowseState.MemberScope -> VaultFolderPath(
                browse.category,
                browse.ownerMemberId,
                VaultSubFolder.UNFILED.id
            )
            is VaultBrowseState.Category -> VaultFolderPath(
                browse.category,
                null,
                VaultSubFolder.UNFILED.id
            )
            is VaultBrowseState.Root -> VaultFolderPath(VaultCategory.OTHER)
        }
    }

    private fun memberLabelFor(ownerId: Long?): String =
        ownerId?.let { id -> familyMembers.value.firstOrNull { it.id == id }?.name }
            ?: VaultFolderTree.HOUSEHOLD_LABEL

    fun saveDocument(uri: android.net.Uri, mimeType: String, folder: VaultFolderPath, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = vaultRepository.saveDocument(uri, mimeType, folder, title)
            if (DriveDataStore.isDriveEnabled(appContext)) enqueueDriveSync(id)
            _uiState.value = VaultUiState.Idle
            if (VaultFolderTree.categoryUsesMemberLevel(folder.category)) {
                navigateTo(VaultBrowseState.Folder(folder))
            } else {
                navigateTo(VaultBrowseState.Category(folder.category))
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

            val storeName = extractStoreName(payload.fullText)
            val parsedItems = ReceiptItemParser.parse(payload.fullText)
            val resolvedItems = receiptResolutionService.resolveReceiptLines(
                parsedItems.map { it.name }, id, storeName
            )

            if (resolvedItems.isNotEmpty()) {
                val pantryItems = resolvedItems.map { resolved ->
                    PantryItem(
                        name = resolved.name,
                        category = PantryCategory.OTHER,
                        quantity = 1f,
                        vaultId = id
                    )
                }
                val pantryRepo = PantryRepositoryImpl(db.pantryDao())
                pantryRepo.stageParsedItems(pantryItems)
            }

            if (DriveDataStore.isDriveEnabled(appContext)) {
                enqueueDriveSync(id)
            }
            clearPending()
            _uiState.value = VaultUiState.ScanSaved(id)
        }
    }

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

    fun moveEntriesToFolder(ids: List<Long>, folder: VaultFolderPath) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultRepository.moveEntriesToFolder(ids, folder)
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
