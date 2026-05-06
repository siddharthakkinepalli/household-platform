package com.household.app.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.ui.viewmodels.Transaction
import com.household.app.ui.viewmodels.ExpensesViewModel
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import kotlin.math.abs

@Composable
fun V2FinanceScreen(
    viewModel: ExpensesViewModel = viewModel()
) {
    val transactions by viewModel.recentTransactions.observeAsState(emptyList())
    val totalSpend = transactions.sumOf { abs(it.amount) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EliteNavy)
            .padding(16.dp)
    ) {
        Text(
            text = "Finances",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextMain
        )
        Text("Current Salary Cycle", color = TextSecondary)

        Spacer(Modifier.height(24.dp))

        EliteGlassCard(glowColor = LumePurple) {
            Text("Total Monthly Spend", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "-EUR${"%.2f".format(totalSpend)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain
            )

            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(color = TextMain.copy(alpha = 0.1f), shape = CircleShape)
            ) {
                val ratio = (totalSpend / 100.0).coerceIn(0.1, 1.0).toFloat()
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .background(LumePurple, shape = CircleShape)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text("Recent Activity", color = TextMain, fontWeight = FontWeight.SemiBold)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            items(transactions, key = { it.id }) { transaction ->
                TransactionRow(transaction)
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(TextMain.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ShoppingCart, contentDescription = null, tint = LumePurple)
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(transaction.description, color = TextMain, fontWeight = FontWeight.Medium)
            Text(transaction.date, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text("-EUR${"%.2f".format(abs(transaction.amount))}", color = TextMain, fontWeight = FontWeight.Bold)
    }
}
