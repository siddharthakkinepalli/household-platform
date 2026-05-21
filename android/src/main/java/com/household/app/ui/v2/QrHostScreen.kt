package com.household.app.ui.v2

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.household.app.data.AppDatabase
import com.household.app.sync.PairingServer
import com.household.app.sync.PairingToken
import com.household.app.sync.ServerState
import com.household.app.sync.WifiUtils
import com.household.app.ui.compose.theme.CriticalRed
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.LumeCyan
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.LumePurple
import com.household.app.ui.compose.theme.LumeWhite
import com.household.app.ui.compose.theme.SurfaceNavy
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextMuted
import com.household.app.ui.v2.components.EliteGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── State ────────────────────────────────────────────────────────────────────

sealed class HostState {
    object Preparing : HostState()
    object NoWifi : HostState()
    data class Ready(
        val syncQr: Bitmap,
        val installQr: Bitmap,
        val installUrl: String,
        val token: PairingToken
    ) : HostState()
    object Transferred : HostState()
    data class Error(val message: String) : HostState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class QrHostViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private var server: PairingServer? = null

    private val _state = MutableStateFlow<HostState>(HostState.Preparing)
    val state: StateFlow<HostState> = _state

    init {
        viewModelScope.launch { prepare() }
    }

    private suspend fun prepare() {
        val ip = WifiUtils.getLocalIp(getApplication())
        if (ip == null) {
            _state.value = HostState.NoWifi
            return
        }

        val port = withContext(Dispatchers.IO) { PairingServer.findFreePort() }
        val token = PairingToken.generate(ip, port)
        val apkPath = PairingServer.getApkPath(getApplication())
        val backupJson = withContext(Dispatchers.IO) { buildBackupJson() }

        val installUrl = "http://$ip:$port/install"
        val syncQr = withContext(Dispatchers.Default) { generateQr(token.toQrPayload()) }
        val installQr = withContext(Dispatchers.Default) { generateQr(installUrl) }

        server = PairingServer(token, backupJson, apkPath) { serverState ->
            when (serverState) {
                is ServerState.Waiting     -> {}
                is ServerState.Transferred -> _state.value = HostState.Transferred
                is ServerState.Error       -> _state.value = HostState.Error(serverState.message)
            }
        }

        _state.value = HostState.Ready(syncQr, installQr, installUrl, token)
        server!!.start()
    }

    private suspend fun buildBackupJson(): String {
        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.LocalDate::class.java,
                com.household.app.domain.services.LocalDateAdapter())
            .create()
        val backup = com.household.app.domain.models.HouseholdBackup(
            walletTransactions = db.walletTransactionDao().getAllTransactions(),
            vaultEntries = db.vaultDao().getAllEntriesList(),
            pantryItems = db.pantryDao().getAllPantry(),
            inventoryEvents = db.inventoryEventDao().getAllEvents(),
            merchantRules = db.merchantRuleDao().getAllRules(),
            categoryThresholds = db.categoryThresholdDao().getAllThresholds(),
            familyMembers = db.familyMemberDao().getAllMembersList(),
            documents = db.documentDao().getAllDocumentsList(),
            documentAlerts = db.documentAlertDao().getAllAlertsList()
        )
        return gson.toJson(backup)
    }

    private fun generateQr(content: String): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        for (x in 0 until 512) for (y in 0 until 512) {
            bmp.setPixel(x, y, if (bits[x, y]) 0xFF0D0D1E.toInt() else 0xFFF2F4FF.toInt())
        }
        return bmp
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private enum class HostTab { INSTALL, SYNC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrHostScreen(onBack: () -> Unit) {
    val vm: QrHostViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(HostTab.INSTALL) }

    Scaffold(
        containerColor = SurfaceNavy,
        topBar = {
            TopAppBar(
                title = { Text("Share with Partner", color = TextMain, fontWeight = FontWeight.SemiBold) },
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
                .padding(padding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is HostState.Preparing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = LumePurple)
                        Text("Preparing…", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is HostState.NoWifi -> {
                    EliteGlassCard {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = LumeWhite.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                            Text("No Wi-Fi detected", color = TextMain, fontWeight = FontWeight.SemiBold)
                            Text("Connect both devices to the same Wi-Fi network first.", color = TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                is HostState.Ready -> {
                    val context = LocalContext.current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp)
                    ) {
                        // Tab toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TabButton(
                                label = "Install App",
                                active = activeTab == HostTab.INSTALL,
                                activeColor = LumeAmber,
                                modifier = Modifier.weight(1f),
                                onClick = { activeTab = HostTab.INSTALL }
                            )
                            TabButton(
                                label = "Sync Data",
                                active = activeTab == HostTab.SYNC,
                                activeColor = LumeCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { activeTab = HostTab.SYNC }
                            )
                        }

                        // QR code
                        val (qrBitmap, accentColor, label, instructions) = when (activeTab) {
                            HostTab.INSTALL -> Quad(
                                s.installQr, LumeAmber,
                                "Scan with default camera",
                                listOf(
                                    "1. Open the camera app on partner's phone",
                                    "2. Scan this code — browser opens automatically",
                                    "3. Tap the downloaded jugaad.apk to install",
                                    "4. Then come back here and switch to Sync Data"
                                )
                            )
                            HostTab.SYNC -> Quad(
                                s.syncQr, LumeCyan,
                                "Scan with Jugaad app",
                                listOf(
                                    "1. Open Jugaad on partner's phone",
                                    "2. Config → Device Sync → Scan Partner's QR",
                                    "3. All data merges — nothing is overwritten"
                                )
                            )
                        }

                        Text(label, color = TextMuted, style = MaterialTheme.typography.bodySmall, letterSpacing = 0.5.sp)

                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .background(LumeWhite, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        EliteGlassCard(glowColor = accentColor.copy(alpha = 0.1f)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("HOW IT WORKS", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.8f), letterSpacing = 1.sp)
                                instructions.forEach {
                                    Text(it, color = TextMain, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        val footerText = if (activeTab == HostTab.SYNC)
                            "Expires in 5 minutes · Same Wi-Fi required"
                        else
                            "Same Wi-Fi required · No internet needed"
                        Text(footerText, color = TextMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)

                        // URL fallback for install tab — tap to copy
                        if (activeTab == HostTab.INSTALL) {
                            EliteGlassCard {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Camera not opening a link?", style = MaterialTheme.typography.labelSmall, color = LumeWhite.copy(alpha = 0.5f), letterSpacing = 1.sp)
                                    Text(
                                        text = s.installUrl,
                                        color = LumeAmber,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Jugaad install URL", s.installUrl))
                                            Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Text("Tap URL to copy · Type it in partner's browser manually", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (activeTab == HostTab.SYNC) {
                            CircularProgressIndicator(color = LumePurple.copy(alpha = 0.5f), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                is HostState.Transferred -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LumeEmerald, modifier = Modifier.size(56.dp))
                        Text("Sync complete!", color = TextMain, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text("Partner's device has imported the backup.", color = TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) { Text("Done", color = LumePurple) }
                    }
                }

                is HostState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = CriticalRed, modifier = Modifier.size(40.dp))
                        Text("Something went wrong", color = TextMain, fontWeight = FontWeight.SemiBold)
                        Text(s.message, color = TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onBack) { Text("Go Back", color = LumePurple) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(label: String, active: Boolean, activeColor: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) activeColor.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) activeColor.copy(alpha = 0.6f) else LumeWhite.copy(alpha = 0.2f)
        )
    ) {
        Text(label, color = if (active) activeColor else TextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

// Tiny destructuring helper — avoids four separate remember{}s
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
