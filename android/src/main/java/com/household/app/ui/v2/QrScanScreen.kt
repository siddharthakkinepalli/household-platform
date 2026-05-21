package com.household.app.ui.v2

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.household.app.data.AppDatabase
import com.household.app.domain.models.HouseholdBackup
import com.household.app.domain.services.LocalDateAdapter
import com.household.app.sync.FetchResult
import com.household.app.sync.PairingClient
import com.household.app.sync.PairingToken
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.SurfaceNavy
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

// ─── ViewModel ────────────────────────────────────────────────────────────────

sealed class ScanState {
    object Scanning : ScanState()
    object Fetching : ScanState()
    data class Importing(val count: Int) : ScanState()
    data class Done(val count: Int) : ScanState()
    data class Error(val message: String) : ScanState()
}

class QrScanViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val gson = GsonBuilder()
        .registerTypeAdapter(java.time.LocalDate::class.java, LocalDateAdapter())
        .create()

    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state: StateFlow<ScanState> = _state

    private var handled = false

    fun onQrDetected(payload: String) {
        if (handled) return
        val token = PairingToken.fromQrPayload(payload) ?: return
        if (token.isExpired()) {
            _state.value = ScanState.Error("QR code has expired — ask your partner to generate a new one")
            return
        }
        handled = true
        viewModelScope.launch {
            _state.value = ScanState.Fetching
            when (val result = PairingClient.fetchBackup(token)) {
                is FetchResult.Failure -> _state.value = ScanState.Error(result.message)
                is FetchResult.Success -> importJson(result.json)
            }
        }
    }

    private suspend fun importJson(json: String) = withContext(Dispatchers.IO) {
        try {
            val backup = gson.fromJson(json, HouseholdBackup::class.java)
                ?: run { _state.value = ScanState.Error("Could not parse backup data"); return@withContext }

            var count = 0
            withContext(Dispatchers.Main) { _state.value = ScanState.Importing(0) }

            if (backup.walletTransactions.isNotEmpty()) {
                db.walletTransactionDao().insertTransactionsIgnore(backup.walletTransactions)
                count += backup.walletTransactions.size
            }
            if (backup.vaultEntries.isNotEmpty()) {
                db.vaultDao().insertEntries(backup.vaultEntries); count += backup.vaultEntries.size
            }
            if (backup.pantryItems.isNotEmpty()) {
                db.pantryDao().insertItemsIgnore(backup.pantryItems); count += backup.pantryItems.size
            }
            if (backup.inventoryEvents.isNotEmpty()) {
                db.inventoryEventDao().insertEvents(backup.inventoryEvents); count += backup.inventoryEvents.size
            }
            if (backup.merchantRules.isNotEmpty()) {
                db.merchantRuleDao().insertRulesIgnore(backup.merchantRules); count += backup.merchantRules.size
            }
            if (backup.categoryThresholds.isNotEmpty()) {
                db.categoryThresholdDao().insertThresholdsIgnore(backup.categoryThresholds); count += backup.categoryThresholds.size
            }
            if (backup.familyMembers.isNotEmpty()) {
                db.familyMemberDao().insertMembers(backup.familyMembers); count += backup.familyMembers.size
            }
            if (backup.documents.isNotEmpty()) {
                db.documentDao().insertDocuments(backup.documents); count += backup.documents.size
            }
            if (backup.documentAlerts.isNotEmpty()) {
                db.documentAlertDao().insertAlertsIgnore(backup.documentAlerts); count += backup.documentAlerts.size
            }
            _state.value = ScanState.Done(count)
        } catch (e: Exception) {
            _state.value = ScanState.Error(e.message ?: "Import failed")
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(onBack: () -> Unit) {
    val vm: QrScanViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var cameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!cameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        containerColor = SurfaceNavy,
        topBar = {
            TopAppBar(
                title = { Text("Scan Partner's QR", color = TextMain, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextMain)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceNavy)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is ScanState.Scanning -> {
                    if (cameraPermission) {
                        QrCameraView(onQrDetected = vm::onQrDetected)
                        // Finder overlay
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                                val corner = 32.dp.toPx()
                                val color = androidx.compose.ui.graphics.Color(0xFF8B5CF6)
                                // four corners
                                drawPath(androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, corner); lineTo(0f, 0f); lineTo(corner, 0f)
                                }, color, style = stroke)
                                drawPath(androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width - corner, 0f); lineTo(size.width, 0f); lineTo(size.width, corner)
                                }, color, style = stroke)
                                drawPath(androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width, size.height - corner); lineTo(size.width, size.height); lineTo(size.width - corner, size.height)
                                }, color, style = stroke)
                                drawPath(androidx.compose.ui.graphics.Path().apply {
                                    moveTo(corner, size.height); lineTo(0f, size.height); lineTo(0f, size.height - corner)
                                }, color, style = stroke)
                            }
                        }
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                        ) {
                            Text(
                                "Point camera at your partner's QR code",
                                color = LumeWhite.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        Text("Camera permission required", color = TextMuted, textAlign = TextAlign.Center)
                    }
                }
                is ScanState.Fetching -> StatusPanel(
                    icon = { CircularProgressIndicator(color = LumePurple, modifier = Modifier.size(44.dp)) },
                    title = "Connecting…",
                    subtitle = "Downloading backup from partner's device"
                )
                is ScanState.Importing -> StatusPanel(
                    icon = { CircularProgressIndicator(color = LumePurple, modifier = Modifier.size(44.dp)) },
                    title = "Importing…",
                    subtitle = "Merging data into your Jugaad"
                )
                is ScanState.Done -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LumeEmerald, modifier = Modifier.size(56.dp))
                        Text("All done!", color = TextMain, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text("${s.count} records merged from partner's device.", color = TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) { Text("Continue", color = LumePurple) }
                    }
                }
                is ScanState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = CriticalRed, modifier = Modifier.size(40.dp))
                        Text("Import failed", color = TextMain, fontWeight = FontWeight.SemiBold)
                        Text(s.message, color = TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onBack) { Text("Go Back", color = LumePurple) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(icon: @Composable () -> Unit, title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        icon()
        Text(title, color = TextMain, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QrCameraView(onQrDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val scanning = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    val previewView = remember { PreviewView(context) }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { imageProxy ->
                if (!scanning.get()) { imageProxy.close(); return@setAnalyzer }
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull { it.rawValue?.startsWith("jugaad://") == true }
                                ?.rawValue
                                ?.let { payload ->
                                    if (scanning.getAndSet(false)) {
                                        onQrDetected(payload)
                                    }
                                }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            scanning.set(false)
            try { ProcessCameraProvider.getInstance(context).get()?.unbindAll() } catch (_: Exception) {}
            executor.shutdown()
        }
    }
}
