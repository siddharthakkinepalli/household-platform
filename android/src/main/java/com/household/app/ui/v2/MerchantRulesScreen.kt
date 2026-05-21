package com.household.app.ui.v2

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.household.app.data.AppDatabase
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.data.repository.MerchantRuleRepositoryImpl
import com.household.app.domain.services.RetroactiveCategorizer
import com.household.app.ui.compose.theme.ConfigAccent
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.EliteHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Categories available for assignment
// ─────────────────────────────────────────────────────────────────────────────

private val AVAILABLE_CATEGORIES = listOf(
    "Groceries", "Eat Out", "Travel", "Utilities", "Transfers", "Shopping", "Exclude"
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

data class MerchantRulesUiState(
    val rules: List<MerchantRuleEntity> = emptyList(),
    val isRecategorizing: Boolean = false,
    val recategorizeResult: String? = null
)

class MerchantRulesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = MerchantRuleRepositoryImpl(db.merchantRuleDao())
    private val categorizer = RetroactiveCategorizer(
        merchantRuleDao = db.merchantRuleDao(),
        walletTransactionDao = db.walletTransactionDao()
    )

    private val _uiState = MutableStateFlow(MerchantRulesUiState())
    val uiState: StateFlow<MerchantRulesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Seed defaults on first run (no-ops if already present)
            withContext(Dispatchers.IO) { repository.seedDefaultRules() }
            // Observe all rules as Flow
            repository.getAllRules().collect { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }
    }

    fun addRule(
        pattern: String,
        categoryId: String,
        isExclusion: Boolean,
        priority: Int
    ) {
        if (pattern.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.upsertRule(
                    MerchantRuleEntity(
                        merchantPattern = pattern.trim(),
                        targetCategoryId = categoryId,
                        isExclusion = isExclusion,
                        isEnabled = true,
                        priority = priority,
                        updatedAt = System.currentTimeMillis().toString()
                    )
                )
            }
        }
    }

    fun toggleRule(rule: MerchantRuleEntity, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.upsertRule(
                    rule.copy(
                        isEnabled = enabled,
                        updatedAt = System.currentTimeMillis().toString()
                    )
                )
            }
        }
    }

    fun deleteRule(pattern: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteRule(pattern) }
        }
    }

    fun recategorizeAll() {
        if (_uiState.value.isRecategorizing) return
        _uiState.update { it.copy(isRecategorizing = true, recategorizeResult = null) }
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { categorizer.recategorizeAll() }
            _uiState.update {
                it.copy(
                    isRecategorizing = false,
                    recategorizeResult = if (updated == 0) "No changes needed." else "$updated transaction(s) updated."
                )
            }
        }
    }

    fun clearRecategorizeResult() {
        _uiState.update { it.copy(recategorizeResult = null) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantRulesScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MerchantRulesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MerchantRulesViewModel(context.applicationContext as Application) }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddSheet by remember { mutableStateOf(false) }
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    // Show snackbar whenever recategorize result arrives
    val recatResult = uiState.recategorizeResult
    if (recatResult != null) {
        androidx.compose.runtime.LaunchedEffect(recatResult) {
            snackbarHostState.showSnackbar(recatResult)
            viewModel.clearRecategorizeResult()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = EliteNavy,
                    contentColor = LumeWhite
                )
            }
        },
        topBar = {
            EliteHeader(
                title = "Merchant Rules",
                subtitle = "Pattern-based auto-categorization",
                onBack = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = ConfigAccent,
                contentColor = EliteNavy,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add rule")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 80.dp + navBarPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Re-categorize All header card
            item {
                RecategorizeCard(
                    isLoading = uiState.isRecategorizing,
                    onRecategorize = { viewModel.recategorizeAll() }
                )
            }

            if (uiState.rules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No rules yet. Tap + to add one.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(uiState.rules, key = { it.merchantPattern }) { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { enabled -> viewModel.toggleRule(rule, enabled) },
                        onDelete = { viewModel.deleteRule(rule.merchantPattern) }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddRuleSheet(
            onDismiss = { showAddSheet = false },
            onSave = { pattern, category, isExclusion, priority ->
                viewModel.addRule(pattern, category, isExclusion, priority)
                showAddSheet = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Re-categorize card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecategorizeCard(isLoading: Boolean, onRecategorize: () -> Unit) {
    EliteGlassCard(glowColor = LumeEmerald.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RE-CATEGORIZE ALL",
                    style = MaterialTheme.typography.labelSmall,
                    color = LumeWhite.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Apply current rules to every transaction in the database.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(12.dp))
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = LumeEmerald,
                    trackColor = LumeWhite.copy(alpha = 0.1f),
                    strokeWidth = 3.dp
                )
            } else {
                Button(
                    onClick = onRecategorize,
                    colors = ButtonDefaults.buttonColors(containerColor = LumeEmerald.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = LumeEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Run", color = LumeEmerald, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rule row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RuleRow(
    rule: MerchantRuleEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    EliteGlassCard(
        glowColor = if (rule.isEnabled) ConfigAccent.copy(alpha = 0.1f) else Color.Transparent,
        borderAlpha = if (rule.isEnabled) 0.18f else 0.08f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = rule.merchantPattern,
                        color = if (rule.isEnabled) TextMain else TextMuted,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = ConfigAccent.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (rule.isExclusion) "Exclude" else rule.targetCategoryId,
                        color = if (rule.isExclusion) CriticalRed.copy(alpha = 0.85f) else ConfigAccent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Priority ${rule.priority}${if (rule.isExclusion) " • Exclusion" else ""}",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ConfigAccent,
                    checkedThumbColor = EliteNavy,
                    uncheckedThumbColor = LumeWhite.copy(alpha = 0.5f),
                    uncheckedTrackColor = LumeWhite.copy(alpha = 0.1f)
                )
            )

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete rule",
                    tint = CriticalRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Rule bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleSheet(
    onDismiss: () -> Unit,
    onSave: (pattern: String, category: String, isExclusion: Boolean, priority: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pattern by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AVAILABLE_CATEGORIES.first()) }
    var isExclusion by remember { mutableStateOf(false) }
    var priority by remember { mutableFloatStateOf(10f) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ConfigAccent,
        unfocusedBorderColor = LumeWhite.copy(alpha = 0.2f),
        focusedTextColor = LumeWhite,
        unfocusedTextColor = LumeWhite,
        cursorColor = ConfigAccent,
        focusedLabelColor = ConfigAccent,
        unfocusedLabelColor = LumeWhite.copy(alpha = 0.55f)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EliteNavy,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(LumeWhite.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Add Merchant Rule",
                    color = LumeWhite,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = LumeWhite.copy(alpha = 0.6f))
                }
            }

            // Pattern field
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("Merchant pattern (e.g. paypal*google*)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors,
                supportingText = {
                    Text(
                        text = "Use * as wildcard. Matching is case-insensitive.",
                        color = LumeWhite.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            // Category dropdown (hidden when isExclusion is true)
            if (!isExclusion) {
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = textFieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        AVAILABLE_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = LumeWhite) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Exclusion toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Exclusion rule", color = TextMain, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Matching transactions will be marked as Exclude.",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Switch(
                    checked = isExclusion,
                    onCheckedChange = { isExclusion = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = CriticalRed,
                        checkedThumbColor = LumeWhite,
                        uncheckedThumbColor = LumeWhite.copy(alpha = 0.5f),
                        uncheckedTrackColor = LumeWhite.copy(alpha = 0.1f)
                    )
                )
            }

            // Priority slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Priority", color = TextMain, fontWeight = FontWeight.Medium)
                    Text(
                        text = priority.toInt().toString(),
                        color = ConfigAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = priority,
                    onValueChange = { priority = it },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = ConfigAccent,
                        activeTrackColor = ConfigAccent.copy(alpha = 0.6f),
                        inactiveTrackColor = LumeWhite.copy(alpha = 0.15f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Low", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text("High", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }

            // Save / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = LumeWhite.copy(alpha = 0.6f))
                }
                Button(
                    onClick = {
                        val effectiveCategory = if (isExclusion) "Exclude" else selectedCategory
                        onSave(pattern.trim(), effectiveCategory, isExclusion, priority.toInt())
                    },
                    enabled = pattern.trim().isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ConfigAccent)
                ) {
                    Text("Save Rule", color = EliteNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
