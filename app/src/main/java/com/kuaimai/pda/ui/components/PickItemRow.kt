package com.kuaimai.pda.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SuccessBg
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.SupplierRed
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextPrimary

/**
 * 取货明细行组件
 * 最小高度72dp, padding 12dp×10dp, rounded 12dp
 * 规格名16sp Medium, 供应商名20sp Bold #DC2626
 * 完成按钮56dp宽, 语义色
 * 已完成状态alpha 0.55, 删除线
 * 触摸目标≥56dp×56dp
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickItemRow(
    item: PickItemEntity,
    onComplete: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit
) {
    val isCompleted = item.status == 1
    val contentAlpha = if (isCompleted) 0.55f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .combinedClickable(
                onClick = { /* 常规点击无操作 */ },
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：规格名 + 供应商名
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.propertiesName.ifEmpty { item.skuOuterId },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.supplierName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SupplierRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧：完成/恢复按钮
            if (isCompleted) {
                // 恢复按钮
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryLightBg),
                    onClick = onRestore
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "恢复",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryLightText
                        )
                    }
                }
            } else {
                // 完成按钮
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessBg),
                    onClick = onComplete
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "完成",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SuccessText
                        )
                    }
                }
            }
        }
    }
}
