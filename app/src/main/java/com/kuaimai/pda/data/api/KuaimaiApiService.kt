package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.SupplierListResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 快麦API服务接口（3个API端点）
 * 所有接口均为POST，使用快麦开放平台通用参数格式
 */
interface KuaimaiApiService {

    /** 供应商查询 supplier.list.query */
    @POST("router")
    suspend fun querySupplierList(
        @Body params: Map<String, String>
    ): SupplierListResponse

    /** 商品备注更新 erp.item.general.addorupdate (remark) */
    @POST("router")
    suspend fun updateItemRemark(
        @Body params: ItemUpdateRequest
    ): Map<String, Any>

    /** 商品供应商更新 erp.item.general.addorupdate (supplier) */
    @POST("router")
    suspend fun updateItemSupplier(
        @Body params: ItemUpdateRequest
    ): Map<String, Any>
}
