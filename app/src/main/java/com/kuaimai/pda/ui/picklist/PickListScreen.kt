package com.kuaimai.pda.ui.picklist

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.ui.components.PickOrderCard
import com.kuaimai.pda.ui.theme.BorderGray
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.DangerText
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceGray
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextPrimary
import com.kuaimai.pda.ui.theme.TextSecondary
import com.kuaimai.pda.ui.theme.WarningBg
import com.kuaimai.pda.ui.theme.WarningText

/**
 * 取货列表页面
 * TopAppBar + [+新建]按钮
 * LazyColumn of PickOrderCard
 * "查看已完成"入口
 * 新建弹窗：拣货区选择
 * 长按卡片→删除确认
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (orderId: Long) -> Unit,
    viewModel: PickListViewModel = hiltViewModel()
) {
    val activeOrders by viewModel.activeOrders.collectAsState()
    val completedOrders by viewModel.completedOrders.collectAsState()
    val areas by viewModel.areas.collectAsState()
    val showNewOrderDialog by viewModel.showNewOrderDialog.collectAsState()
    val showCompletedList by viewModel.showCompletedList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val deleteTarget by viewModel.deleteTarget.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 错误消息提示
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 创建成功后自动跳转详情页
    LaunchedEffect(Unit) {
        viewModel.navigateToOrderEvent.collectLatest { orderId ->
            onNavigateToDetail(orderId)
        }
    }

    // 页面回到前台时刷新列表
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshActiveOrders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("取货列表", color = SurfaceWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = SurfaceWhite)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.showNewOrderDialog() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = PrimaryLightBg,
                            contentColor = PrimaryLightText
                        ),
                        modifier = Modifier.height(36.dp).defaultMinSize(minWidth = 56.dp)
                    ) {
                        Text("+ 新建", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandBlue)
            )
        }
    ) { innerPadding ->
        if (showCompletedList) {
            // 已完成列表
            Column(modifier = Modifier.padding(innerPadding)) {
                CompletedOrdersList(
                    orders = completedOrders,
                    onBack = { viewModel.hideCompletedList() },
                    onOrderClick = { onNavigateToDetail(it.id) }
                )
            }
        } else {
            // 进行中列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (activeOrders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无进行中的取货单\n点击右上角[+ 新建]新建",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                } else {
                    // F24: 按拣货区分组排序
                    val sortedActiveOrders = activeOrders.sortedWith(
                        compareBy<PickOrderEntity> {
                            // 从orderNo中提取拣货区名称（格式: 拣货区-yyyyMMdd-X）
                            it.orderNo.substringBefore("-", "未知")
                        }.thenByDescending { it.createdAt }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        items(
                            items = sortedActiveOrders,
                            key = { it.id }
                        ) { order ->
                            PickOrderCard(
                                order = order,
                                onClick = { onNavigateToDetail(order.id) },
                                onDelete = { viewModel.requestDelete(order) },
                                onPublish = if (order.visibility == "private" && order.assignedTo == order.createdBy)
                                    { { viewModel.publishOrder(order.id) } } else null,
                                onClaim = if (order.visibility == "public")
                                    { { viewModel.claimOrder(order.id) } } else null
                            )
                        }
                    }
                }

                // 查看已完成入口
                TextButton(
                    onClick = { viewModel.showCompletedList() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "查看已完成 (${completedOrders.size})",
                        color = PrimaryLightText
                    )
                }
            }
        }
    }

    // 新建取货单弹窗
    if (showNewOrderDialog) {
        NewOrderDialog(
            areas = areas,
            isLoading = isLoading,
            onDismiss = { viewModel.hideNewOrderDialog() },
            onConfirm = { areaName -> viewModel.createOrder(areaName) }
        )
    }

    // 删除确认弹窗
    deleteTarget?.let { order ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            shape = RoundedCornerShape(16.dp),
            title = { Text("确认删除") },
            text = { Text("确定删除此取货单？") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerText
                    )
                ) {
                    Text("删除", color = SurfaceWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 新建取货单弹窗 — 拣货区选择（原型风格网格布局）
 */
@Composable
private fun NewOrderDialog(
    areas: List<String>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = SurfaceWhite,
        tonalElevation = 8.dp,
        title = {
            Text(
                text = "选择拣货区",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                if (isLoading) {
                    // 加载状态
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "加载中…",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                } else if (areas.isEmpty()) {
                    // 空状态
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "暂无拣货区，请先在设置中配置",
                            fontSize = 14.sp,
                            color = WarningText,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 拣货区网格（原型风格：2列网格）
                    areas.chunked(2).forEach { rowAreas ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowAreas.forEach { area ->
                                Button(
                                    onClick = { onConfirm(area) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceWhite,
                                        contentColor = TextPrimary,
                                        disabledContainerColor = SurfaceWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, BorderGray)
                                ) {
                                    Text(
                                        text = area,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!isLoading) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceGray,
                        contentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 80.dp).height(36.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp
                    )
                }
            }
        }
    )
}

/**
 * 已完成取货单列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletedOrdersList(
    orders: List<PickOrderEntity>,
    onBack: () -> Unit,
    onOrderClick: (PickOrderEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (orders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "近7天无已完成取货单",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(
                    items = orders,
                    key = { it.id }
                ) { order ->
                    PickOrderCard(
                        order = order,
                        onClick = { onOrderClick(order) }
                    )
                }
            }
        }
    }
}
