package com.kuaimai.pda.data.api.dto

/**
 * 快麦session相关DTO
 * 对应后端 /api/kuaimai 接口
 */

/** 快麦session状态响应 */
data class KuaimaiSessionStatusResponse(
    val success: Boolean = true,
    val message: String = "",
    val isValid: Boolean = false,
    val daysLeft: Int? = null,
    val updatedAt: String = "",
    val hasRefreshToken: Boolean = false
)

/** 快麦session刷新响应 */
data class KuaimaiRefreshResponse(
    val success: Boolean = true,
    val message: String = "",
    val daysLeft: Int? = null
)

/** 快麦凭证响应（登录后从后端同步） */
data class KuaimaiCredentialsResponse(
    val appKey: String = "",
    val appSecret: String = "",
    val session: String = ""
)

/** 快麦供应商项（后端 /api/kuaimai/suppliers 响应） */
data class KuaimaiSupplierItem(
    val code: String = "",
    val name: String = "",
    val id: Long = 0
)

/** 快麦供应商列表响应 */
data class KuaimaiSuppliersResponse(
    val suppliers: List<KuaimaiSupplierItem> = emptyList()
)
