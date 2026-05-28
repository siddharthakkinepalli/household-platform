package com.household.app.ui.v2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.data.entities.TaxTagEntity
import com.household.app.data.entities.VaultEntity
import com.household.app.domain.models.vault.VaultBrowseState
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.domain.models.vault.VaultFolderPath
import com.household.app.domain.models.vault.VaultFolderTree
import com.household.app.ui.compose.theme.ConfigAccent
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.ConfirmScanSheet
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.ScanConfirmationState
import com.household.app.ui.v2.components.VaultFolderBreadcrumb
import com.household.app.ui.v2.components.VaultFolderList
import com.household.app.ui.v2.components.VaultFolderPickerSheet
import com.household.app.ui.v2.components.VaultMoveToFolderSheet
import com.household.app.ui.viewmodels.VaultUiState
import com.household.app.ui.viewmodels.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2DocumentVaultScreen(
    onScanClick: () -> Unit = {},
    onStagingRequested: (vaultId: Long) -> Unit = {},
    onPantryClick: () -> Unit = {},
    onNavigateToFamily: (() -> Unit)? = null,
    onNavigateToSubscriptionHub: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val viewModel: VaultViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = viewModelFactory {
            initializer { VaultViewModel(context.applicationContext as android.app.Application) }
        }
    )
    val vaultUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vaultEntries by viewModel.vaultEntries.collectAsStateWithLifecycle()
    val browseState by viewModel.browseState.collectAsStateWithLifecycle()
    val folderRows by viewModel.folderRows.collectAsStateWithLifecycle()
    val showFolderBrowser by viewModel.showFolderBrowser.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val upcomingAlerts by viewModel.upcomingAlerts.collectAsStateWithLifecycle()

    var fabExpanded by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<VaultEntity?>(null) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMime by remember { mutableStateOf("application/octet-stream") }
    var pendingFilename by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showMoveDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        fabExpanded = false
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            // Extract the display name and strip extension for use as default title
            val displayName = context.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: ""
            pendingFilename = displayName.substringBeforeLast('.').replace('_', ' ').trim()
            pendingUri = uri
            pendingMime = mime
            showUploadSheet = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiState
            .filterIsInstance<VaultUiState.ScanSaved>()
            .collect { saved ->
                onStagingRequested(saved.vaultId)
                viewModel.acknowledgeScanned()
            }
    }

    BackHandler(enabled = browseState !is VaultBrowseState.Root) {
        viewModel.navigateBack()
    }

    val breadcrumb = remember(browseState, familyMembers) {
        VaultFolderTree.breadcrumb(browseState, familyMembers)
    }

    Scaffold(
        topBar = { VaultTopBar(onPantryClick = onPantryClick) },
        floatingActionButton = {
            VaultFab(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onScanClick = { fabExpanded = false; onScanClick() },
                onUploadClick = { fabExpanded = false; filePicker.launch(arrayOf("image/*", "application/pdf")) }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VaultFolderBreadcrumb(
                segments = breadcrumb,
                onNavigate = { viewModel.navigateTo(it) }
            )

            if (upcomingAlerts.isNotEmpty()) {
                DocumentExpiryTimelineCard(alerts = upcomingAlerts)
            }

            if (onNavigateToSubscriptionHub != null) {
                SubscriptionHubShortcutCard(onClick = onNavigateToSubscriptionHub)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    vaultUiState is VaultUiState.Loading -> LoadingGlow()
                    showFolderBrowser -> VaultFolderList(
                        rows = folderRows,
                        onRowClick = { viewModel.navigateTo(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                    vaultEntries.isEmpty() -> EmptyVaultPrompt(browseState)
                    else -> VaultGallery(
                        entries = vaultEntries,
                        members = familyMembers,
                        selectedIds = selectedIds,
                        onEntryClick = { entry ->
                            if (selectedIds.isNotEmpty()) {
                                if (selectedIds.contains(entry.id)) selectedIds.remove(entry.id)
                                else selectedIds.add(entry.id)
                            } else {
                                selectedEntry = entry
                            }
                        },
                        onEntryLongClick = { entry ->
                            if (selectedIds.contains(entry.id)) selectedIds.remove(entry.id)
                            else selectedIds.add(entry.id)
                        }
                    )
                }
                if (fabExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { fabExpanded = false }
                    )
                }

                // Multi-select action bar
                MultiSelectActionBar(
                    selectedCount = selectedIds.size,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDelete = { viewModel.deleteEntries(selectedIds.toList()); selectedIds.clear() },
                    onMove = { showMoveDialog = true },
                    onCancel = { selectedIds.clear() }
                )
            }
        }
    }

    if (vaultUiState is VaultUiState.ConfirmScan) {
        val confirmState = vaultUiState as VaultUiState.ConfirmScan
        ConfirmScanSheet(
            state = ScanConfirmationState(
                refinedScan = confirmState.refinedScan,
                candidates = confirmState.candidates,
                vaultId = -1L
            ),
            onLinkConfirmed = { _, expenseId, editedScan ->
                val matched = confirmState.candidates.firstOrNull { it.id.toLong() == expenseId }
                if (matched != null) viewModel.linkPendingScanToExpense(matched, editedScan)
            },
            onManualSave = { editedScan -> viewModel.savePendingScanToVault(editedScan) },
            onDismiss = { viewModel.dismissConfirmation() }
        )
    }

    if (showUploadSheet && pendingUri != null) {
        VaultFolderPickerSheet(
            title = "Save document",
            members = familyMembers,
            initialFolder = viewModel.defaultSaveFolder(),
            initialDocumentTitle = pendingFilename,
            onDismiss = { showUploadSheet = false; pendingUri = null; pendingFilename = "" },
            onConfirm = { folder, title ->
                viewModel.saveDocument(pendingUri!!, pendingMime, folder, title)
                showUploadSheet = false
                pendingUri = null
                pendingFilename = ""
            },
            onAddMember = onNavigateToFamily?.let { navigate ->
                {
                    showUploadSheet = false
                    pendingUri = null
                    pendingFilename = ""
                    navigate()
                }
            }
        )
    }

    selectedEntry?.let { entry ->
        DocumentDetailSheet(
            entry = entry,
            onDismiss = { selectedEntry = null },
            onDelete = { e ->
                viewModel.deleteEntry(e.id)
                selectedEntry = null
            }
        )
    }

    if (showMoveDialog) {
        VaultMoveToFolderSheet(
            members = familyMembers,
            onDismiss = { showMoveDialog = false },
            onConfirm = { folder ->
                viewModel.moveEntriesToFolder(selectedIds.toList(), folder)
                selectedIds.clear()
                showMoveDialog = false
            }
        )
    }
}

// ── Multi-select action bar ───────────────────────────────────────────────────

@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCancel: () -> Unit
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EliteNavy.copy(alpha = 0.96f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$selectedCount selected",
                color = LumeWhite,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B4B).copy(alpha = 0.18f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onMove,
                colors = ButtonDefaults.buttonColors(containerColor = LumeAmber.copy(alpha = 0.18f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move", tint = LumeAmber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Move", color = LumeAmber, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onCancel) {
                Text("Cancel", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultTopBar(onPantryClick: () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "VAULT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    text = "Document Manager",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        },
        actions = {
            IconButton(onClick = onPantryClick) {
                Icon(Icons.Rounded.ShoppingCart, contentDescription = "Pantry", tint = LumeAmber)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = TextMain
        )
    )
}

// ── Expandable FAB ────────────────────────────────────────────────────────────

@Composable
private fun FabOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(EliteNavy.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(label, color = LumeWhite, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(10.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun VaultFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onScanClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.navigationBarsPadding()
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                FabOption(
                    icon = Icons.Rounded.UploadFile,
                    label = "Upload Document",
                    containerColor = EliteNavy,
                    contentColor = LumeWhite,
                    onClick = onUploadClick
                )
                FabOption(
                    icon = Icons.Rounded.DocumentScanner,
                    label = "Scan Receipt",
                    containerColor = LumeAmber,
                    contentColor = EliteNavy,
                    onClick = onScanClick
                )
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = LumeAmber,
            contentColor = EliteNavy,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Add,
                contentDescription = if (expanded) "Close" else "Add"
            )
        }
    }
}

// ── Gallery states ─────────────────────────────────────────────────────────────

@Composable
private fun LoadingGlow() {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LumeAmber.copy(alpha = alpha * 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.DocumentScanner, null,
                    tint = LumeAmber.copy(alpha = alpha),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Parsing receipt…", style = MaterialTheme.typography.bodyMedium, color = TextMuted.copy(alpha = alpha))
        }
    }
}

@Composable
private fun EmptyVaultPrompt(browse: VaultBrowseState) {
    val (title, hint) = when (browse) {
        is VaultBrowseState.Folder -> "No documents in this folder" to "Tap + to upload here."
        is VaultBrowseState.Category -> "No ${browse.category.label} yet" to "Open a person folder or upload a document."
        is VaultBrowseState.MemberScope -> "No documents for ${browse.memberLabel}" to "Pick a subfolder or upload."
        is VaultBrowseState.Root -> "Vault is empty" to "Tap + to upload or scan a receipt."
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        EliteGlassCard(glowColor = LumeAmber.copy(alpha = 0.4f)) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.FolderOpen, null, tint = LumeAmber, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(text = hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

// ── Staggered Evidence Board ──────────────────────────────────────────────────

@Composable
private fun VaultGallery(
    entries: List<VaultEntity>,
    members: List<com.household.app.data.entities.FamilyMemberEntity>,
    selectedIds: List<Long>,
    onEntryClick: (VaultEntity) -> Unit,
    onEntryLongClick: (VaultEntity) -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
        verticalItemSpacing = 16.dp,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            DocumentCard(
                entry = entry,
                members = members,
                isSelected = selectedIds.contains(entry.id),
                onClick = { onEntryClick(entry) },
                onLongClick = { onEntryLongClick(entry) }
            )
        }
    }
}

// ── Document Card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(
    entry: VaultEntity,
    members: List<com.household.app.data.entities.FamilyMemberEntity>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val category = runCatching { VaultCategory.valueOf(entry.category) }.getOrDefault(VaultCategory.OTHER)
    val isReceipt = category == VaultCategory.RECEIPT
    val glowColor = if (entry.isLinkedToExpense) LumeEmerald else categoryColor(category)
    val selectionBorderColor = Color(0xFF2DD4BF) // teal

    Box(modifier = Modifier.fillMaxWidth()) {
    EliteGlassCard(
        glowColor = if (isSelected) selectionBorderColor else glowColor,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Thumbnail or document icon
        if (isReceipt || entry.mimeType.startsWith("image")) {
            AsyncReceiptImage(
                imagePath = entry.imagePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 220.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(glowColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.mimeType.contains("pdf")) Icons.Rounded.PictureAsPdf
                                  else Icons.Rounded.Description,
                    contentDescription = null,
                    tint = glowColor,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Category badge
        Box(
            modifier = Modifier
                .background(glowColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = VaultFolderTree.displayPath(entry, members),
                style = MaterialTheme.typography.labelSmall,
                color = glowColor,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = entry.documentTitle ?: entry.merchantName ?: "Untitled",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.totalAmount?.let { "€${"%.2f".format(it)}" } ?: "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ConfigAccent
            )
            if (isReceipt) {
                Icon(
                    imageVector = if (entry.isLinkedToExpense) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                    contentDescription = null,
                    tint = (if (entry.isLinkedToExpense) LumeEmerald else LumeAmber).copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    } // end EliteGlassCard

    // Selection checkmark overlay
    if (isSelected) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF2DD4BF).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .border(2.dp, Color(0xFF2DD4BF), RoundedCornerShape(20.dp))
        )
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Selected",
            tint = Color(0xFF2DD4BF),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(22.dp)
        )
    }
    } // end outer Box
}

// ── Document Detail Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDetailSheet(
    entry: VaultEntity,
    onDismiss: () -> Unit,
    onDelete: (VaultEntity) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTaxTagPicker by remember { mutableStateOf(false) }
    var currentTag by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entry.id) {
        val existing = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.household.app.data.AppDatabase.getInstance(context).taxTagDao().getTagsFor("vault_doc", entry.id)
        }
        currentTag = existing.firstOrNull()?.taxCategory
    }

    var extractedEntities by remember { mutableStateOf<List<com.household.app.data.entities.VaultDocumentEntityRecord>>(emptyList()) }

    LaunchedEffect(entry.id) {
        extractedEntities = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.household.app.data.AppDatabase.getInstance(context).documentEntityDao().getForDocument(entry.id)
            }.getOrDefault(emptyList())
        }
    }

    val category = runCatching { VaultCategory.valueOf(entry.category) }.getOrDefault(VaultCategory.OTHER)
    val isReceipt = category == VaultCategory.RECEIPT
    val glowColor = if (entry.isLinkedToExpense) LumeEmerald else categoryColor(category)
    val date = remember(entry.dateEpoch) { LocalDate.ofEpochDay(entry.dateEpoch) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E1117)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isReceipt || entry.mimeType.startsWith("image")) {
                AsyncReceiptImage(
                    imagePath = entry.imagePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(glowColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (entry.mimeType.contains("pdf")) Icons.Rounded.PictureAsPdf
                                      else Icons.Rounded.Description,
                        contentDescription = null,
                        tint = glowColor,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Text(
                text = entry.documentTitle ?: entry.merchantName ?: "Untitled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                entry.totalAmount?.let { amount ->
                    Column {
                        Text("Amount", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("€${"%.2f".format(amount)}", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold, color = ConfigAccent)
                    }
                }
                Column {
                    Text("Date", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(date.format(dateFmt), style = MaterialTheme.typography.bodyLarge, color = TextMain)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(glowColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("${category.emoji} ${category.label}", style = MaterialTheme.typography.labelSmall, color = glowColor)
                }
                if (isReceipt) {
                    val linked = entry.isLinkedToExpense
                    Box(
                        modifier = Modifier
                            .background(
                                (if (linked) LumeEmerald else LumeAmber).copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (linked) "Linked to expense" else "Unlinked",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (linked) LumeEmerald else LumeAmber
                        )
                    }
                }
            }

            ExtractedInfoSection(extractedEntities)

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!entry.mimeType.startsWith("image")) {
                    Button(
                        onClick = { openFileExternally(context, entry.imagePath, entry.mimeType) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = glowColor.copy(alpha = 0.15f)),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, tint = glowColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open", color = glowColor)
                    }
                }
                Button(
                    onClick = { showTaxTagPicker = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = LumeEmerald.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, tint = LumeEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(currentTag ?: "Tag", color = LumeEmerald, maxLines = 1)
                }
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B4B).copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete document?", color = TextMain) },
            text = { Text("This cannot be undone.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete(entry) }) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = Color(0xFF0E1117)
        )
    }

    if (showTaxTagPicker) {
        val taxCategories = listOf("Work Equipment", "Home Office", "Medical Expenses", "Donations", "Work Commute", "Other")
        val year = LocalDate.ofEpochDay(entry.dateEpoch).year
        AlertDialog(
            onDismissRequest = { showTaxTagPicker = false },
            title = { Text("Tag for Tax", color = TextMain) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    taxCategories.forEach { cat ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.household.app.data.AppDatabase.getInstance(context).taxTagDao().upsert(
                                            TaxTagEntity(
                                                entityType = "vault_doc",
                                                entityId = entry.id,
                                                taxCategory = cat,
                                                year = year
                                            )
                                        )
                                    }
                                    currentTag = cat
                                }
                                showTaxTagPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (cat == currentTag) "✓ $cat" else cat,
                                color = if (cat == currentTag) LumeEmerald else TextSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTaxTagPicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF0E1117)
        )
    }
}

private fun openFileExternally(context: android.content.Context, path: String, mimeType: String) {
    try {
        val file = java.io.File(path)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("VaultScreen", "Cannot open file: ${e.message}")
        android.widget.Toast.makeText(context, "Cannot open file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// ── Async image loader ─────────────────────────────────────────────────────────

@Composable
private fun AsyncReceiptImage(imagePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = imagePath) {
        value = withContext(Dispatchers.IO) {
            if (imagePath.isBlank()) return@withContext null
            if (imagePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(imagePath))
                    ?.use { BitmapFactory.decodeStream(it) }
            } else {
                BitmapFactory.decodeFile(imagePath)
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(EliteNavy.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Description, null, tint = TextMuted, modifier = Modifier.size(36.dp))
        }
    }
}

// ── Document Expiry Timeline Card ─────────────────────────────────────────────

@Composable
private fun DocumentExpiryTimelineCard(
    alerts: List<com.household.app.data.entities.DocumentAlertEntity>
) {
    val sorted = alerts.sortedBy { it.daysUntil }
    val visible = sorted.take(5)
    val overflow = sorted.size - 5

    EliteGlassCard(
        glowColor = LumeAmber.copy(alpha = 0.3f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = LumeAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Next 90 Days",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = TextMain
            )
        }

        Spacer(Modifier.height(8.dp))

        // Alert rows
        visible.forEach { alert ->
            val dotColor = when {
                alert.daysUntil <= 7  -> CriticalRed
                alert.daysUntil <= 30 -> LumeAmber
                else                  -> LumeEmerald
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "in ${alert.daysUntil} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // Overflow indicator
        if (overflow > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "+ $overflow more",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

// ── Subscription Hub Shortcut Card ────────────────────────────────────────────

@Composable
private fun SubscriptionHubShortcutCard(onClick: () -> Unit) {
    EliteGlassCard(
        glowColor = LumeCyan.copy(alpha = 0.14f),
        borderAlpha = 0.14f,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                    text = "RECURRING",
                    style = MaterialTheme.typography.labelSmall,
                    color = LumeWhite.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Subscription Hub",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Fixed costs, contracts and monthly commitments",
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

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun categoryColor(category: VaultCategory): Color = when (category) {
    VaultCategory.RECEIPT      -> LumeAmber
    VaultCategory.IDENTITY     -> LumePurple
    VaultCategory.CONTRACT     -> Color(0xFF60A5FA)
    VaultCategory.PROPERTY     -> Color(0xFFF59E0B)
    VaultCategory.UTILITY_BILL -> Color(0xFF34D399)
    VaultCategory.INSURANCE    -> Color(0xFFFB7185)
    VaultCategory.MEDICAL      -> Color(0xFF4ADE80)
    VaultCategory.OTHER        -> LumeWhite.copy(alpha = 0.5f)
}

// ── Extracted Info Section ────────────────────────────────────────────────────

@Composable
private fun ExtractedInfoSection(entities: List<com.household.app.data.entities.VaultDocumentEntityRecord>) {
    if (entities.isEmpty()) return

    // Filter to the most useful display entities, highest confidence first
    val displayTypes = setOf(
        "FULL_NAME", "DATE_OF_BIRTH", "EXPIRY_DATE", "ISSUE_DATE",
        "PASSPORT_NUMBER", "AADHAAR_NUMBER", "PAN_NUMBER", "VOTER_ID_NUMBER",
        "DRIVING_LICENCE_NUMBER", "OCI_NUMBER", "DOCUMENT_NUMBER",
        "NATIONALITY", "STEUERNUMMER", "STEUER_ID", "IBAN", "POLICY_NUMBER",
        "MONTHLY_COST", "EMPLOYER_NAME", "ADDRESS"
    )

    val displayEntities = entities
        .filter { it.entityType in displayTypes }
        .sortedByDescending { it.confidence }
        .distinctBy { it.entityType }   // one row per type, highest confidence wins
        .take(8)                         // cap at 8 rows to avoid overwhelming the sheet

    if (displayEntities.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Header row — tappable to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = LumeCyan,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    "Extracted Info",
                    style = MaterialTheme.typography.labelMedium,
                    color = LumeCyan,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "(${displayEntities.size} fields)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp
                else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.04f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayEntities.forEach { entity ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = formatEntityType(entity.entityType),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.weight(0.42f)
                        )
                        Text(
                            text = entity.normalizedValue.ifBlank { entity.rawValue },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMain,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.58f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatEntityType(raw: String): String = when (raw) {
    "FULL_NAME"              -> "Name"
    "DATE_OF_BIRTH"          -> "Date of Birth"
    "EXPIRY_DATE"            -> "Expiry Date"
    "ISSUE_DATE"             -> "Issue Date"
    "PASSPORT_NUMBER"        -> "Passport No."
    "AADHAAR_NUMBER"         -> "Aadhaar No."
    "PAN_NUMBER"             -> "PAN No."
    "VOTER_ID_NUMBER"        -> "Voter ID"
    "DRIVING_LICENCE_NUMBER" -> "DL Number"
    "OCI_NUMBER"             -> "OCI No."
    "DOCUMENT_NUMBER"        -> "Doc Number"
    "NATIONALITY"            -> "Nationality"
    "STEUERNUMMER"           -> "Steuernummer"
    "STEUER_ID"              -> "Steuer-ID"
    "IBAN"                   -> "IBAN"
    "POLICY_NUMBER"          -> "Policy No."
    "MONTHLY_COST"           -> "Monthly Cost"
    "EMPLOYER_NAME"          -> "Employer"
    "ADDRESS"                -> "Address"
    else -> raw.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
