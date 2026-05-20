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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PantryBrowserViewModel(application: Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val repo = PantryRepositoryImpl(AppDatabase.getInstance(application).pantryDao())

    val items: StateFlow<List<PantryItem>> = repo.getConfirmedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                        PantryCategorySection(category = cat, items = catItems)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PantryCategorySection(category: PantryCategory, items: List<PantryItem>) {
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
            PantryItemRow(item = item, accentColor = color)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PantryItemRow(item: PantryItem, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accentColor.copy(alpha = 0.7f), CircleShape)
            )
            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = TextMain)
        }
        if (item.quantity != 1f) {
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
