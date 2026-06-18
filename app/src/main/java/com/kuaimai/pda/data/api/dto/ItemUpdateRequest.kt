package com.kuaimai.pda.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * 商品更新请求 erp.item.general.addorupdate
 * 用于备注更新和供应商更新
 */
data class ItemUpdateRequest(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("method") val method: String = "",
    @SerializedName("outerId") val outerId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("skus") val skus: List<SkuUpdateDto> = emptyList()
)

/** SKU备注更新（V2: id + outerId + propertiesName + remark + suppliers） */
data class SkuUpdateDto(
    @SerializedName("id") val skuId: Long = 0,
    @SerializedName("outerId") val skuOuterId: String = "",
    @SerializedName("propertiesName") val skuPropertiesName: String = "",
    @SerializedName("remark") val skuRemark: String = "",
    @SerializedName("suppliers") val skuSuppliers: List<SupplierUpdateDto> = emptyList()
)

/** 供应商更新（V2: code + itemTitle） */
data class SupplierUpdateDto(
    @SerializedName("code") val supplierCode: String = "",
    @SerializedName("itemTitle") val supplierName: String = ""
)
