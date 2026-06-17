package com.kuaimai.pda.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待操作队列实体（离线操作缓存）
 * 索引：orderId
 * 操作类型：ADD_ITEM/COMPLETE_ITEM/RESTORE_ITEM/COMPLETE_ALL/DELETE_ITEM/DELETE_ORDER
 * 以及：update_remark, update_supplier, upload_image
 */
@Entity(
    tableName = "pending_operation",
    indices = [Index("order_id")]
)
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * 操作类型（统一小写下划线风格）：
     * - complete_item: 完成取货明细
     * - restore_item: 恢复取货明细
     * - complete_all: 批量完成
     * - add_item: 添加取货明细
     * - delete_item: 删除取货明细
     * - delete_order: 删除取货单
     * - update_remark: 更新备注
     * - update_supplier: 更新供应商
     * - upload_image: 上传图片
     */
    @ColumnInfo(name = "operation_type")
    val operationType: String,

    @ColumnInfo(name = "order_id")
    val orderId: Long,

    @ColumnInfo(name = "target_id")
    val targetId: Long,

    /** 操作载荷JSON */
    val payload: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /**
     * 重试计数：
     * - 0~2: 正常重试
     * - -1: 冲突标记（超过最大重试次数）
     */
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)
