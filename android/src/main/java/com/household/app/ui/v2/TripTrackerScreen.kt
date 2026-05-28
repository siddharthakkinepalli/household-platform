package com.household.app.ui.v2

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.household.app.data.AppDatabase
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.data.entities.WalletTripEntity
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ── ViewModel ────────────────────────────────────────────────────────────────

class TripTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy { AppDatabase.getInstance(getApplication()) }

    private val _trips = MutableStateFlow<List<WalletTripEntity>>(emptyList())
    val trips: StateFlow<List<WalletTripEntity>> = _trips

    private val _tripTransactions = MutableStateFlow<List<WalletTransactionEntity>>(emptyList())
    val tripTransactions: StateFlow<List<WalletTransactionEntity>> = _tripTransactions

    private val _spentByTrip = MutableStateFlow<Map<String, Double>>(emptyMap())
    val spentByTrip: StateFlow<Map<String, Double>> = _spentByTrip

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val trips = db.walletTripDao().getAllTrips()
            _trips.value = trips
            val spent = trips.associate { trip ->
                trip.name to db.walletTransactionDao()
                    .getTransactionsByTrip(trip.name)
                    .sumOf { abs(it.amount) }
            }
            _spentByTrip.value = spent
        }
    }

    fun addTrip(name: String, budget: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            db.walletTripDao().insertTrip(WalletTripEntity(name = name.trim(), budget = budget))
            refresh()
        }
    }

    fun deleteTrip(trip: WalletTripEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.walletTripDao().deleteTrip(trip)
            refresh()
        }
    }

    fun loadTripTransactions(tripName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _tripTransactions.value = db.walletTransactionDao().getTransactionsByTrip(tripName)
        }
    }

    fun clearTripTransactions() {
        _tripTransactions.value = emptyList()
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTrackerScreen(
    onBack: () -> Unit = {},
    viewModel: TripTrackerViewModel = viewModel()
) {
    val trips by viewModel.trips.collectAsState()
    val spentByTrip by viewModel.spentByTrip.collectAsState()
    val tripTransactions by viewModel.tripTransactions.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTrip by remember { mutableStateOf<WalletTripEntity?>(null) }
    var confirmDeleteTrip by remember { mutableStateOf<WalletTripEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trip Tracker",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextMain
                        )
                        Text(
                            text = "${trips.size} trip${if (trips.size != 1) "s" else ""} • budget vs. spent",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, contentDescription = "Back", tint = TextMuted)
                    }
                }
            }

            if (trips.isEmpty()) {
                item {
                    EliteGlassCard(glowColor = LumeCyan.copy(alpha = 0.10f)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Flight,
                                contentDescription = null,
                                tint = LumeCyan.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No trips yet",
                                color = TextMain,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Tap + to create a trip and track its budget",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(trips, key = { it.name }) { trip ->
                    TripCard(
                        trip = trip,
                        spent = spentByTrip[trip.name] ?: 0.0,
                        onClick = {
                            selectedTrip = trip
                            viewModel.loadTripTransactions(trip.name)
                        },
                        onDelete = { confirmDeleteTrip = trip }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp),
            containerColor = LumeCyan,
            contentColor = Color.Black,
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add trip")
        }
    }

    // ── Add trip dialog ───────────────────────────────────────────────────────
    if (showAddDialog) {
        AddTripDialog(
            onAdd = { name, budget ->
                viewModel.addTrip(name, budget)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────────
    confirmDeleteTrip?.let { trip ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTrip = null },
            title = { Text("Delete trip?", color = TextMain) },
            text = {
                Text(
                    "\"${trip.name}\" will be removed. Transactions tagged to this trip are not deleted.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrip(trip)
                    confirmDeleteTrip = null
                }) {
                    Text("Delete", color = CriticalRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTrip = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF0E1117)
        )
    }

    // ── Trip detail sheet ─────────────────────────────────────────────────────
    selectedTrip?.let { trip ->
        TripDetailSheet(
            trip = trip,
            spent = spentByTrip[trip.name] ?: 0.0,
            transactions = tripTransactions,
            onDismiss = {
                selectedTrip = null
                viewModel.clearTripTransactions()
            }
        )
    }
}

// ── Trip card ─────────────────────────────────────────────────────────────────

@Composable
private fun TripCard(
    trip: WalletTripEntity,
    spent: Double,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val fraction = if (trip.budget > 0) (spent / trip.budget).coerceIn(0.0, 1.0).toFloat() else 0f
    val barColor = when {
        fraction < 0.75f -> LumeEmerald
        fraction < 1.00f -> LumeAmber
        else             -> CriticalRed
    }
    val remaining = trip.budget - spent

    EliteGlassCard(glowColor = barColor.copy(alpha = 0.10f)) {
        Column(
            modifier = Modifier.clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(LumeCyan.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Flight, null, tint = LumeCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.name,
                        color = TextMain,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Budget €${"%.0f".format(trip.budget)}",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Delete, null, tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.12f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spent €${"%.0f".format(spent)}",
                    color = barColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (remaining >= 0) "€${"%.0f".format(remaining)} left"
                           else "€${"%.0f".format(-remaining)} over",
                    color = if (remaining >= 0) TextSecondary else CriticalRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ── Trip detail bottom sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailSheet(
    trip: WalletTripEntity,
    spent: Double,
    transactions: List<WalletTransactionEntity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10141D),
        contentColor = TextMain
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                trip.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            Text(
                "€${"%.2f".format(spent)} spent of €${"%.2f".format(trip.budget)} budget",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            if (transactions.isEmpty()) {
                Text(
                    "No transactions tagged to this trip yet.\nTag transactions via the edit (pencil) action in Wallet.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                transactions.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.title, color = TextMain, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text(tx.date.toString(), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            "€${"%.2f".format(abs(tx.amount))}",
                            color = LumeAmber,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// ── Add trip dialog ───────────────────────────────────────────────────────────

@Composable
private fun AddTripDialog(
    onAdd: (name: String, budget: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var budgetText by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = LumeCyan,
        unfocusedBorderColor = TextMuted.copy(alpha = 0.30f),
        focusedTextColor = TextMain,
        unfocusedTextColor = TextMain,
        cursorColor = LumeCyan,
        focusedLabelColor = LumeCyan,
        unfocusedLabelColor = TextMuted
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1117),
        title = { Text("New Trip", color = TextMain, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trip name") },
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Budget (€)") },
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val budget = budgetText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onAdd(name, budget)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LumeCyan.copy(alpha = 0.18f),
                    contentColor = LumeCyan
                )
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
