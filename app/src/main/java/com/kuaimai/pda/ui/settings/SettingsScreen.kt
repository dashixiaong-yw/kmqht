package com.kuaimai.pda.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaimai.pda.data.api.dto.AreaResponse
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.DangerBg
import com.kuaimai.pda.ui.theme.DangerText
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary

/**
 * 设置页面
 * 包含：拣货区管理、服务器地址、API Key、扫码方式、反馈开关、版本信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val areas by viewModel.areas.collectAsState()
    val newAreaName by viewModel.newAreaName.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val scanMethod by viewModel.scanMethod.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<AreaResponse?>(null) }

    // 消息提示
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置", color = SurfaceWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = SurfaceWhite)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 拣货区管理
            SectionCard(title = "拣货区管理") {
                areas.forEach { area ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = area.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { deleteTarget = area },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DangerBg,
                                contentColor = DangerText
                            )
                        ) {
                            Text("删除", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                    }
                }
                if (areas.isEmpty()) {
                    Text("暂无拣货区", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newAreaName,
                        onValueChange = { viewModel.updateNewAreaName(it) },
                        placeholder = { Text("输入新拣货区名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.addArea() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryLightBg,
                            contentColor = PrimaryLightText
                        )
                    ) {
                        Text("+ 添加")
                    }
                }
            }

            // 服务器地址
            SectionCard(title = "服务器地址") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { viewModel.updateServerUrl(it) },
                        placeholder = { Text("http://192.168.1.100:8000") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.saveServerUrl() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryLightBg,
                            contentColor = PrimaryLightText
                        )
                    ) {
                        Text("保存")
                    }
                }
            }

            // API Key
            SectionCard(title = "API Key") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.updateApiKey(it) },
                        placeholder = { Text("输入API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.saveApiKey() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryLightBg,
                            contentColor = PrimaryLightText
                        )
                    ) {
                        Text("保存")
                    }
                }
            }

            // 扫码方式
            SectionCard(title = "扫码方式") {
                val scanMethods = listOf(
                    0 to "PDA硬件扫码（iData/Urovo/Zebra/Newland）",
                    1 to "相机扫码（ML Kit）",
                    2 to "手动输入条码"
                )
                scanMethods.forEach { (index, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = scanMethod == index,
                            onClick = { viewModel.setScanMethod(index) }
                        )
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // 扫码反馈
            SectionCard(title = "扫码反馈") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("声音反馈")
                    Switch(checked = soundEnabled, onCheckedChange = { viewModel.toggleSound() })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("振动反馈")
                    Switch(checked = vibrationEnabled, onCheckedChange = { viewModel.toggleVibration() })
                }
            }

            // 版本信息
            SectionCard(title = "关于") {
                Text(
                    text = "快麦取货通 v0.10",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }

    // 删除拣货区确认弹窗
    deleteTarget?.let { area ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除拣货区「${area.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteArea(area)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerText)
                ) {
                    Text("删除", color = SurfaceWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 设置分组卡片
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = BrandBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
