package com.kuaimai.pda.ui.pickdetail

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.scanner.ScanFeedbackType
import com.kuaimai.pda.ui.components.PickItemRow
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.DangerText
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SuccessBg
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary

/**
 * 取货单详情页面
 * TopAppBar + 单号
 * 扫码输入框（自动聚焦, 边框2dp BrandBlue, 圆角8dp）
 * 供应商过滤Chips（FlowRow, 默认"全部"）
 * LazyColumn of PickItemRow
 * 底部：进度 X/Y + [全部完成]按钮
 * 重复扫码：中等振动 + 高亮
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PickDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProduct: (String) -> Unit = {},
    viewModel: PickDetailViewModel = hiltViewModel()
) {
    val order by viewModel.order.collectAsState()
    val items by viewModel.items.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val currentSupplier by viewModel.currentSupplier.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val duplicateScan by viewModel.duplicateScan.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    var scanInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<PickItemEntity?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewImageLabel by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // GAP-05: 屏幕常亮
    val activity = context.findActivity()
    LaunchedEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    // 离开页面时取消常亮
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // GAP-08: 重复扫码反馈（振动+声音+滚动到该行）
    LaunchedEffect(duplicateScan) {
        if (duplicateScan) {
            viewModel.provideFeedback(context, ScanFeedbackType.DUPLICATE)
            snackbarHostState.showSnackbar("重复扫码！该SKU已在当前取货单中")
            // 滚动到重复行
            val duplicateSku = viewModel.lastScannedSku
            if (duplicateSku.isNotEmpty()) {
                val duplicateIndex = items.indexOfFirst { it.skuOuterId == duplicateSku }
                if (duplicateIndex >= 0) {
                    listState.animateScrollToItem(duplicateIndex)
                }
            }
            viewModel.clearDuplicateScan()
        }
    }

    // 扫码成功反馈 + 清空输入框并重新聚焦
    LaunchedEffect(Unit) {
        viewModel.scanSuccessEvent.collectLatest {
            viewModel.provideFeedback(context, ScanFeedbackType.SUCCESS)
            scanInput = ""
            focusRequester.requestFocus()
        }
    }

    // 扫码失败反馈
    LaunchedEffect(Unit) {
        viewModel.scanFailureEvent.collectLatest { message ->
            viewModel.provideFeedback(context, ScanFeedbackType.FAILURE)
        }
    }

    // 监听PDA硬件扫码结果
    LaunchedEffect(Unit) {
        viewModel.scannerManager.scanResult.collectLatest { barcode ->
            if (barcode.isNotEmpty()) {
                viewModel.onBarcodeScanned(barcode)
                viewModel.scannerManager.clearResult()
            }
        }
    }

    // 错误消息提示
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 根据供应商过滤明细 + GAP-07: 按状态+时间排序（未完成在上，已完成在下，同状态按时间倒序）
    val filteredItems = (if (currentSupplier == AppConstants.SUPPLIER_ALL_LABEL) {
        items
    } else {
        items.filter { it.supplierName == currentSupplier }
    }).sortedWith(compareBy<PickItemEntity> { it.status }.thenByDescending { it.createdAt }.thenByDescending { it.id })

    val completedCount = items.count { it.status == 1 }
    val totalCount = items.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(order?.orderNo ?: "取货单详情", color = SurfaceWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = SurfaceWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandBlue)
            )
        }
    ) { innerPadding ->
        // F15: 下拉刷新
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 扫码输入框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = scanInput,
                    onValueChange = { scanInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .border(2.dp, BrandBlue, RoundedCornerShape(8.dp))
                        .focusRequester(focusRequester),
                    placeholder = { Text("按PDA扫码键扫描规格编码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (scanInput.isNotBlank()) {
                                viewModel.onBarcodeScanned(scanInput.trim())
                                scanInput = ""
                                val view = (context as? android.app.Activity)?.currentFocus
                                if (view != null) {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as?
                                        android.view.inputmethod.InputMethodManager
                                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 相机扫码按钮（暂未开放）
                IconButton(
                    onClick = {
                        // TODO: 启动CameraScanScreen
                    },
                    enabled = false,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "相机扫码",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 供应商过滤Chips - 始终显示（GAP-10: 去掉suppliers.size > 2条件）
            FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suppliers.forEach { supplier ->
                        FilterChip(
                            selected = currentSupplier == supplier,
                            onClick = { viewModel.setSupplierFilter(supplier) },
                            label = { Text(supplier, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryLightBg,
                                selectedLabelColor = PrimaryLightText
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

            // 明细列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                state = listState
            ) {
                items(
                    items = filteredItems,
                    key = { it.id }
                ) { item ->
                    // GAP-01: 查询SKU图片URL
                    var areaImageUrl by remember { mutableStateOf<String?>(null) }
                    var boxImageUrl by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(item.skuOuterId) {
                        val urls = viewModel.getImageUrls(item.skuOuterId)
                        areaImageUrl = urls.first
                        boxImageUrl = urls.second
                    }

                    PickItemRow(
                        item = item,
                        onComplete = { viewModel.completeItem(item.id) },
                        onRestore = { viewModel.restoreItem(item.id) },
                        onLongPress = { showDeleteConfirm = item },
                        onImageClick = { onNavigateToProduct(item.skuOuterId) },
                        areaImageUrl = areaImageUrl,
                        boxImageUrl = boxImageUrl,
                        onAreaImageClick = {
                            if (areaImageUrl != null) {
                                previewImageUrl = areaImageUrl
                                previewImageLabel = "库区图 - ${item.skuOuterId}"
                            }
                        },
                        onBoxImageClick = {
                            if (boxImageUrl != null) {
                                previewImageUrl = boxImageUrl
                                previewImageLabel = "箱图 - ${item.skuOuterId}"
                            }
                        }
                    )
                }
            }

            // 底部：进度 + 全部完成按钮
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 进度条
                    LinearProgressIndicator(
                        progress = { if (totalCount > 0) completedCount.toFloat() / totalCount else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = SuccessText,
                        trackColor = PrimaryLightBg
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "进度 $completedCount/$totalCount",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (completedCount == totalCount && totalCount > 0) SuccessText else TextSecondary
                        )

                        // 全部完成按钮
                        Button(
                            onClick = { viewModel.completeAllItems() },
                            enabled = completedCount < totalCount && totalCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessBg)
                        ) {
                            Text(
                                text = "全部完成",
                                color = SuccessText
                            )
                        }
                    }
                }
            }
        }
        } // PullToRefreshBox end
    }

    // 自动聚焦扫码输入框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // GAP-06: 删除确认弹窗
    showDeleteConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${item.propertiesName.ifEmpty { item.skuOuterId }}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerText)
                ) {
                    Text("删除", color = SurfaceWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 图片大图预览弹窗
    previewImageUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { previewImageUrl = null },
            title = { Text(previewImageLabel) },
            text = {
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = previewImageLabel,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = { previewImageUrl = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

/**
 * 从Context查找Activity
 */
private fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
