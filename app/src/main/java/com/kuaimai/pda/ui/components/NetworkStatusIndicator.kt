package com.kuaimai.pda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuaimai.pda.ui.theme.AppAlignment
import com.kuaimai.pda.ui.theme.DangerText
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.WarningYellow
import com.kuaimai.pda.util.NetworkMonitor

/**
 * 网络状态指示器
 * 在线：绿色3dp横条
 * 离线：红色横条 + "已离线" 文字
 * 弱网：黄色横条 + "网络不稳定" 文字
 */
@Composable
fun NetworkStatusIndicator(networkMonitor: NetworkMonitor) {
    val networkStatus by networkMonitor.networkStatus.collectAsState()

    when (networkStatus) {
        NetworkMonitor.Status.ONLINE -> {
            // 在线：绿色3dp横条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(SuccessText)
            )
        }
        NetworkMonitor.Status.OFFLINE -> {
            // 离线：红色横条 + "已离线"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DangerText.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = AppAlignment.RowCenter
            ) {
                Text(
                    text = "已离线",
                    color = SurfaceWhite,
                    fontSize = 12.sp
                )
            }
        }
        NetworkMonitor.Status.WEAK -> {
            // 弱网：黄色横条 + "网络不稳定"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarningYellow.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = AppAlignment.RowCenter
            ) {
                Text(
                    text = "网络不稳定",
                    color = SurfaceWhite,
                    fontSize = 12.sp
                )
            }
        }
    }
}
