package com.kuaimai.pda.ui.product

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.theme.BorderGray
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.DangerText
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
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
    userRepository: UserRepository,
    viewModel: ProductViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // F22: 图片删除确认弹窗状态
    var showImageDeleteConfirm by remember { mutableStateOf<String?>(null) } // "area" or "box"

    // 图片上传：记录待上传的图片类型
    var pendingImageType by remember { mutableStateOf<String?>(null) }

    // 图片选择器
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val type = pendingImageType ?: return@let
            val file = uriToFile(it, context)
            if (file != null) {
                viewModel.uploadImage(file, type)
            }
            pendingImageType = null
        }
    }

    // 权限检查
    val canUpdateSupplier = userRepository.hasPermission("update_supplier")
    val canUpdateRemark = userRepository.hasPermission("update_remark")
    val canManageAreaImage = userRepository.hasPermission("manage_area_image")
    val canManageBoxImage = userRepository.hasPermission("manage_box_image")

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
                    onChangeSupplier = viewModel::showSupplierDialog,
                    canChangeSupplier = canUpdateSupplier
                )

                // 备注编辑区域（仅update_remark权限可见）
                if (canUpdateRemark) {
                    RemarkSection(
                        remark = uiState.remark,
                        onRemarkChange = viewModel::updateRemark,
                        onSaveRemark = viewModel::requestSaveRemark,
                        isSaving = uiState.isSavingRemark
                    )
                }

                // 图片上传区域（2列网格）
                ImageUploadGrid(
                    areaImageUrl = uiState.areaImageUrl,
                    boxImageUrl = uiState.boxImageUrl,
                    isUploading = uiState.isUploading,
                    uploadProgress = uiState.uploadProgress,
                    onUploadArea = {
                        pendingImageType = "area"
                        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onUploadBox = {
                        pendingImageType = "box"
                        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onDeleteArea = { showImageDeleteConfirm = "area" },
                    onDeleteBox = { showImageDeleteConfirm = "box" },
                    canManageAreaImage = canManageAreaImage,
                    canManageBoxImage = canManageBoxImage
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
            // 信息提示（非错误）
            uiState.infoMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
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

        // F22: 图片删除确认弹窗
        showImageDeleteConfirm?.let { imageType ->
            val label = if (imageType == "area") "库区图" else "装箱图"
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showImageDeleteConfirm = null },
                title = { Text("确认删除") },
                text = { Text("确定要删除${label}吗？此操作不可撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteImage(imageType)
                            showImageDeleteConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerText)
                    ) {
                        Text("删除", color = SurfaceWhite)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImageDeleteConfirm = null }) {
                        Text("取消")
                    }
                }
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
    onChangeSupplier: () -> Unit,
    canChangeSupplier: Boolean = true
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
                    .clip(RoundedCornerShape(10.dp))
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 规格编码 14sp
                Text(
                    text = skuOuterId,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                        color = if (supplierName.isNotBlank()) SupplierRed else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (canChangeSupplier) {
                        TextButton(onClick = onChangeSupplier) {
                            Text("切换", fontSize = 14.sp, color = BrandBlue)
                        }
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
                    containerColor = PrimaryLightBg,
                    contentColor = PrimaryLightText
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryLightText
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
 * 无权限时仍显示图片（只读），仅隐藏上传/删除操作
 */
@Composable
private fun ImageUploadGrid(
    areaImageUrl: String?,
    boxImageUrl: String?,
    isUploading: Boolean,
    uploadProgress: Int,
    onUploadArea: () -> Unit,
    onUploadBox: () -> Unit,
    onDeleteArea: () -> Unit = {},
    onDeleteBox: () -> Unit = {},
    canManageAreaImage: Boolean = true,
    canManageBoxImage: Boolean = true
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
            // 库区图（有图片时始终显示只读，无图片且无权限时不显示）
            if (areaImageUrl != null || canManageAreaImage) {
                ImageUploadSlot(
                    label = "库区图",
                    imageUrl = areaImageUrl,
                    onClick = if (canManageAreaImage) onUploadArea else {{}},
                    onLongClick = if (canManageAreaImage && areaImageUrl != null) onDeleteArea else null,
                    modifier = Modifier.weight(1f)
                )
            }
            // 装箱图（有图片时始终显示只读，无图片且无权限时不显示）
            if (boxImageUrl != null || canManageBoxImage) {
                ImageUploadSlot(
                    label = "装箱图",
                    imageUrl = boxImageUrl,
                    onClick = if (canManageBoxImage) onUploadBox else ({} as () -> Unit),
                    onLongClick = if (canManageBoxImage && boxImageUrl != null) onDeleteBox else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 上传进度
        if (isUploading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // GAP-14: 图片上传提示文字
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "点击上传/替换 · 长按删除 · 上传前自动压缩",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/**
 * 单个图片上传槽位
 * 宽高比1:1，虚线边框2dp，圆角12dp，最小高度120dp
 * F22: 已上传图片可点击替换或长按删除
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageUploadSlot(
    label: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stroke = Stroke(
        width = 2.dp.value,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    )
    val borderColor = BorderGray

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = stroke,
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center
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
                        .padding(4.dp),
                    contentScale = ContentScale.Crop
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

/**
 * 将Uri转换为File（用于图片上传）
 */
private fun uriToFile(uri: Uri, context: Context): java.io.File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = java.io.File.createTempFile("upload_", ".jpg", context.cacheDir)
        try {
            tempFile.outputStream().use { output ->
                inputStream.use { it.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    } catch (e: Exception) {
        null
    }
}
