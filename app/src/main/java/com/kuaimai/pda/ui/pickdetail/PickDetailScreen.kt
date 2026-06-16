package com.kuaimai.pda.ui.pickdetail

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaimai.pda.ui.components.PickItemRow
import com.kuaimai.pda.ui.theme.BrandBlue
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
 * 连续扫码模式开关
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
    val continuousScanMode by viewModel.continuousScanMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val duplicateScan by viewModel.duplicateScan.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    var scanInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 重复扫码振动提示
    LaunchedEffect(duplicateScan) {
        if (duplicateScan) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            snackbarHostState.showSnackbar("重复扫码！该SKU已在当前取货单中")
            viewModel.clearDuplicateScan()
        }
    }

    // 错误消息提示
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 根据供应商过滤明细
    val filteredItems = if (currentSupplier == "全部") {
        items
    } else {
        items.filter { it.supplierName == currentSupplier }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    placeholder = { Text("扫码或输入条码") },
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
                            }
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                // 相机扫码按钮
                IconButton(
                    onClick = {
                        // TODO: 启动CameraScanScreen
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "相机扫码",
                        tint = BrandBlue
                    )
                }
            }

            // 连续扫码模式开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "连续扫码",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = continuousScanMode,
                    onCheckedChange = { viewModel.toggleContinuousScanMode() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 供应商过滤Chips
            if (suppliers.size > 2) {
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
                            label = { Text(supplier, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryLightBg,
                                selectedLabelColor = PrimaryLightText
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 明细列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(
                    items = filteredItems,
                    key = { it.id }
                ) { item ->
                    PickItemRow(
                        item = item,
                        onComplete = { viewModel.completeItem(item.id) },
                        onRestore = { viewModel.restoreItem(item.id) },
                        onLongPress = { /* TODO: 操作菜单 */ }
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
                            text = "$completedCount/$totalCount",
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
    }

    // 自动聚焦扫码输入框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
