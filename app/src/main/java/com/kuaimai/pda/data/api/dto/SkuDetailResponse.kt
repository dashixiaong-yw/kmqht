package com.kuaimai.pda.data.api.dto

/**
 * 后端 /api/sku/{sku_outer_id} 响应
 * 实时从快麦获取的SKU详细信息
 */
data class SkuDetailResponse(
    val skuOuterId: String = "",
    val propertiesName: String = "",
    val picPath: String = "",
    val remark: String = "",
    val supplierName: String = "",
    val supplierCode: String = "",
    val itemTitle: String = "",
    val sysItemId: Long = 0,
    val sysSkuId: Long = 0,
    val itemOuterId: String = ""
)
