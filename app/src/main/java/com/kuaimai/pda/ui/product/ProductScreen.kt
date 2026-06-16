package com.kuaimai.pda.ui.product

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kuaimai.pda.ui.theme.BorderGray
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.SuccessBg
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.SupplierRed
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary
import com.kuaimai.pda.ui.theme.WarningYellow

/**
 * 商品详情页面
 * 包含：扫码输入、SKU信息卡、备注编辑、图片上传、供应商切换
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 保持屏幕常亮
    KeepScreenOn(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("商品详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBlue,
                    titleContentColor = SurfaceWhite,
                    navigationIconContentColor = SurfaceWhite
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 扫码输入区域
            ScanInputSection(
                scanInput = uiState.scanInput,
                onScanInputChange = viewModel::updateScanInput,
                onConfirmScan = viewModel::confirmScanInput
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterHorizontally)
                )
            } else if (uiState.skuOuterId.isNotEmpty()) {
                // SKU信息卡片
                SkuInfoCard(
                    picPath = uiState.picPath,
                    propertiesName = uiState.propertiesName,
                    skuOuterId = uiState.skuOuterId,
                    supplierName = uiState.supplierName,
                    onChangeSupplier = viewModel::showSupplierDialog
                )

                // 备注编辑区域
                RemarkSection(
                    remark = uiState.remark,
                    onRemarkChange = viewModel::updateRemark,
                    onSaveRemark = viewModel::requestSaveRemark,
                    isSaving = uiState.isSavingRemark
                )

                // 图片上传区域（2列网格）
                ImageUploadGrid(
                    areaImageUrl = uiState.areaImageUrl,
                    boxImageUrl = uiState.boxImageUrl,
                    isUploading = uiState.isUploading,
                    uploadProgress = uiState.uploadProgress,
                    onUploadArea = { /* 由外部图片选择器触发 */ },
                    onUploadBox = { /* 由外部图片选择器触发 */ }
                )
            } else {
                // 空状态提示
                Text(
                    text = "请扫描商品条码或手动输入SKU编码",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            // 错误提示
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = WarningYellow.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = error,
                        color = WarningYellow,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 供应商选择对话框
        if (uiState.showSupplierDialog) {
            val suppliers by viewModel.suppliers.collectAsState()
            SupplierSelectDialog(
                suppliers = suppliers,
                onSelect = viewModel::selectSupplier,
                onDismiss = viewModel::hideSupplierDialog
            )
        }

        // 确认对话框
        uiState.showConfirmDialog?.let { confirmType ->
            ConfirmDialog(
                confirmType = confirmType,
                onConfirm = when (confirmType) {
                    is ConfirmType.Remark -> viewModel::confirmSaveRemark
                    is ConfirmType.Supplier -> viewModel::confirmChangeSupplier
                },
                onDismiss = viewModel::dismissConfirmDialog
            )
        }
    }
}

/**
 * 扫码输入区域
 */
@Composable
private fun ScanInputSection(
    scanInput: String,
    onScanInputChange: (String) -> Unit,
    onConfirmScan: () -> Unit
) {
    OutlinedTextField(
        value = scanInput,
        onValueChange = onScanInputChange,
        label = { Text("扫码/输入SKU编码") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(20.dp))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onConfirmScan() })
    )
}

/**
 * SKU信息卡片
 */
@Composable
private fun SkuInfoCard(
    picPath: String,
    propertiesName: String,
    skuOuterId: String,
    supplierName: String,
    onChangeSupplier: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // SKU图片 72dp 圆角10dp
            AsyncImage(
                model = picPath.ifBlank { null },
                contentDescription = "商品图片",
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Top)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // SKU信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                // 规格名称 18sp SemiBold
                Text(
                    text = propertiesName.ifBlank { "未知规格" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 规格编码 14sp
                Text(
                    text = skuOuterId,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 供应商 20sp Bold SupplierRed
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = supplierName.ifBlank { "未设置供应商" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (supplierName.isNotBlank()) SupplierRed else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onChangeSupplier) {
                        Text("切换", fontSize = 14.sp, color = BrandBlue)
                    }
                }
            }
        }
    }
}

/**
 * 备注编辑区域
 */
@Composable
private fun RemarkSection(
    remark: String,
    onRemarkChange: (String) -> Unit,
    onSaveRemark: () -> Unit,
    isSaving: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "备注",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = remark,
                onValueChange = onRemarkChange,
                placeholder = { Text("输入备注信息") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSaveRemark,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessBg,
                    contentColor = SuccessText
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SuccessText
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text("保存备注")
            }
        }
    }
}

/**
 * 图片上传2列网格
 */
@Composable
private fun ImageUploadGrid(
    areaImageUrl: String?,
    boxImageUrl: String?,
    isUploading: Boolean,
    uploadProgress: Int,
    onUploadArea: () -> Unit,
    onUploadBox: () -> Unit
) {
    Column {
        Text(
            text = "商品图片",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 库区图
            ImageUploadSlot(
                label = "库区图",
                imageUrl = areaImageUrl,
                onClick = onUploadArea,
                modifier = Modifier.weight(1f)
            )
            // 装箱图
            ImageUploadSlot(
                label = "装箱图",
                imageUrl = boxImageUrl,
                onClick = onUploadBox,
                modifier = Modifier.weight(1f)
            )
        }

        // 上传进度
        if (isUploading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 单个图片上传槽位
 * 宽高比1:1，虚线边框2dp，圆角12dp，最小高度120dp
 */
@Composable
private fun ImageUploadSlot(
    label: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stroke = Stroke(
        width = 2.dp.value,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    )
    val borderColor = BorderGray

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = stroke,
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (imageUrl != null) {
                // 显示已上传图片
                AsyncImage(
                    model = imageUrl,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            } else {
                // 空状态：虚线框+加号
                Text(
                    text = "+",
                    fontSize = 32.sp,
                    color = TextSecondary
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * 确认对话框
 */
@Composable
private fun ConfirmDialog(
    confirmType: ConfirmType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (confirmType) {
        is ConfirmType.Remark -> "确认保存备注"
        is ConfirmType.Supplier -> "确认切换供应商"
    }
    val message = when (confirmType) {
        is ConfirmType.Remark -> "是否保存备注修改？"
        is ConfirmType.Supplier -> "是否将供应商切换为「${confirmType.name}」？"
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认", color = BrandBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 保持屏幕常亮
 */
@Composable
private fun KeepScreenOn(context: Context) {
    val activity = context as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
