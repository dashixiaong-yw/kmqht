package com.kuaimai.pda.data.api.dto

/**
 * SKU列表查询响应 erp.item.sku.list.get
 */
data class SkuListResponse(
    val code: Int = 0,
    val msg: String = "",
    val skus: List<SkuDto> = emptyList()
)

data class SkuDto(
    val skuId: Long = 0,
    val itemId: Long = 0,
    val propertiesName: String = "",
    val skuRemark: String = "",
    val skuPicPath: String = ""
)
