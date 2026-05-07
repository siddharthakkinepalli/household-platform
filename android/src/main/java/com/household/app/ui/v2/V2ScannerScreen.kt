package com.household.app.ui.v2

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.household.app.data.refiner.toVisionTextPayload
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
    onScanProcessed: () -> Unit
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
                            imageCapture = imageCapture
                        )
                    }
                }
            )

            ScannerOverlay()

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
                                                processCapturedImage(context, outFile)
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

                    Text(
                        text = "Position receipt inside the frame",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain,
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

@Composable
private fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val left = size.width * 0.12f
        val top = size.height * 0.22f
        val right = size.width * 0.88f
        val bottom = size.height * 0.72f
        val corner = 62f
        val stroke = 7f

        drawLine(LumeAmber, Offset(left, top), Offset(left + corner, top), strokeWidth = stroke)
        drawLine(LumeAmber, Offset(left, top), Offset(left, top + corner), strokeWidth = stroke)

        drawLine(LumeAmber, Offset(right, top), Offset(right - corner, top), strokeWidth = stroke)
        drawLine(LumeAmber, Offset(right, top), Offset(right, top + corner), strokeWidth = stroke)

        drawLine(LumeAmber, Offset(left, bottom), Offset(left + corner, bottom), strokeWidth = stroke)
        drawLine(LumeAmber, Offset(left, bottom), Offset(left, bottom - corner), strokeWidth = stroke)

        drawLine(LumeAmber, Offset(right, bottom), Offset(right - corner, bottom), strokeWidth = stroke)
        drawLine(LumeAmber, Offset(right, bottom), Offset(right, bottom - corner), strokeWidth = stroke)
    }
}

private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    preview: Preview,
    imageCapture: ImageCapture
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        },
        ContextCompat.getMainExecutor(context)
    )
}

private suspend fun processCapturedImage(
    context: android.content.Context,
    file: File
): VisionTextPayload = withContext(Dispatchers.IO) {
    val inputImage = InputImage.fromFilePath(context, file.toUri())
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        recognizer.process(inputImage).awaitResult().toVisionTextPayload()
    } finally {
        recognizer.close()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
