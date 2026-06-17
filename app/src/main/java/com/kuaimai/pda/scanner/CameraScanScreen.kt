package com.kuaimai.pda.scanner

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.SurfaceGray
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary

/**
 * ML Kit相机扫码页面
 * 使用CameraX + ML Kit BarcodeScanning
 * 扫描到条码后通过callback返回结果
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    // 相机权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        hasCameraPermission = granted
    }

    // 初始请求权限
    DisposableEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { /* 清理 */ }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("扫码", color = SurfaceWhite) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = SurfaceWhite)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandBlue)
        )

        if (hasCameraPermission) {
            CameraPreview(
                onBarcodeScanned = onBarcodeScanned
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "需要相机权限才能扫码",
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * CameraX相机预览 + ML Kit条码分析
 */
@Composable
private fun CameraPreview(
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScanned by remember { mutableStateOf(false) }

    // ML Kit条码扫描器
    val scanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx),
                            { imageProxy ->
                                processImageProxy(scanner, imageProxy) { barcode ->
                                    if (!isScanned) {
                                        isScanned = true
                                        onBarcodeScanned(barcode)
                                    }
                                }
                            }
                        )
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "相机绑定失败: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 处理相机帧，使用ML Kit检测条码
 * @param scanner ML Kit条码扫描器
 * @param imageProxy 相机帧数据
 * @param onResult 条码结果回调
 */
@Suppress("UNCHECKED_CAST")
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes: List<Barcode> ->
            for (barcode in barcodes) {
                val rawValue = barcode.rawValue
                if (!rawValue.isNullOrEmpty()) {
                    onResult(rawValue)
                    break
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
        .addOnFailureListener {
            imageProxy.close()
        }
}
