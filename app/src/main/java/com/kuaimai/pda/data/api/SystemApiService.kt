package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.KuaimaiCredentialsResponse
import com.kuaimai.pda.data.api.dto.KuaimaiRefreshResponse
import com.kuaimai.pda.data.api.dto.KuaimaiSessionStatusResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * 后端系统API服务接口
 * 包含快麦session状态查询和手动刷新
 */
interface SystemApiService {

    /** 查询快麦session状态 */
    @GET("api/kuaimai/session-status")
    suspend fun getSessionStatus(
        @Header("X-User-Token") token: String
    ): KuaimaiSessionStatusResponse

    /** 手动刷新快麦session */
    @POST("api/kuaimai/refresh-session")
    suspend fun refreshSession(
        @Header("X-User-Token") token: String
    ): KuaimaiRefreshResponse

    /** 获取快麦凭证（登录后同步到本地） */
    @GET("api/kuaimai/credentials")
    suspend fun getKuaimaiCredentials(
        @Header("X-User-Token") token: String
    ): KuaimaiCredentialsResponse
}
