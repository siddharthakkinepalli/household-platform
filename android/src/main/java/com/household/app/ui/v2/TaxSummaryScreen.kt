package com.household.app.ui.v2

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.data.AppDatabase
import com.household.app.data.entities.TaxTagEntity
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.SurfaceNavy
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// ─── ViewModel ───────────────────────────────────────────────────────────────

class TaxSummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val taxTagDao = AppDatabase.getInstance(application).taxTagDao()

    val selectedYear: MutableStateFlow<Int> = MutableStateFlow(
        Calendar.getInstance().get(Calendar.YEAR)
    )

    private val _availableYears = MutableStateFlow<List<Int>>(
        listOf(Calendar.getInstance().get(Calendar.YEAR))
    )
    val availableYears: StateFlow<List<Int>> = _availableYears

    private val _tagsByCategory = MutableStateFlow<Map<String, List<TaxTagEntity>>>(emptyMap())
    val tagsByCategory: StateFlow<Map<String, List<TaxTagEntity>>> = _tagsByCategory

    init {
        viewModelScope.launch {
            loadAvailableYears()
            selectedYear.collect { year ->
                loadTagsForYear(year)
            }
        }
    }

    private suspend fun loadAvailableYears() {
        val years = withContext(Dispatchers.IO) {
            taxTagDao.getAvailableYears().toMutableList()
        }
        val current = selectedYear.value
        if (current !in years) years.add(0, current)
        _availableYears.value = years
    }

    private suspend fun loadTagsForYear(year: Int) {
        val tags = withContext(Dispatchers.IO) {
            taxTagDao.getAllForYear(year)
        }
        _tagsByCategory.value = tags.groupBy { it.taxCategory }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun TaxSummaryScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: TaxSummaryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TaxSummaryViewModel(context.applicationContext as android.app.Application) }
        }
    )

    val availableYears by viewModel.availableYears.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val tagsByCategory by viewModel.tagsByCategory.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EliteNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextMain
                    )
                }
                Text(
                    text = "Tax Summary",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, buildCsv(tagsByCategory))
                            putExtra(Intent.EXTRA_SUBJECT, "Tax Summary $selectedYear")
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Tax Summary"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Export",
                        tint = LumeEmerald
                    )
                }
            }

            // Year picker row (only when multiple years exist)
            if (availableYears.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableYears.forEach { year ->
                        val isSelected = year == selectedYear
                        TextButton(
                            onClick = { viewModel.selectedYear.value = year },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isSelected) LumeEmerald else TextSecondary
                            ),
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) LumeEmerald.copy(alpha = 0.15f)
                                    else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Text(
                                text = "$year",
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Per-category cards or empty state
            if (tagsByCategory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tax tags yet. Long-press a transaction or document to tag it.",
                        fontSize = 14.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                tagsByCategory.forEach { (category, tags) ->
                    EliteGlassCard(glowColor = LumeEmerald.copy(alpha = 0.08f)) {
                        // Category title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LumeEmerald,
                                letterSpacing = 1.5.sp
                            )
                            // Count badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = LumeEmerald.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${tags.size} item${if (tags.size != 1) "s" else ""}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LumeEmerald
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        Spacer(Modifier.height(8.dp))

                        // Tag rows
                        tags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (tag.entityType == "transaction")
                                        Icons.Rounded.AccountBalanceWallet
                                    else
                                        Icons.Rounded.FolderOpen,
                                    contentDescription = tag.entityType,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${tag.entityId}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextMain
                                    )
                                    if (!tag.note.isNullOrBlank()) {
                                        Text(
                                            text = tag.note,
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─── Export helpers ───────────────────────────────────────────────────────────

private fun buildCsv(tagsByCategory: Map<String, List<TaxTagEntity>>): String {
    val sb = StringBuilder()
    sb.appendLine("Category,Type,EntityId,Note")
    tagsByCategory.forEach { (cat, tags) ->
        tags.forEach { tag ->
            sb.appendLine("\"$cat\",\"${tag.entityType}\",${tag.entityId},\"${tag.note ?: ""}\"")
        }
    }
    return sb.toString()
}
