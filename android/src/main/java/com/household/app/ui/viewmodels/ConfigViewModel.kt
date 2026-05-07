package com.household.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.household.app.data.AppDatabase
import com.household.app.data.DashboardPrefs
import com.household.app.data.config.CsvParserService
import com.household.app.data.config.ImportErrorType
import com.household.app.data.config.ImportParseResult
import com.household.app.data.config.ImportSummary
import com.household.app.data.config.RuleEngineService
import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.ImportAuditEntity
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.domain.utils.FiscalDateUtils
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
}

sealed class ImportWorkflow {
    object Idle : ImportWorkflow()
    data class Hashing(val fileName: String) : ImportWorkflow()
    data class Parsing(val progress: Float, val stage: String = "") : ImportWorkflow()
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
    val typedErrors: List<ConfigError> = emptyList()
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy { AppDatabase.getInstance(getApplication()) }
    private val parser = CsvParserService()

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

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
                    importWorkflow = currentWorkflow
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
                    _uiState.update { it.copy(importWorkflow = ImportWorkflow.NeedsReview(parseResult.summary)) }
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
                }
                refreshState()
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
        val anchor = value.coerceIn(1, 28)
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

    private fun mergeThresholds(entities: List<CategoryThresholdEntity>): List<CategoryThreshold> {
        val persisted = entities.associateBy { it.categoryId }
        return defaultThresholds().map { base ->
            base.copy(limit = persisted[base.id]?.limitAmount ?: base.limit)
        }
    }

    private fun defaultThresholds(): List<CategoryThreshold> {
        return listOf(
            CategoryThreshold("groceries", "Groceries", 450f),
            CategoryThreshold("housing", "Housing", 1200f),
            CategoryThreshold("transport", "Transport", 280f),
            CategoryThreshold("dining", "Dining Out", 240f),
            CategoryThreshold("utilities", "Utilities", 320f),
            CategoryThreshold("family", "Family", 300f)
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