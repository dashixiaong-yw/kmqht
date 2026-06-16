package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.AreaListResponse
import retrofit2.http.GET

/**
 * 后端拣货区API服务接口
 */
interface AreaApiService {

    /** 获取拣货区列表 */
    @GET("api/areas")
    suspend fun getAreas(): AreaListResponse
}
