package com.household.app.ui.v2

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.household.app.data.AppDatabase
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.DocumentType
import com.household.app.data.repository.DocumentRepositoryImpl
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.AddDocumentSheet
import com.household.app.ui.v2.components.EliteGlassCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// ── ViewModel ────────────────────────────────────────────────────────────────

class DocumentsViewModel(
    app: Application,
    private val ownerIdFilter: Long? = null
) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repo = DocumentRepositoryImpl(
        documentDao = db.documentDao(),
        documentAlertDao = db.documentAlertDao()
    )

    val documents: StateFlow<List<DocumentEntity>> = (
        if (ownerIdFilter != null) {
            db.documentDao().getDocumentsForOwner(ownerIdFilter)
        } else {
            repo.getAllDocuments()
        }
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addDocument(doc: DocumentEntity) {
        viewModelScope.launch { repo.insertDocument(doc) }
    }

    fun updateDocument(doc: DocumentEntity) {
        viewModelScope.launch { repo.updateDocument(doc) }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch { repo.deleteDocument(id) }
    }

    fun deleteDocuments(ids: List<Long>) {
        viewModelScope.launch { ids.forEach { repo.deleteDocument(it) } }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentsScreen(
    navController: NavController,
    ownerId: Long? = null,
    screenTitle: String = "Documents",
    viewModel: DocumentsViewModel = viewModel(
        key = "documents-${ownerId ?: "all"}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = navController.context.applicationContext as Application
                return DocumentsViewModel(app, ownerId) as T
            }
        }
    )
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val isMultiSelect = selectedIds.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    text = if (isMultiSelect)
                        "${selectedIds.size} selected — long-press to deselect"
                    else
                        "${documents.size} document${if (documents.size != 1) "s" else ""}",
                    color = if (isMultiSelect) LumeCyan else TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(20.dp))

            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No documents yet", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Tap + to track passports, contracts and IDs",
                            color = TextMuted.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = documents, key = { it.id }) { doc ->
                        val isSelected = selectedIds.contains(doc.id)

                        if (isMultiSelect) {
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (isSelected) selectedIds.remove(doc.id)
                                        else selectedIds.add(doc.id)
                                    },
                                    onLongClick = {
                                        if (isSelected) selectedIds.remove(doc.id)
                                        else selectedIds.add(doc.id)
                                    }
                                )
                            ) {
                                DocumentCard(doc = doc, isSelected = isSelected)
                            }
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteDocument(doc.id)
                                        true
                                    } else false
                                }
                            )

                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = { editingDoc = doc },
                                    onLongClick = { selectedIds.add(doc.id) }
                                )
                            ) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        val color by animateColorAsState(
                                            targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                                CriticalRed.copy(alpha = 0.85f) else Color.Transparent,
                                            animationSpec = tween(200),
                                            label = "swipe_bg"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(color, RoundedCornerShape(20.dp))
                                                .padding(end = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    },
                                    content = {
                                        DocumentCard(doc = doc, isSelected = false)
                                    }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        if (!isMultiSelect) {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = LumeCyan,
                contentColor = Color(0xFF07090D),
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add document")
            }
        }

        DocumentMultiSelectBar(
            selectedCount = selectedIds.size,
            modifier = Modifier.align(Alignment.BottomCenter),
            onDelete = {
                viewModel.deleteDocuments(selectedIds.toList())
                selectedIds.clear()
            },
            onCancel = { selectedIds.clear() }
        )
    }

    if (showAddSheet) {
        AddDocumentSheet(
            onDismiss = { showAddSheet = false },
            onSave = { doc ->
                viewModel.addDocument(doc)
                showAddSheet = false
            }
        )
    }

    editingDoc?.let { doc ->
        AddDocumentSheet(
            onDismiss = { editingDoc = null },
            existingDocument = doc,
            onSave = { updated ->
                viewModel.updateDocument(updated)
                editingDoc = null
            }
        )
    }
}

// ── Multi-select bar ──────────────────────────────────────────────────────────

@Composable
private fun DocumentMultiSelectBar(
    selectedCount: Int,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
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
                .background(
                    Color(0xFF1A1F2E),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCount selected",
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, "Delete selected", tint = Color(0xFFEF4444))
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, "Cancel selection", tint = TextMuted)
                }
            }
        }
    }
}

// ── Document Card ─────────────────────────────────────────────────────────────

@Composable
private fun DocumentCard(doc: DocumentEntity, isSelected: Boolean = false) {
    val expiryInfo = doc.expiryDate?.let { computeExpiryInfo(it) }
    val badgeColor = expiryInfo?.color ?: Color.Transparent
    val typeIcon = doc.type.icon()
    val typeColor = doc.type.color()

    EliteGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(1.5.dp, LumeCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                else Modifier
            ),
        glowColor = if (isSelected) LumeCyan.copy(alpha = 0.22f) else badgeColor.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isSelected) LumeCyan.copy(alpha = 0.15f) else typeColor.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = LumeCyan,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = doc.type.displayName(),
                        color = typeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    )
                    if (doc.monthlyCost != null) {
                        Text("·", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "€${"%.2f".format(doc.monthlyCost)}/mo",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (expiryInfo != null && !isSelected) {
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(expiryInfo.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = expiryInfo.label,
                            color = expiryInfo.color,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                    if (doc.expiryDate != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatEpochDate(doc.expiryDate),
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private data class ExpiryInfo(val label: String, val color: Color)

private fun computeExpiryInfo(expiryEpoch: Long): ExpiryInfo {
    val now = Instant.now()
    val expiry = Instant.ofEpochMilli(expiryEpoch)
    val days = ChronoUnit.DAYS.between(now, expiry).toInt()

    return when {
        days < 0 -> ExpiryInfo("Expired ${abs(days)}d ago", CriticalRed)
        days < 7 -> ExpiryInfo("${days}d left", CriticalRed)
        days < 30 -> ExpiryInfo("${days}d left", LumeAmber)
        else -> ExpiryInfo("${days}d left", LumeEmerald)
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatEpochDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)

private fun DocumentType.icon(): ImageVector = when (this) {
    DocumentType.CONTRACT -> Icons.Rounded.Article
    DocumentType.ID -> Icons.Rounded.Badge
    DocumentType.MEDICAL -> Icons.Rounded.LocalHospital
    DocumentType.PROPERTY -> Icons.Rounded.Home
    DocumentType.OTHER -> Icons.Rounded.FolderOpen
}

private fun DocumentType.color(): Color = when (this) {
    DocumentType.CONTRACT -> LumeCyan
    DocumentType.ID -> Color(0xFF8B5CF6)
    DocumentType.MEDICAL -> Color(0xFFEC4899)
    DocumentType.PROPERTY -> LumeAmber
    DocumentType.OTHER -> TextSecondary
}

private fun DocumentType.displayName(): String = when (this) {
    DocumentType.CONTRACT -> "CONTRACT"
    DocumentType.ID -> "ID"
    DocumentType.MEDICAL -> "MEDICAL"
    DocumentType.PROPERTY -> "PROPERTY"
    DocumentType.OTHER -> "OTHER"
}
