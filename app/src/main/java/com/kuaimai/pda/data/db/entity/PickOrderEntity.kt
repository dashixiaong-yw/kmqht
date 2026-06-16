package com.kuaimai.pda.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 取货单实体
 */
@Entity(tableName = "pick_order")
data class PickOrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "order_no")
    val orderNo: String,

    /** 状态：0-进行中 1-已完成 */
    val status: Int = 0,

    /** 完成类型：0-未完成 1-全部完成 */
    @ColumnInfo(name = "completion_type")
    val completionType: Int = 0,

    @ColumnInfo(name = "total_count")
    val totalCount: Int = 0,

    @ColumnInfo(name = "completed_count")
    val completedCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "expire_at")
    val expireAt: Long
)
