package com.household.app.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.motion.Motion
import com.household.app.ui.compose.motion.pressEffect
import com.household.app.ui.compose.state.Insight
import com.household.app.ui.compose.state.InsightType
import com.household.app.ui.compose.theme.Orange
import com.household.app.ui.compose.theme.Green
import com.household.app.ui.compose.theme.Purple

/**
 * InsightCard — rendered above the greeting row on HomeScreen.
 *
 * Only shown when insight data is present. Maximum 1 per screen (highest-priority wins in VM).
 * Dismiss via swipe-left (TODO Phase 3) or tap-X (implemented here).
 */
@Composable
fun InsightCard(
    insight: Insight,
    onDismiss: () -> Unit,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val color: Color = when (insight.type) {
        InsightType.WARNING -> Orange
        InsightType.INFO    -> Purple
        InsightType.SUCCESS -> Green
    }
    val icon = when (insight.type) {
        InsightType.WARNING -> Icons.Rounded.WarningAmber
        InsightType.INFO -> Icons.Rounded.Info
        InsightType.SUCCESS -> Icons.Rounded.CheckCircle
    }

    Card(
        modifier = if (onTap != null) {
            modifier.pressEffect().clickable(onClick = onTap)
        } else {
            modifier
        },
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left solid bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.width(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text     = insight.message,
                color    = color,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = "Dismiss insight",
                    tint               = color
                )
            }
        }
    }
}

/**
 * Wrapper that owns its own visibility state for staggered reveal.
 * Used by QuickCaptureFab to stagger mini-FAB appearance.
 *
 * @param visible parent-driven desired visibility (e.g. FAB expanded state)
 * @param index   position in list — delays visibility by index * FAB_STAGGER_MS
 */
@Composable
fun StaggeredMiniFab(
    visible: Boolean,
    index: Int,
    staggerMs: Long = Motion.FAB_STAGGER_MS,
    content: @Composable () -> Unit
) {
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(index * staggerMs)
            show = true
        } else {
            show = false
        }
    }

    AnimatedVisibility(
        visible = show,
        enter   = fadeIn(Motion.FadeIn) + slideInVertically(animationSpec = Motion.Slide) { it / 2 },
        exit    = fadeOut(Motion.FadeOut) + slideOutVertically(animationSpec = Motion.Slide)
    ) {
        content()
    }
}
