package com.household.app.ui.compose.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.theme.Green
import com.household.app.ui.compose.theme.NavBg
import com.household.app.ui.compose.theme.TextOnColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private data class SpeedDialItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit = {}
)

/**
 * QuickCaptureFab — speed dial FAB with staggered mini-FAB reveal.
 *
 * Touch safety:
 *   - Scrim is rendered via matchParentSize() inside the parent Box ONLY when expanded.
 *   - Scrim uses pointerInput + detectTapGestures (not clickable) to intercept taps.
 *   - BackHandler collapses on system back while expanded.
 *
 * Stagger:
 *   - Each mini-FAB uses StaggeredMiniFab composable (owns its own show state).
 *   - Stagger delay = index * 80ms. All items off instantly on collapse.
 */
@Composable
fun QuickCaptureFab(
    onScan: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onParse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val iconRotation by animateFloatAsState(
        targetValue  = if (expanded) 45f else 0f,
        label        = "fab_icon_rotation"
    )

    val items = listOf(
        SpeedDialItem(Icons.Rounded.Inbox, "Parse inbox",   onParse),
        SpeedDialItem(Icons.Rounded.Mic,   "Voice note",    onSpeak),
        // Note: DocumentScanner not in default icons — using a text-based workaround
        SpeedDialItem(Icons.Rounded.Add,   "Scan receipt",  onScan)
    )

    // BackHandler — collapse FAB before system back
    BackHandler(enabled = expanded) { expanded = false }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Scrim — ONLY when expanded, uses matchParentSize ──────────────
        if (expanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .pointerInput(Unit) {
                        detectTapGestures { expanded = false }
                    }
            )
        }

        // ── FAB column — bottom-right ─────────────────────────────────────
        Column(
            modifier              = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment   = Alignment.End,
            verticalArrangement   = Arrangement.spacedBy(12.dp)
        ) {
            // Mini-FABs (staggered reveal, bottom to top)
            items.forEachIndexed { index, item ->
                StaggeredMiniFab(visible = expanded, index = index) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Label chip
                        Box(
                            modifier = Modifier
                                .background(
                                    NavBg.copy(alpha = 0.85f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text  = item.label,
                                color = TextOnColor,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallFloatingActionButton(
                            onClick            = { item.onClick(); expanded = false },
                            containerColor     = Green,
                            contentColor       = TextOnColor,
                            modifier           = Modifier.size(40.dp)
                        ) {
                            Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Main FAB
            FloatingActionButton(
                onClick        = {
                    if (!expanded) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = !expanded
                },
                containerColor = Green,
                contentColor   = TextOnColor
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Add,
                    contentDescription = if (expanded) "Close" else "Quick capture",
                    modifier           = Modifier.rotate(iconRotation)
                )
            }
        }
    }
}
