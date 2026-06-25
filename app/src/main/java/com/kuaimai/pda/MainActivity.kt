package com.kuaimai.pda

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.kuaimai.pda.data.OrderSyncWorker
import com.kuaimai.pda.data.api.dto.AppVersionResponse
import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.scanner.ScannerManager
import com.kuaimai.pda.ui.navigation.AppNavigation
import com.kuaimai.pda.ui.theme.KuaimaiTheme
import com.kuaimai.pda.update.AppUpdateManager
import com.kuaimai.pda.update.CheckResult
import com.kuaimai.pda.update.DownloadState
import com.kuaimai.pda.util.NetworkMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 单Activity架构，承载Compose导航
 * 负责PDA扫码生命周期管理 + 离线同步WorkManager触发
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var scannerManager: ScannerManager

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 请求通知权限（APK下载进度）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        setContent {
                KuaimaiTheme {
                // 启动时自动检查更新弹窗状态
                var showUpdateDialog by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<AppVersionResponse?>(null) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadErrorMsg by remember { mutableStateOf<String?>(null) }

                // 启动时自动检查更新
                LaunchedEffect(Unit) {
                    when (val result = appUpdateManager.checkForUpdate()) {
                        is CheckResult.HasUpdate -> {
                            updateInfo = result.info
                            showUpdateDialog = true
                        }
                        else -> {}
                    }
                }

                // 更新弹窗
                if (showUpdateDialog && updateInfo != null) {
                    val info = updateInfo!!
                    AlertDialog(
                        onDismissRequest = {
                            if (!info.forceUpdate && !isDownloading) {
                                showUpdateDialog = false
                                updateInfo = null
                                downloadErrorMsg = null
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        title = { Text("发现新版本") },
                        text = {
                            Column {
                                Text("最新版本: v${info.latestVersion}")
                                if (info.updateNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(info.updateNotes)
                                }
                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("正在下载更新...", style = MaterialTheme.typography.bodySmall)
                                }
                                if (downloadErrorMsg != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = downloadErrorMsg!!,
                                        color = androidx.compose.ui.graphics.Color(0xFFDC2626),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (!isDownloading) {
                                        downloadErrorMsg = null
                                        isDownloading = true
                                        appUpdateManager.downloadApk(info)
                                        lifecycleScope.launch {
                                            appUpdateManager.downloadState.collect { state ->
                                                when (state) {
                                                    is DownloadState.Completed -> {
                                                            val installed = appUpdateManager.installApk(state.file)
                                                            if (installed) {
                                                                showUpdateDialog = false
                                                                updateInfo = null
                                                            } else {
                                                                isDownloading = false
                                                                downloadErrorMsg = "下载成功，但安装失败：系统未找到安装器，请手动安装 APK 文件"
                                                            }
                                                    }
                                                    is DownloadState.Failed -> {
                                                        isDownloading = false
                                                        downloadErrorMsg = "下载失败: ${state.message}"
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isDownloading
                            ) {
                                Text(if (isDownloading) "下载中..." else "立即更新")
                            }
                        },
                        dismissButton = {
                            if (!info.forceUpdate) {
                                TextButton(
                                    onClick = {
                                        showUpdateDialog = false
                                        updateInfo = null
                                        downloadErrorMsg = null
                                    }
                                ) {
                                    Text("稍后再说")
                                }
                            }
                        }
                    )
                }

                AppNavigation(
                    userRepository = userRepository,
                    authRepository = authRepository,
                    networkMonitor = networkMonitor
                )
            }
        }
        // 启动时触发离线同步Worker（网络恢复后自动重试）
        enqueueSyncWorker()
    }

    override fun onResume() {
        super.onResume()
        scannerManager.register(this)
    }

    override fun onPause() {
        super.onPause()
        scannerManager.unregister(this)
    }

    /**
     * 触发离线同步Worker
     * 仅在存在pending_operations时实际执行，网络不满足条件时等待
     */
    private fun enqueueSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<OrderSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this)
            .beginUniqueWork("order_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
            .enqueue()
    }
}
