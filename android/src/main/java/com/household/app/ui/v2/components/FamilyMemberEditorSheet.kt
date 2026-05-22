package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.domain.models.FamilyRoles
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted

object FamilyColorPresets {
    val options = listOf(
        "#A78BFA" to LumePurple,
        "#FBBF24" to LumeAmber,
        "#34D399" to LumeEmerald,
        "#22D3EE" to LumeCyan,
        "#F472B6" to Color(0xFFF472B6),
        "#60A5FA" to Color(0xFF60A5FA)
    )
    val defaultHex = "#FBBF24"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FamilyMemberEditorSheet(
    existing: FamilyMemberEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, colorCode: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var role by remember(existing) { mutableStateOf(existing?.role ?: "Adult") }
    var colorCode by remember(existing) {
        mutableStateOf(existing?.colorCode ?: FamilyColorPresets.defaultHex)
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
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existing == null) "Add household member" else "Edit member",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            Text("Role", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FamilyRoles.ALL.forEach { option ->
                    RoleChip(
                        label = option,
                        selected = role == option,
                        onClick = { role = option }
                    )
                }
            }

            Text("Color", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FamilyColorPresets.options.forEach { (hex, swatch) ->
                    val selected = colorCode.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) LumeWhite else LumeWhite.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { colorCode = hex },
                        contentAlignment = Alignment.Center
                    ) {}
                }
            }

            Button(
                onClick = { onSave(name, role, colorCode) },
                enabled = name.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LumeAmber)
            ) {
                Text("Save", color = EliteNavy, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) LumeAmber.copy(alpha = 0.25f) else LumeWhite.copy(alpha = 0.08f)
            )
            .border(
                1.dp,
                if (selected) LumeAmber else LumeWhite.copy(alpha = 0.15f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) LumeAmber else TextMuted,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    focusedBorderColor = LumeAmber,
    unfocusedBorderColor = LumeWhite.copy(alpha = 0.2f),
    cursorColor = LumeAmber
)
