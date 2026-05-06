package com.household.app.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.ui.compose.components.AlertItem
import com.household.app.ui.compose.components.InsightCard
import com.household.app.ui.compose.components.ModuleGrid
import com.household.app.ui.compose.components.QuickCaptureFab
import com.household.app.ui.compose.components.TodayAlertsRow
import com.household.app.ui.compose.components.TodayItem
import com.household.app.ui.compose.state.HomeIntent
import com.household.app.ui.compose.state.HomeViewModel
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.DeepBackground
import com.household.app.ui.v2.components.GlassCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun V2HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    val todayItems = listOf(
        TodayItem("Add household task", "10:00 AM"),
        TodayItem("Review meal plan", "7:00 PM"),
        TodayItem("Archive receipt", "9:30 AM")
    )
    val alertItems = listOf(
        AlertItem("Connect your own wallet data source", isUrgent = true),
        AlertItem("Review repository usage notice", isUrgent = true),
        AlertItem("Replace placeholders before release", isUrgent = false)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        DeepBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.insights.firstOrNull()?.let { topInsight ->
                InsightCard(
                    insight = topInsight,
                    onDismiss = { viewModel.handle(HomeIntent.DismissInsight(topInsight.id)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Good day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = todayDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )

                    if (state.loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(
                            text = state.balanceFormatted,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Private placeholder data",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            TodayAlertsRow(
                todayItems = todayItems,
                alerts = alertItems,
                modifier = Modifier.fillMaxWidth()
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Modules",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    ModuleGrid(
                        modules = viewModel.modules,
                        onModuleTap = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Public Repository Notice",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "This v2 surface reuses the same ViewModel logic while keeping public builds on placeholder content. JUGAAD assets and branded app content are copyrighted and are not licensed for republishing in other apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }

            Spacer(Modifier.height(88.dp))
        }

        QuickCaptureFab(modifier = Modifier.fillMaxSize())
    }
}