package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.BaseResponse
import com.kuaimai.pda.data.api.dto.CreateUserRequest
import com.kuaimai.pda.data.api.dto.LoginRequest
import com.kuaimai.pda.data.api.dto.LoginResponse
import com.kuaimai.pda.data.api.dto.UpdateUserRequest
import com.kuaimai.pda.data.api.dto.UserListResponse
import com.kuaimai.pda.data.api.dto.UserResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * 后端用户管理API服务接口
 * 对应后端 /api/users 路由
 */
interface UserApiService {

    /** 用户登录 */
    @POST("api/users/login")
    suspend fun login(
        @Body req: LoginRequest
    ): LoginResponse

    /** 获取当前用户信息 */
    @GET("api/users/me")
    suspend fun getCurrentUser(
        @Header("X-User-Token") token: String
    ): UserResponse

    /** 获取用户列表（需settings权限） */
    @GET("api/users")
    suspend fun getUsers(
        @Header("X-User-Token") token: String
    ): UserListResponse

    /** 创建用户（需settings权限） */
    @POST("api/users")
    suspend fun createUser(
        @Header("X-User-Token") token: String,
        @Body req: CreateUserRequest
    ): BaseResponse

    /** 更新用户（需settings权限） */
    @PUT("api/users/{userId}")
    suspend fun updateUser(
        @Header("X-User-Token") token: String,
        @Path("userId") userId: Long,
        @Body req: UpdateUserRequest
    ): BaseResponse

    /** 删除用户（需settings权限） */
    @DELETE("api/users/{userId}")
    suspend fun deleteUser(
        @Header("X-User-Token") token: String,
        @Path("userId") userId: Long
    ): BaseResponse

    /** 退出登录 */
    @POST("api/users/logout")
    suspend fun logout(
        @Header("X-User-Token") token: String
    ): BaseResponse
}
