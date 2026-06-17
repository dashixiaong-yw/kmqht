package com.kuaimai.pda.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.ui.theme.BorderGray
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SuccessBg
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextPrimary
import com.kuaimai.pda.ui.theme.TextSecondary
import com.kuaimai.pda.util.TimeUtils

/**
 * 取货单卡片组件
 * Card padding 16dp×12dp, rounded 12dp
 * 单号18sp SemiBold, 区域+进度14sp Medium, 状态徽章rounded 20dp
 * 长按触发删除
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickOrderCard(
    order: PickOrderEntity,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 第一行：单号 + 状态徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.orderNo,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                // 状态徽章
                val (bgColor, textColor, label) = when (order.status) {
                    0 -> Triple(PrimaryLightBg, PrimaryLightText, "进行中")
                    1 -> Triple(SuccessBg, SuccessText, "已完成")
                    else -> Triple(PrimaryLightBg, PrimaryLightText, "未知")
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 第二行：进度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "进度: ${order.completedCount}/${order.totalCount}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (order.completedCount == order.totalCount && order.totalCount > 0)
                        SuccessText else TextSecondary
                )

                Text(
                    text = TimeUtils.formatTimestamp(order.createdAt),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // GAP-15: 进度点指示器（绿色=已完成，灰色=未完成）
            if (order.totalCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(order.totalCount) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (index < order.completedCount) SuccessText else BorderGray,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}
