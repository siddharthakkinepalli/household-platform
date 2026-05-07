package com.household.app.ui.v2.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.household.app.domain.models.Expense
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.ScanField
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.SurfaceNavy
import com.household.app.ui.compose.theme.TextMain
import java.time.LocalDate

data class ScanConfirmationState(
    val refinedScan: RefinedScan,
    val candidates: List<Expense> = emptyList(),
    val vaultId: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScanSheet(
    state: ScanConfirmationState,
    onLinkConfirmed: (Long, Long, RefinedScan) -> Unit,
    onManualSave: (RefinedScan) -> Unit,
    onDismiss: () -> Unit
) {
    var merchantText by remember(state.refinedScan) {
        mutableStateOf(state.refinedScan.merchant.value.orEmpty())
    }
    var amountText by remember(state.refinedScan) {
        mutableStateOf("%.2f".format(state.refinedScan.amount.value ?: 0.0))
    }
    var dateText by remember(state.refinedScan) {
        mutableStateOf((state.refinedScan.date.value ?: LocalDate.now()).toString())
    }

    fun buildConfirmedScan(): RefinedScan {
        val parsedAmount = amountText.replace(",", ".").toDoubleOrNull()
        val parsedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()

        val merchantEdited = merchantText != (state.refinedScan.merchant.value ?: "")
        val amountEdited = parsedAmount != (state.refinedScan.amount.value ?: 0.0)
        val dateEdited = parsedDate != (state.refinedScan.date.value ?: LocalDate.now())

        return state.refinedScan.copy(
            merchant = ScanField(
                value = merchantText.ifBlank { state.refinedScan.merchant.value },
                confidence = if (merchantEdited) 1f else state.refinedScan.merchant.confidence
            ),
            amount = ScanField(
                value = parsedAmount,
                confidence = if (amountEdited) 1f else state.refinedScan.amount.confidence
            ),
            date = ScanField(
                value = parsedDate,
                confidence = if (dateEdited) 1f else state.refinedScan.date.confidence
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceNavy.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Verify Scan",
                style = MaterialTheme.typography.headlineSmall,
                color = TextMain,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.padding(top = 8.dp))

            EditableFinancialCard(
                merchant = merchantText,
                amount = amountText,
                date = dateText,
                amountConfidence = state.refinedScan.amount.confidence,
                onMerchantChanged = { merchantText = it },
                onAmountChanged = { amountText = it },
                onDateChanged = { dateText = it }
            )

            Spacer(Modifier.padding(top = 20.dp))

            if (state.candidates.isNotEmpty()) {
                Text(
                    text = "Match found in Wallet",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMain.copy(alpha = 0.75f)
                )
                Spacer(Modifier.padding(top = 8.dp))

                state.candidates.forEach { expense ->
                    CandidateLinkRow(expense = expense) {
                        onLinkConfirmed(state.vaultId, expense.id.toLong(), buildConfirmedScan())
                    }
                }
            } else {
                Text(
                    text = "No matching transaction found. Saving to Vault only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.padding(top = 20.dp))

            Button(
                onClick = { onManualSave(buildConfirmedScan()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.3f))
            ) {
                Text(text = "Just Save to Vault", color = TextMain)
            }
        }
    }
}

@Composable
private fun EditableFinancialCard(
    merchant: String,
    amount: String,
    date: String,
    amountConfidence: Float,
    onMerchantChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onDateChanged: (String) -> Unit
) {
    val amountHighlight = amountConfidence < 0.6f

    EliteGlassCard(glowColor = if (amountHighlight) LumeAmber else LumeEmerald) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = merchant,
                onValueChange = onMerchantChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Merchant") },
                singleLine = true
            )

            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (amountHighlight) LumeAmber.copy(alpha = 0.14f) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    ),
                label = { Text("Amount (€)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = date,
                onValueChange = onDateChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
        }
    }
}

@Composable
private fun CandidateLinkRow(expense: Expense, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var animatePulse by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (animatePulse) 1.02f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "paperclip_scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (animatePulse) LumeEmerald else LumeEmerald.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 180),
        label = "paperclip_border"
    )

    LaunchedEffect(animatePulse) {
        if (animatePulse) {
            kotlinx.coroutines.delay(180)
            animatePulse = false
        }
    }

    Surface(
        color = LumeEmerald.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                animatePulse = true
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = LumeEmerald
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, style = MaterialTheme.typography.bodyMedium, color = TextMain)
                Text(
                    text = "${"%.2f".format(expense.amount)} € • ${expense.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMain.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("LINK", style = MaterialTheme.typography.labelMedium, color = LumeEmerald)
        }
    }
}
