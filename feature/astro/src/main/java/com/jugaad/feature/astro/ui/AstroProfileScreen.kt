package com.jugaad.feature.astro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstroProfileScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AstroProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.success) {
        if (uiState.success) onSuccess()
    }

    Scaffold(
        containerColor = Color(0xFF07090D),
        topBar = {
            TopAppBar(
                title = { Text("Astro Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Personal Alignment",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            // ── Basic Info Section ──────────────────────────────────────────
            ProfileInputCard(title = "IDENTIFICATION") {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true
                )
            }

            // ── Birth Details Section ───────────────────────────────────────
            ProfileInputCard(title = "TEMPORAL ORIGIN") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectorField(
                        label = "Date",
                        value = uiState.dobLabel,
                        icon = Icons.Rounded.CalendarMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { showDatePicker = true }
                    )
                    SelectorField(
                        label = "Time",
                        value = uiState.tobLabel,
                        icon = Icons.Rounded.Schedule,
                        modifier = Modifier.weight(1f),
                        onClick = { showTimePicker = true }
                    )
                }
            }

            // ── Location Section ────────────────────────────────────────────
            ProfileInputCard(title = "SPATIAL COORDINATES") {
                OutlinedTextField(
                    value = uiState.city,
                    onValueChange = viewModel::onCityChange,
                    label = { Text("City & Country") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Rounded.LocationCity, null) },
                    trailingIcon = {
                        IconButton(onClick = viewModel::lookupCoordinates) {
                            if (uiState.isLookingUp) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Search, "Lookup")
                            }
                        }
                    },
                    placeholder = { Text("e.g. Ulm, Germany") }
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.latitude,
                        onValueChange = viewModel::onLatChange,
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.longitude,
                        onValueChange = viewModel::onLonChange,
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = viewModel::saveProfile,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = uiState.canSave && !uiState.isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Align Profile", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    // ── Material 3 Pickers ──────────────────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDateSelected(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Confirm") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeSelected(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Confirm") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

@Composable
private fun ProfileInputCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.5.sp
        )
        content()
    }
}

@Composable
private fun SelectorField(label: String, value: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(56.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}
