package com.kuaimai.pda.data.api.dto

/**
 * 商品更新通用响应 erp.item.general.addorupdate
 */
data class ItemUpdateResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val msg: String = ""
)
