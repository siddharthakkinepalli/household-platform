package com.household.app.ui.v2

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.data.entities.DocumentEntity
import com.household.app.domain.models.vault.VaultFolderTree
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextOnDark
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.AddDocumentSheet
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.EliteHeader
import com.household.app.ui.v2.components.FamilyMemberEditorSheet
import com.household.app.ui.viewmodels.FamilyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FamilyMemberDetailScreen(
    memberId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenVault: (Long) -> Unit,
    onOpenContracts: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: FamilyViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FamilyViewModel(context.applicationContext as Application) }
        }
    )
    val detail by remember(memberId) { viewModel.observeMemberDetail(memberId) }.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var showAddContract by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var contractToDelete by remember { mutableStateOf<Long?>(null) }

    val member = detail?.memberUi?.member
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            EliteHeader(
                title = member?.name ?: "Member",
                subtitle = member?.role,
                onBack = onBack
            )
        }

        if (detail != null && member != null) {
            val ui = detail!!.memberUi
            val glow = parseMemberColor(member.colorCode)

            item {
                EliteGlassCard(glowColor = glow.copy(alpha = 0.5f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(glow.copy(alpha = 0.2f), CircleShape)
                                .border(2.dp, glow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.initial(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextOnDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(member.name, style = MaterialTheme.typography.titleLarge, color = TextMain)
                        Text(member.role, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Vault docs", ui.vaultDocCount.toString(), Modifier.weight(1f))
                    StatCard("Contracts", ui.contractDocCount.toString(), Modifier.weight(1f))
                }
            }

            if (detail!!.expiringSoon.isNotEmpty()) {
                item {
                    SectionTitle("Expiring soon", Icons.Rounded.Warning, CriticalRed)
                }
                items(detail!!.expiringSoon, key = { "exp-${it.id}" }) { doc ->
                    DocumentRow(
                        title = doc.title,
                        subtitle = doc.expiryDate?.let { dateFormat.format(Date(it)) } ?: "No date",
                        accent = CriticalRed,
                        onClick = { onOpenContracts(memberId, member.name) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOpenVault(memberId) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = LumeCyan.copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Vault", color = EliteNavy, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onOpenContracts(memberId, member.name) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("All contracts")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Contracts & IDs", null, TextMain)
                    TextButton(onClick = { showAddContract = true }) {
                        Text("+ Add", color = LumeAmber)
                    }
                }
            }

            if (detail!!.contracts.isEmpty()) {
                item {
                    Text(
                        "No tracked contracts yet. Add passport expiry, insurance, or subscriptions.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(detail!!.contracts, key = { it.id }) { doc ->
                    DocumentRow(
                        title = doc.title,
                        subtitle = buildString {
                            append(doc.type.name.lowercase().replace('_', ' '))
                            doc.expiryDate?.let { append(" · exp ${dateFormat.format(Date(it))}") }
                            doc.monthlyCost?.let { append(" · €%.2f/mo".format(it)) }
                        },
                        accent = LumeAmber,
                        onClick = { onOpenContracts(memberId, member.name) },
                        onLongClick = { contractToDelete = doc.id }
                    )
                }
            }

            if (detail!!.vaultDocs.isNotEmpty()) {
                item {
                    SectionTitle("Vault files", Icons.Rounded.FolderOpen, LumeCyan)
                }
                items(detail!!.vaultDocs.take(6), key = { "vault-${it.id}" }) { entry ->
                    DocumentRow(
                        title = entry.documentTitle ?: entry.merchantName ?: "Document",
                        subtitle = VaultFolderTree.displayPath(entry, listOf(member)),
                        accent = LumeCyan,
                        onClick = { onOpenVault(memberId) }
                    )
                }
            }

            item {
                Button(
                    onClick = { showEditor = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LumeAmber)
                ) {
                    Text("Edit member", color = EliteNavy, fontWeight = FontWeight.Bold)
                }
            }

            item {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalRed)
                ) {
                    Text("Remove member")
                }
            }
        } else {
            item { Text("Member not found", color = TextMuted) }
        }

        item { Spacer(Modifier.size(24.dp)) }
    }

    if (showEditor && member != null) {
        FamilyMemberEditorSheet(
            existing = member,
            onDismiss = { showEditor = false },
            onSave = { name, role, color ->
                viewModel.updateMember(member.copy(name = name.trim(), role = role, colorCode = color))
                showEditor = false
            }
        )
    }

    if (showAddContract && member != null) {
        AddDocumentSheet(
            onDismiss = { showAddContract = false },
            onSave = { doc ->
                viewModel.addContract(doc.copy(ownerId = member.id))
                showAddContract = false
            },
            ownerMemberId = member.id,
            ownerOptions = listOf(member.id to member.name)
        )
    }

    if (showDeleteConfirm && member != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove ${member.name}?", color = TextMain) },
            text = {
                Text(
                    "Vault files become unassigned. Contract records stay but lose this owner.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMember(member.id)
                    showDeleteConfirm = false
                    onDeleted()
                }) {
                    Text("Remove", color = CriticalRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = EliteNavy
        )
    }

    contractToDelete?.let { docId ->
        AlertDialog(
            onDismissRequest = { contractToDelete = null },
            title = { Text("Delete contract?", color = TextMain) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteContract(docId)
                    contractToDelete = null
                }) {
                    Text("Delete", color = CriticalRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { contractToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = EliteNavy
        )
    }
}

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, tint = tint, modifier = Modifier.size(18.dp)) }
        Text(title, color = TextMain, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentRow(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    EliteGlassCard(
        glowColor = accent.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth().then(clickModifier)
    ) {
        Column {
            Text(title, color = TextMain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            if (onLongClick != null) {
                Text(
                    "Long-press to delete",
                    color = TextMuted.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    EliteGlassCard(glowColor = LumeCyan.copy(alpha = 0.35f), modifier = modifier) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LumeCyan
            )
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
