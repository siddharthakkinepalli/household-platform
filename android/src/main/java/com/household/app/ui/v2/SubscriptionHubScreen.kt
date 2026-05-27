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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.data.AppDatabase
import com.household.app.data.entities.RecurringBillEntity
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ── ViewModel ────────────────────────────────────────────────────────────────

class SubscriptionHubViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy { AppDatabase.getInstance(getApplication()) }

    val autoBills: StateFlow<List<RecurringBillEntity>> = db.recurringBillDao()
        .observeAutoBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val confirmedBills: StateFlow<List<RecurringBillEntity>> = db.recurringBillDao()
        .observeConfirmedBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Bills due within the next 30 days, sorted by days-until
    val upcomingOutflows: StateFlow<List<Pair<RecurringBillEntity, LocalDate>>> =
        db.recurringBillDao()
            .observeConfirmedBills()
            .combine(kotlinx.coroutines.flow.flowOf(LocalDate.now())) { bills, today ->
                val zone = ZoneId.systemDefault()
                val window = today.plusDays(30)
                bills.filter { it.isActive }.mapNotNull { bill ->
                    val lastSeen = Instant.ofEpochMilli(bill.lastSeenDate)
                        .atZone(zone).toLocalDate()
                    val nextDebit = lastSeen.plusDays(30)
                    if (!nextDebit.isBefore(today) && !nextDebit.isAfter(window)) bill to nextDebit
                    else null
                }.sortedBy { it.second }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _contractCosts = kotlinx.coroutines.flow.MutableStateFlow<List<Pair<String, Double>>>(emptyList())
    val contractCosts: StateFlow<List<Pair<String, Double>>> = _contractCosts

    private val _salaryAmount = kotlinx.coroutines.flow.MutableStateFlow<Double?>(null)
    val salaryAmount: StateFlow<Double?> = _salaryAmount

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val salary = db.salarySourceDao().getSalarySource()?.lastAmount
            val docs = db.documentDao().getAllDocumentsList()
                .filter { it.monthlyCost != null && it.monthlyCost > 0 }
                .map { it.title to (it.monthlyCost ?: 0.0) }
            _salaryAmount.value = salary
            _contractCosts.value = docs
        }
    }

    fun confirmBill(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.recurringBillDao().updateSource(id, "CONFIRMED")
        }
    }

    fun dismissBill(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.recurringBillDao().updateSource(id, "DISMISSED")
            db.recurringBillDao().setActive(id, false)
        }
    }

    fun toggleActive(id: Long, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.recurringBillDao().setActive(id, active)
        }
    }

    fun editBill(id: Long, pattern: String, amount: Double, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.recurringBillDao().updateBill(id, pattern, amount, category)
        }
    }

    fun addManual(name: String, amount: Double, category: String) {
        val pattern = name.take(14).lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        viewModelScope.launch(Dispatchers.IO) {
            db.recurringBillDao().insert(
                RecurringBillEntity(
                    merchantPattern = pattern,
                    normalizedAmount = amount,
                    minAmount = amount * 0.85,
                    maxAmount = amount * 1.15,
                    category = category,
                    lastSeenDate = System.currentTimeMillis(),
                    cycleCount = 0,
                    isActive = true,
                    source = "MANUAL"
                )
            )
        }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionHubScreen(
    viewModel: SubscriptionHubViewModel = viewModel()
) {
    val autoBills by viewModel.autoBills.collectAsState()
    val confirmedBills by viewModel.confirmedBills.collectAsState()
    val contractCosts by viewModel.contractCosts.collectAsState()
    val salaryAmount by viewModel.salaryAmount.collectAsState()
    val upcomingOutflows by viewModel.upcomingOutflows.collectAsState()

    var editingBill by remember { mutableStateOf<RecurringBillEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    val confirmedTotal = confirmedBills.sumOf { it.normalizedAmount }
    val contractTotal = contractCosts.sumOf { it.second }
    val grandTotal = confirmedTotal + contractTotal

    val salaryPct = salaryAmount?.let { if (it > 0) grandTotal / it else null }
    val totalColor = when {
        salaryPct == null -> LumeEmerald
        salaryPct < 0.40  -> LumeEmerald
        salaryPct < 0.60  -> LumeAmber
        else              -> CriticalRed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(LumeCyan.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.80f, size.height * 0.14f),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.80f, size.height * 0.14f)
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Column {
                    Text(
                        text = "Monthly Commitments",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMain
                    )
                    Text(
                        text = "Confirmed fixed costs • ${autoBills.size} pending review",
                        color = if (autoBills.isNotEmpty()) LumeAmber else TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ── Total card ────────────────────────────────────────────────────
            item {
                EliteGlassCard(glowColor = totalColor.copy(alpha = 0.18f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CONFIRMED FIXED",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.8.sp,
                                color = TextMain.copy(alpha = 0.50f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "€${"%.0f".format(grandTotal)}/month",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = totalColor
                            )
                            if (salaryAmount != null && salaryPct != null) {
                                Text(
                                    text = "${"%.1f".format(salaryPct * 100)}% of €${"%.0f".format(salaryAmount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = totalColor.copy(alpha = 0.75f)
                                )
                            } else {
                                Text(
                                    text = "Link salary via CSV import",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            SubTotalChip(label = "Bills", amount = confirmedTotal, color = LumeEmerald)
                            Spacer(Modifier.height(4.dp))
                            SubTotalChip(label = "Contracts", amount = contractTotal, color = LumeCyan)
                        }
                    }
                }
            }

            // ── Unreviewed Detections ─────────────────────────────────────────
            if (autoBills.isNotEmpty()) {
                item {
                    HubSectionHeader(
                        title = "UNREVIEWED DETECTIONS",
                        badge = autoBills.size.toString(),
                        badgeColor = LumeAmber
                    )
                }
                items(autoBills, key = { it.id }) { bill ->
                    DetectionCard(
                        bill = bill,
                        onConfirm = { viewModel.confirmBill(bill.id) },
                        onDismiss = { viewModel.dismissBill(bill.id) }
                    )
                }
            }

            // ── Confirmed Bills ───────────────────────────────────────────────
            item {
                HubSectionHeader(
                    title = "CONFIRMED BILLS",
                    badge = confirmedBills.size.toString(),
                    badgeColor = LumeEmerald
                )
            }

            if (confirmedBills.isEmpty()) {
                item {
                    Text(
                        text = if (autoBills.isNotEmpty()) "Confirm detections above to add them here"
                               else "No bills yet — use + to add manually",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(confirmedBills, key = { it.id }) { bill ->
                    ConfirmedBillRow(
                        bill = bill,
                        onToggleActive = { viewModel.toggleActive(bill.id, it) },
                        onEdit = { editingBill = bill }
                    )
                }
            }

            // ── Upcoming Outflows ─────────────────────────────────────────────
            if (upcomingOutflows.isNotEmpty()) {
                item {
                    HubSectionHeader(
                        title = "UPCOMING THIS MONTH",
                        badge = upcomingOutflows.size.toString(),
                        badgeColor = LumeAmber
                    )
                }
                items(upcomingOutflows, key = { it.first.id }) { (bill, date) ->
                    val daysUntil = java.time.temporal.ChronoUnit.DAYS
                        .between(LocalDate.now(), date).toInt()
                    val urgentColor = if (daysUntil <= 5) CriticalRed else LumeAmber
                    EliteGlassCard(glowColor = urgentColor.copy(alpha = 0.08f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bill.merchantPattern.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMain
                                )
                                Text(
                                    text = if (daysUntil == 0) "Due today"
                                           else "Due in $daysUntil day${if (daysUntil == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = urgentColor
                                )
                            }
                            Text(
                                text = "€${"%.0f".format(bill.normalizedAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = urgentColor
                            )
                        }
                    }
                }
            }

            // ── Contract Costs ────────────────────────────────────────────────
            if (contractCosts.isNotEmpty()) {
                item {
                    HubSectionHeader(
                        title = "CONTRACT COSTS",
                        badge = contractCosts.size.toString(),
                        badgeColor = LumeCyan
                    )
                }
                items(contractCosts, key = { it.first }) { (title, cost) ->
                    EliteGlassCard(glowColor = LumeCyan.copy(alpha = 0.06f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMain
                                )
                                Text(
                                    text = "from Vault document",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            Text(
                                text = "€${"%.0f".format(cost)}/mo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = LumeCyan
                            )
                        }
                    }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp),
            containerColor = LumeEmerald,
            contentColor = Color.Black,
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add bill")
        }
    }

    // ── Edit bottom sheet ─────────────────────────────────────────────────────
    editingBill?.let { bill ->
        BillEditSheet(
            title = "Edit Bill",
            initialName = bill.merchantPattern.replaceFirstChar { it.uppercase() },
            initialAmount = bill.normalizedAmount,
            initialCategory = bill.category,
            onSave = { name, amount, category ->
                viewModel.editBill(bill.id, name, amount, category)
                editingBill = null
            },
            onDismiss = { editingBill = null }
        )
    }

    // ── Add manual sheet ──────────────────────────────────────────────────────
    if (showAddSheet) {
        BillEditSheet(
            title = "Add Manual Bill",
            initialName = "",
            initialAmount = 0.0,
            initialCategory = "Subscriptions",
            onSave = { name, amount, category ->
                viewModel.addManual(name, amount, category)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun SubTotalChip(label: String, amount: Double, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label €${"%.0f".format(amount)}",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun HubSectionHeader(title: String, badge: String, badgeColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.8.sp,
            color = TextMain.copy(alpha = 0.45f)
        )
        Box(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DetectionCard(
    bill: RecurringBillEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    EliteGlassCard(glowColor = LumeAmber.copy(alpha = 0.10f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bill.merchantPattern.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMain
                    )
                    Text(
                        text = "${bill.cycleCount} cycles • ${bill.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Text(
                    text = "€${"%.0f".format(bill.normalizedAmount)}/mo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = LumeAmber
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = LumeEmerald.copy(alpha = 0.18f),
                        contentColor = LumeEmerald
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Confirm", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextMuted)
                    Spacer(Modifier.width(4.dp))
                    Text("Dismiss", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ConfirmedBillRow(
    bill: RecurringBillEntity,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val sourceColor = if (bill.source == "MANUAL") LumeCyan else LumeEmerald
    EliteGlassCard(glowColor = sourceColor.copy(alpha = if (bill.isActive) 0.08f else 0.03f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bill.merchantPattern.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (bill.isActive) TextMain else TextMuted
                    )
                    Box(
                        modifier = Modifier
                            .background(sourceColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = bill.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = sourceColor,
                            fontSize = 9.sp
                        )
                    }
                }
                Text(
                    text = "€${"%.0f".format(bill.normalizedAmount)}/mo • ${bill.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bill.isActive) TextSecondary else TextMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = bill.isActive,
                    onCheckedChange = onToggleActive,
                    modifier = Modifier.size(width = 44.dp, height = 24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LumeEmerald,
                        checkedTrackColor = LumeEmerald.copy(alpha = 0.30f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = TextMuted.copy(alpha = 0.15f)
                    )
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillEditSheet(
    title: String,
    initialName: String,
    initialAmount: Double,
    initialCategory: String,
    onSave: (name: String, amount: Double, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName) }
    var amountText by remember { mutableStateOf(if (initialAmount > 0) "%.2f".format(initialAmount) else "") }
    var category by remember { mutableStateOf(initialCategory) }

    LaunchedEffect(initialName) { name = initialName }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = LumeEmerald,
        unfocusedBorderColor = TextMuted.copy(alpha = 0.30f),
        focusedTextColor = TextMain,
        unfocusedTextColor = TextMain,
        cursorColor = LumeEmerald,
        focusedLabelColor = LumeEmerald,
        unfocusedLabelColor = TextMuted
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10141D),
        contentColor = TextMain
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Merchant name") },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Monthly amount (€)") },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = TextMuted)
                }
                FilledTonalButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank() && amount > 0) {
                            onSave(name.trim(), amount, category.trim().ifBlank { "Other" })
                        }
                    },
                    enabled = name.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = LumeEmerald.copy(alpha = 0.20f),
                        contentColor = LumeEmerald
                    )
                ) {
                    Text("Save")
                }
            }
        }
    }
}
