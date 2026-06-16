package com.kuaimai.pda.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kuaimai.pda.ui.theme.BorderGray
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary

/**
 * 图片上传区域组件（2列网格布局）
 * 支持库区图(area)和装箱图(box)两种类型
 * 每个槽位：1:1宽高比，虚线边框2dp，圆角12dp，最小高度120dp
 */
@Composable
fun ImageUploadSection(
    areaImageUrl: String?,
    boxImageUrl: String?,
    isUploading: Boolean,
    uploadProgress: Int,
    onUploadArea: () -> Unit,
    onUploadBox: () -> Unit,
    onDeleteArea: (() -> Unit)? = null,
    onDeleteBox: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "商品图片",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 库区图
            ImageSlot(
                label = "库区图",
                imageUrl = areaImageUrl,
                onClick = onUploadArea,
                onDelete = onDeleteArea,
                modifier = Modifier.weight(1f)
            )
            // 装箱图
            ImageSlot(
                label = "装箱图",
                imageUrl = boxImageUrl,
                onClick = onUploadBox,
                onDelete = onDeleteBox,
                modifier = Modifier.weight(1f)
            )
        }

        // 上传进度
        if (isUploading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 单个图片上传槽位
 * 宽高比1:1，虚线边框2dp，圆角12dp
 */
@Composable
private fun ImageSlot(
    label: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val stroke = Stroke(
        width = 2.dp.value,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    )
    val borderColor = BorderGray
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = stroke,
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (imageUrl != null) {
                        showMenu = true
                    } else {
                        onClick()
                    }
                }
        ) {
            if (imageUrl != null) {
                // 显示已上传图片
                AsyncImage(
                    model = imageUrl,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )

                // 已上传图片的操作菜单
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("替换图片") },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("删除图片") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            } else {
                // 空状态：虚线框+图标+文字
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = "上传",
                        modifier = Modifier.size(32.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
