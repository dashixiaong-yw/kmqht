package com.kuaimai.pda.data.api.dto

/**
 * 商品更新请求 erp.item.general.addorupdate
 * 用于备注更新和供应商更新
 */
data class ItemUpdateRequest(
    val id: Long = 0,
    val method: String = "",
    val skus: List<SkuUpdateDto> = emptyList(),
    val suppliers: List<SupplierUpdateDto> = emptyList()
)

/** SKU备注更新 */
data class SkuUpdateDto(
    val skuId: Long = 0,
    val skuRemark: String = ""
)

/** 供应商更新 */
data class SupplierUpdateDto(
    val supplierCode: String = "",
    val supplierName: String = ""
)
