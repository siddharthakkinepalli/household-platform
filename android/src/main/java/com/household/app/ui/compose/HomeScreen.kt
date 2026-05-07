package com.household.app.ui.compose

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.ui.compose.components.AlertItem
import com.household.app.ui.compose.components.HeroCard
import com.household.app.ui.compose.components.InsightCard
import com.household.app.ui.compose.components.ModuleGrid
import com.household.app.ui.compose.components.QuickCaptureFab
import com.household.app.ui.compose.components.TodayAlertsRow
import com.household.app.ui.compose.components.TodayItem
import com.household.app.ui.compose.state.HomeIntent
import com.household.app.ui.compose.state.HomeViewModel
import com.household.app.ui.compose.theme.BgBase
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * HomeScreen — the main Compose screen replacing HomeFragment.
 *
 * Layout (top to bottom):
 *   1. InsightCard (hidden when no insights)
 *   2. Greeting row
 *   3. Hero balance card
 *   4. Today + Alerts section (adaptive: stacked/side-by-side)
 *   5. Module grid (adaptive: 2 col phone / 3 col tablet)
 *   FAB overlaid at bottom-right via Box
 *
 * Data: collected from HomeViewModel via StateFlow (lifecycle-aware).
 * Phase 2: placeholder balance + public-repo notice from ViewModel.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val todayDate = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    val todayItems = listOf(
        TodayItem("Review rent payment", "09:00 AM"),
        TodayItem("Finalize dinner plan", "07:00 PM"),
        TodayItem("Archive utility receipt", "09:30 PM")
    )
    val alertItems = listOf(
        AlertItem("Rent is due in 2 days", isUrgent = true),
        AlertItem("Insurance renewal in 12 days", isUrgent = true),
        AlertItem("Meal plan has one unfilled dinner slot", isUrgent = false)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            val topInsight = state.insights.firstOrNull()
            if (topInsight != null) {
                InsightCard(
                    insight   = topInsight,
                    onDismiss = { viewModel.handle(HomeIntent.DismissInsight(topInsight.id)) },
                    onTap     = if (topInsight.action.isNotBlank()) ({ onNavigate(topInsight.action) }) else null,
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }

            GreetingRow(name = state.userName, dateText = todayDate)
            Spacer(Modifier.height(16.dp))

            if (state.loading) {
                Box(
                    modifier          = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment  = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HeroCard(
                    balanceFormatted = state.balanceFormatted,
                    deltaPercent     = state.deltaPercent,
                    modifier         = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))

            // 4. Today + Alerts
            TodayAlertsRow(
                todayItems = todayItems,
                alerts     = alertItems,
                modifier   = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            Text(
                text  = "Modules",
                style = TextStyle(
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Medium,
                    color         = TextMuted,
                    letterSpacing = 0.88.sp
                )
            )
            Spacer(Modifier.height(12.dp))

            ModuleGrid(
                modules      = viewModel.modules,
                onModuleTap  = onNavigate,
                modifier     = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(88.dp))
        }

        QuickCaptureFab(
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun GreetingRow(name: String, dateText: String) {
    androidx.compose.foundation.layout.Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = "Good morning, $name",
            style = TextStyle(
                fontSize   = 14.sp,
                fontWeight = FontWeight.Normal,
                color      = TextSecondary
            )
        )
        Text(
            text  = dateText,
            style = TextStyle(
                fontSize   = 13.sp,
                fontWeight = FontWeight.Normal,
                color      = TextMuted
            )
        )
    }
}
