package com.jugaad.feature.astro.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jugaad.feature.astro.ui.state.BirthChartDisplay
import com.jugaad.feature.astro.ui.state.PlanetDisplayRow

/**
 * Natal birth chart display screen.
 *
 * Architecture rules:
 *  - Reads exclusively from [AstroDashboardViewModel.uiState] via collectAsStateWithLifecycle.
 *  - Zero computation in composable scope — all values are pre-resolved strings.
 *
 * @param profileId The Room [UserProfileEntity.id] to compute the chart for.
 */
@Composable
fun BirthChartScreen(
    profileId: Long,
    viewModel: AstroDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(profileId) {
        viewModel.loadBirthChart(profileId)
    }

    // Launch system share sheet when PDF is ready
    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Birth Chart PDF"))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        when {
            uiState.isBirthChartLoading -> BirthChartLoading()
            uiState.birthChart != null  -> BirthChartContent(
                chart       = uiState.birthChart!!,
                isExporting = uiState.isExporting,
                onExport    = viewModel::exportBirthChartPdf
            )
            else                        -> BirthChartEmpty()
        }
    }
}

@Composable
private fun BirthChartContent(
    chart: BirthChartDisplay,
    isExporting: Boolean,
    onExport: () -> Unit
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        item {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Natal Chart",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text  = "Lagna: ${chart.lagnaSign} · ${chart.lagnaNakshatra}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(onClick = onExport) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // ── Lagna + Moon summary ──────────────────────────────────────────
        item {
            AstroGlassCard(label = "BIRTH PROFILE", accent = Color(0xFF7C3AED)) {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BirthProfileCell(
                        label = "Ascendant (Lagna)",
                        value = chart.lagnaSign,
                        sub   = chart.lagnaNakshatra
                    )
                    BirthProfileCell(
                        label = "Moon Sign (Rashi)",
                        value = chart.moonSign,
                        sub   = "${chart.moonNakshatra} P${chart.birthNakshatraPada}"
                    )
                }
            }
        }

        // ── Strongest planets ─────────────────────────────────────────────
        if (chart.topStrengthNames.isNotEmpty()) {
            item {
                AstroGlassCard(label = "STRONGEST PLANETS", accent = Color(0xFF059669)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chart.topStrengthNames.forEach { name ->
                            Card(
                                shape  = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF059669).copy(alpha = 0.12f)
                                )
                            ) {
                                Text(
                                    text     = name,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color    = Color(0xFF059669)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Natal planet grid ─────────────────────────────────────────────
        item {
            Text(
                text  = "NATAL POSITIONS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(chart.planets, key = { it.planetId }) { planet ->
            NatalPlanetRow(planet)
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun BirthProfileCell(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text  = sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NatalPlanetRow(planet: PlanetDisplayRow) {
    val warColor = when (planet.warResult) {
        "loser"  -> Color(0xFFEF4444)
        "winner" -> Color(0xFF059669)
        else     -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = planet.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (planet.isInWar) warColor else MaterialTheme.colorScheme.onSurface
                    )
                    if (planet.retrograde) {
                        Spacer(Modifier.width(4.dp))
                        Text("℞", fontSize = 12.sp, color = Color(0xFFF59E0B))
                    }
                }
                Text(
                    text  = "${planet.signName} ${planet.longitude} · ${planet.nakshatraName} P${planet.pada}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (planet.warResult != null) {
                    Text(
                        text  = "⚔ Graha Yuddha ${planet.warResult}",
                        style = MaterialTheme.typography.labelSmall,
                        color = warColor
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Shadbala bar + score
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(64.dp)) {
                Text(
                    text  = "Śaktī ${planet.shadabalaScore}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress   = { planet.shadabalaScore / 100f },
                    modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color      = shadabalaBarColor(planet.shadabalaScore),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap  = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun BirthChartLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF7C3AED))
            Spacer(Modifier.height(12.dp))
            Text("Computing natal chart…", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BirthChartEmpty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No birth data found. Add a profile to generate your natal chart.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp))
    }
}

private fun shadabalaBarColor(score: Int): Color = when {
    score >= 75 -> Color(0xFF059669)
    score >= 55 -> Color(0xFF3B82F6)
    score >= 40 -> Color(0xFFF59E0B)
    else        -> Color(0xFFEF4444)
}
