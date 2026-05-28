package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.DocumentType
import com.household.app.domain.models.vault.VaultFolderTree
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentSheet(
    onDismiss: () -> Unit,
    onSave: (DocumentEntity) -> Unit,
    ownerMemberId: Long? = null,
    ownerOptions: List<Pair<Long?, String>> = emptyList(),
    existingDocument: DocumentEntity? = null
) {
    val isEditing = existingDocument != null
    val sheetState = rememberModalBottomSheetState()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val initialExpiryText = existingDocument?.expiryDate?.let {
        dateFormat.format(Date(it))
    } ?: ""

    var title by remember { mutableStateOf(existingDocument?.title ?: "") }
    var selectedType by remember { mutableStateOf(existingDocument?.type ?: DocumentType.OTHER) }
    var expiryDateText by remember { mutableStateOf(initialExpiryText) }
    var noticePeriodDays by remember { mutableFloatStateOf(existingDocument?.noticePeriodDays?.toFloat() ?: 30f) }
    var monthlyCost by remember { mutableStateOf(existingDocument?.monthlyCost?.let { "%.2f".format(it) } ?: "") }
    var notes by remember { mutableStateOf(existingDocument?.notes ?: "") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var ownerMenuExpanded by remember { mutableStateOf(false) }
    var selectedOwnerId by remember(ownerMemberId) { mutableStateOf(existingDocument?.ownerId ?: ownerMemberId) }
    var expiryError by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = LumeCyan,
        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
        focusedTextColor = TextMain,
        unfocusedTextColor = TextMain,
        cursorColor = LumeCyan,
        focusedLabelColor = LumeCyan,
        unfocusedLabelColor = TextMuted
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10141D),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (isEditing) "Edit Document" else "Add Document",
                color = TextMain,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Track expiry dates, costs and contracts",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(4.dp))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("e.g. Passport, Netflix contract", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                singleLine = true
            )

            if (ownerOptions.isNotEmpty()) {
                val ownerLabel = ownerOptions.firstOrNull { it.first == selectedOwnerId }?.second
                    ?: VaultFolderTree.HOUSEHOLD_LABEL
                ExposedDropdownMenuBox(
                    expanded = ownerMenuExpanded,
                    onExpandedChange = { ownerMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = ownerLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Owner") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ownerMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = ownerMenuExpanded,
                        onDismissRequest = { ownerMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF10141D))
                    ) {
                        ownerOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = TextMain) },
                                onClick = {
                                    selectedOwnerId = id
                                    ownerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Type dropdown
            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedType.displayName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Document Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = fieldColors
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                    modifier = Modifier.background(Color(0xFF10141D))
                ) {
                    DocumentType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName(), color = TextMain) },
                            onClick = {
                                selectedType = type
                                typeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Expiry date
            OutlinedTextField(
                value = expiryDateText,
                onValueChange = {
                    expiryDateText = it
                    expiryError = false
                },
                label = { Text("Expiry Date (optional, dd/MM/yyyy)") },
                placeholder = { Text("31/12/2026", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (expiryError) OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFEF4444),
                    unfocusedBorderColor = Color(0xFFEF4444),
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                    cursorColor = Color(0xFFEF4444),
                    focusedLabelColor = Color(0xFFEF4444),
                    unfocusedLabelColor = TextMuted
                ) else fieldColors,
                isError = expiryError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                supportingText = if (expiryError) {
                    { Text("Enter date as dd/MM/yyyy", color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall) }
                } else null
            )

            // Notice period slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Notice Period",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${noticePeriodDays.toInt()} days",
                        color = LumeCyan,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = noticePeriodDays,
                    onValueChange = { noticePeriodDays = it },
                    valueRange = 1f..90f,
                    steps = 88,
                    colors = SliderDefaults.colors(
                        thumbColor = LumeCyan,
                        activeTrackColor = LumeCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 day", color = TextMuted, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    Text("90 days", color = TextMuted, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
            }

            // Monthly cost
            OutlinedTextField(
                value = monthlyCost,
                onValueChange = { monthlyCost = it },
                label = { Text("Monthly Cost (optional)") },
                placeholder = { Text("e.g. 9.99", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Additional details", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    if (trimmedTitle.isEmpty()) return@Button

                    val expiryEpoch: Long? = if (expiryDateText.isNotBlank()) {
                        try {
                            dateFormat.isLenient = false
                            dateFormat.parse(expiryDateText.trim())?.time
                        } catch (e: Exception) {
                            expiryError = true
                            null
                        }
                    } else null

                    if (expiryError) return@Button

                    val now = System.currentTimeMillis()
                    onSave(
                        DocumentEntity(
                            id = existingDocument?.id ?: 0,
                            ownerId = selectedOwnerId,
                            title = trimmedTitle,
                            type = selectedType,
                            expiryDate = expiryEpoch,
                            noticePeriodDays = noticePeriodDays.toInt(),
                            monthlyCost = monthlyCost.trim().toDoubleOrNull(),
                            notes = notes.trim().ifEmpty { null },
                            createdAt = existingDocument?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LumeEmerald,
                    disabledContainerColor = LumeEmerald.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (isEditing) "Update Document" else "Save Document",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun DocumentType.displayName(): String = when (this) {
    DocumentType.CONTRACT -> "Contract"
    DocumentType.ID -> "ID / Passport"
    DocumentType.MEDICAL -> "Medical"
    DocumentType.PROPERTY -> "Property"
    DocumentType.OTHER -> "Other"
}

