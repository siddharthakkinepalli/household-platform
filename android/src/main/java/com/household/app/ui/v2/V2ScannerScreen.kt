package com.household.app.ui.v2

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.tasks.Task
import com.household.app.vault.scan.DetectionState
import com.household.app.vault.scan.DocumentCornerDetector
import com.household.app.vault.scan.DocumentScanner
import com.household.app.vault.scan.FrameDetectionResult
import com.household.app.vault.scan.LiveDocumentDetector
import com.household.app.vault.scan.OcrEngineProvider
import com.household.app.vault.scan.ReceiptPreprocessor
import com.household.app.vault.scan.ScanMode
import com.household.app.vault.scan.toViewQuad
import com.household.app.domain.models.vault.VisionTextPayload
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeAmber
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.viewmodels.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun V2ScannerScreen(
    onBack: () -> Unit,
    onScanProcessed: () -> Unit,
    scanMode: ScanMode = ScanMode.RECEIPT
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel: VaultViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = viewModelFactory {
            initializer {
                VaultViewModel(context.applicationContext as Application)
            }
        }
    )

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val preview = remember { Preview.Builder().build() }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isProcessing by remember { mutableStateOf(false) }
    var detectionState by remember { mutableStateOf<DetectionState>(DetectionState.Searching) }

    val liveDetector = remember {
        LiveDocumentDetector(intervalMs = 250L) { state ->
            detectionState = state
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EliteNavy)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        bindCamera(
                            context = ctx,
                            lifecycleOwner = lifecycleOwner,
                            preview = preview,
                            imageCapture = imageCapture,
                            scanMode = scanMode,
                            liveDetector = liveDetector,
                            analysisExecutor = cameraExecutor
                        )
                    }
                }
            )

            ScannerOverlay(detectionState = detectionState)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (isProcessing) return@FloatingActionButton

                            val outDir = File(context.filesDir, "vault_receipts")
                            if (!outDir.exists()) outDir.mkdirs()
                            val outFile = File(outDir, "scan_${System.currentTimeMillis()}.jpg")
                            val output = ImageCapture.OutputFileOptions.Builder(outFile).build()

                            isProcessing = true
                            imageCapture.takePicture(
                                output,
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(
                                        outputFileResults: ImageCapture.OutputFileResults
                                    ) {
                                        coroutineScope.launch {
                                            runCatching {
                                                processCapturedImage(context, outFile, scanMode)
                                            }.onSuccess { payload ->
                                                viewModel.processScanResult(
                                                    visionText = payload,
                                                    imagePath = outFile.absolutePath
                                                )
                                                onScanProcessed()
                                            }.onFailure {
                                                isProcessing = false
                                                Toast.makeText(
                                                    context,
                                                    "Failed to process receipt image",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isProcessing = false
                                        Toast.makeText(
                                            context,
                                            "Capture failed: ${exception.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        },
                        containerColor = LumeAmber,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Capture receipt",
                            tint = EliteNavy,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    val hintText = when {
                        detectionState is DetectionState.NoDocument ->
                            "Place on a plain, contrasting surface"
                        scanMode == ScanMode.DOCUMENT ->
                            "Align document edges with the guide"
                        else ->
                            "Position receipt inside the frame"
                    }
                    val hintColor = if (detectionState is DetectionState.NoDocument)
                        LumeAmber else TextMain

                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = hintColor,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LumeAmber)
                        Text(
                            text = "Reading receipt...",
                            color = TextMain,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DocumentScanner,
                    contentDescription = null,
                    tint = LumeAmber,
                    modifier = Modifier.size(42.dp)
                )
                Text(
                    text = "Camera permission is required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "Grant permission to scan receipts into your Vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )
                FloatingActionButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    containerColor = LumeAmber,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = "Grant camera permission",
                        tint = EliteNavy
                    )
                }
            }
        }
    }
}

/**
 * Overlay that draws detected document quad in green when found,
 * or falls back to static amber corner brackets while searching.
 * Uses real frame dimensions + rotation to map quad points to view space correctly.
 */
@Composable
private fun ScannerOverlay(detectionState: DetectionState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (val state = detectionState) {
            is DetectionState.Found -> {
                // Transform from sensor space (with rotation) → display space → view space
                val scaled = state.result.toViewQuad(w, h)
                val path = Path().apply {
                    moveTo(scaled.topLeft.x, scaled.topLeft.y)
                    lineTo(scaled.topRight.x, scaled.topRight.y)
                    lineTo(scaled.bottomRight.x, scaled.bottomRight.y)
                    lineTo(scaled.bottomLeft.x, scaled.bottomLeft.y)
                    close()
                }
                drawPath(path, Color(0xFF4CAF50), style = Stroke(width = 5f))
            }
            is DetectionState.NoDocument -> {
                // Red-tinted brackets signal "move to better surface"
                drawGuideBrackets(w, h, Color(0xFFFF5252))
            }
            is DetectionState.Searching -> {
                drawGuideBrackets(w, h, LumeAmber)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGuideBrackets(
    w: Float, h: Float, color: Color
) {
    val left   = w * 0.05f
    val top    = h * 0.08f
    val right  = w * 0.95f
    val bottom = h * 0.88f
    val corner = 62f
    val stroke = 7f

    drawLine(color, Offset(left, top), Offset(left + corner, top), strokeWidth = stroke)
    drawLine(color, Offset(left, top), Offset(left, top + corner), strokeWidth = stroke)
    drawLine(color, Offset(right, top), Offset(right - corner, top), strokeWidth = stroke)
    drawLine(color, Offset(right, top), Offset(right, top + corner), strokeWidth = stroke)
    drawLine(color, Offset(left, bottom), Offset(left + corner, bottom), strokeWidth = stroke)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - corner), strokeWidth = stroke)
    drawLine(color, Offset(right, bottom), Offset(right - corner, bottom), strokeWidth = stroke)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - corner), strokeWidth = stroke)
}

private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    preview: Preview,
    imageCapture: ImageCapture,
    scanMode: ScanMode,
    liveDetector: LiveDocumentDetector,
    analysisExecutor: java.util.concurrent.Executor
) {
    val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also {
            it.setAnalyzer(analysisExecutor) { image ->
                // Skip Canny/contour pass entirely in receipt mode — no quad overlay needed
                if (scanMode == ScanMode.DOCUMENT) liveDetector.analyze(image)
                else image.close()
            }
        }

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                imageAnalysis
            )
        },
        ContextCompat.getMainExecutor(context)
    )
}

private suspend fun processCapturedImage(
    context: android.content.Context,
    file: File,
    scanMode: ScanMode
): VisionTextPayload = withContext(Dispatchers.IO) {
    val enhanced = when (scanMode) {
        ScanMode.RECEIPT  -> ReceiptPreprocessor.process(file)   // CLAHE + bilateral, no warp
        ScanMode.DOCUMENT -> DocumentScanner.process(file)       // detect → warp → binarize
    }
    OcrEngineProvider.engine.recognize(context, enhanced.toUri())
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
