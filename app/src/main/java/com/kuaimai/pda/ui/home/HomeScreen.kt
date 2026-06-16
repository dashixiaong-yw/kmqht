package com.kuaimai.pda.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kuaimai.pda.ui.components.NetworkStatusIndicator
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary
import com.kuaimai.pda.util.NetworkMonitor

/**
 * 主页：3个模块入口卡片
 * 无底部导航栏，使用卡片式入口
 * 冷启动优化：先从Room缓存加载，后台刷新
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPickList: () -> Unit,
    onNavigateToProduct: () -> Unit,
    onNavigateToSettings: () -> Unit,
    networkMonitor: NetworkMonitor? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "快麦取货通",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBlue,
                    titleContentColor = SurfaceWhite
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 网络状态指示器
            if (networkMonitor != null) {
                NetworkStatusIndicator(networkMonitor = networkMonitor)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 取货列表入口
                ModuleCard(
                    title = "取货列表",
                    description = "查看和管理取货单",
                    icon = { Icon(Icons.Default.List, contentDescription = "取货列表", modifier = Modifier.size(36.dp)) },
                    onClick = onNavigateToPickList
                )

                // 商品详情入口
                ModuleCard(
                    title = "商品详情",
                    description = "查看商品信息和图片",
                    icon = { Icon(Icons.Default.Search, contentDescription = "商品详情", modifier = Modifier.size(36.dp)) },
                    onClick = onNavigateToProduct
                )

                // 设置入口
                ModuleCard(
                    title = "设置",
                    description = "配置API密钥和服务器地址",
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置", modifier = Modifier.size(36.dp)) },
                    onClick = onNavigateToSettings
                )
            }
        }
    }
}

/**
 * 模块入口卡片
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
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryLightText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
