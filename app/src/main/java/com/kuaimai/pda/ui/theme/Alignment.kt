package com.kuaimai.pda.ui.theme

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement

/**
 * 对齐常量收敛
 * 页面只引用常量，禁止混用padding偏移模拟对齐
 */
object AppAlignment {
    val RowBetween = Arrangement.SpaceBetween
    val RowCenter = Alignment.CenterVertically
    val ItemStart = Alignment.TopStart
    val ButtonEnd = Arrangement.End
    val ColumnCenter = Alignment.CenterHorizontally
}
