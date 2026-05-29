package com.household.app.ui.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun ThemeSelector(
    currentTheme: JugaadThemeSelection,
    onThemeSelected: (JugaadThemeSelection) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "App Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextMain,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Render themes in a grid-like list of cards
        JugaadThemeSelection.entries.chunked(2).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowThemes.forEach { option ->
                    ThemeOptionCard(
                        option = option,
                        isSelected = option == currentTheme,
                        onClick = { onThemeSelected(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowThemes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ThemeOptionCard(
    option: JugaadThemeSelection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (primary, secondary, tertiary) = when (option) {
        JugaadThemeSelection.LUMINESCENT_GLASS -> Triple(LumeCyan, LumeEmerald, LumeAmber)
        JugaadThemeSelection.JUGAAD_CHILLI     -> Triple(Color(0xFFD32F2F), Color(0xFFFFB300), Color(0xFFFF7043))
        JugaadThemeSelection.NORDIC_EINKAUF    -> Triple(Color(0xFF1A237E), Color(0xFF00796B), Color(0xFFF57F17))
        JugaadThemeSelection.MATRIX_PIPELINE   -> Triple(Color(0xFF00FF41), Color(0xFF00CC33), Color(0xFF4ADE80))
        JugaadThemeSelection.MONSOON_FOREST    -> Triple(Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF80DEEA))
        JugaadThemeSelection.TWILIGHT_CASHMERE -> Triple(Color(0xFFB39DDB), Color(0xFFF48FB1), Color(0xFFFFCC80))
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, primary) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = option.name.replace("_", " ")
                    .lowercase(Locale.getDefault())
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } },
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) TextMain else TextMuted,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorSwatch(primary)
                ColorSwatch(secondary)
                ColorSwatch(tertiary)
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
    )
}
