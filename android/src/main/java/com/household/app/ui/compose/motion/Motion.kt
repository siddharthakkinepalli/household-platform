package com.household.app.ui.compose.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Central animation token file. All durations and curves must come from here.
 * Never hardcode tween(200) or spring() inline — use these named constants.
 *
 * Type rules:
 *   tween<Float>     → alpha, scale
 *   tween<IntOffset> → positional slide (IntOffset)
 *   spring<Float>    → spring-based float animations (width, scale)
 */
object Motion {

    /** Default spring: smooth, slightly bouncy. Use for nav rail, pill, most transitions. */
    val SpringDefault = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMedium
    )

    val SpringDefaultDp = spring<Dp>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMedium
    )

    /** Snappy spring: tight, fast. Use for press feedback (scale down on tap). */
    val SpringSnappy = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessHigh
    )

    /** Fade in — 180ms ease out. Use for screen enter, card appear. */
    val FadeIn = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

    /** Fade out — 120ms. Slightly faster than fade-in (exit is less noticeable). */
    val FadeOut = tween<Float>(durationMillis = 120)

    /** Slide spec for IntOffset-based positional animation. */
    val Slide = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

    /** Duration constants for use in AnimatedContent transition specs. */
    const val DURATION_ENTER = 180
    const val DURATION_EXIT = 120
    const val DURATION_SLIDE = 220
    const val FAB_STAGGER_MS = 80L
}

fun Modifier.pressEffect(): Modifier = composed {
    val scale = remember { Animatable(1f) }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                scale.animateTo(0.96f, Motion.SpringSnappy)
                tryAwaitRelease()
                scale.animateTo(1f, Motion.SpringDefault)
            }
        )
    }
}
