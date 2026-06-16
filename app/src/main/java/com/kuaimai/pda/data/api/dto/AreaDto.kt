package com.kuaimai.pda.data.api.dto

/**
 * 拣货区相关DTO
 */

/** 拣货区响应 */
data class AreaResponse(
    val id: Long = 0,
    val name: String = "",
    val createdAt: String = ""
)

/** 拣货区列表响应 */
data class AreaListResponse(
    val success: Boolean = true,
    val message: String = "",
    val data: List<AreaResponse> = emptyList()
)
