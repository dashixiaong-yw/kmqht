package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.AppVersionResponse
import com.kuaimai.pda.data.api.dto.KuaimaiCredentialsResponse
import com.kuaimai.pda.data.api.dto.KuaimaiRefreshResponse
import com.kuaimai.pda.data.api.dto.KuaimaiSessionStatusResponse
import com.kuaimai.pda.data.api.dto.KuaimaiSuppliersResponse
import com.kuaimai.pda.data.api.dto.SkuDetailResponse
import com.kuaimai.pda.data.api.dto.SkuStockResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 后端系统API服务接口
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

    /** 获取单个SKU详细信息（实时从快麦获取） */
    @GET("api/sku/{skuOuterId}")
    suspend fun getSkuDetail(
        @Header("X-User-Token") token: String,
        @Path("skuOuterId") skuOuterId: String
    ): SkuDetailResponse

    /** 查询SKU实际总库存 */
    @GET("api/sku/{skuOuterId}/stock")
    suspend fun getSkuStock(
        @Header("X-User-Token") token: String,
        @Path("skuOuterId") skuOuterId: String
    ): SkuStockResponse
}
