package com.kuaimai.pda.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * 商品列表查询响应 item.list.query
 */
data class ItemListResponse(
    val code: Int = 0,
    val msg: String = "",
    val items: List<ItemDto> = emptyList()
)

data class ItemDto(
    val id: Long = 0,
    val outerId: String = "",
    val barcode: String = "",
    val picPath: String = "",
    val name: String = "",
    val remark: String = ""
)
