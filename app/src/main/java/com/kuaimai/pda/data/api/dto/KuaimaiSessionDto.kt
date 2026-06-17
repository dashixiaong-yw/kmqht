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
