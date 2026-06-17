package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.AreaListResponse
import com.kuaimai.pda.data.api.dto.AreaResponse
import com.kuaimai.pda.data.api.dto.BaseResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 后端拣货区API服务接口
 * 所有接口需要X-User-Token认证
 */
interface AreaApiService {

    /** 获取拣货区列表 */
    @GET("api/areas")
    suspend fun getAreas(
        @Header("X-User-Token") token: String
    ): AreaListResponse

    /** 创建拣货区（需settings权限） */
    @POST("api/areas")
    suspend fun createArea(
        @Header("X-User-Token") token: String,
        @Body req: AreaCreateRequest
    ): AreaResponse

    /** 删除拣货区（需settings权限） */
    @DELETE("api/areas/{areaId}")
    suspend fun deleteArea(
        @Header("X-User-Token") token: String,
        @Path("areaId") areaId: Long
    ): BaseResponse
}

/**
 * 创建拣货区请求
 */
data class AreaCreateRequest(
    val name: String
)
