package com.kuaimai.pda.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * 商品更新请求 erp.item.general.addorupdate
 * 用于备注更新和供应商更新
 */
data class ItemUpdateRequest(
    val id: Long = 0,
    val method: String = "",
    val outerId: String = "",
    val title: String = "",
    val skus: List<SkuUpdateDto> = emptyList(),
    val suppliers: List<SupplierUpdateDto> = emptyList()
)

/** SKU备注更新（V2: id + outerId + propertiesName + remark） */
data class SkuUpdateDto(
    @SerializedName("id") val skuId: Long = 0,
    @SerializedName("outerId") val skuOuterId: String = "",
    @SerializedName("propertiesName") val skuPropertiesName: String = "",
    @SerializedName("remark") val skuRemark: String = ""
)

/** 供应商更新（V2: code + itemTitle） */
data class SupplierUpdateDto(
    @SerializedName("code") val supplierCode: String = "",
    @SerializedName("itemTitle") val supplierName: String = ""
)
