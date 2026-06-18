package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.ItemUpdateResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 快麦API服务接口
 * 所有接口均为POST，使用快麦开放平台通用参数格式
 */
interface KuaimaiApiService {

    /** 商品备注更新 erp.item.general.addorupdate (remark) */
    @POST("router")
    suspend fun updateItemRemark(
        @Body params: ItemUpdateRequest
    ): ItemUpdateResponse

    /** 商品供应商更新 erp.item.general.addorupdate (supplier) */
    @POST("router")
    suspend fun updateItemSupplier(
        @Body params: ItemUpdateRequest
    ): ItemUpdateResponse
}
