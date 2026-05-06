package com.household.app.ui.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.theme.GlassStroke
import com.household.app.ui.compose.theme.Green
import com.household.app.ui.compose.theme.RailBottom
import com.household.app.ui.compose.theme.RailTop
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextOnDark

/**
 * NavigationRailComposable — custom nav rail replacing the XML NavigationRailView.
 *
 * Design rules enforced here:
 * 1. Toggle (expand/collapse) is on a DEDICATED IconButton at top — NEVER on nav items.
 *    Nav item onClick only updates selected destination.
 * 2. Pill indicator animates its Y offset between items (no instant jump).
 * 3. Width animates via animateDpAsState with spring curve.
 * 4. Labels fade in AFTER rail reaches full width (200ms delay via tween).
 * 5. Edge swipe: applied via edgeSwipeRail Modifier (passed in from AppShell).
 *
 * Each item height = 56dp. Pill Y offset = selectedIndex * 56dp.
 */
@Composable
fun NavigationRailComposable(
    currentRoute: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = Screen.all.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    // ── Animated dimensions ────────────────────────────────────────────────
    val railWidth by animateDpAsState(
        targetValue  = if (expanded) 180.dp else 72.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label        = "rail_width"
    )

    val labelAlpha by animateFloatAsState(
        targetValue   = if (expanded) 1f else 0f,
        animationSpec = tween(200),
        label         = "label_alpha"
    )

    // Pill Y offset — slides between items
    val pillOffsetY by animateDpAsState(
        targetValue   = (selectedIndex * 56).dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label         = "pill_offset_y"
    )

    Box(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(RailTop, RailBottom)
                )
            )
            .border(1.dp, GlassStroke.copy(alpha = 0.08f), RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )

        // ── Pill indicator overlay ─────────────────────────────────────────
        // Rendered behind the icon buttons so icons remain tappable
        Box(
            modifier = Modifier
                // Top padding = toggle button height (48dp) + top padding (12dp) + spacer (8dp) = 68dp
                .offset(x = 14.dp, y = pillOffsetY + 68.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Green.copy(alpha = 0.24f), Green.copy(alpha = 0.08f))
                    )
                )
        )

        // ── Rail content column ────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxHeight()
                .padding(top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Toggle button — ONLY this controls expand/collapse
            IconButton(
                onClick  = onToggle,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector        = if (expanded) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                    contentDescription = if (expanded) "Collapse navigation" else "Expand navigation",
                    tint               = TextOnDark,
                    modifier           = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Nav items — onClick updates ONLY selected, never toggles expand
            Screen.all.forEach { screen ->
                val isSelected = screen.route == currentRoute

                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onNavigate(screen.route) }  // navigation only
                        .background(
                            color = if (isSelected) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = screen.icon,
                        contentDescription = screen.label,
                        tint               = if (isSelected) Green else TextMuted,
                        modifier           = Modifier.size(24.dp)
                    )

                    // Labels: only render when alpha > 0 to avoid invisible tap targets
                    if (expanded || labelAlpha > 0f) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text     = screen.label,
                            style    = TextStyle(
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color      = TextOnDark
                            ),
                            modifier = Modifier.alpha(labelAlpha)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modifier extension: edge swipe to expand/collapse the nav rail.
 * Applied to the outer Row in AppShell.
 *
 * @param expanded current expansion state
 * @param onExpand  called when user swipes right with dragAmount > threshold
 * @param onCollapse called when user swipes left with dragAmount < -threshold
 */
fun Modifier.edgeSwipeRail(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit
): Modifier = this.pointerInput(expanded) {
    // key = expanded so pointerInput block re-attaches when state changes
    detectHorizontalDragGestures { _, dragAmount ->
        if (!expanded && dragAmount > 30f) onExpand()
        else if (expanded && dragAmount < -30f) onCollapse()
    }
}
