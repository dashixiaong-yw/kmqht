package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.ItemListResponse
import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.SkuListResponse
import com.kuaimai.pda.data.api.dto.SupplierListResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 快麦API服务接口（6个API端点）
 * 所有接口均为POST，使用快麦开放平台通用参数格式
 * 注意：session刷新统一通过后端SystemApiService中转，不在此接口定义
 */
interface KuaimaiApiService {

    /** 1. 商品列表查询 item.list.query */
    @POST("router")
    suspend fun queryItemList(
        @Body params: Map<String, String>
    ): ItemListResponse

    /** 2. SKU列表查询 erp.item.sku.list.get */
    @POST("router")
    suspend fun getSkuList(
        @Body params: Map<String, String>
    ): SkuListResponse

    /** 3. 供应商列表查询 erp.item.supplier.list.get */
    @POST("router")
    suspend fun getSupplierList(
        @Body params: Map<String, String>
    ): SupplierListResponse

    /** 4. 供应商查询 supplier.list.query */
    @POST("router")
    suspend fun querySupplierList(
        @Body params: Map<String, String>
    ): SupplierListResponse

    /** 5. 商品备注更新 erp.item.general.addorupdate (remark) */
    @POST("router")
    suspend fun updateItemRemark(
        @Body params: ItemUpdateRequest
    ): Map<String, Any>

    /** 6. 商品供应商更新 erp.item.general.addorupdate (supplier) */
    @POST("router")
    suspend fun updateItemSupplier(
        @Body params: ItemUpdateRequest
    ): Map<String, Any>
}
