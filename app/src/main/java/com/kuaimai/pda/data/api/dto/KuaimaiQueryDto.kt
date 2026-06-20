package com.kuaimai.pda.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * erp.item.single.sku.get 请求
 */
data class SkuQueryRequest(
    @SerializedName("method") val method: String = "erp.item.single.sku.get",
    @SerializedName("skuOuterId") val skuOuterId: String = ""
)

/**
 * erp.item.single.sku.get 响应（wrapper）
 */
data class SkuQueryWrapper(
    @SerializedName("erp_item_single_sku_get_response")
    val response: SkuQueryData? = null
)

data class SkuQueryData(
    val itemSku: List<SkuItemInfo> = emptyList()
)

data class SkuItemInfo(
    val itemOuterId: String = "",
    val sysItemId: Long = 0,
    val sysSkuId: Long = 0,
    val propertiesName: String = "",
    val skuOuterId: String = "",
    val title: String = ""
)

/**
 * item.single.get 请求
 */
data class ItemGetRequest(
    @SerializedName("method") val method: String = "item.single.get",
    @SerializedName("outerId") val outerId: String = ""
)

/**
 * item.single.get 响应（wrapper）
 */
data class ItemGetWrapper(
    @SerializedName("item_single_get_response")
    val response: ItemGetData? = null
)

data class ItemGetData(
    val item: ItemInfo? = null
)

data class ItemInfo(
    val title: String = ""
)
