package com.household.app.ui.v2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.activity.ComponentActivity
import com.household.app.data.entities.VaultEntity
import com.household.app.ui.compose.theme.ConfigAccent
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.v2.components.ConfirmScanSheet
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.ScanConfirmationState
import com.household.app.ui.viewmodels.VaultUiState
import com.household.app.ui.viewmodels.VaultViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2DocumentVaultScreen(
    onScanClick: () -> Unit = {},
    onStagingRequested: (vaultId: Long) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as ComponentActivity
    val viewModel: VaultViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = viewModelFactory {
            initializer {
                VaultViewModel(context.applicationContext as android.app.Application)
            }
        }
    )
    val vaultUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vaultEntries by viewModel.vaultEntries.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiState
            .filterIsInstance<VaultUiState.ScanSaved>()
            .collect { saved ->
                val vaultId = saved.vaultId
                onStagingRequested(vaultId)
                viewModel.acknowledgeScanned()
            }
    }

    Scaffold(
        topBar = { VaultTopBar() },
        floatingActionButton = {
            ScanFAB(
                onClick = onScanClick
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                vaultUiState is VaultUiState.Loading -> LoadingGlow()
                vaultEntries.isEmpty()               -> EmptyVaultPrompt()
                else                                 -> VaultGallery(vaultEntries)
            }
        }
    }

    if (vaultUiState is VaultUiState.ConfirmScan) {
        val confirmState = vaultUiState as VaultUiState.ConfirmScan
        ConfirmScanSheet(
            state = ScanConfirmationState(
                refinedScan = confirmState.refinedScan,
                candidates = confirmState.candidates,
                vaultId = -1L
            ),
            onLinkConfirmed = { _, expenseId, editedScan ->
                val matchedExpense = confirmState.candidates.firstOrNull { it.id.toLong() == expenseId }
                if (matchedExpense != null) {
                    viewModel.linkPendingScanToExpense(matchedExpense, editedScan)
                }
            },
            onManualSave = { editedScan -> viewModel.savePendingScanToVault(editedScan) },
            onDismiss = { viewModel.dismissConfirmation() }
        )
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = "VAULT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    text  = "Evidence Board",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = TextMain
        )
    )
}

// ── FAB ───────────────────────────────────────────────────────────────────────

@Composable
private fun ScanFAB(onClick: () -> Unit) {
    FloatingActionButton(
        onClick       = onClick,
        shape         = CircleShape,
        containerColor = LumeAmber,
        contentColor  = EliteNavy,
        modifier      = Modifier.navigationBarsPadding()
    ) {
        Icon(
            imageVector        = Icons.Rounded.DocumentScanner,
            contentDescription = "Scan receipt",
            modifier           = Modifier.size(24.dp)
        )
    }
}

// ── Gallery states ─────────────────────────────────────────────────────────────

@Composable
private fun LoadingGlow() {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue   = 0.3f,
        targetValue    = 0.9f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LumeAmber.copy(alpha = alpha * 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Rounded.DocumentScanner,
                    contentDescription = null,
                    tint               = LumeAmber.copy(alpha = alpha),
                    modifier           = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text  = "Parsing receipt…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun EmptyVaultPrompt() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        EliteGlassCard(glowColor = LumeAmber.copy(alpha = 0.4f)) {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector        = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint               = LumeAmber,
                    modifier           = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text  = "Vault is empty",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "Tap the scan button to capture your first receipt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

// ── Staggered Evidence Board ──────────────────────────────────────────────────

@Composable
private fun VaultGallery(entries: List<VaultEntity>) {
    LazyVerticalStaggeredGrid(
        columns              = StaggeredGridCells.Fixed(2),
        modifier             = Modifier.fillMaxSize(),
        contentPadding       = PaddingValues(
            start  = 16.dp,
            top    = 16.dp,
            end    = 16.dp,
            bottom = 80.dp
        ),
        verticalItemSpacing  = 16.dp,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            ReceiptCard(entry)
        }
    }
}

// ── Receipt Card ──────────────────────────────────────────────────────────────

@Composable
private fun ReceiptCard(entry: VaultEntity) {
    val glowColor = if (entry.isLinkedToExpense) LumeEmerald else LumeAmber

    EliteGlassCard(
        glowColor = glowColor,
        modifier  = Modifier.fillMaxWidth()
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────────────
        AsyncReceiptImage(
            imagePath = entry.imagePath,
            modifier  = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 250.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        )

        Spacer(Modifier.height(10.dp))

        // ── Merchant & Amount ─────────────────────────────────────────────────
        Text(
            text      = entry.merchantName ?: "Processing…",
            style     = MaterialTheme.typography.bodyMedium,
            color     = TextMain,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = entry.totalAmount?.let { "€${"%.2f".format(it)}" } ?: "--",
                style     = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color     = ConfigAccent
            )
            // ── Paperclip status icon ─────────────────────────────────────────
            Icon(
                imageVector        = if (entry.isLinkedToExpense) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                contentDescription = if (entry.isLinkedToExpense) "Linked" else "Unlinked",
                tint               = glowColor.copy(alpha = 0.85f),
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ── Async receipt image loader ─────────────────────────────────────────────────

@Composable
private fun AsyncReceiptImage(imagePath: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = imagePath) {
        value = withContext(Dispatchers.IO) {
            if (imagePath.isBlank()) return@withContext null
            if (imagePath.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(imagePath))
                    ?.use { BitmapFactory.decodeStream(it) }
            } else {
                BitmapFactory.decodeFile(imagePath)
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap             = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = modifier
        )
    } else {
        Box(
            modifier         = modifier.background(EliteNavy.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Rounded.Description,
                contentDescription = null,
                tint               = TextMuted,
                modifier           = Modifier.size(36.dp)
            )
        }
    }
}
