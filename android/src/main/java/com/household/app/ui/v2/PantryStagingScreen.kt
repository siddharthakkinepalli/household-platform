package com.household.app.ui.v2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.domain.models.PantryCategory
import com.household.app.ui.compose.theme.ConfigAccent
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.SurfaceNavy
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.ui.v2.components.EliteHeader
import com.household.app.ui.viewmodels.PantryIntent
import com.household.app.ui.viewmodels.PantryUiState
import com.household.app.ui.viewmodels.PantryViewModel
import com.household.app.ui.viewmodels.StagedItemUi

@Composable
fun PantryStagingScreen(
    vaultId: Long,
    onBack: () -> Unit = {},
    onConfirmed: () -> Unit = {}
) {
    val viewModel: PantryViewModel = viewModel(factory = PantryViewModel.factory(vaultId))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedCount) {
        if (state.savedCount > 0) {
            snackbarHostState.showSnackbar(
                message = "${state.savedCount} item${if (state.savedCount > 1) "s" else ""} added to pantry",
                duration = SnackbarDuration.Short
            )
            onConfirmed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EliteNavy)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                EliteHeader(
                    title = "Receipt Items",
                    subtitle = buildSubtitle(state),
                    onBack = onBack
                )
            }
        ) { padding ->
            when {
                state.isLoading -> LoadingContent(Modifier.padding(padding))
                state.items.isEmpty() -> EmptyContent(Modifier.padding(padding))
                else -> StagingContent(
                    state = state,
                    onIntent = viewModel::onIntent,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun StagingContent(
    state: PantryUiState,
    onIntent: (PantryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCount = state.items.count { it.isSelected }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.items, key = { it.item.id }) { ui ->
                StagedItemRow(
                    ui = ui,
                    onToggle = { onIntent(PantryIntent.ToggleItem(ui.item.id)) },
                    onNameChange = { onIntent(PantryIntent.EditName(ui.item.id, it)) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        BulkAddButton(
            selectedCount = selectedCount,
            isSaving = state.isSaving,
            onConfirm = { onIntent(PantryIntent.BulkConfirm) }
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StagedItemRow(
    ui: StagedItemUi,
    onToggle: () -> Unit,
    onNameChange: (String) -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (ui.isSelected) Color.White.copy(alpha = 0.04f) else Color.Transparent,
        animationSpec = tween(200),
        label = "rowBg"
    )

    EliteGlassCard(
        glowColor = if (ui.isSelected) LumeEmerald.copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(bgColor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = ui.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = LumeEmerald,
                    uncheckedColor = TextSecondary,
                    checkmarkColor = EliteNavy
                )
            )

            BasicTextField(
                value = ui.editedName,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = if (ui.isSelected) TextMain else TextSecondary
                ),
                singleLine = true,
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) { inner() }
                }
            )

            CategoryPill(category = ui.item.category)
        }
    }
}

@Composable
private fun CategoryPill(category: PantryCategory) {
    val (label, color) = categoryDisplay(category)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BulkAddButton(
    selectedCount: Int,
    isSaving: Boolean,
    onConfirm: () -> Unit
) {
    val label = when {
        isSaving -> "Adding…"
        selectedCount == 0 -> "Nothing Selected"
        else -> "Add $selectedCount ${if (selectedCount == 1) "Item" else "Items"} to Pantry"
    }

    Button(
        onClick = { if (!isSaving && selectedCount > 0) onConfirm() },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (selectedCount > 0 && !isSaving)
                    Modifier.neonGlow(LumeEmerald, 12.dp)
                else
                    Modifier
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = LumeEmerald,
            contentColor = EliteNavy,
            disabledContainerColor = SurfaceNavy,
            disabledContentColor = TextMuted
        ),
        enabled = selectedCount > 0 && !isSaving,
        shape = RoundedCornerShape(14.dp)
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = EliteNavy,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LumeEmerald)
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No items parsed", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "The receipt didn't contain recognizable grocery items.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun buildSubtitle(state: PantryUiState): String = when {
    state.isLoading -> "Parsing receipt…"
    state.items.isEmpty() -> "No items found"
    else -> "${state.items.size} item${if (state.items.size != 1) "s" else ""} found from receipt"
}

private fun categoryDisplay(category: PantryCategory): Pair<String, Color> = when (category) {
    PantryCategory.DAIRY      -> "Dairy"      to LumeCyan
    PantryCategory.GRAINS     -> "Grains"     to LumeAmber
    PantryCategory.PRODUCE    -> "Produce"    to LumeEmerald
    PantryCategory.MEAT_FISH  -> "Meat/Fish"  to Color(0xFFEF4444)
    PantryCategory.BEVERAGES  -> "Beverages"  to Color(0xFF60A5FA)
    PantryCategory.SNACKS     -> "Snacks"     to LumePurple
    PantryCategory.FROZEN     -> "Frozen"     to ConfigAccent
    PantryCategory.CONDIMENTS -> "Condiments" to Color(0xFFF97316)
    PantryCategory.HOUSEHOLD  -> "Household"  to TextSecondary
    PantryCategory.OTHER      -> "Other"      to TextMuted
}

// Neon glow modifier using hardware-accelerated setShadowLayer
private fun Modifier.neonGlow(color: Color, blurRadius: Dp): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(blurRadius.toPx(), 0f, 0f, color.copy(alpha = 0.85f).toArgb())
            }
        }
        canvas.drawRoundRect(0f, 0f, size.width, size.height, 14.dp.toPx(), 14.dp.toPx(), paint)
    }
}
