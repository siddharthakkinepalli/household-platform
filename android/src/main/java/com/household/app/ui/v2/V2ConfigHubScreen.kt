package com.household.app.ui.v2

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EuroSymbol
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.data.AppDatabase
import com.household.app.data.config.ImportErrorType
import com.household.app.data.config.ImportSummary
import com.household.app.BuildConfig
import com.household.app.domain.services.HouseholdExportService
import com.household.app.domain.services.HouseholdImportService
import com.household.app.ui.compose.theme.ConfigAccent
import com.household.app.ui.compose.theme.ConfigPanelStroke
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
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
fun V2ConfigHubScreen(
    onBack: () -> Unit = {},
    onNavigateToMerchantRules: () -> Unit = {}
) {
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
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onIntent(ConfigIntent.HandleSignInResult(result.data))
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

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EliteHeader(
                title = "System Setup",
                subtitle = "Household OS Command Center",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp + navBarPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1 — Salary Anchor horizontal date picker
            item {
                SalaryAnchorPickerCard(
                    selectedDay = uiState.salaryAnchor,
                    fiscalLabel = uiState.currentFiscalLabel,
                    onIntent = viewModel::onIntent
                )
            }

            // 2 — Cloud Sync toggle
            item {
                CloudSyncCard(
                    isEnabled = uiState.isCloudSyncEnabled,
                    connectedEmail = uiState.connectedEmail,
                    signInError = uiState.signInError,
                    onIntent = viewModel::onIntent,
                    onSignIn = {
                        val intent = com.household.app.vault.DriveAuthManager(context).signInIntent()
                        signInLauncher.launch(intent)
                    }
                )
            }

            // 3 — Currency / Locale (read-only)
            item {
                CurrencyLocaleCard()
            }

            // 4 — Bank CSV intake
            item {
                CsvIngestCard(
                    uiState = uiState,
                    onPickFile = {
                        filePicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
                    },
                    onIntent = viewModel::onIntent
                )
            }

            // 5 — Budget thresholds header
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

            // 6 — Transaction rules
            item {
                TransactionRulesCard(
                    uiState = uiState,
                    onIntent = viewModel::onIntent
                )
            }

            // 7 — Merchant Rules navigation entry
            item {
                MerchantRulesNavCard(onClick = onNavigateToMerchantRules)
            }

            // 8 — Recent import audit trail
            if (uiState.recentAudits.isNotEmpty()) {
                item {
                    RecentImportsCard(uiState.recentAudits)
                }
            }

            // 9 — Local backup / restore
            item {
                LocalBackupCard()
            }

            // 10 — About
            item {
                AboutCard()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Salary Anchor — horizontal day picker
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SalaryAnchorPickerCard(
    selectedDay: Int,
    fiscalLabel: String,
    onIntent: (ConfigIntent) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)

    // Haptic tick every time a new item snaps into view while scrolling
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .drop(1)
            .collect {
                if (listState.isScrollInProgress) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    LaunchedEffect(selectedDay) {
        val scrollTo = (selectedDay - 4).coerceAtLeast(0)
        listState.animateScrollToItem(scrollTo)
    }

    EliteGlassCard(glowColor = ConfigAccent.copy(alpha = 0.18f)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "FISCAL CYCLE",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            Text(
                text = "Budget Reset Day",
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            if (fiscalLabel.isNotBlank()) {
                Text(
                    text = "Current cycle: $fiscalLabel",
                    color = LumeWhite.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyRow(
                state = listState,
                flingBehavior = snapFling,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(31) { index ->
                    val day = index + 1
                    DayChip(
                        day = day,
                        isSelected = day == selectedDay,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onIntent(ConfigIntent.UpdateSalaryAnchor(day))
                        }
                    )
                }
            }

            Text(
                text = "Salary lands on the ${selectedDay}th — cycles run ${selectedDay}th → ${selectedDay}th",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DayChip(day: Int, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ConfigAccent else LumeWhite.copy(alpha = 0.06f),
        animationSpec = tween(durationMillis = 180),
        label = "day_bg_$day"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) EliteNavy else LumeWhite.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = 180),
        label = "day_text_$day"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .then(if (isSelected) Modifier.neonGlow(ConfigAccent, blurRadius = 10.dp) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(
                if (isSelected) Modifier.border(1.dp, ConfigAccent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Day $day" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$day",
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cloud Sync toggle card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CloudSyncCard(
    isEnabled: Boolean,
    connectedEmail: String?,
    signInError: String? = null,
    onIntent: (ConfigIntent) -> Unit,
    onSignIn: () -> Unit = {}
) {
    val glowColor = if (isEnabled) LumeEmerald.copy(alpha = 0.22f) else Color.Transparent
    val statusIcon = if (isEnabled) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff
    val statusTint = if (isEnabled) LumeEmerald else LumeWhite.copy(alpha = 0.35f)
    val isConnected = connectedEmail != null

    EliteGlassCard(glowColor = glowColor) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "CLOUD BACKUP",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(statusTint.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(statusIcon, contentDescription = null, tint = statusTint, modifier = Modifier.size(22.dp))
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Drive Sync",
                        color = TextMain,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when {
                            !isConnected -> "No Google account connected"
                            isEnabled    -> "Active — uploads on Wi-Fi"
                            else         -> "Paused"
                        },
                        color = if (isEnabled && isConnected) LumeEmerald else LumeWhite.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (connectedEmail != null) {
                        Text(
                            text = connectedEmail,
                            color = LumeWhite.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onIntent(ConfigIntent.ToggleCloudSync) },
                    enabled = isConnected,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EliteNavy,
                        checkedTrackColor = LumeEmerald,
                        uncheckedThumbColor = LumeWhite.copy(alpha = 0.6f),
                        uncheckedTrackColor = LumeWhite.copy(alpha = 0.1f),
                        disabledUncheckedTrackColor = LumeWhite.copy(alpha = 0.05f),
                        disabledUncheckedThumbColor = LumeWhite.copy(alpha = 0.2f)
                    )
                )
            }

            if (!isConnected) {
                OutlinedButton(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LumeWhite),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        LumeWhite.copy(alpha = 0.25f)
                    )
                ) {
                    Text("Connect Google Account", fontWeight = FontWeight.Medium)
                }
                if (signInError != null) {
                    Text(
                        text = signInError,
                        color = LumeAmber,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                TextButton(
                    onClick = { onIntent(ConfigIntent.SignOutGoogle) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Disconnect", color = LumeWhite.copy(alpha = 0.45f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Currency / Locale — read-only grounding card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CurrencyLocaleCard() {
    EliteGlassCard(glowColor = LumeAmber.copy(alpha = 0.14f)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "LOCALE & CURRENCY",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LocaleDataPill(
                    icon = Icons.Rounded.EuroSymbol,
                    label = "EUR (€)",
                    sub = "Euro",
                    tint = LumeAmber,
                    modifier = Modifier.weight(1f)
                )
                LocaleDataPill(
                    icon = Icons.Rounded.Language,
                    label = "Germany",
                    sub = "Region",
                    tint = LumePurple,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "OCR parser is calibrated for German supermarket receipts — ALDI, REWE, LIDL, Kaufland. Price format: 1,99 €",
                color = LumeWhite.copy(alpha = 0.48f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LocaleDataPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(tint.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Column {
            Text(label, color = TextMain, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text(sub, color = tint.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CSV intake (unchanged logic, kept as private composable)
// ─────────────────────────────────────────────────────────────────────────────

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
                        if (uiState.importWorkflow is ImportWorkflow.Idle) ConfigPanelStroke
                        else glowColor.copy(alpha = 0.45f),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = ConfigAccent,
            trackColor = LumeWhite.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun ReviewState(summary: ImportSummary, salaryAnchor: Int, onIntent: (ConfigIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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
                Text("Confirm Import", color = EliteNavy, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WarningState(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(48.dp))
        Text(
            text,
            color = Color(0xFF22C55E),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center
        )
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(Icons.Rounded.WarningAmber, null, tint = CriticalRed, modifier = Modifier.size(24.dp))
            Text(
                message,
                color = LumeWhite.copy(0.8f),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = { onIntent(ConfigIntent.CancelImport) },
            colors = ButtonDefaults.buttonColors(containerColor = ConfigAccent.copy(alpha = 0.2f))
        ) {
            Text("Try Another File", color = ConfigAccent)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Budget threshold editor row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryLimitRow(
    threshold: CategoryThreshold,
    displayValue: Float,
    isEditing: Boolean,
    onIntent: (ConfigIntent) -> Unit
) {
    val haptics = LocalHapticFeedback.current

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
                    Text(
                        threshold.name.take(1),
                        color = categoryColor(threshold.id),
                        fontWeight = FontWeight.Bold
                    )
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
                    onValueChange = { onIntent(ConfigIntent.UpdateDraftLimit(it)) },
                    haptics = haptics
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
private fun LumeStepper(
    currentValue: Float,
    onValueChange: (Float) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    valueRange: ClosedFloatingPointRange<Float> = 0f..2000f,
    stepAmount: Float = 10f
) {
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
        androidx.compose.material3.Slider(
            value = currentValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange(it)
            },
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
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

// ─────────────────────────────────────────────────────────────────────────────
// Transaction rules card
// ─────────────────────────────────────────────────────────────────────────────

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
                                containerColor = if (rule.isEnabled) ConfigAccent.copy(alpha = 0.18f)
                                else Color.Transparent
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

// ─────────────────────────────────────────────────────────────────────────────
// Import audit trail
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// About
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutCard() {
    EliteGlassCard(glowColor = LumeWhite.copy(alpha = 0.06f), borderAlpha = 0.1f) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            Text(
                text = "Jugaad Home",
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                color = LumeWhite.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "com.jugaad.home",
                color = LumeWhite.copy(alpha = 0.3f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Merchant Rules navigation card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MerchantRulesNavCard(onClick: () -> Unit) {
    EliteGlassCard(
        glowColor = LumePurple.copy(alpha = 0.14f),
        borderAlpha = 0.14f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MERCHANT RULES",
                    style = MaterialTheme.typography.labelSmall,
                    color = LumeWhite.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Merchant Rules",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Manage pattern-based auto-categorization rules",
                    color = LumeWhite.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = LumeWhite.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Local backup / restore card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocalBackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val exportService = remember { HouseholdExportService(db) }
    val importService = remember { HouseholdImportService(db) }

    var isBusy by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isBusy = true
            scope.launch {
                val result = importService.import(context, uri)
                isBusy = false
                val msg = if (result.success) {
                    "${result.message} (${result.imported} records)"
                } else {
                    result.message
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    EliteGlassCard(glowColor = LumePurple.copy(alpha = 0.14f)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "LOCAL BACKUP",
                style = MaterialTheme.typography.labelSmall,
                color = LumeWhite.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )

            Text(
                text = "Export / Import Data",
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Save a full JSON backup of all household data to device storage, or restore from a previous backup.",
                color = LumeWhite.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )

            if (isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = LumePurple,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            isBusy = true
                            scope.launch {
                                try {
                                    val uri = exportService.export(context)
                                    Toast.makeText(
                                        context,
                                        "Backup saved: ${uri.lastPathSegment}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Export failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LumePurple.copy(alpha = 0.22f)
                        )
                    ) {
                        Icon(
                            Icons.Rounded.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Export Data", color = LumeWhite, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LumeWhite.copy(alpha = 0.08f)
                        )
                    ) {
                        Icon(
                            Icons.Rounded.RestoreFromTrash,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LumeAmber
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Import Data", color = LumeAmber, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Modifier.neonGlow(color: Color, blurRadius: Dp): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(blurRadius.toPx(), 0f, 0f, color.copy(alpha = 0.85f).toArgb())
            }
        }
        canvas.drawRoundRect(0f, 0f, size.width, size.height, 12.dp.toPx(), 12.dp.toPx(), paint)
    }
}

private fun categoryColor(categoryId: String): Color = when (categoryId) {
    "groceries" -> Color(0xFF22C55E)
    "travel"    -> Color(0xFF60A5FA)
    "dining"    -> Color(0xFFFB7185)
    "shopping"  -> Color(0xFFEC4899)
    else        -> ConfigAccent
}
