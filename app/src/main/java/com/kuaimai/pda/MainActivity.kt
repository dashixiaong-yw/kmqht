package com.kuaimai.pda

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kuaimai.pda.data.OrderSyncWorker
import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.scanner.ScannerManager
import com.kuaimai.pda.ui.navigation.AppNavigation
import com.kuaimai.pda.ui.theme.KuaimaiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named

/**
 * 单Activity架构，承载Compose导航
 * 负责PDA扫码生命周期管理 + 离线同步WorkManager触发
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var prefs: SharedPreferences

    @Inject
    @field:Named("encrypted")
    lateinit var encryptedPrefs: SharedPreferences

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var scannerManager: ScannerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuaimaiTheme {
                AppNavigation(
                    userRepository = userRepository,
                    prefs = prefs,
                    encryptedPrefs = encryptedPrefs,
                    authRepository = authRepository
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
            .beginUniqueWork("order_sync", ExistingWorkPolicy.KEEP, workRequest)
            .enqueue()
    }
}
