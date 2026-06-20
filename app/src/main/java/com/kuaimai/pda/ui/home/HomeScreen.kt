package com.kuaimai.pda.ui.home

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ListAlt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.kuaimai.pda.ui.theme.BorderGray
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.DangerBg
import com.kuaimai.pda.ui.theme.DangerText
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceGray
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextPrimary
import com.kuaimai.pda.ui.theme.TextSecondary
import com.kuaimai.pda.ui.theme.WarningBg
import com.kuaimai.pda.ui.theme.WarningText
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.NetworkMonitor

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
    DisposableEffect(networkMonitor) {
        networkMonitor?.register()
        onDispose {
            networkMonitor?.unregister()
        }
    }

    var showGuide by remember {
        mutableStateOf(prefs?.getBoolean(KEY_GUIDE_SHOWN, false) != true)
    }

    var showSessionWarning by remember { mutableStateOf(false) }
    var sessionWarningText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            authRepository?.let {
                val expireTime = it.getSessionExpireTime()
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
            kotlinx.coroutines.delay(60 * 60 * 1000L)
        }
    }

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
        if (networkMonitor != null) {
            NetworkStatusIndicator(networkMonitor = networkMonitor)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimaryLightBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Inventory,
                    contentDescription = "Logo",
                    tint = BrandBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

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

            if (showGuide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryLightBg, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToSettings() }
                        .padding(12.dp),
                    verticalAlignment = AppAlignment.RowCenter
                ) {
                    Text(
                        text = "首次使用？点击设置配置服务器地址和扫码方式",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = PrimaryLightText,
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
                            tint = PrimaryLightText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

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

            Spacer(modifier = Modifier.height(14.dp))

            ModuleCard(
                title = "取货列表",
                description = "查看和管理取货单",
                iconBgColor = PrimaryLightBg,
                icon = { Icon(Icons.Default.ListAlt, contentDescription = null, tint = PrimaryLightText, modifier = Modifier.size(24.dp)) },
                onClick = onNavigateToPickList
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModuleCard(
                title = "商品详情",
                description = "扫码查看规格信息",
                iconBgColor = DangerBg,
                icon = { Icon(Icons.Default.Search, contentDescription = null, tint = DangerText, modifier = Modifier.size(24.dp)) },
                onClick = onNavigateToProduct
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModuleCard(
                title = "设置",
                description = "扫码方式、反馈开关",
                iconBgColor = BorderGray,
                icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = onNavigateToSettings
            )
        }
    }

    if (showTokenExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showTokenExpiredDialog = false },
            shape = RoundedCornerShape(16.dp),
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

@Composable
private fun ModuleCard(
    title: String,
    description: String,
    iconBgColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
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
