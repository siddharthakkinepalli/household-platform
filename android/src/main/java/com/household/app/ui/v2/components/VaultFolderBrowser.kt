package com.household.app.ui.v2.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.app.domain.models.vault.VaultBrowseState
import com.household.app.domain.models.vault.VaultFolderRow
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary

@Composable
fun VaultFolderBreadcrumb(
    segments: List<Pair<String, VaultBrowseState?>>,
    onNavigate: (VaultBrowseState) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segments.forEachIndexed { index, (label, target) ->
            if (index > 0) {
                Text("›", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            if (target != null) {
                TextButton(
                    onClick = { onNavigate(target) },
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text(label, color = LumeAmber, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Text(
                    label,
                    color = TextMain,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun VaultFolderList(
    rows: List<VaultFolderRow>,
    onRowClick: (VaultBrowseState) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows, key = { "${it.title}-${it.subtitle}" }) { row ->
            VaultFolderRowCard(row = row, onClick = { onRowClick(row.target) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun VaultFolderRowCard(
    row: VaultFolderRow,
    onClick: () -> Unit
) {
    EliteGlassCard(
        glowColor = LumeAmber.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = LumeAmber,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    color = TextMain,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = row.subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = row.itemCount.toString(),
                color = LumeWhite.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
