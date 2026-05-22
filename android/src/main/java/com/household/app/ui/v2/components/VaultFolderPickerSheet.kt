package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.domain.models.vault.VaultFolderPath
import com.household.app.domain.models.vault.VaultFolderTree
import com.household.app.domain.models.vault.VaultSubFolder
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFolderPickerSheet(
    title: String,
    members: List<FamilyMemberEntity>,
    initialFolder: VaultFolderPath,
    onDismiss: () -> Unit,
    onConfirm: (VaultFolderPath, String) -> Unit,
    showTitleField: Boolean = true,
    initialDocumentTitle: String = "",
    onAddMember: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()
    var docTitle by remember { mutableStateOf(initialDocumentTitle) }
    var selectedCategory by remember { mutableStateOf(initialFolder.category) }
    var selectedMemberId by remember { mutableStateOf(initialFolder.ownerMemberId) }
    var selectedSubFolder by remember {
        mutableStateOf(VaultSubFolder.fromId(initialFolder.subFolder))
    }

    val memberOptions = remember(members) {
        listOf(null to VaultFolderTree.HOUSEHOLD_LABEL) +
            members.map { it.id to it.name }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EliteNavy,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )

            if (showTitleField) {
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Document title (optional)", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                        focusedBorderColor = LumeAmber,
                        unfocusedBorderColor = LumeWhite.copy(alpha = 0.2f),
                        cursorColor = LumeAmber
                    )
                )
            }

            PickerSection(label = "Category") {
                VaultCategory.entries.forEach { cat ->
                    PickerChip(
                        label = "${cat.emoji} ${cat.label}",
                        selected = cat == selectedCategory,
                        onClick = {
                            selectedCategory = cat
                            selectedSubFolder = VaultSubFolder.forCategory(cat).firstOrNull()
                                ?: VaultSubFolder.UNFILED
                        }
                    )
                }
            }

            if (VaultFolderTree.categoryUsesMemberLevel(selectedCategory)) {
                PickerSection(label = "Person") {
                    if (members.isEmpty() && onAddMember != null) {
                        PickerChip(
                            label = "Add household member first",
                            selected = false,
                            onClick = onAddMember
                        )
                    }
                    memberOptions.forEach { (id, label) ->
                        PickerChip(
                            label = label,
                            selected = selectedMemberId == id,
                            onClick = { selectedMemberId = id }
                        )
                    }
                }
            }

            PickerSection(label = "Folder") {
                val subs = VaultSubFolder.forCategory(selectedCategory) + VaultSubFolder.UNFILED
                subs.forEach { sub ->
                    PickerChip(
                        label = sub.label,
                        selected = sub == selectedSubFolder,
                        onClick = { selectedSubFolder = sub }
                    )
                }
            }

            Button(
                onClick = {
                    onConfirm(
                        VaultFolderPath(
                            category = selectedCategory,
                            ownerMemberId = if (VaultFolderTree.categoryUsesMemberLevel(selectedCategory)) {
                                selectedMemberId
                            } else null,
                            subFolder = selectedSubFolder.id
                        ),
                        docTitle
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LumeAmber)
            ) {
                Text("Save", color = EliteNavy, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PickerSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LumeWhite.copy(alpha = 0.6f)
        )
        content()
    }
}

@Composable
private fun PickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) LumeAmber.copy(alpha = 0.2f) else LumeWhite.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (selected) LumeAmber else LumeWhite.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) LumeAmber else TextMuted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultMoveToFolderSheet(
    members: List<FamilyMemberEntity>,
    onDismiss: () -> Unit,
    onConfirm: (VaultFolderPath) -> Unit
) {
    VaultFolderPickerSheet(
        title = "Move to folder",
        members = members,
        initialFolder = VaultFolderPath(VaultCategory.OTHER),
        onDismiss = onDismiss,
        onConfirm = { folder, _ -> onConfirm(folder) },
        showTitleField = false
    )
}
