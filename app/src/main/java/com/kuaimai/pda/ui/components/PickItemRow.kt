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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val contentAlpha = if (isCompleted) 0.65f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .combinedClickable(
                onClick = { },
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clickable { onImageClick() }
                    .size(width = 52.dp, height = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceGray),
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
                    Text(text = "规格图", fontSize = 9.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
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
                    color = if (isCompleted) TextMuted else SupplierRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                            Text(text = "库区", fontSize = 9.sp, color = TextMuted)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(SurfaceGray.copy(alpha = 0.8f))
                                .align(Alignment.BottomCenter),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "库区",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                            Text(text = "箱图", fontSize = 9.sp, color = TextMuted)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(SurfaceGray.copy(alpha = 0.8f))
                                .align(Alignment.BottomCenter),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "箱图",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isCompleted) {
                    TextButton(
                        onClick = onRestore,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = SurfaceGray,
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("↩ 恢复", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    TextButton(
                        onClick = onComplete,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = SuccessBg,
                            contentColor = SuccessText
                        ),
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("✓ 完成", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
