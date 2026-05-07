package com.household.app.ui.v2

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.data.config.ImportErrorType
import com.household.app.data.config.ImportSummary
import com.household.app.ui.compose.theme.ConfigAccent
import com.household.app.ui.compose.theme.ConfigPanelStroke
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.EliteHeader
import com.household.app.ui.viewmodels.CategoryThreshold
import com.household.app.ui.viewmodels.ConfigIntent
import com.household.app.ui.viewmodels.ConfigUiState
import com.household.app.ui.viewmodels.ConfigViewModel
import com.household.app.ui.viewmodels.ImportAuditRecord
import com.household.app.ui.viewmodels.ImportWorkflow

@Composable
fun V2ConfigHubScreen() {
    val context = LocalContext.current
    val viewModel: ConfigViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ConfigViewModel(context.applicationContext as Application)
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.onIntent(ConfigIntent.FileSelected(uri))
        }
    }

    LaunchedEffect(uiState.undoStack.lastOrNull()?.actionId) {
        val action = uiState.undoStack.lastOrNull() ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Change applied",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.onIntent(ConfigIntent.RequestUndo(action.actionId))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EliteHeader(
                title = "System Setup",
                subtitle = "Household OS Command Center"
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SalaryAnchorCard(
                    salaryAnchor = uiState.salaryAnchor,
                    fiscalLabel = uiState.currentFiscalLabel,
                    onIntent = viewModel::onIntent
                )
            }

            item {
                CsvIngestCard(
                    uiState = uiState,
                    onPickFile = {
                        filePicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
                    },
                    onIntent = viewModel::onIntent
                )
            }

            item {
                Text(
                    text = "Budget Thresholds",
                    color = LumeWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(uiState.persistedCategories, key = { it.id }) { category ->
                CategoryLimitRow(
                    threshold = category,
                    displayValue = if (uiState.activeEditorId == category.id) {
                        uiState.draftLimitValue ?: category.limit
                    } else {
                        category.limit
                    },
                    isEditing = uiState.activeEditorId == category.id,
                    onIntent = viewModel::onIntent
                )
            }

            item {
                TransactionRulesCard(
                    uiState = uiState,
                    onIntent = viewModel::onIntent
                )
            }

            if (uiState.recentAudits.isNotEmpty()) {
                item {
                    RecentImportsCard(uiState.recentAudits)
                }
            }
        }
    }
}

@Composable
private fun SalaryAnchorCard(
    salaryAnchor: Int,
    fiscalLabel: String,
    onIntent: (ConfigIntent) -> Unit
) {
    EliteGlassCard(glowColor = LumeWhite.copy(alpha = 0.18f), borderAlpha = 0.18f) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "FISCAL CYCLE",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.62f)
            )
            Text(
                text = "Salary Payment Date",
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Cycles currently run across $fiscalLabel",
                color = LumeWhite.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium
            )
            SalaryAnchorDial(
                anchor = salaryAnchor,
                onIncrement = { onIntent(ConfigIntent.UpdateSalaryAnchor((salaryAnchor + 1).coerceAtMost(28))) },
                onDecrement = { onIntent(ConfigIntent.UpdateSalaryAnchor((salaryAnchor - 1).coerceAtLeast(1))) }
            )
            Text(
                text = "Anchor: ${salaryAnchor}th of each month",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CsvIngestCard(
    uiState: ConfigUiState,
    onPickFile: () -> Unit,
    onIntent: (ConfigIntent) -> Unit
) {
    val glowColor = when (uiState.importWorkflow) {
        is ImportWorkflow.NeedsReview -> ConfigAccent
        ImportWorkflow.DuplicateDetected -> Color(0xFFF59E0B)
        is ImportWorkflow.Success -> Color(0xFF22C55E)
        is ImportWorkflow.Failed -> CriticalRed
        else -> ConfigAccent.copy(alpha = 0.72f)
    }
    val clickable = when (uiState.importWorkflow) {
        is ImportWorkflow.Hashing,
        is ImportWorkflow.Parsing,
        ImportWorkflow.Committing,
        is ImportWorkflow.NeedsReview -> false
        else -> true
    }

    EliteGlassCard(glowColor = glowColor, borderAlpha = 0.18f) {
        Column {
            Text(
                text = "BANK DATA INTAKE",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp)
                    .border(
                        1.dp,
                        if (uiState.importWorkflow is ImportWorkflow.Idle) ConfigPanelStroke else glowColor.copy(alpha = 0.45f),
                        RoundedCornerShape(20.dp)
                    )
                    .background(LumeWhite.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                    .clickable(enabled = clickable, onClick = onPickFile)
                    .padding(vertical = 20.dp)
                    .semantics { contentDescription = "Upload bank statement" },
                contentAlignment = Alignment.Center
            ) {
                when (val workflow = uiState.importWorkflow) {
                    ImportWorkflow.Idle -> UploadIdleState()
                    is ImportWorkflow.Hashing -> LoadingState(0f, "Reading file…")
                    is ImportWorkflow.Parsing -> LoadingState(workflow.progress, workflow.stage)
                    is ImportWorkflow.NeedsReview -> ReviewState(workflow.summary, uiState.salaryAnchor, onIntent)
                    ImportWorkflow.DuplicateDetected -> WarningState("Duplicate import detected from audit trail")
                    ImportWorkflow.Committing -> LoadingState(0.95f, "Saving to database…")
                    is ImportWorkflow.Success -> SuccessState("Imported ${workflow.importedCount} transactions")
                    is ImportWorkflow.Failed -> ErrorState(workflow.error, onIntent)
                }
            }
        }
    }
}

@Composable
private fun UploadIdleState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CloudUpload, null, tint = ConfigAccent.copy(alpha = 0.82f), modifier = Modifier.size(34.dp))
        Text(
            text = "Tap to upload bank CSV",
            color = LumeWhite.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 12.dp),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Import, review, then commit to Wallet",
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LoadingState(progress: Float, stage: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "import_progress"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        CircularProgressIndicator(
            progress = { if (progress <= 0f) 0.08f else animatedProgress.coerceIn(0.08f, 1f) },
            color = ConfigAccent,
            trackColor = LumeWhite.copy(alpha = 0.1f),
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stage.ifBlank { "Processing…" },
            color = LumeWhite,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (progress > 0f) "${(progress * 100).toInt()}% complete" else "Preparing…",
            color = ConfigAccent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { if (progress <= 0f) 0.08f else animatedProgress.coerceIn(0.08f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = ConfigAccent,
            trackColor = LumeWhite.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun ReviewState(summary: ImportSummary, salaryAnchor: Int, onIntent: (ConfigIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = ConfigAccent)
            Text(
                text = "Detected ${summary.detectedBank} • ${summary.parsedCount} parsed • ${summary.skippedCount} skipped",
                color = LumeWhite,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Text(
            text = "${summary.fileName} • delimiter '${summary.delimiter}' • ${summary.warningCount} warnings",
            color = TextMuted
        )
        Text(
            text = "This import will use salary anchor ${salaryAnchor}th for cycle grouping.",
            color = LumeWhite.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onIntent(ConfigIntent.CancelImport) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Cancel", color = LumeWhite.copy(alpha = 0.72f))
            }
            Button(
                onClick = { onIntent(ConfigIntent.ConfirmImport) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ConfigAccent)
            ) {
                Text("Confirm Import", color = LumeWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WarningState(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(32.dp))
        Text(text, color = LumeWhite, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
        Text(
            text = "This file already exists in the import audit trail.",
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessState(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(48.dp))
        Text(text, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
        Text(
            text = "Wallet refresh will pick these up automatically.",
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorState(error: ImportErrorType, onIntent: (ConfigIntent) -> Unit) {
    val message = when (error) {
        ImportErrorType.EmptyFile -> "The selected file is empty or unreadable"
        ImportErrorType.MissingColumns -> "Required CSV columns were not detected. Check file format."
        is ImportErrorType.ParseFailure -> error.reason
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(Icons.Rounded.WarningAmber, null, tint = CriticalRed, modifier = Modifier.size(24.dp))
            Text(message, color = LumeWhite.copy(0.8f), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { onIntent(ConfigIntent.CancelImport) },
            colors = ButtonDefaults.buttonColors(containerColor = ConfigAccent.copy(alpha = 0.2f))
        ) {
            Text("Try Another File", color = ConfigAccent)
        }
    }
}

@Composable
private fun CategoryLimitRow(
    threshold: CategoryThreshold,
    displayValue: Float,
    isEditing: Boolean,
    onIntent: (ConfigIntent) -> Unit
) {
    EliteGlassCard(
        glowColor = if (isEditing) ConfigAccent else Color.Transparent,
        borderAlpha = if (isEditing) 0.22f else 0.12f
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(categoryColor(threshold.id).copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(threshold.name.take(1), color = categoryColor(threshold.id), fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(threshold.name, color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Limit: €${displayValue.toInt()}",
                        color = if (isEditing) ConfigAccent else LumeWhite.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onIntent(ConfigIntent.EditThreshold(threshold.id)) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = LumeWhite.copy(alpha = 0.7f))
                }
            }

            if (isEditing) {
                LumeStepper(
                    currentValue = displayValue,
                    onValueChange = { onIntent(ConfigIntent.UpdateDraftLimit(it)) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onIntent(ConfigIntent.SaveThreshold) },
                        colors = ButtonDefaults.buttonColors(containerColor = ConfigAccent.copy(alpha = 0.2f))
                    ) {
                        Text("Save", color = LumeWhite)
                    }
                    Button(
                        onClick = { onIntent(ConfigIntent.CancelThresholdEdit) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Cancel", color = LumeWhite.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRulesCard(
    uiState: ConfigUiState,
    onIntent: (ConfigIntent) -> Unit
) {
    EliteGlassCard(glowColor = LumeWhite.copy(alpha = 0.12f)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "DATA CORRECTION RULES",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f)
            )
            if (uiState.pendingRules.isEmpty()) {
                Text(
                    text = "No rules saved yet. Merchant-learning rules from Wallet will appear here.",
                    color = LumeWhite.copy(alpha = 0.72f)
                )
            } else {
                uiState.pendingRules.take(4).forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LumeWhite.copy(alpha = 0.04f), CircleShape)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.pattern, color = TextMain, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (rule.isExclusion) {
                                    "Excludes matching transactions"
                                } else {
                                    "Routes to ${rule.targetCategoryId} • collisions ${rule.collisionCount}"
                                },
                                color = LumeWhite.copy(alpha = 0.55f)
                            )
                        }
                        Button(
                            onClick = { onIntent(ConfigIntent.ToggleRule(rule.id, !rule.isEnabled)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (rule.isEnabled) ConfigAccent.copy(alpha = 0.18f) else Color.Transparent
                            )
                        ) {
                            Text(if (rule.isEnabled) "Enabled" else "Paused", color = LumeWhite)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LumeStepper(
    currentValue: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..2000f,
    stepAmount: Float = 10f
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(LumeWhite.copy(alpha = 0.05f), CircleShape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onValueChange((currentValue - stepAmount).coerceAtLeast(valueRange.start))
        }) {
            Icon(Icons.Rounded.Remove, null, tint = ConfigAccent)
        }
        Slider(
            value = currentValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange(it)
            },
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = ConfigAccent,
                activeTrackColor = ConfigAccent.copy(alpha = 0.52f),
                inactiveTrackColor = LumeWhite.copy(alpha = 0.16f)
            )
        )
        IconButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onValueChange((currentValue + stepAmount).coerceAtMost(valueRange.endInclusive))
        }) {
            Icon(Icons.Rounded.Add, null, tint = ConfigAccent)
        }
    }
}

@Composable
private fun RecentImportsCard(audits: List<ImportAuditRecord>) {
    EliteGlassCard(glowColor = LumeWhite.copy(alpha = 0.12f), borderAlpha = 0.16f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "IMPORT AUDIT TRAIL",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f)
            )
            audits.forEach { audit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LumeWhite.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(audit.fileName, color = TextMain, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${audit.detectedBank} • ${audit.importedAtLabel} • ${audit.cycleLabel}",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun categoryColor(categoryId: String): Color {
    return when (categoryId) {
        "groceries" -> Color(0xFF22C55E)
        "housing" -> Color(0xFFF59E0B)
        "transport" -> Color(0xFF60A5FA)
        "dining" -> Color(0xFFFB7185)
        "utilities" -> Color(0xFFA78BFA)
        else -> ConfigAccent
    }
}

@Composable
private fun SalaryAnchorDial(
    anchor: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val normalized = ((anchor - 1).toFloat() / 27f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDecrement()
        }) {
            Icon(Icons.Rounded.Remove, null, tint = ConfigAccent)
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(156.dp)) {
            Canvas(modifier = Modifier.size(156.dp)) {
                val stroke = 10.dp.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = size.copy(width = size.width - stroke, height = size.height - stroke)

                drawArc(
                    color = LumeWhite.copy(alpha = 0.12f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ConfigAccent,
                    startAngle = 135f,
                    sweepAngle = 270f * normalized,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Salary Day", color = LumeWhite.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
                Text("${anchor}th", color = TextMain, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        IconButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onIncrement()
        }) {
            Icon(Icons.Rounded.Add, null, tint = ConfigAccent)
        }
    }
}