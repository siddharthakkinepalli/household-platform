package com.household.app.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.household.app.data.AppDatabase
import com.household.app.data.DashboardPrefs
import com.household.app.data.config.CsvParserService
import com.household.app.data.config.ImportErrorType
import com.household.app.data.config.ImportParseResult
import com.household.app.data.config.ImportSummary
import com.household.app.data.TransactionCategorizer
import com.household.app.data.config.RuleEngineService
import com.household.app.data.service.RecurringDetectionService
import com.household.app.domain.services.RetroactiveCategorizer
import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.ImportAuditEntity
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.data.entities.RecurringBillEntity
import com.household.app.data.entities.SalarySourceEntity
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.domain.utils.FiscalDateUtils
import com.household.app.vault.DriveAuthManager
import com.household.app.vault.DriveDataStore
import com.household.app.vault.workers.PipelineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class PipelineStatus {
    object Idle : PipelineStatus()
    object Running : PipelineStatus()
    data class Done(val recurringFound: Int) : PipelineStatus()
    data class Error(val message: String) : PipelineStatus()
}

data class CategoryThreshold(
    val id: String,
    val name: String,
    val limit: Float
)

data class MerchantRule(
    val id: String,
    val pattern: String,
    val targetCategoryId: String,
    val isEnabled: Boolean,
    val isExclusion: Boolean,
    val priority: Int,
    val collisionCount: Int = 0
)

data class SalaryCandidate(
    val transactionId: Int,
    val title: String,
    val amount: Double,
    val date: String
)

data class ImportAuditRecord(
    val id: Long,
    val fileName: String,
    val detectedBank: String,
    val importedAtLabel: String,
    val cycleLabel: String,
    val salaryAnchorDate: Int
)

sealed class ConfigIntent {
    data class FileSelected(val uri: Uri) : ConfigIntent()
    object ConfirmImport : ConfigIntent()
    object CancelImport : ConfigIntent()
    data class UpdateSalaryAnchor(val value: Int) : ConfigIntent()
    data class EditThreshold(val categoryId: String) : ConfigIntent()
    data class UpdateDraftLimit(val value: Float) : ConfigIntent()
    object SaveThreshold : ConfigIntent()
    object CancelThresholdEdit : ConfigIntent()
    data class ToggleRule(val ruleId: String, val enabled: Boolean) : ConfigIntent()
    data class RequestUndo(val actionId: String) : ConfigIntent()
    object ToggleCloudSync : ConfigIntent()
    data class HandleSignInResult(val data: Intent?) : ConfigIntent()
    object SignOutGoogle : ConfigIntent()
    data class ConfirmSalary(val transactionId: Int) : ConfigIntent()
    object SkipSalary : ConfigIntent()
    data class AssignCategory(val transactionId: Int, val category: String) : ConfigIntent()
    data class ConfirmReview(val makeRules: List<Pair<String, String>>) : ConfigIntent() // pattern→category pairs
    object ClearAllData : ConfigIntent()
}

sealed class ImportWorkflow {
    object Idle : ImportWorkflow()
    data class Hashing(val fileName: String) : ImportWorkflow()
    data class Parsing(val progress: Float, val stage: String = "") : ImportWorkflow()
    data class SalaryConfirmation(
        val candidates: List<SalaryCandidate>,
        val pendingSummary: ImportSummary
    ) : ImportWorkflow()
    data class ReviewUncategorized(
        val pending: List<com.household.app.data.config.ParsedTransactionCandidate>,
        val allAssignments: Map<Int, String>,   // txId → category (starts empty)
        val fullSummary: ImportSummary
    ) : ImportWorkflow()
    data class NeedsReview(val summary: ImportSummary) : ImportWorkflow()
    object DuplicateDetected : ImportWorkflow()
    object Committing : ImportWorkflow()
    data class Success(val importedCount: Int) : ImportWorkflow()
    data class Failed(val error: ImportErrorType) : ImportWorkflow()
}

sealed class ConfigError {
    data class Import(val error: ImportErrorType) : ConfigError()
    data class Persistence(val message: String) : ConfigError()
    data class Validation(val message: String) : ConfigError()
}

sealed class UndoableAction(open val actionId: String) {
    data class ThresholdChange(
        override val actionId: String,
        val categoryId: String,
        val previousValue: Float
    ) : UndoableAction(actionId)

    data class RuleToggle(
        override val actionId: String,
        val ruleId: String,
        val previousEnabled: Boolean
    ) : UndoableAction(actionId)
}

data class ConfigUiState(
    val salaryAnchor: Int = 25,
    val currentFiscalLabel: String = "",
    val persistedCategories: List<CategoryThreshold> = emptyList(),
    val importWorkflow: ImportWorkflow = ImportWorkflow.Idle,
    val activeEditorId: String? = null,
    val draftLimitValue: Float? = null,
    val pendingRules: List<MerchantRule> = emptyList(),
    val recentAudits: List<ImportAuditRecord> = emptyList(),
    val undoStack: List<UndoableAction> = emptyList(),
    val typedErrors: List<ConfigError> = emptyList(),
    val isCloudSyncEnabled: Boolean = false,
    val connectedEmail: String? = null,
    val signInError: String? = null
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy { AppDatabase.getInstance(getApplication()) }
    private val parser = CsvParserService()
    private val retroactiveCategorizer by lazy {
        RetroactiveCategorizer(
            merchantRuleDao = db.merchantRuleDao(),
            walletTransactionDao = db.walletTransactionDao()
        )
    }

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    private val _pipelineStatus = MutableStateFlow<PipelineStatus>(PipelineStatus.Idle)
    val pipelineStatus: StateFlow<PipelineStatus> = _pipelineStatus

    fun runPipeline() {
        if (_pipelineStatus.value is PipelineStatus.Running) return
        viewModelScope.launch {
            _pipelineStatus.value = PipelineStatus.Running
            try {
                val anchorDay = withContext(Dispatchers.IO) {
                    DashboardPrefs.getSalaryAnchorDay(getApplication())
                }
                withContext(Dispatchers.IO) {
                    retroactiveCategorizer.recategorizeAll()
                    RecurringDetectionService(
                        walletTransactionDao = db.walletTransactionDao(),
                        recurringBillDao = db.recurringBillDao(),
                        salaryAnchorDay = anchorDay
                    ).detectAndStore()
                }
                val count = withContext(Dispatchers.IO) { db.recurringBillDao().getActiveBills().size }
                _pipelineStatus.value = PipelineStatus.Done(count)
            } catch (e: Exception) {
                _pipelineStatus.value = PipelineStatus.Error(e.message ?: "Pipeline failed")
            }
        }
    }

    init {
        refreshState()
    }

    fun onIntent(intent: ConfigIntent) {
        when (intent) {
            is ConfigIntent.FileSelected -> handleFileSelected(intent.uri)
            ConfigIntent.ConfirmImport -> commitImport()
            ConfigIntent.CancelImport -> _uiState.update { it.copy(importWorkflow = ImportWorkflow.Idle) }
            is ConfigIntent.UpdateSalaryAnchor -> updateSalaryAnchor(intent.value)
            is ConfigIntent.EditThreshold -> beginThresholdEdit(intent.categoryId)
            is ConfigIntent.UpdateDraftLimit -> updateDraftLimit(intent.value)
            ConfigIntent.SaveThreshold -> saveThreshold()
            ConfigIntent.CancelThresholdEdit -> cancelThresholdEdit()
            is ConfigIntent.ToggleRule -> toggleRule(intent.ruleId, intent.enabled)
            is ConfigIntent.RequestUndo -> undo(intent.actionId)
            ConfigIntent.ToggleCloudSync -> toggleCloudSync()
            is ConfigIntent.HandleSignInResult -> handleSignInResult(intent.data)
            ConfigIntent.SignOutGoogle -> signOutGoogle()
            is ConfigIntent.ConfirmSalary -> handleConfirmSalary(intent.transactionId)
            ConfigIntent.SkipSalary -> handleSkipSalary()
            is ConfigIntent.AssignCategory -> handleAssignCategory(intent.transactionId, intent.category)
            is ConfigIntent.ConfirmReview -> handleConfirmReview(intent.makeRules)
            ConfigIntent.ClearAllData -> clearAllData()
        }
    }

    private fun refreshState() {
        viewModelScope.launch {
            val thresholds = withContext(Dispatchers.IO) {
                db.categoryThresholdDao().getAllThresholds()
            }
            val rules = withContext(Dispatchers.IO) {
                db.merchantRuleDao().getAllRules()
            }
            val recentAudits = withContext(Dispatchers.IO) {
                db.importAuditDao().getRecentAudits(limit = 5)
            }
            val salaryAnchor = withContext(Dispatchers.IO) {
                DashboardPrefs.getSalaryAnchorDay(getApplication())
            }
            val collisions = RuleEngineService.findCollisions(rules)
            val currentWorkflow = _uiState.value.importWorkflow

            val ctx = getApplication<Application>()
            val cloudEnabled = DriveDataStore.isDriveEnabled(ctx)
            val connectedEmail = DriveAuthManager(ctx).lastSignedInAccount()?.email

            _uiState.update {
                it.copy(
                    salaryAnchor = salaryAnchor,
                    currentFiscalLabel = FiscalDateUtils.formatRangeLabel(
                        FiscalDateUtils.getFiscalCycleRange(LocalDate.now(), salaryAnchor)
                    ),
                    persistedCategories = mergeThresholds(thresholds),
                    pendingRules = rules.map { rule ->
                        MerchantRule(
                            id = rule.merchantPattern,
                            pattern = rule.merchantPattern,
                            targetCategoryId = rule.targetCategoryId,
                            isEnabled = rule.isEnabled,
                            isExclusion = rule.isExclusion,
                            priority = rule.priority,
                            collisionCount = collisions[rule.merchantPattern]?.size ?: 0
                        )
                    },
                    recentAudits = recentAudits.map(::mapAudit),
                    importWorkflow = currentWorkflow,
                    isCloudSyncEnabled = cloudEnabled,
                    connectedEmail = connectedEmail
                )
            }
        }
    }

    private fun handleFileSelected(uri: Uri) {
        viewModelScope.launch {
            val fileName = queryFileName(uri)
            _uiState.update { it.copy(importWorkflow = ImportWorkflow.Hashing(fileName), typedErrors = emptyList()) }

            val parseResult = withContext(Dispatchers.IO) {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext ImportParseResult.Error(ImportErrorType.EmptyFile)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(importWorkflow = ImportWorkflow.Parsing(0.15f, "Checking for duplicates…")) }
                }
                val fileHash = sha256(bytes)
                val duplicate = db.importAuditDao().getLatestByHash(fileHash)
                if (duplicate != null) {
                    return@withContext null
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(importWorkflow = ImportWorkflow.Parsing(0.35f, "Reading bank data…")) }
                }
                val existingTransactions = db.walletTransactionDao().getAllTransactions()
                val startingId = (existingTransactions.maxOfOrNull { it.id } ?: 0) + 1
                val csvText = bytes.toString(Charsets.UTF_8)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(importWorkflow = ImportWorkflow.Parsing(0.55f, "Detecting columns & bank…")) }
                }
                val result = parser.parse(csvText, fileName, fileHash, startingId)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(importWorkflow = ImportWorkflow.Parsing(0.85f, "Categorizing transactions…")) }
                }
                if (result is ImportParseResult.Success) {
                    ImportParseResult.Success(
                        deduplicateImportedTransactions(result.summary, existingTransactions)
                    )
                } else {
                    result
                }
            }

            when (parseResult) {
                null -> {
                    _uiState.update { it.copy(importWorkflow = ImportWorkflow.DuplicateDetected) }
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000L)
                        _uiState.update { it.copy(importWorkflow = ImportWorkflow.Idle) }
                    }
                }
                is ImportParseResult.Success -> {
                    val summary = parseResult.summary
                    val nextWorkflow = withContext(Dispatchers.IO) {
                        resolveSalaryWorkflow(summary)
                    }
                    _uiState.update { it.copy(importWorkflow = nextWorkflow) }
                }
                is ImportParseResult.Error -> {
                    _uiState.update {
                        it.copy(
                            importWorkflow = ImportWorkflow.Failed(parseResult.error),
                            typedErrors = it.typedErrors + ConfigError.Import(parseResult.error)
                        )
                    }
                }
            }
        }
    }

    private fun advanceFromSalary(summary: ImportSummary): ImportWorkflow {
        val uncategorized = summary.transactions.filter { it.category == "Other" }
        return if (uncategorized.isNotEmpty()) {
            ImportWorkflow.ReviewUncategorized(
                pending = uncategorized,
                allAssignments = emptyMap(),
                fullSummary = summary
            )
        } else {
            ImportWorkflow.NeedsReview(summary)
        }
    }

    private suspend fun resolveSalaryWorkflow(summary: ImportSummary): ImportWorkflow {
        val anchor = _uiState.value.salaryAnchor
        val stored = db.salarySourceDao().getSalarySource()
        val categorizer = TransactionCategorizer()

        // All incoming credit transactions (positive amounts)
        val allCredits = summary.transactions.filter { it.amount > 0.0 }
        if (allCredits.isEmpty()) return advanceFromSalary(summary)

        // Step 1: if a previously confirmed salary source exists, try to auto-match first
        if (stored != null) {
            val pattern = stored.merchantPattern.take(8).lowercase()
            val autoMatch = allCredits.firstOrNull { tx ->
                val titleLow = tx.title.lowercase()
                val titleMatch = pattern.isNotBlank() &&
                    (titleLow.contains(pattern) || pattern.contains(titleLow.take(8)))
                val amountMatch = tx.amount in stored.minAmount..stored.maxAmount
                titleMatch || amountMatch
            }
            if (autoMatch != null) {
                db.salarySourceDao().upsert(
                    stored.copy(
                        lastAmount = autoMatch.amount,
                        minAmount = autoMatch.amount * 0.80,
                        maxAmount = autoMatch.amount * 1.20,
                        confirmedAt = System.currentTimeMillis()
                    )
                )
                return advanceFromSalary(summary)
            }
        }

        // Step 2: score all credit transactions; auto-confirm if a single high-confidence hit exists
        data class ScoredCandidate(val tx: com.household.app.data.config.ParsedTransactionCandidate, val score: Int)
        val scored = allCredits.map { tx ->
            ScoredCandidate(tx, categorizer.salaryConfidenceScore(tx.title, tx.amount))
        }.sortedByDescending { it.score }

        val topCandidate = scored.firstOrNull()
        if (topCandidate != null && topCandidate.score >= 70) {
            // Auto-confirm — high confidence, no user prompt needed
            val tx = topCandidate.tx
            val pattern = tx.title.take(16).lowercase().trim()
            db.salarySourceDao().upsert(
                SalarySourceEntity(
                    merchantPattern = pattern,
                    lastAmount = tx.amount,
                    minAmount = tx.amount * 0.80,
                    maxAmount = tx.amount * 1.20,
                    confirmedAt = System.currentTimeMillis()
                )
            )
            return advanceFromSalary(summary)
        }

        // Step 3: gather candidates near anchor day with score >= 20 (medium confidence)
        val anchorCandidates = allCredits.filter { tx ->
            val dayOfMonth = tx.date.dayOfMonth
            val dist = minOf(
                abs(dayOfMonth - anchor),
                abs(dayOfMonth - anchor + 31),
                abs(dayOfMonth - anchor - 31)
            )
            dist <= 10 && tx.amount >= 300.0
        }.sortedByDescending { it.amount }

        // Merge anchor candidates and scored candidates, dedup, take top 5
        val combined = (anchorCandidates + scored.filter { it.score >= 20 }.map { it.tx })
            .distinctBy { it.id.toString() }
            .take(5)

        if (combined.isEmpty()) return advanceFromSalary(summary)

        val salaryCandidates = combined.map { tx ->
            SalaryCandidate(
                transactionId = tx.id,
                title = tx.title,
                amount = tx.amount,
                date = tx.date.toString()
            )
        }
        return ImportWorkflow.SalaryConfirmation(salaryCandidates, summary)
    }

    private fun handleConfirmSalary(transactionId: Int) {
        val current = _uiState.value.importWorkflow as? ImportWorkflow.SalaryConfirmation ?: return
        val picked = current.candidates.firstOrNull { it.transactionId == transactionId } ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val pattern = picked.title.take(12).lowercase().trim()
                db.salarySourceDao().upsert(
                    SalarySourceEntity(
                        merchantPattern = pattern,
                        lastAmount = picked.amount,
                        minAmount = picked.amount * 0.80,
                        maxAmount = picked.amount * 1.20
                    )
                )
                db.recurringBillDao().upsertAll(listOf(
                    RecurringBillEntity(
                        merchantPattern = pattern,
                        normalizedAmount = picked.amount,
                        minAmount = picked.amount * 0.80,
                        maxAmount = picked.amount * 1.20,
                        category = "SALARY",
                        lastSeenDate = System.currentTimeMillis(),
                        cycleCount = 1,
                        isActive = true
                    )
                ))
            }
            _uiState.update { it.copy(importWorkflow = advanceFromSalary(current.pendingSummary)) }
        }
    }

    private fun handleSkipSalary() {
        val current = _uiState.value.importWorkflow as? ImportWorkflow.SalaryConfirmation ?: return
        _uiState.update { it.copy(importWorkflow = advanceFromSalary(current.pendingSummary)) }
    }

    private fun handleAssignCategory(txId: Int, category: String) {
        val current = _uiState.value.importWorkflow as? ImportWorkflow.ReviewUncategorized ?: return
        _uiState.update {
            it.copy(importWorkflow = current.copy(allAssignments = current.allAssignments + (txId to category)))
        }
    }

    private fun handleConfirmReview(makeRules: List<Pair<String, String>>) {
        val current = _uiState.value.importWorkflow as? ImportWorkflow.ReviewUncategorized ?: return
        viewModelScope.launch {
            if (makeRules.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val now = java.time.LocalDate.now().toString()
                    makeRules.forEach { (pattern, category) ->
                        db.merchantRuleDao().upsertRule(
                            MerchantRuleEntity(
                                merchantPattern = pattern,
                                targetCategoryId = category,
                                isExclusion = false,
                                isEnabled = true,
                                priority = 0,
                                updatedAt = now
                            )
                        )
                    }
                }
            }
            // Apply user's category assignments to the full summary
            val updatedTransactions = current.fullSummary.transactions.map { tx ->
                val assigned = current.allAssignments[tx.id]
                if (assigned != null) tx.copy(category = assigned) else tx
            }
            val updatedSummary = current.fullSummary.copy(transactions = updatedTransactions)
            _uiState.update { it.copy(importWorkflow = ImportWorkflow.NeedsReview(updatedSummary)) }
        }
    }

    private fun commitImport() {
        val current = _uiState.value.importWorkflow as? ImportWorkflow.NeedsReview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(importWorkflow = ImportWorkflow.Committing) }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importWorkflow = ImportWorkflow.Parsing(0.92f, "Writing to database…")) }
            }
            try {
                withContext(Dispatchers.IO) {
                    db.walletTransactionDao().insertTransactions(
                        current.summary.transactions.map { tx ->
                            WalletTransactionEntity(
                                id = tx.id,
                                title = tx.title,
                                category = tx.category,
                                amount = tx.amount,
                                date = tx.date,
                                paymentType = "Bank",
                                trip = null,
                                note = tx.note,
                                bankName = tx.bankName,
                                excluded = false,
                                syncedFromJson = false
                            )
                        }
                    )
                    db.importAuditDao().insertAudit(
                        ImportAuditEntity(
                            timestamp = System.currentTimeMillis(),
                            fileName = current.summary.fileName,
                            fileHash = current.summary.fileHash,
                            rowCount = current.summary.rowCount,
                            skippedCount = current.summary.skippedCount,
                            detectedBank = current.summary.detectedBank,
                            delimiter = current.summary.delimiter.toString(),
                            warningCount = current.summary.warningCount,
                            salaryAnchorDate = _uiState.value.salaryAnchor
                        )
                    )
                    // Apply merchant rules to newly imported transactions
                    retroactiveCategorizer.recategorizeAll()
                }
                refreshState()
                PipelineManager.enqueueCsvImportChain(getApplication())
                if (DriveDataStore.isDriveEnabled(getApplication())) {
                    PipelineManager.enqueueBankStatementUpload(
                        getApplication(),
                        current.summary.fileName,
                        "Imported ${current.summary.parsedCount} transactions from ${current.summary.detectedBank} (${current.summary.fileName})"
                    )
                }
                _uiState.update { it.copy(importWorkflow = ImportWorkflow.Success(current.summary.parsedCount)) }
                // Auto-reset to Idle after 1.5 seconds so user can import again
                kotlinx.coroutines.delay(1500L)
                _uiState.update { it.copy(importWorkflow = ImportWorkflow.Idle) }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        importWorkflow = ImportWorkflow.Failed(ImportErrorType.ParseFailure(error.message ?: "Commit failed")),
                        typedErrors = it.typedErrors + ConfigError.Persistence(error.message ?: "Failed to persist import")
                    )
                }
            }
        }
    }

    private fun updateSalaryAnchor(value: Int) {
        val anchor = value.coerceIn(1, 31)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DashboardPrefs.setSalaryAnchorDay(getApplication(), anchor)
            }
            _uiState.update {
                it.copy(
                    salaryAnchor = anchor,
                    currentFiscalLabel = FiscalDateUtils.formatRangeLabel(
                        FiscalDateUtils.getFiscalCycleRange(LocalDate.now(), anchor)
                    )
                )
            }
        }
    }

    private fun beginThresholdEdit(categoryId: String) {
        val threshold = _uiState.value.persistedCategories.firstOrNull { it.id == categoryId } ?: return
        _uiState.update {
            it.copy(
                activeEditorId = categoryId,
                draftLimitValue = threshold.limit
            )
        }
    }

    private fun updateDraftLimit(value: Float) {
        _uiState.update { it.copy(draftLimitValue = value.coerceIn(0f, 2000f)) }
    }

    private fun saveThreshold() {
        val categoryId = _uiState.value.activeEditorId ?: return
        val draft = _uiState.value.draftLimitValue ?: return
        val previous = _uiState.value.persistedCategories.firstOrNull { it.id == categoryId }?.limit ?: draft
        val actionId = "threshold-$categoryId-${System.currentTimeMillis()}"

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.categoryThresholdDao().upsertThreshold(
                    CategoryThresholdEntity(categoryId = categoryId, limitAmount = draft)
                )
            }
            _uiState.update {
                it.copy(
                    persistedCategories = it.persistedCategories.map { category ->
                        if (category.id == categoryId) category.copy(limit = draft) else category
                    },
                    activeEditorId = null,
                    draftLimitValue = null,
                    undoStack = it.undoStack + UndoableAction.ThresholdChange(actionId, categoryId, previous)
                )
            }
        }
    }

    private fun cancelThresholdEdit() {
        _uiState.update { it.copy(activeEditorId = null, draftLimitValue = null) }
    }

    private fun toggleRule(ruleId: String, enabled: Boolean) {
        val currentRule = _uiState.value.pendingRules.firstOrNull { it.id == ruleId } ?: return
        val actionId = "rule-$ruleId-${System.currentTimeMillis()}"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.merchantRuleDao().upsertRule(
                    MerchantRuleEntity(
                        merchantPattern = currentRule.pattern,
                        targetCategoryId = currentRule.targetCategoryId,
                        isExclusion = currentRule.isExclusion,
                        isEnabled = enabled,
                        priority = currentRule.priority,
                        updatedAt = System.currentTimeMillis().toString()
                    )
                )
            }
            _uiState.update {
                it.copy(
                    pendingRules = it.pendingRules.map { rule ->
                        if (rule.id == ruleId) rule.copy(isEnabled = enabled) else rule
                    },
                    undoStack = it.undoStack + UndoableAction.RuleToggle(actionId, ruleId, currentRule.isEnabled)
                )
            }
        }
    }

    private fun undo(actionId: String) {
        val action = _uiState.value.undoStack.lastOrNull { it.actionId == actionId } ?: return
        when (action) {
            is UndoableAction.ThresholdChange -> {
                _uiState.update {
                    it.copy(
                        activeEditorId = action.categoryId,
                        draftLimitValue = action.previousValue,
                        undoStack = it.undoStack.filterNot { item -> item.actionId == actionId }
                    )
                }
                saveThreshold()
            }
            is UndoableAction.RuleToggle -> {
                toggleRule(action.ruleId, action.previousEnabled)
                _uiState.update {
                    it.copy(undoStack = it.undoStack.filterNot { item -> item.actionId == actionId })
                }
            }
        }
    }

    private fun toggleCloudSync() {
        val ctx = getApplication<Application>()
        val newEnabled = !_uiState.value.isCloudSyncEnabled
        _uiState.update { it.copy(isCloudSyncEnabled = newEnabled) }
        viewModelScope.launch {
            DriveDataStore.setDriveEnabled(ctx, newEnabled)
        }
    }

    private fun handleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data ?: return)
        if (task.isSuccessful) {
            val email = task.result?.email
            _uiState.update { it.copy(connectedEmail = email, signInError = null) }
            refreshState()
        } else {
            val apiEx = task.exception as? ApiException
            val code = apiEx?.statusCode ?: -1
            val codeName = CommonStatusCodes.getStatusCodeString(code)
            Log.e("DriveAuth", "Sign-in failed: code=$code ($codeName)", task.exception)
            val hint = when (code) {
                10   -> "DEVELOPER_ERROR — check SHA-1 + package name in GCP OAuth client"
                12500 -> "SIGN_IN_FAILED — ensure Drive API is enabled and OAuth consent screen is configured"
                12501 -> "Sign-in cancelled"
                else -> "error $code ($codeName)"
            }
            _uiState.update { it.copy(signInError = "Sign-in failed: $hint") }
        }
    }

    private fun signOutGoogle() {
        val ctx = getApplication<Application>()
        DriveAuthManager(ctx).signOut()
        viewModelScope.launch {
            DriveDataStore.clearOnSignOut(ctx)
            _uiState.update { it.copy(isCloudSyncEnabled = false, connectedEmail = null) }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.walletTransactionDao().deleteAllTransactions()
                db.recurringBillDao().clearAll()
                db.salarySourceDao().clear()
                db.importAuditDao().clearAll()
                db.transactionOverrideDao().deleteAllOverrides()
                db.excludedTransactionDao().deleteAllExcluded()
            }
            _uiState.update { it.copy(importWorkflow = ImportWorkflow.Idle, recentAudits = emptyList()) }
            refreshState()
        }
    }

    private fun mergeThresholds(entities: List<CategoryThresholdEntity>): List<CategoryThreshold> {
        val persisted = entities.associateBy { it.categoryId }
        return defaultThresholds().map { base ->
            base.copy(limit = persisted[base.id]?.limitAmount ?: base.limit)
        }
    }

    private fun defaultThresholds(): List<CategoryThreshold> {
        return listOf(
            CategoryThreshold("groceries", "Groceries",       600f),
            CategoryThreshold("travel",    "Travel",          195f),
            CategoryThreshold("dining",    "Dining / Eat Out", 100f),
            CategoryThreshold("shopping",  "Shopping",        100f)
        )
    }

    private fun queryFileName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return "import.csv"
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun mapAudit(audit: ImportAuditEntity): ImportAuditRecord {
        val timestamp = Instant.ofEpochMilli(audit.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
        return ImportAuditRecord(
            id = audit.id,
            fileName = audit.fileName,
            detectedBank = audit.detectedBank,
            importedAtLabel = timestamp.format(formatter),
            cycleLabel = "Anchor ${audit.salaryAnchorDate}",
            salaryAnchorDate = audit.salaryAnchorDate
        )
    }

    private fun deduplicateImportedTransactions(
        summary: ImportSummary,
        existingTransactions: List<WalletTransactionEntity>
    ): ImportSummary {
        val existingFingerprints = existingTransactions
            .map { transactionFingerprint(it.title, it.amount, it.date, it.category) }
            .toHashSet()

        val seenInFile = mutableSetOf<String>()
        val keptTransactions = summary.transactions.filter { tx ->
            val fingerprint = transactionFingerprint(tx.title, tx.amount, tx.date, tx.category)
            val alreadyInDb = fingerprint in existingFingerprints
            val duplicateWithinFile = !seenInFile.add(fingerprint)
            !alreadyInDb && !duplicateWithinFile
        }

        val removedCount = summary.transactions.size - keptTransactions.size
        return summary.copy(
            parsedCount = keptTransactions.size,
            skippedCount = summary.skippedCount + removedCount,
            transactions = keptTransactions
        )
    }

    private fun transactionFingerprint(
        title: String,
        amount: Double,
        date: LocalDate,
        category: String
    ): String {
        val normalizedTitle = title
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val transferLike = category.equals("Transfers", ignoreCase = true) ||
            normalizedTitle.contains("transfer") ||
            normalizedTitle.contains("sepa") ||
            normalizedTitle.contains("iban") ||
            normalizedTitle.contains("main account") ||
            normalizedTitle.contains("siddharth") ||
            normalizedTitle.contains("n26") ||
            normalizedTitle.contains("commerz") ||
            normalizedTitle.contains("ing")

        val titleKey = if (transferLike) {
            normalizedTitle
                .replace(Regex("\\b(main account|travel|siddharth|akkinepalli|ntbs|n26|commerz|ing|iban|sepa|transfer|wise|wire|interbank)\\b"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        } else {
            normalizedTitle
        }

        val amountKey = if (transferLike) abs(amount) else amount
        val categoryKey = if (transferLike) "transfers" else category.lowercase(Locale.getDefault())
        return "$date|$amountKey|$titleKey|$categoryKey"
    }
}