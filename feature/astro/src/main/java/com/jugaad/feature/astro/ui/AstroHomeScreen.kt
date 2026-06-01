package com.jugaad.feature.astro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jugaad.feature.astro.domain.model.EventAssessment
import com.jugaad.feature.astro.domain.model.LifeEventCategory
import com.jugaad.feature.astro.domain.model.Verdict
import com.jugaad.feature.astro.ui.state.AstroLoadState
import com.jugaad.feature.astro.ui.state.AstroUiState
import com.jugaad.feature.astro.ui.state.PlanetDisplayRow
import com.jugaad.feature.astro.ui.state.RahuKaalDisplay

@Composable
fun AstroHomeScreen(
    profileId: Long? = null,
    onNavigateToProfile: () -> Unit = {},
    viewModel: AstroDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(profileId) {
        viewModel.loadDay(0, profileId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState.loadState) {
            is AstroLoadState.Loading -> AstroLoadingScreen()
            is AstroLoadState.Error   -> AstroErrorScreen(state.message) {
                viewModel.loadDay(0, profileId)
            }
            else                      -> AstroContent(uiState, viewModel, onNavigateToProfile)
        }
    }
}

@Composable
private fun AstroContent(
    state: AstroUiState,
    viewModel: AstroDashboardViewModel,
    onNavigateToProfile: () -> Unit
) {
    var showMechanics by remember { mutableStateOf(false) }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AstroHeader(state.displayDate, state.panchanga?.vara ?: "", onNavigateToProfile)
        }

        // ── DAY SELECTOR ──
        item {
            DayTabSelector(
                selectedOffset = state.selectedDayOffset,
                onSelect       = { offset -> viewModel.loadDay(offset) }
            )
        }

        // ── TIER 2: MOMENTUM HERO CARD (Rule Engine Fallback) ──
        item {
            val cardLabel = when (state.selectedDayOffset) {
                -1 -> "YESTERDAY'S MOMENTUM"
                1  -> "TOMORROW'S MOMENTUM"
                else -> "TODAY'S MOMENTUM"
            }
            MomentumHeroCard(
                label = cardLabel,
                score = state.momentumScore,
                text  = state.predictionText.ifBlank { state.ruleSummary }
            )
        }

        // ── ACTIONABLE TIMING ──
        item {
            TimingWindowsSection(state.auspiciousWindows, state.avoidWindows)
        }

        // ── LIFE EVENT PLANNER ──
        if (state.lifeEventAssessments.isNotEmpty()) {
            item {
                LifeEventPlannerSection(
                    assessments      = state.lifeEventAssessments,
                    selectedCategory = state.selectedLifeEvent,
                    onSelect         = viewModel::selectLifeEvent
                )
            }
        }

        // ── AI Personalization Nudge ──
        if (state.birthChart == null) {
            item {
                AstroGlassCard(label = "PERSONALIZE", accent = Color(0xFFF59E0B)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Get precise horoscope & timing for your chart.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onNavigateToProfile) {
                            Text("Set up", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        }
                    }
                }
            }
        }

        // ── PROGRESSIVE DISCLOSURE ──
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableNoRipple { showMechanics = !showMechanics }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showMechanics) "CLOSE DETAILED MECHANICS" else "VIEW ASTRONOMICAL MECHANICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = if (showMechanics) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showMechanics) {
            state.panchanga?.let { panchanga ->
                item {
                    AstroGlassCard(label = "PANCHANGA", accent = Color(0xFF7C3AED)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PanchangaChip("Tithi", panchanga.tithi)
                            PanchangaChip("Nakshatra", panchanga.nakshatra)
                            PanchangaChip("Yoga", panchanga.yoga)
                            PanchangaChip("Vara", panchanga.vara)
                        }
                    }
                }
            }

            state.rahuKaal?.let { rk -> item { RahuKaalCard(rk) } }

            if (state.activeWarLabels.isNotEmpty()) {
                item { GrahaYuddhaWarning(state.activeWarLabels) }
            }

            items(state.planets, key = { it.planetId }) { planet ->
                PlanetTransitRow(planet)
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DayTabSelector(selectedOffset: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(-1 to "Yesterday", 0 to "Today", 1 to "Tomorrow")
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (offset, label) ->
            val selected = offset == selectedOffset
            Surface(
                modifier  = Modifier.weight(1f).clickable { onSelect(offset) },
                shape     = RoundedCornerShape(10.dp),
                color     = if (selected) Color(0xFF3B82F6).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                border    = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (selected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.15f)
                )
            ) {
                Text(
                    text      = label,
                    modifier  = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    style     = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color     = if (selected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MomentumHeroCard(label: String, score: Int, text: String) {
    AstroGlassCard(label = label, accent = Color(0xFF3B82F6)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF3B82F6),
                    strokeWidth = 6.dp,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
                Text(text = "$score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimingWindowsSection(auspicious: List<String>, avoid: List<String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("GOOD TIME", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669))
            Spacer(Modifier.height(8.dp))
            auspicious.forEach { win -> TimingBadge(win, Color(0xFF059669)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("AVOID TIME", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444))
            Spacer(Modifier.height(8.dp))
            avoid.forEach { win -> TimingBadge(win, Color(0xFFEF4444)) }
        }
    }
}

@Composable
private fun TimingBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AstroHeader(date: String, vara: String, onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Vedic Transit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("$date  •  $vara", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onProfileClick) {
            Icon(Icons.Rounded.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PanchangaChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RahuKaalCard(rk: RahuKaalDisplay) {
    val accentColor = if (rk.isActive) Color(0xFFEF4444) else Color(0xFFF59E0B)
    val animProgress by animateFloatAsState(rk.progressFraction, tween(800), label = "rk")

    AstroGlassCard(label = "RAHU KAAL", accent = accentColor) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${rk.startLabel} – ${rk.endLabel}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = accentColor)
                Text(if (rk.isActive) "Active now" else "Upcoming window", style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { animProgress }, color = accentColor, strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun PlanetTransitRow(planet: PlanetDisplayRow) {
    val warColor = if (planet.warResult == "loser") Color(0xFFEF4444) else Color(0xFF059669)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (planet.isInWar) warColor else MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(planet.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (planet.retrograde) Text(" ℞", color = Color(0xFFF59E0B))
                }
                Text("${planet.signName} ${planet.longitude} · ${planet.nakshatraName}", style = MaterialTheme.typography.bodySmall)
            }
            Text("${planet.shadabalaScore}", fontWeight = FontWeight.Bold, color = shadabalaColor(planet.shadabalaScore))
        }
    }
}

@Composable
fun AstroGlassCard(label: String, accent: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accent, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
private fun AstroLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AstroErrorScreen(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚠ Error", style = MaterialTheme.typography.titleLarge)
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun GrahaYuddhaWarning(labels: List<String>) {
    AstroGlassCard("PLANETARY WAR", Color(0xFFEF4444)) {
        labels.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

// ── Life Event Planner ────────────────────────────────────────────────────────

@Composable
private fun LifeEventPlannerSection(
    assessments: Map<LifeEventCategory, EventAssessment>,
    selectedCategory: LifeEventCategory?,
    onSelect: (LifeEventCategory) -> Unit
) {
    AstroGlassCard(label = "LIFE EVENT PLANNER", accent = Color(0xFF7C3AED)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Tap a decision to see today's cosmic + numerology alignment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Category chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LifeEventCategory.entries.toList()) { category ->
                    val assessment = assessments[category]
                    val isSelected = category == selectedCategory
                    val chipColor  = if (assessment != null) verdictColor(assessment.verdict) else Color(0xFF7C3AED)
                    Surface(
                        modifier = Modifier.clickable { onSelect(category) },
                        shape    = RoundedCornerShape(20.dp),
                        color    = if (isSelected) chipColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border   = BorderStroke(1.dp, if (isSelected) chipColor else Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text     = "${category.emoji} ${category.label}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style    = MaterialTheme.typography.labelMedium,
                            color    = if (isSelected) chipColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Assessment detail card
            AnimatedVisibility(
                visible = selectedCategory != null && assessments.containsKey(selectedCategory),
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                val assessment = assessments[selectedCategory] ?: return@AnimatedVisibility
                EventAssessmentCard(assessment)
            }
        }
    }
}

@Composable
private fun EventAssessmentCard(assessment: EventAssessment) {
    val accent = verdictColor(assessment.verdict)
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Score ring + verdict label
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress  = { assessment.score / 100f },
                    modifier  = Modifier.fillMaxSize(),
                    color     = accent,
                    strokeWidth = 5.dp,
                    trackColor = accent.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text      = "${assessment.score}",
                    style     = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color     = accent
                )
            }
            Column {
                Text(verdictLabel(assessment.verdict), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                Text(assessment.category.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider(color = accent.copy(alpha = 0.15f))

        IndicatorRow(label = "ASTRO",       value = assessment.astroIndicator)
        IndicatorRow(label = "NUMEROLOGY",  value = assessment.numerologyIndicator)

        HorizontalDivider(color = accent.copy(alpha = 0.15f))

        Text(assessment.advice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

        assessment.bestWindowHint?.let { hint ->
            Text("📅  $hint", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IndicatorRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

private fun verdictColor(verdict: Verdict) = when (verdict) {
    Verdict.GO      -> Color(0xFF059669)
    Verdict.CAUTION -> Color(0xFFF59E0B)
    Verdict.NO_GO   -> Color(0xFFEF4444)
}

private fun verdictLabel(verdict: Verdict) = when (verdict) {
    Verdict.GO      -> "✓ Aligned — Go"
    Verdict.CAUTION -> "⚠ Caution Advised"
    Verdict.NO_GO   -> "✕ Hold Off"
}

private fun shadabalaColor(score: Int) = when {
    score >= 75 -> Color(0xFF059669)
    score >= 55 -> Color(0xFF3B82F6)
    else -> Color(0xFFF59E0B)
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = composed {
    clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
}
