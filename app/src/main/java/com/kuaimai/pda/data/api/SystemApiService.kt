package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.AppVersionResponse
import com.kuaimai.pda.data.api.dto.KuaimaiCredentialsResponse
import com.kuaimai.pda.data.api.dto.KuaimaiRefreshResponse
import com.kuaimai.pda.data.api.dto.KuaimaiSessionStatusResponse
import com.kuaimai.pda.data.api.dto.KuaimaiSuppliersResponse
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

    /** 获取快麦供应商列表（含编码） */
    @GET("api/kuaimai/suppliers")
    suspend fun getKuaimaiSuppliers(
        @Header("X-User-Token") token: String
    ): KuaimaiSuppliersResponse

    /** 获取已分发的最新应用版本（无需 token，匿名访问） */
    @GET("api/app-version")
    suspend fun getAppVersion(): AppVersionResponse
}
