package com.kuaimai.pda.data.api.dto

/**
 * 用户与权限相关DTO
 * 对应后端 /api/users 接口
 */

/** 登录请求 */
data class LoginRequest(
    val username: String,
    val password: String
)

/** 登录响应 */
data class LoginResponse(
    val success: Boolean = true,
    val message: String = "",
    val token: String = "",
    val userId: Long = 0,
    val username: String = "",
    val permissions: List<String> = emptyList(),
    val mustChangePassword: Boolean = false
)

/** 创建用户请求 */
data class CreateUserRequest(
    val username: String,
    val password: String,
    val permissions: List<String> = emptyList()
)

/** 更新用户请求 */
data class UpdateUserRequest(
    val password: String? = null,
    val permissions: List<String>? = null,
    val isActive: Boolean? = null
)

/** 用户响应 */
data class UserResponse(
    val id: Long = 0,
    val username: String = "",
    val isActive: Boolean = true,
    val permissions: List<String> = emptyList(),
    val createdAt: String = ""
)

/** 用户列表响应 */
data class UserListResponse(
    val success: Boolean = true,
    val message: String = "",
    val data: List<UserResponse> = emptyList()
)
