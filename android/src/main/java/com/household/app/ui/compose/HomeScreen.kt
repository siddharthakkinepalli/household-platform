package com.household.app.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.household.app.ui.compose.theme.BgBottomGlow
import com.household.app.ui.compose.theme.BgTopGlow
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
 * Phase 2: mock balance + mock insight from ViewModel.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val todayDate = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    // Phase 2 mock data for Today + Alerts sections
    val todayItems = listOf(
        TodayItem("Grocery run",      "10:00 AM"),
        TodayItem("Dinner with kids", "7:00 PM"),
        TodayItem("Dentist (Priya)",  "9:30 AM")
    )
    val alertItems = listOf(
        AlertItem("Rent due in 3 days",       isUrgent = true),
        AlertItem("Netflix renewal tomorrow", isUrgent = true),
        AlertItem("Grocery budget 80% used",  isUrgent = false)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgTopGlow, BgBase, BgBottomGlow)
                )
            )
    ) {
        HomeBackdrop()

        // ── Scrollable content ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 1. Insight card — hidden when list is empty
            val topInsight = state.insights.firstOrNull()
            if (topInsight != null) {
                InsightCard(
                    insight   = topInsight,
                    onDismiss = { viewModel.handle(HomeIntent.DismissInsight(topInsight.id)) },
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }

            // 2. Greeting row
            GreetingRow(dateText = todayDate)
            Spacer(Modifier.height(16.dp))

            // 3. Hero balance card
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

            // 5. Module section header
            Text(
                text  = "Modules",
                style = TextStyle(
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Medium,
                    color         = TextMuted,
                    letterSpacing = 0.08.sp
                )
            )
            Spacer(Modifier.height(12.dp))

            // 5b. Module grid
            ModuleGrid(
                modules      = viewModel.modules,
                onModuleTap  = { /* navigation handled by AppShell via NavHost */ },
                modifier     = Modifier.fillMaxWidth()
            )

            // Bottom padding so last card isn't covered by FAB
            Spacer(Modifier.height(88.dp))
        }

        // ── FAB overlay ────────────────────────────────────────────────────
        QuickCaptureFab(
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun BoxScope.HomeBackdrop() {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = (-84).dp, y = (-48).dp)
            .size(220.dp)
            .blur(28.dp)
            .background(
                color = Color(0x3322C55E),
                shape = CircleShape
            )
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 40.dp, y = 24.dp)
            .size(260.dp)
            .blur(40.dp)
            .background(
                color = Color(0x228B5CF6),
                shape = CircleShape
            )
    )
}

@Composable
private fun GreetingRow(dateText: String) {
    androidx.compose.foundation.layout.Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = "Good morning",
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
