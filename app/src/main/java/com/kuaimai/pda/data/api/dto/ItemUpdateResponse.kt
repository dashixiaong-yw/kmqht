package com.kuaimai.pda.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * 商品更新通用响应 erp.item.general.addorupdate
 */
data class ItemUpdateResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val msg: String = ""
)

/**
 * 快麦API响应包裹层
 * erp.item.general.addorupdate 返回 {"erp_item_general_addorupdate_response": {...}}
 */
data class ItemUpdateWrapper(
    @SerializedName("erp_item_general_addorupdate_response")
    val response: ItemUpdateResponse? = null
)
