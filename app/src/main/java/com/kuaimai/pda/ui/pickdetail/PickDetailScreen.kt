package com.kuaimai.pda.ui.pickdetail

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.kuaimai.pda.ui.components.PickItemRow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.kuaimai.pda.util.ScrollLogger
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.kuaimai.pda.ui.components.StandardTopAppBar
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.DangerText
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SuccessBg
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.SurfaceGray
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextMuted
import com.kuaimai.pda.ui.theme.TextPrimary
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
    var previewPicImageUrl by remember { mutableStateOf<String?>(null) }
    var showDuplicateTip by remember { mutableStateOf(false) }
    var duplicateTipText by remember { mutableStateOf("") }
    val listState = remember(viewModel.orderId) { LazyListState() }

    // 根据供应商过滤明细 + GAP-07: 按状态+时间排序（未完成在上，已完成在下，同状态按时间倒序）
    val filteredItems by remember {
        derivedStateOf {
            (if (currentSupplier == AppConstants.SUPPLIER_ALL_LABEL) {
                items
            } else {
                items.filter { it.supplierName == currentSupplier }
            }).sortedWith(compareBy<PickItemEntity> { it.status }.thenByDescending { it.createdAt }.thenByDescending { it.id })
        }
    }

    val completedCount = items.count { it.status == 1 }
    val totalCount = items.size
    val allCompleted = completedCount >= totalCount && totalCount > 0

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

    // GAP-08: 重复扫码反馈 + 居中弹窗提示
    LaunchedEffect(duplicateScan) {
        if (duplicateScan) {
            ScrollLogger.appendLog(context.applicationContext, "duplicateScan触发")
            viewModel.provideFeedback(context, ScanFeedbackType.DUPLICATE)
            showDuplicateTip = true
            duplicateTipText = "该商品已在列表中"
            // 滚动到重复行
            val duplicateSku = viewModel.lastScannedSku
            if (duplicateSku.isNotEmpty()) {
                val duplicateIndex = filteredItems.indexOfFirst { it.skuOuterId == duplicateSku }
                ScrollLogger.appendLog(context.applicationContext, "animateScrollToItem(duplicateIndex=$duplicateIndex) 即将执行")
                if (duplicateIndex >= 0) {
                    listState.animateScrollToItem(duplicateIndex)
                }
            }
            kotlinx.coroutines.delay(1500)
            showDuplicateTip = false
            viewModel.clearDuplicateScan()
        }
    }

    // 扫码成功反馈 + 清空输入框并重新聚焦（不再滚动，数据还没就绪）
    LaunchedEffect(Unit) {
        viewModel.scanSuccessEvent.collectLatest {
            viewModel.provideFeedback(context, ScanFeedbackType.SUCCESS)
            scanInput = ""
            focusRequester.requestFocus()
        }
    }

    // 添加完成（成功）后滚动到顶部显示新商品（进入页面时也生效）
    val needScroll by viewModel.needScroll.collectAsState()
    LaunchedEffect(viewModel.orderId, needScroll) {
        val ctx = context.applicationContext
        ScrollLogger.appendLog(ctx, "=== LaunchedEffect触发: orderId=${viewModel.orderId}, needScroll=$needScroll ===")
        val firstVisible = listState.firstVisibleItemIndex
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size
        val firstVisibleSku = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
            filteredItems.getOrNull(it.index)?.let { item -> "${item.skuOuterId.take(12)}(idx=${it.index})" }
        } ?: "none"
        val lastVisibleSku = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
            filteredItems.getOrNull(it.index)?.let { item -> "${item.skuOuterId.take(12)}(idx=${it.index})" }
        } ?: "none"
        ScrollLogger.appendLog(ctx, "scroll前: filteredSize=${filteredItems.size}, listIndex=$firstVisible, visibleCount=$visibleCount, firstVisible=$firstVisibleSku, lastVisible=$lastVisibleSku")
        if (filteredItems.isNotEmpty() && listState.firstVisibleItemIndex != 0) {
            ScrollLogger.appendLog(ctx, "scrollToItem(0) 即将执行")
            listState.scrollToItem(0)
            val afterFirst = listState.firstVisibleItemIndex
            val afterFirstSku = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                filteredItems.getOrNull(it.index)?.let { item -> "${item.skuOuterId.take(12)}(idx=${it.index})" }
            } ?: "none"
            ScrollLogger.appendLog(ctx, "scrollToItem(0) 完成: postIndex=$afterFirst, firstVisible=$afterFirstSku")
        } else {
            ScrollLogger.appendLog(ctx, "scroll跳过: filteredItems为空")
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

    // 自动聚焦扫码输入框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StandardTopAppBar(
                title = order?.orderNo ?: "取货单详情",
                onNavigateBack = onNavigateBack
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
                            modifier = Modifier.height(32.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandBlue,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

            // 明细列表
            val imageUrlsMap by viewModel.imageUrlsMap.collectAsState()
            val pendingItems by viewModel.pendingItems.collectAsState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                state = listState
            ) {
                // 待处理占位行（扫码后立即显示，API返回前）
                items(pendingItems) { barcode ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(start = 12.dp, top = 20.dp, bottom = 20.dp, end = 12.dp)
                                .alpha(0.7f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp, 90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceGray),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    barcode,
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text("添加中...", fontSize = 13.sp, color = TextMuted)
                            }
                        }
                    }
                }

                // 真实商品列表
                items(
                    items = filteredItems,
                    key = { "${it.status}_${it.id}" }
                ) { item ->
                    val urls = imageUrlsMap[item.skuOuterId]
                    PickItemRow(
                        item = item,
                        onComplete = { viewModel.completeItem(item.id) },
                        onRestore = { viewModel.restoreItem(item.id) },
                        onLongPress = { if (order?.status != 1 && !allCompleted) showDeleteConfirm = item },
                        orderCompleted = order?.status == 1 || allCompleted,
                        onSkuNameClick = { onNavigateToProduct(item.skuOuterId) },
                        onSkuImageClick = {
                            if (item.picPath.isNotEmpty()) {
                                previewPicImageUrl = item.picPath
                            }
                        },
                        areaImageUrl = urls?.areaUrl,
                        boxImageUrl = urls?.boxUrl,
                        areaThumbUrl = urls?.areaThumbUrl,
                        boxThumbUrl = urls?.boxThumbUrl,
                        onAreaImageClick = {
                            if (urls?.areaUrl != null) {
                                previewImageUrl = urls.areaUrl
                                previewImageLabel = "库区图 - ${item.skuOuterId}"
                            }
                        },
                        onBoxImageClick = {
                            if (urls?.boxUrl != null) {
                                previewImageUrl = urls.boxUrl
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
                            enabled = completedCount < totalCount && totalCount > 0 && order?.status != 1,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLightBg)
                        ) {
                            Text(
                                text = "全部完成",
                                color = PrimaryLightText
                            )
                        }
                    }

                    // 取货单已完成提示
                    if (order?.status == 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "取货单已完成，不可操作明细",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
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
            shape = RoundedCornerShape(16.dp),
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

    // 重复添加提示 - 居中弹窗，1.5秒后自动消失
    if (showDuplicateTip) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(16.dp),
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✓", fontSize = 36.sp, color = SuccessText)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(duplicateTipText, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            },
            confirmButton = {}
        )
    }

    // 图片大图预览弹窗
    previewImageUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { previewImageUrl = null },
            shape = RoundedCornerShape(16.dp),
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

    // SKU图大图预览
    previewPicImageUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { previewPicImageUrl = null },
            shape = RoundedCornerShape(16.dp),
            title = { Text("SKU图") },
            text = {
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = "SKU图",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = { previewPicImageUrl = null }) {
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
