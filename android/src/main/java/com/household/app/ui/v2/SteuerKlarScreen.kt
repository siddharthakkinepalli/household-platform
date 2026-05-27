package com.household.app.ui.v2

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.household.app.data.AppDatabase
import com.household.app.data.dao.TaxCheckDao
import com.household.app.data.entities.TaxCheckEntity
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.SurfaceNavy
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import com.household.app.vault.workers.TaxChecklistWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

// ─── ViewModel ───────────────────────────────────────────────────────────────

class SteuerKlarViewModel(application: Application) : AndroidViewModel(application) {

    private val taxCheckDao: TaxCheckDao =
        AppDatabase.getInstance(application).taxCheckDao()

    val selectedYear: MutableStateFlow<Int> = MutableStateFlow(
        Calendar.getInstance().get(Calendar.YEAR)
    )

    val availableYears: StateFlow<List<Int>> = taxCheckDao.observeAll()
        .combine(selectedYear) { checks, selected ->
            val years = checks.map { it.year }.distinct().sorted().reversed().toMutableList()
            if (selected !in years) years.add(0, selected)
            years
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(Calendar.getInstance().get(Calendar.YEAR)))

    val taxCheck: StateFlow<TaxCheckEntity?> = taxCheckDao.observeAll()
        .combine(selectedYear) { checks, year ->
            checks.firstOrNull { it.year == year }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun triggerScan(context: android.content.Context) {
        val year = selectedYear.value
        val request = OneTimeWorkRequestBuilder<TaxChecklistWorker>()
            .setInputData(workDataOf("year" to year))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun markComplete(complete: Boolean) {
        viewModelScope.launch {
            taxCheckDao.setComplete(selectedYear.value, complete)
        }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun SteuerKlarScreen(onNavigateToTaxSummary: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: SteuerKlarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SteuerKlarViewModel(context.applicationContext as Application) }
        }
    )

    val taxCheck by viewModel.taxCheck.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

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
            // Header
            SteuerKlarHeader(
                year = selectedYear,
                taxCheck = taxCheck
            )

            // Year picker (only when multiple years exist)
            if (availableYears.size > 1) {
                YearPickerRow(
                    years = availableYears,
                    selected = selectedYear,
                    onSelect = { viewModel.selectedYear.value = it }
                )
            }

            // Sync row
            SyncRow(
                taxCheck = taxCheck,
                onScanNow = { viewModel.triggerScan(context) }
            )

            // Main checklist card
            if (taxCheck != null) {
                ChecklistCard(
                    taxCheck = taxCheck!!,
                    onUploadMissing = {
                        Toast.makeText(context, "Open Vault to upload", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // No folder linked yet
                LinkFolderCard(
                    year = selectedYear,
                    onScan = { viewModel.triggerScan(context) }
                )
            }

            // Mark complete button
            taxCheck?.let { check ->
                MarkCompleteButton(
                    year = selectedYear,
                    isComplete = check.isComplete,
                    onClick = { viewModel.markComplete(!check.isComplete) }
                )
            }

            // Tax summary — tagged items
            FilledTonalButton(
                onClick = onNavigateToTaxSummary,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SurfaceNavy,
                    contentColor = LumeEmerald
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Tagged Items", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun SteuerKlarHeader(year: Int, taxCheck: TaxCheckEntity?) {
    val totalItems = 8
    val okCount = if (taxCheck != null) countOkItems(taxCheck) else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "STEUERKLÄR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 2.sp
            )
            Text(
                text = "$year",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMain,
                letterSpacing = (-0.5).sp
            )
        }

        if (taxCheck != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$okCount of $totalItems OK",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (okCount == totalItems) LumeEmerald else LumeAmber
                )
                StatusDot(
                    color = when {
                        okCount == totalItems -> LumeEmerald
                        okCount >= totalItems / 2 -> LumeAmber
                        else -> CriticalRed
                    },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun YearPickerRow(
    years: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        years.forEach { year ->
            val isSelected = year == selected
            Box(
                modifier = Modifier
                    .clickable { onSelect(year) }
                    .background(
                        color = if (isSelected) LumeEmerald.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$year",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) LumeEmerald else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SyncRow(
    taxCheck: TaxCheckEntity?,
    onScanNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (taxCheck?.lastSyncedAt != null && taxCheck.lastSyncedAt > 0L)
                "Last synced: ${formatRelativeTime(taxCheck.lastSyncedAt)}"
            else
                "Not synced yet",
            fontSize = 12.sp,
            color = TextMuted
        )

        FilledTonalButton(
            onClick = onScanNow,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = SurfaceNavy,
                contentColor = LumeEmerald
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Scan Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChecklistCard(
    taxCheck: TaxCheckEntity,
    onUploadMissing: () -> Unit
) {
    EliteGlassCard(glowColor = LumeEmerald.copy(alpha = 0.08f)) {
        val items = buildChecklistItems(taxCheck)
        items.forEachIndexed { index, item ->
            ChecklistRow(item = item, onUpload = onUploadMissing)
            if (index < items.lastIndex) {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem, onUpload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            StatusDot(color = item.statusColor)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = item.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.detail,
                    fontSize = 11.sp,
                    color = if (item.statusColor == CriticalRed) CriticalRed.copy(alpha = 0.8f) else TextMuted
                )
            }
        }

        if (item.showUpload) {
            Box(
                modifier = Modifier
                    .clickable { onUpload() }
                    .background(
                        color = LumeEmerald.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Upload",
                        tint = LumeEmerald,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("Upload", fontSize = 11.sp, color = LumeEmerald, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LinkFolderCard(year: Int, onScan: () -> Unit) {
    EliteGlassCard(glowColor = LumeAmber.copy(alpha = 0.1f)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = LumeAmber,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No Tax Folder Linked",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Make sure Google Drive is connected and your 'Income Tax' folder is inside the Jugaad folder.",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onScan,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LumeAmber.copy(alpha = 0.15f),
                    contentColor = LumeAmber
                )
            ) {
                Text("Link Tax Folder for $year", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MarkCompleteButton(year: Int, isComplete: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isComplete) LumeEmerald.copy(alpha = 0.2f) else SurfaceNavy,
            contentColor = if (isComplete) LumeEmerald else LumeWhite
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isComplete) "$year Tax — Marked Complete ✓" else "Mark $year Complete",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .background(color = color, shape = CircleShape)
    )
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private data class ChecklistItem(
    val label: String,
    val detail: String,
    val statusColor: Color,
    val showUpload: Boolean = false
)

private fun buildChecklistItems(t: TaxCheckEntity): List<ChecklistItem> {
    return listOf(
        ChecklistItem(
            label = "Tax Excel",
            detail = if (t.taxExcelFound) "found" else "MISSING",
            statusColor = if (t.taxExcelFound) LumeEmerald else CriticalRed,
            showUpload = !t.taxExcelFound
        ),
        ChecklistItem(
            label = "Payslips",
            detail = if (t.payslipsFound > 0) "${t.payslipsFound} files" else "MISSING",
            statusColor = if (t.payslipsFound > 0) LumeEmerald else CriticalRed,
            showUpload = t.payslipsFound == 0
        ),
        ChecklistItem(
            label = "Lohnsteuerbescheinigung",
            detail = if (t.lohnsteuerFound) "found" else "MISSING",
            statusColor = if (t.lohnsteuerFound) LumeEmerald else CriticalRed,
            showUpload = !t.lohnsteuerFound
        ),
        ChecklistItem(
            label = "Lohnsteuer (Chithra)",
            detail = if (t.lohnsteuerChitraFound) "found" else "MISSING",
            statusColor = if (t.lohnsteuerChitraFound) LumeEmerald else CriticalRed,
            showUpload = !t.lohnsteuerChitraFound
        ),
        ChecklistItem(
            label = "Kita documents",
            detail = if (t.kitaDocsFound > 0) "${t.kitaDocsFound} files" else "0 files",
            statusColor = if (t.kitaDocsFound > 0) LumeEmerald else CriticalRed,
            showUpload = t.kitaDocsFound == 0
        ),
        ChecklistItem(
            label = "Bank Statements",
            detail = if (t.bankStatementsFound > 0) "${t.bankStatementsFound} files" else "0 files",
            statusColor = when {
                t.bankStatementsFound >= 3 -> LumeEmerald
                t.bankStatementsFound > 0 -> LumeAmber
                else -> CriticalRed
            },
            showUpload = t.bankStatementsFound == 0
        ),
        ChecklistItem(
            label = "Donation receipts",
            detail = if (t.donationDocsFound > 0) "${t.donationDocsFound} files" else "0 files",
            statusColor = if (t.donationDocsFound > 0) LumeEmerald else Color.White.copy(alpha = 0.25f)
        ),
        ChecklistItem(
            label = "Utility bills",
            detail = if (t.utilityBillsFound > 0) "${t.utilityBillsFound} files" else "0 files",
            statusColor = if (t.utilityBillsFound > 0) LumeEmerald else CriticalRed,
            showUpload = t.utilityBillsFound == 0
        )
    )
}

private fun countOkItems(t: TaxCheckEntity): Int {
    var count = 0
    if (t.taxExcelFound) count++
    if (t.payslipsFound > 0) count++
    if (t.lohnsteuerFound) count++
    if (t.lohnsteuerChitraFound) count++
    if (t.kitaDocsFound > 0) count++
    if (t.bankStatementsFound > 0) count++
    if (t.donationDocsFound > 0) count++
    if (t.utilityBillsFound > 0) count++
    return count
}

private fun formatRelativeTime(epochMs: Long?): String {
    if (epochMs == null || epochMs == 0L) return "never"
    return try {
        val then = Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val now = LocalDateTime.now()
        val minutes = ChronoUnit.MINUTES.between(then, now)
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    } catch (e: Exception) {
        "unknown"
    }
}
