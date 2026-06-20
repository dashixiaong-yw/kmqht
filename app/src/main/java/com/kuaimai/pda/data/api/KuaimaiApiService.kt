package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.ItemGetRequest
import com.kuaimai.pda.data.api.dto.ItemGetWrapper
import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.ItemUpdateWrapper
import com.kuaimai.pda.data.api.dto.SkuQueryRequest
import com.kuaimai.pda.data.api.dto.SkuQueryWrapper
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
    ): ItemUpdateWrapper

    /** 商品供应商更新 erp.item.general.addorupdate (supplier) */
    @POST("router")
    suspend fun updateItemSupplier(
        @Body params: ItemUpdateRequest
    ): ItemUpdateWrapper

    /** SKU查询 erp.item.single.sku.get */
    @POST("router")
    suspend fun getSkuInfo(
        @Body params: SkuQueryRequest
    ): SkuQueryWrapper

    /** 商品查询 item.single.get */
    @POST("router")
    suspend fun getItemDetail(
        @Body params: ItemGetRequest
    ): ItemGetWrapper
}
