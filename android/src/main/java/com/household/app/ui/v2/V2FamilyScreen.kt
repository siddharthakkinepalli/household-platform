package com.household.app.ui.v2

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextOnDark
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.EliteHeader
import com.household.app.ui.v2.components.FamilyMemberEditorSheet
import com.household.app.ui.viewmodels.FamilyExpiryAlert
import com.household.app.ui.viewmodels.FamilyMemberUi
import com.household.app.ui.viewmodels.FamilyViewModel

@Composable
fun V2FamilyScreen(
    onMemberClick: (Long) -> Unit = {},
    onExpiryAlertClick: (ownerId: Long?) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: FamilyViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FamilyViewModel(context.applicationContext as Application) }
        }
    )
    val members by viewModel.members.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val expiringAlerts by viewModel.expiringAlerts.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEditor = true },
                containerColor = LumeAmber,
                contentColor = EliteNavy,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add member")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                EliteHeader(
                    title = "Family",
                    subtitle = if (summary.total == 0) {
                        "Add people to organize documents"
                    } else {
                        "${summary.total} household member${if (summary.total == 1) "" else "s"}"
                    }
                )
            }

            if (expiringAlerts.isNotEmpty()) {
                item {
                    FamilyExpiryAlertsCard(
                        alerts = expiringAlerts,
                        onAlertClick = { ownerId -> onExpiryAlertClick(ownerId) }
                    )
                }
            }

            if (summary.total > 0) {
                item {
                    FamilySummaryRow(adults = summary.adults, children = summary.children)
                }
            }

            if (members.isEmpty()) {
                item {
                    FamilyEmptyCard(onAddClick = { showEditor = true })
                }
            } else {
                items(members, key = { it.member.id }) { item ->
                    FamilyMemberCard(item = item, onClick = { onMemberClick(item.member.id) })
                }
            }

            item { Spacer(Modifier.size(72.dp)) }
        }
    }

    if (showEditor) {
        FamilyMemberEditorSheet(
            existing = null,
            onDismiss = { showEditor = false },
            onSave = { name, role, color ->
                viewModel.addMember(name, role, color)
                showEditor = false
            }
        )
    }
}

@Composable
private fun FamilyExpiryAlertsCard(
    alerts: List<FamilyExpiryAlert>,
    onAlertClick: (Long?) -> Unit
) {
    EliteGlassCard(glowColor = CriticalRed.copy(alpha = 0.35f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Warning, null, tint = CriticalRed, modifier = Modifier.size(20.dp))
                Text(
                    text = "Expiring soon",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold
                )
            }
            alerts.take(4).forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAlertClick(alert.ownerId) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alert.title, color = TextMain, style = MaterialTheme.typography.bodySmall)
                        Text(
                            alert.memberName ?: "Household",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        "${alert.daysUntil}d",
                        color = CriticalRed,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilySummaryRow(adults: Int, children: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryMetric(
            label = "Adults",
            value = adults.toString(),
            color = LumePurple,
            modifier = Modifier.weight(1f)
        )
        SummaryMetric(
            label = "Children",
            value = children.toString(),
            color = LumeEmerald,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    EliteGlassCard(glowColor = color.copy(alpha = 0.4f), modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun FamilyEmptyCard(onAddClick: () -> Unit) {
    EliteGlassCard(glowColor = LumeAmber.copy(alpha = 0.35f)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Group, contentDescription = null, tint = LumeAmber, modifier = Modifier.size(40.dp))
            Text(
                text = "No household members yet",
                style = MaterialTheme.typography.titleSmall,
                color = TextMain,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Add family members to file passports, contracts, and IDs under each person in Documents.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                text = "Add first member",
                color = LumeAmber,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddClick)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun FamilyMemberCard(item: FamilyMemberUi, onClick: () -> Unit) {
    val glow = parseMemberColor(item.member.colorCode)
    EliteGlassCard(
        glowColor = glow.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(glow.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, glow.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.member.initial(),
                    color = TextOnDark,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.member.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.member.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "${item.vaultDocCount} vault · ${item.contractDocCount} contracts",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}
