package com.household.app.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.Expense
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain

@Composable
fun ScanResultSheet(
    result: RefinedScan,
    candidates: List<Expense>,
    onLinkConfirmed: (Expense) -> Unit,
    onSaveOnly: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Text(
            text = "Verify Scan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextMain
        )

        EliteGlassCard(
            modifier = Modifier.padding(top = 12.dp),
            glowColor = if (result.compositeConfidence > 0.8f) LumeEmerald else LumeAmber
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultRow("Merchant", result.merchant.value ?: "Unknown", result.merchant.confidence)
                ResultRow("Amount", "${"%.2f".format(result.amount.value ?: 0.0)} €", result.amount.confidence)
                ResultRow("Date", "${result.date.value ?: "Unknown"}", result.date.confidence)
            }
        }

        if (candidates.isNotEmpty()) {
            Text(
                text = "Suggested Wallet Matches",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { expense ->
                    CandidateRow(expense = expense) {
                        onLinkConfirmed(expense)
                    }
                }
            }
        }

        Button(
            onClick = onSaveOnly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.3f))
        ) {
            Text("Just Save to Vault", color = TextMain)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, confidence: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMain.copy(alpha = 0.62f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextMain)
        }
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (confidence > 0.8f) LumeEmerald else LumeAmber
        )
    }
}

@Composable
private fun CandidateRow(expense: Expense, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(expense.description, color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${expense.date} • ${expense.category}",
                color = TextMain.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Text(
            text = "€${"%.2f".format(expense.amount)}",
            color = TextMain,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(88.dp)
        )
    }
}
