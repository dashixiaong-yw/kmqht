package com.kuaimai.pda.ui.home

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.components.NetworkStatusIndicator
import com.kuaimai.pda.ui.settings.SettingsViewModel.Companion.KEY_GUIDE_SHOWN
import com.kuaimai.pda.ui.theme.AppAlignment
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary
import com.kuaimai.pda.ui.theme.WarningBg
import com.kuaimai.pda.ui.theme.WarningText
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.NetworkMonitor

/**
 * 主页：居中Logo + 模块入口卡片
 * 无底部导航栏，使用卡片式入口
 * 首次使用显示引导提示条
 * 会话即将过期显示黄色警告条
 * Token刷新失败弹出对话框
 */
@Composable
fun HomeScreen(
    userRepository: UserRepository,
    onNavigateToPickList: () -> Unit,
    onNavigateToProduct: () -> Unit,
    onNavigateToSettings: () -> Unit,
    networkMonitor: NetworkMonitor? = null,
    prefs: SharedPreferences? = null,
    authRepository: AuthRepository? = null
) {
    // 网络监听注册/注销
    DisposableEffect(networkMonitor) {
        networkMonitor?.register()
        onDispose {
            networkMonitor?.unregister()
        }
    }

    // 首次使用引导提示
    var showGuide by remember {
        mutableStateOf(prefs?.getBoolean(KEY_GUIDE_SHOWN, false) != true)
    }

    // 会话即将过期预警（距过期<5天）
    var showSessionWarning by remember { mutableStateOf(false) }
    var sessionWarningText by remember { mutableStateOf("") }
    LaunchedEffect(authRepository) {
        if (authRepository != null) {
            val expireTime = authRepository.getSessionExpireTime()
            if (expireTime > 0L) {
                val now = System.currentTimeMillis()
                if (expireTime <= now) {
                    showSessionWarning = true
                    sessionWarningText = "会话已过期，请重新授权"
                } else {
                    val daysLeft = (expireTime - now) / (1000L * 60 * 60 * 24)
                    val hoursLeft = (expireTime - now) / (1000L * 60 * 60)
                    if (daysLeft < AppConstants.SESSION_WARNING_DAYS) {
                        showSessionWarning = true
                        sessionWarningText = when {
                            daysLeft > 1 -> "会话将在${daysLeft}天后过期，请及时刷新"
                            daysLeft == 1L -> "会话将在1天后过期，请及时刷新"
                            hoursLeft > 0 -> "会话将在${hoursLeft}小时后过期，请立即刷新"
                            else -> "会话即将过期，请立即刷新"
                        }
                    }
                }
            }
        }
    }

    // Token刷新失败弹窗
    var showTokenExpiredDialog by remember { mutableStateOf(false) }
    LaunchedEffect(authRepository) {
        if (authRepository != null) {
            authRepository.tokenRefreshFailed.collectLatest {
                showTokenExpiredDialog = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWhite)
    ) {
        // 网络状态指示器
        if (networkMonitor != null) {
            NetworkStatusIndicator(networkMonitor = networkMonitor)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Logo区域 - 居中
            Text(
                text = "快麦取货通",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "扫码取货·高效管理",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 首次使用引导提示条（所有用户可见）
            if (showGuide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningBg, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToSettings() }
                        .padding(12.dp),
                    verticalAlignment = AppAlignment.RowCenter
                ) {
                    Text(
                        text = "首次使用？点击设置配置服务器地址和扫码方式",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = WarningText,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            showGuide = false
                            prefs?.edit()?.putBoolean(KEY_GUIDE_SHOWN, true)?.apply()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = WarningText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 会话即将过期警告条
            if (showSessionWarning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningBg, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToSettings() }
                        .padding(12.dp),
                    verticalAlignment = AppAlignment.RowCenter
                ) {
                    Text(
                        text = sessionWarningText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = WarningText,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { showSessionWarning = false },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = WarningText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 取货列表入口 - 水平布局
            ModuleCard(
                title = "取货列表",
                description = "查看和管理取货单",
                icon = {
                    Icon(
                        Icons.Default.List,
                        contentDescription = "取货列表",
                        modifier = Modifier.size(32.dp),
                        tint = BrandBlue
                    )
                },
                onClick = onNavigateToPickList
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 商品详情入口 - 水平布局
            ModuleCard(
                title = "商品详情",
                description = "查看商品信息和图片",
                icon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "商品详情",
                        modifier = Modifier.size(32.dp),
                        tint = BrandBlue
                    )
                },
                onClick = onNavigateToProduct
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 设置入口（所有用户可见）
            ModuleCard(
                title = "设置",
                description = "扫码方式、反馈开关",
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.size(32.dp),
                            tint = BrandBlue
                        )
                    },
                    onClick = onNavigateToSettings
                )
        }
    }

    // Token刷新失败弹窗
    if (showTokenExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showTokenExpiredDialog = false },
            title = { Text("会话已过期") },
            text = { Text("快麦API会话已过期，请重新授权") },
            confirmButton = {
                TextButton(onClick = {
                    showTokenExpiredDialog = false
                    onNavigateToSettings()
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenExpiredDialog = false }) {
                    Text("稍后处理")
                }
            }
        )
    }
}

/**
 * 模块入口卡片 - 水平布局（左侧蓝色图标框，右侧标题+描述）
 * 匹配HTML原型设计：白色卡片背景+左侧52dp蓝色图标框
 */
@Composable
private fun ModuleCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧52dp蓝色图标框
            Column(
                modifier = Modifier
                    .background(PrimaryLightBg)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                icon()
            }
            // 右侧标题+描述
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLightText
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
