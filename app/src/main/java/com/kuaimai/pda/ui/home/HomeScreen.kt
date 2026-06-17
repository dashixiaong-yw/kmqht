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
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.components.NetworkStatusIndicator
import com.kuaimai.pda.ui.settings.SettingsViewModel.Companion.KEY_GUIDE_SHOWN
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary
import com.kuaimai.pda.ui.theme.WarningBg
import com.kuaimai.pda.ui.theme.WarningText
import com.kuaimai.pda.util.NetworkMonitor

/**
 * 主页：居中Logo + 模块入口卡片
 * 无底部导航栏，使用卡片式入口
 * 首次使用显示引导提示条
 */
@Composable
fun HomeScreen(
    userRepository: UserRepository,
    onNavigateToPickList: () -> Unit,
    onNavigateToProduct: () -> Unit,
    onNavigateToSettings: () -> Unit,
    networkMonitor: NetworkMonitor? = null,
    prefs: SharedPreferences? = null
) {
    val hasSettingsPermission = userRepository.hasPermission("settings")

    // 首次使用引导提示
    var showGuide by remember {
        mutableStateOf(prefs?.getBoolean(KEY_GUIDE_SHOWN, false) == false)
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

            // 首次使用引导提示条
            if (showGuide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningBg, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToSettings() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = WarningText,
                            modifier = Modifier.size(16.dp)
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

            // 设置入口（仅settings权限用户可见）
            if (hasSettingsPermission) {
                ModuleCard(
                    title = "设置",
                    description = "配置服务器地址、扫码方式",
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
    }
}

/**
 * 模块入口卡片 - 水平布局（图标在左，标题+描述在右）
 * 匹配HTML原型设计
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
            containerColor = PrimaryLightBg
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            // 右侧标题+描述
            Column {
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
