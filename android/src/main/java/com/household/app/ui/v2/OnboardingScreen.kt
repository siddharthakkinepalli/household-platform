package com.household.app.ui.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary

private data class OnboardingStep(
    val icon: ImageVector,
    val accentColor: Color,
    val title: String,
    val subtitle: String,
    val hint: String
)

private val STEPS = listOf(
    OnboardingStep(
        icon = Icons.Rounded.AutoAwesome,
        accentColor = LumePurple,
        title = "Your household, at a glance",
        subtitle = "Smart budget & spending pulse",
        hint = "The Home screen shows your salary cycle budget, top spending categories, and AI-powered alerts — new subscriptions, upcoming bills, tax-deductible totals, and more."
    ),
    OnboardingStep(
        icon = Icons.Rounded.AccountBalanceWallet,
        accentColor = LumePurple,
        title = "Import your transactions",
        subtitle = "Connect your bank history",
        hint = "Go to Wallet → tap the import icon → select your bank CSV. ING, Sparkasse, DKB, N26 and more are auto-detected. Spending is categorised automatically."
    ),
    OnboardingStep(
        icon = Icons.Rounded.Repeat,
        accentColor = LumeEmerald,
        title = "Track every recurring bill",
        subtitle = "Subscription Hub",
        hint = "Documents → Subscription Hub shows all your fixed monthly costs — rent, streaming, phone, insurance. Confirm what we detected or add bills manually."
    ),
    OnboardingStep(
        icon = Icons.Rounded.CameraAlt,
        accentColor = LumeCyan,
        title = "Scan & vault your documents",
        subtitle = "Smart document storage",
        hint = "Documents → tap the scan button. Passports, insurance policies and contracts are auto-classified, and their expiry dates are tracked so nothing slips through."
    ),
    OnboardingStep(
        icon = Icons.Rounded.Receipt,
        accentColor = LumeAmber,
        title = "Scan receipts, stock your pantry",
        subtitle = "OCR receipt parsing",
        hint = "Point the camera at a till receipt. We extract the total, merchant, date and every line item — groceries land directly in your Pantry as unconfirmed items, ready to review."
    ),
    OnboardingStep(
        icon = Icons.Rounded.CloudDone,
        accentColor = LumeEmerald,
        title = "Tax checklist & Drive backup",
        subtitle = "Year-end made easy",
        hint = "The Tax tab tallies deductible expenses automatically. All your vault documents can be backed up to Google Drive in one tap — your data, always safe."
    ),
    OnboardingStep(
        icon = Icons.Rounded.NotificationsActive,
        accentColor = LumeAmber,
        title = "Never miss an expiry or bill",
        subtitle = "Smart alerts & reminders",
        hint = "Enable notifications and we'll remind you 90 days before your ID expires, 60 days before insurance renewals, and whenever a new subscription appears on your statement."
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = STEPS[currentStep]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E16))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(step.accentColor.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.3f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.3f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Skip button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinished) {
                    Text("Skip", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Animated content area
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 2 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 2 } + fadeOut())
                    }
                },
                label = "onboarding_step"
            ) { stepIdx ->
                val s = STEPS[stepIdx]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(s.accentColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = s.icon,
                            contentDescription = null,
                            tint = s.accentColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMain,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = s.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = s.accentColor,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = s.hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Bottom controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Step dots — pill for active, circle for inactive
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STEPS.forEachIndexed { idx, s ->
                        Box(
                            modifier = Modifier
                                .size(if (idx == currentStep) 24.dp else 8.dp, 8.dp)
                                .background(
                                    if (idx == currentStep) s.accentColor
                                    else TextMuted.copy(alpha = 0.30f),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }

                // Step counter (e.g. "3 / 7") — helpful with 7 steps
                Text(
                    text = "${currentStep + 1} / ${STEPS.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                Button(
                    onClick = {
                        if (currentStep < STEPS.lastIndex) currentStep++
                        else onFinished()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = step.accentColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (currentStep < STEPS.lastIndex) "Next" else "Get started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}
