package com.household.app.ui.v2

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.TextOnDark
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.EliteHeader

@Composable
fun V2FamilyScreen() {
    val pulse = rememberInfiniteTransition(label = "family_pulse")
    val alpha = pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    Column(modifier = Modifier.padding(16.dp)) {
        EliteHeader(
            title = "Family",
            subtitle = "Member auras"
        )

        EliteGlassCard(glowColor = LumeAmber) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuraAvatar(initial = "A", glow = LumePurple, subtitle = "Primary")
                AuraAvatar(initial = "K", glow = LumePurple.copy(alpha = 0.7f), subtitle = "Shared")
                AuraAvatar(initial = "R", glow = LumeAmber.copy(alpha = alpha.value), subtitle = "Pickup alert")
            }
            Spacer(Modifier.size(16.dp))
            Text(
                text = "R needs school pickup soon",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AuraAvatar(initial: String, glow: Color, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(glow.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, glow.copy(alpha = 0.62f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = TextOnDark, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.size(8.dp))
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}