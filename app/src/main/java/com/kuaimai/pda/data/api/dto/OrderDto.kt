package com.kuaimai.pda.data.api.dto

import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.util.TimeUtils

/**
 * 后端取货单相关DTO
 * 对应后端 /api/orders 接口
 */

/** 创建取货单请求 */
data class CreateOrderRequest(
    val areaName: String
)

/** 取货单响应 */
data class OrderResponse(
    val id: Long = 0,
    val orderNo: String = "",
    val status: Int = 0,
    val completionType: Int = 0,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val createdAt: String = "",
    val completedAt: String? = null,
    val expireAt: String = "",
    val createdBy: String = "",
    val assignedTo: String = "",
    val visibility: String = "private"
) {
    fun toOrderEntity(): PickOrderEntity = PickOrderEntity(
        id = id,
        orderNo = orderNo,
        status = status,
        completionType = completionType,
        totalCount = totalCount,
        completedCount = completedCount,
        createdAt = TimeUtils.parseBeijingTime(createdAt).let { if (it > 0) it else TimeUtils.now() },
        completedAt = completedAt?.let { TimeUtils.parseBeijingTime(it) },
        expireAt = TimeUtils.parseBeijingTime(expireAt).let { if (it > 0) it else TimeUtils.now() + TimeUtils.DEFAULT_EXPIRE_MS },
        createdBy = createdBy,
        assignedTo = assignedTo,
        visibility = visibility
    )
}

/** 取货单详情响应（含明细） */
data class OrderDetailResponse(
    val id: Long = 0,
    val orderNo: String = "",
    val status: Int = 0,
    val completionType: Int = 0,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val createdAt: String = "",
    val completedAt: String? = null,
    val expireAt: String = "",
    val createdBy: String = "",
    val assignedTo: String = "",
    val visibility: String = "private",
    val items: List<OrderItemResponse> = emptyList()
) {
    /** 转换为基础OrderResponse */
    fun toOrderResponse(): OrderResponse = OrderResponse(
        id = id,
        orderNo = orderNo,
        status = status,
        completionType = completionType,
        totalCount = totalCount,
        completedCount = completedCount,
        createdAt = createdAt,
        completedAt = completedAt,
        expireAt = expireAt,
        createdBy = createdBy,
        assignedTo = assignedTo,
        visibility = visibility
    )
}

/** 取货单列表响应 */
data class OrderListResponse(
    val success: Boolean = true,
    val message: String = "",
    val data: List<OrderResponse> = emptyList()
)

/** 取货明细响应 */
data class OrderItemResponse(
    val id: Long = 0,
    val skuOuterId: String = "",
    val sysItemId: Long = 0,
    val sysSkuId: Long = 0,
    val propertiesName: String = "",
    val picPath: String = "",
    val status: Int = 0,
    val supplierName: String = "",
    val supplierCode: String = "",
    val remark: String = "",
    val itemOuterId: String = "",
    val createdAt: String = "",
    val completedAt: String? = null
)

/** 添加取货明细请求 */
data class AddOrderItemRequest(
    val skuOuterId: String
)

/** 通用响应 */
data class BaseResponse(
    val success: Boolean = true,
    val message: String = ""
)
