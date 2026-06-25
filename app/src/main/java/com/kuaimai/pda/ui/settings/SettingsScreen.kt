package com.kuaimai.pda.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaimai.pda.BuildConfig
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.components.StandardTopAppBar
import com.kuaimai.pda.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 权限代码与显示名称映射
 */
private val PERMISSION_LABELS = mapOf(
    "settings" to "设置管理",
    "update_supplier" to "修改供应商",
    "update_remark" to "修改备注",
    "manage_area_image" to "库区图管理",
    "manage_box_image" to "箱规图管理"
)

/**
 * 个人设置页面
 * 包含：当前用户信息、扫码方式、反馈开关、退出登录
 * 系统管理功能已迁移到Web管理后台（浏览器访问 /admin）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userRepository: UserRepository,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentUser by userRepository.currentUser.collectAsState()
    val updateCheckState by viewModel.updateCheckResult.collectAsState()
    var showLogoutDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var isLoggingOut by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLogDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showScrollLogDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val appContext = LocalContext.current

    // 退出登录确认弹窗
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoggingOut) showLogoutDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("退出登录") },
            text = {
                Text(if (isLoggingOut) "正在退出..." else "确定要退出当前账号吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isLoggingOut) return@TextButton
                        isLoggingOut = true
                        scope.launch {
                            userRepository.logout()
                            isLoggingOut = false
                            showLogoutDialog = false
                            onLogout()
                        }
                    },
                    enabled = !isLoggingOut
                ) {
                    Text(if (isLoggingOut) "退出中..." else "确定",
                         color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                if (!isLoggingOut) {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    // 检查更新弹窗
    when (val state = updateCheckState) {
        is UpdateCheckUiState.Checking -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateCheck() },
                shape = RoundedCornerShape(16.dp),
                title = { Text("检查更新") },
                text = { Text("正在检查更新，请稍候...") },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissUpdateCheck() }) {
                        Text("取消")
                    }
                }
            )
        }
        is UpdateCheckUiState.HasUpdate -> {
            AlertDialog(
                onDismissRequest = {
                    if (!state.info.forceUpdate) viewModel.dismissUpdateCheck()
                },
                shape = RoundedCornerShape(16.dp),
                title = { Text("发现新版本") },
                text = {
                    Text("最新版本: v${state.info.latestVersion}\n\n${state.info.updateNotes}")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.startDownload(state.info)
                        viewModel.dismissUpdateCheck()
                    }) {
                        Text("立即更新")
                    }
                },
                dismissButton = {
                    if (!state.info.forceUpdate) {
                        TextButton(onClick = { viewModel.dismissUpdateCheck() }) {
                            Text("稍后再说")
                        }
                    }
                }
            )
        }
        is UpdateCheckUiState.NoUpdate -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateCheck() },
                shape = RoundedCornerShape(16.dp),
                title = { Text("检查更新") },
                text = { Text("当前已是最新版本") },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissUpdateCheck() }) {
                        Text("确定")
                    }
                }
            )
        }
        is UpdateCheckUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateCheck() },
                shape = RoundedCornerShape(16.dp),
                title = { Text("检查更新失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissUpdateCheck() }) {
                        Text("确定")
                    }
                }
            )
        }
        else -> {}
    }

    // 同步日志显示弹窗
    if (showLogDialog) {
        val logContent = try {
            java.io.File(appContext.cacheDir, "sync_log.txt").readText()
        } catch (_: Exception) { "暂无同步日志" }

        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("同步日志") },
            text = {
                Column {
                    Text(
                        text = logContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "可复制后发送",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("关闭")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = appContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("sync_log", logContent))
                    android.widget.Toast.makeText(appContext, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("复制")
                }
            }
        )
    }

    // 滚动日志显示弹窗
    if (showScrollLogDialog) {
        val logContent = try {
            java.io.File(appContext.cacheDir, "scroll_log.txt").readText()
        } catch (_: Exception) { "暂无滚动日志" }

        AlertDialog(
            onDismissRequest = { showScrollLogDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("滚动日志") },
            text = {
                Column {
                    Text(
                        text = logContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "可复制后发送",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showScrollLogDialog = false }) {
                    Text("关闭")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = appContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("scroll_log", logContent))
                    android.widget.Toast.makeText(appContext, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("复制")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            StandardTopAppBar(
                title = "个人设置",
                onNavigateBack = onNavigateBack
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
            // 当前用户信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PrimaryLightBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前用户",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentUser?.username ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLightText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "权限: ${currentUser?.permissions?.map { PERMISSION_LABELS[it] ?: it }?.joinToString("、") ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 扫码与反馈配置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "扫码与反馈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 扫码方式选择
                    val scanMethod by viewModel.scanMethod.collectAsState()
                    Text("扫码方式", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scanMethod == 0,
                            onClick = { viewModel.setScanMethod(0) }
                        )
                        Text("PDA硬件扫码", modifier = Modifier.clickable { viewModel.setScanMethod(0) })
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = scanMethod == 2,
                            onClick = { viewModel.setScanMethod(2) }
                        )
                        Text("手动输入", modifier = Modifier.clickable { viewModel.setScanMethod(2) })
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // 声音开关
                    val soundEnabled by viewModel.soundEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = AppAlignment.RowBetween,
                        verticalAlignment = AppAlignment.RowCenter
                    ) {
                        Text("扫码声音反馈")
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { viewModel.toggleSound(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 振动开关
                    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = AppAlignment.RowBetween,
                        verticalAlignment = AppAlignment.RowCenter
                    ) {
                        Text("扫码振动反馈")
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { viewModel.toggleVibration(it) }
                        )
                    }
                }
            }

            // 管理后台入口提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "系统管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "用户管理、拣货区管理、快麦配置、服务器配置等系统管理功能已迁移到Web管理后台。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "请用电脑浏览器访问管理后台",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 退出登录按钮
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessBg,
                    contentColor = SuccessText
                )
            ) {
                Text("退出登录", style = MaterialTheme.typography.titleMedium)
            }

            // 版本信息
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.checkForUpdate() },
                textAlign = TextAlign.Center
            )

            // 查看同步日志
            TextButton(
                onClick = { showLogDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看同步日志", color = TextSecondary, fontSize = 12.sp)
            }

            // 查看滚动日志（用于取货单详情视口问题排查）
            TextButton(
                onClick = { showScrollLogDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看滚动日志", color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
