package com.kuaimai.pda.data.api

import com.kuaimai.pda.data.api.dto.AddOrderItemRequest
import com.kuaimai.pda.data.api.dto.BaseResponse
import com.kuaimai.pda.data.api.dto.CreateOrderRequest
import com.kuaimai.pda.data.api.dto.OrderDetailResponse
import com.kuaimai.pda.data.api.dto.OrderItemResponse
import com.kuaimai.pda.data.api.dto.OrderListResponse
import com.kuaimai.pda.data.api.dto.OrderResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 后端取货单API服务接口
 * 对应后端 /api/orders 路由
 * 所有接口需要X-User-Token认证
 */
interface OrderApiService {

    /** 创建取货单 */
    @POST("api/orders")
    suspend fun createOrder(
        @Header("X-User-Token") token: String,
        @Body req: CreateOrderRequest
    ): OrderResponse

    /** 获取取货单列表 */
    @GET("api/orders")
    suspend fun listOrders(
        @Header("X-User-Token") token: String,
        @Query("status") status: Int? = null
    ): OrderListResponse

    /** 获取取货单详情（含明细） */
    @GET("api/orders/{orderId}")
    suspend fun getOrderDetail(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long,
        @Query("supplierName") supplierName: String? = null
    ): OrderDetailResponse

    /** 添加取货明细 */
    @POST("api/orders/{orderId}/items")
    suspend fun addItem(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long,
        @Body req: AddOrderItemRequest
    ): OrderItemResponse

    /** 完成取货明细（幂等） */
    @PUT("api/orders/{orderId}/items/{itemId}/complete")
    suspend fun completeItem(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long,
        @Path("itemId") itemId: Long
    ): BaseResponse

    /** 恢复取货明细 */
    @PUT("api/orders/{orderId}/items/{itemId}/restore")
    suspend fun restoreItem(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long,
        @Path("itemId") itemId: Long
    ): BaseResponse

    /** 批量完成所有明细 */
    @PUT("api/orders/{orderId}/complete-all")
    suspend fun completeAllItems(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long
    ): BaseResponse

    /** 删除取货单 */
    @DELETE("api/orders/{orderId}")
    suspend fun deleteOrder(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long
    ): BaseResponse

    /** 删除取货明细 */
    @DELETE("api/orders/{orderId}/items/{itemId}")
    suspend fun deleteItem(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long,
        @Path("itemId") itemId: Long
    ): BaseResponse

    /** 发布取货单到公共列表 */
    @POST("api/orders/{orderId}/publish")
    suspend fun publishOrder(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long
    ): BaseResponse

    /** 领取公开取货单 */
    @POST("api/orders/{orderId}/claim")
    suspend fun claimOrder(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long
    ): BaseResponse

    /** 获取取货单供应商列表 */
    @GET("api/orders/{orderId}/suppliers")
    suspend fun getSuppliers(
        @Header("X-User-Token") token: String,
        @Path("orderId") orderId: Long
    ): List<String>
}
