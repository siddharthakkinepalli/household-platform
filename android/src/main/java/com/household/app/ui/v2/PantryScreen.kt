package com.household.app.ui.v2

import android.app.Application
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.data.AppDatabase
import com.household.app.data.repository.PantryRepositoryImpl
import com.household.app.domain.models.PantryCategory
import com.household.app.domain.models.PantryItem
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PantryBrowserViewModel(application: Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = PantryRepositoryImpl(db.pantryDao(), db.inventoryEventDao())
    private val eventDao = db.inventoryEventDao()

    val items: StateFlow<List<PantryItem>> = repo.getConfirmedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun consumeItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.consumeItem(id, 1)
        }
    }

    suspend fun getConsumeCount(pantryItemId: Long): Int =
        eventDao.getConsumeCountForItem(pantryItemId)

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                throw UnsupportedOperationException("Use the application-context factory")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val vm: PantryBrowserViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PantryBrowserViewModel(context.applicationContext as Application) as T
        }
    )
    val items by vm.items.collectAsStateWithLifecycle()
    val grouped = items.groupBy { it.category }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PANTRY", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = TextMain)
                        Text("${items.size} items", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TextMain)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.ShoppingCart, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Text("Pantry is empty", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
                    Text("Scan a grocery receipt to populate", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PantryCategory.entries.forEach { cat ->
                    val catItems = grouped[cat] ?: return@forEach
                    item(key = cat.name) {
                        PantryCategorySection(category = cat, items = catItems, vm = vm)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PantryCategorySection(category: PantryCategory, items: List<PantryItem>, vm: PantryBrowserViewModel) {
    val color = categoryAccent(category)
    EliteGlassCard(glowColor = color, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${categoryEmoji(category)} ${category.name.replace('_', ' ')}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            letterSpacing = 1.2.sp,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(10.dp))
        items.forEach { item ->
            PantryItemRow(item = item, accentColor = color, vm = vm)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantryItemRow(item: PantryItem, accentColor: Color, vm: PantryBrowserViewModel) {
    val isConsumed = item.quantity <= 0f
    val consumeCount by produceState(initialValue = 0, key1 = item.id) {
        value = vm.getConsumeCount(item.id)
    }

    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            vm.consumeItem(item.id)
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Text(
                    text = "Consumed",
                    color = Color(0xFFEF4444),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isConsumed) Modifier.background(Color.Transparent) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = if (isConsumed) Modifier.weight(1f) else Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                accentColor.copy(alpha = if (isConsumed) 0.3f else 0.7f),
                                CircleShape
                            )
                    )
                    if (isConsumed) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                                    append(item.name)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMain.copy(alpha = 0.4f)
                        )
                    } else {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, color = TextMain)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (consumeCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEF4444).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Consumed ${consumeCount}×",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                    if (item.quantity != 1f && !isConsumed) {
                        Box(
                            modifier = Modifier
                                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (item.quantity % 1f == 0f) "×${item.quantity.toInt()}" else "×${"%.2f".format(item.quantity)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun categoryAccent(cat: PantryCategory): Color = when (cat) {
    PantryCategory.DAIRY      -> Color(0xFF60A5FA)
    PantryCategory.GRAINS     -> LumeAmber
    PantryCategory.PRODUCE    -> LumeEmerald
    PantryCategory.MEAT_FISH  -> Color(0xFFFC8181)
    PantryCategory.BEVERAGES  -> LumeCyan
    PantryCategory.SNACKS     -> LumePurple
    PantryCategory.FROZEN     -> Color(0xFF93C5FD)
    PantryCategory.CONDIMENTS -> Color(0xFFFBBF24)
    PantryCategory.HOUSEHOLD  -> Color(0xFFA78BFA)
    PantryCategory.OTHER      -> Color(0xFF9CA3AF)
}

private fun categoryEmoji(cat: PantryCategory): String = when (cat) {
    PantryCategory.DAIRY      -> "🥛"
    PantryCategory.GRAINS     -> "🌾"
    PantryCategory.PRODUCE    -> "🥦"
    PantryCategory.MEAT_FISH  -> "🥩"
    PantryCategory.BEVERAGES  -> "🥤"
    PantryCategory.SNACKS     -> "🍫"
    PantryCategory.FROZEN     -> "🧊"
    PantryCategory.CONDIMENTS -> "🫙"
    PantryCategory.HOUSEHOLD  -> "🧼"
    PantryCategory.OTHER      -> "📦"
}
