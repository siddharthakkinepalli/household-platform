package com.household.app.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.state.Module
import com.household.app.ui.compose.theme.BorderSubtle
import com.household.app.ui.compose.theme.SurfaceVariant
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextPrimary

/**
 * ModuleGrid — adaptive grid of module shortcut cards.
 * Phone (≤600dp): 2 columns.
 * Tablet (>600dp): 3 columns.
 *
 * Each card navigates to its Screen.route on tap.
 * This is intentionally NOT lazy: Home screen only has a few fixed modules, and
 * a lazy vertical grid nested inside a vertically scrolling parent crashes due to
 * unbounded height constraints.
 */
@Composable
fun ModuleGrid(
    modules: List<Module>,
    onModuleTap: (route: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = if (screenWidthDp > 600) 3 else 2
    val rows = modules.chunked(columns)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowModules ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowModules.forEach { module ->
                    ModuleCard(
                        module = module,
                        onTap = { onModuleTap(module.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                repeat(columns - rowModules.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: Module,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        border    = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White, SurfaceVariant.copy(alpha = 0.7f))
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 22.dp, y = 18.dp)
                    .size(52.dp)
                    .background(module.color.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            )
            Column(
                modifier              = Modifier.padding(16.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(module.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector        = module.icon,
                        contentDescription = module.title,
                        tint               = module.color,
                        modifier           = Modifier.size(24.dp)
                    )
                }
                Text(
                    text  = module.title,
                    style = TextStyle(
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextPrimary
                    )
                )
                if (module.subtitle.isNotEmpty()) {
                    Text(
                        text  = module.subtitle,
                        style = TextStyle(fontSize = 11.sp, color = TextMuted)
                    )
                }
            }
        }
    }
}
