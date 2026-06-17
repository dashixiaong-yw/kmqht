package com.kuaimai.pda.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.ui.theme.BorderGray
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SuccessBg
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.SupplierRed
import com.kuaimai.pda.ui.theme.SurfaceGray
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextMuted
import com.kuaimai.pda.ui.theme.TextPrimary
import com.kuaimai.pda.ui.theme.TextSecondary

/**
 * 取货明细行组件
 * 匹配HTML原型：左侧52dp规格图+底部标注，中间规格名+供应商名，右侧40dp库区图+装箱图+完成按钮
 * 最小高度72dp, 触摸目标≥56dp×56dp
 * 已完成状态alpha 0.55, 删除线
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickItemRow(
    item: PickItemEntity,
    onComplete: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
    onImageClick: () -> Unit = {},
    areaImageUrl: String? = null,
    boxImageUrl: String? = null,
    onAreaImageClick: () -> Unit = {},
    onBoxImageClick: () -> Unit = {}
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：52dp规格图（带底部"规格图"标注）
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceGray)
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (item.picPath.isNotEmpty()) {
                    AsyncImage(
                        model = item.picPath,
                        contentDescription = "规格图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 无图占位
                    Text(
                        text = "规格图",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
                // 底部"规格图"标注
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(SurfaceGray.copy(alpha = 0.8f))
                        .align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "规格图",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 中间：规格名 + 供应商名
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

            // 右侧：库区图 + 装箱图 + 完成/恢复按钮
            // 库区图小方块
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceGray)
                    .clickable { onAreaImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!areaImageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = areaImageUrl,
                        contentDescription = "库区图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "库区",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
                // 底部"库区"标注
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(SurfaceGray.copy(alpha = 0.8f))
                        .align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "库区",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 装箱图小方块
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceGray)
                    .clickable { onBoxImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!boxImageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = boxImageUrl,
                        contentDescription = "装箱图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "箱图",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
                // 底部"箱图"标注
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(SurfaceGray.copy(alpha = 0.8f))
                        .align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "箱图",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 完成/恢复按钮（F17: 最小触摸热区56dp×44dp）
            if (isCompleted) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryLightBg),
                    onClick = onRestore
                ) {
                    Box(
                        modifier = Modifier.size(width = 56.dp, height = 56.dp),
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
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessBg),
                    onClick = onComplete
                ) {
                    Box(
                        modifier = Modifier.size(width = 56.dp, height = 44.dp),
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
