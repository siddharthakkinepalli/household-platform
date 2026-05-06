package com.household.app.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.EliteHeader
import com.household.app.vault.RenewalHintDetector

@Composable
fun V2DocumentVaultScreen(
    onUploadClick: () -> Unit = {}
) {
    var showRenewalPrompt by remember { mutableStateOf(false) }
    val sampleOcrText = "Insurance policy renews on 15/12/2026"
    val docs = listOf(
        DocumentAsset("Contract_2026.pdf", "1.2 MB"),
        DocumentAsset("Insurance_Policy.pdf", "882 KB"),
        DocumentAsset("Lease_Agreement.pdf", "2.4 MB"),
        DocumentAsset("Passport_Copy.jpg", "640 KB"),
        DocumentAsset("Tax_Proof_2025.pdf", "1.8 MB"),
        DocumentAsset("Warranty_Card.png", "512 KB")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EliteNavy)
            .padding(16.dp)
    ) {
        EliteHeader("Vault", "Secure Contracts & Docs")

        EliteGlassCard(
            modifier = Modifier
                .height(180.dp)
                .clickable {
                    onUploadClick()
                    if (RenewalHintDetector.shouldSuggestRenewal(sampleOcrText)) {
                        showRenewalPrompt = true
                    }
                },
            glowColor = LumeAmber
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = LumeAmber, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Upload Document", color = TextMain, fontWeight = FontWeight.Bold)
                Text("PDF, JPG, or PNG", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (showRenewalPrompt) {
            AlertDialog(
                onDismissRequest = { showRenewalPrompt = false },
                title = { Text("Set Renewal Reminder?", color = LumeAmber, fontWeight = FontWeight.Bold) },
                text = { Text("This file looks like a contract or policy. Add a reminder now?", color = TextMain) },
                confirmButton = {
                    TextButton(onClick = { showRenewalPrompt = false }) {
                        Text("Set Reminder", color = LumeAmber)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenewalPrompt = false }) {
                        Text("Later", color = TextMuted)
                    }
                }
            )
        }

        Spacer(Modifier.height(32.dp))

        Text("Recent Documents", color = TextMain, fontWeight = FontWeight.SemiBold)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            items(docs, key = { it.name }) { doc ->
                DocumentAssetCard(doc.name, doc.size)
            }
        }
    }
}

private data class DocumentAsset(
    val name: String,
    val size: String
)

@Composable
private fun DocumentAssetCard(name: String, size: String) {
    EliteGlassCard(glowColor = LumeAmber) {
        Icon(Icons.Rounded.Description, contentDescription = null, tint = LumeAmber)
        Spacer(Modifier.height(12.dp))
        Text(name, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(size, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
