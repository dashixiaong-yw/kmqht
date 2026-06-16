package com.kuaimai.pda.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 取货明细实体
 * 索引：orderId, skuOuterId, orderId+status联合索引
 */
@Entity(
    tableName = "pick_item",
    indices = [
        Index("order_id"),
        Index("sku_outer_id"),
        Index(value = ["order_id", "status"]),
        Index(value = ["order_id", "sku_outer_id"], unique = true)
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PickOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class PickItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "order_id")
    val orderId: Long,

    @ColumnInfo(name = "sku_outer_id")
    val skuOuterId: String,

    @ColumnInfo(name = "sys_item_id")
    val sysItemId: Long = 0,

    @ColumnInfo(name = "sys_sku_id")
    val sysSkuId: Long = 0,

    @ColumnInfo(name = "properties_name")
    val propertiesName: String = "",

    @ColumnInfo(name = "pic_path")
    val picPath: String = "",

    /** 状态：0-待取货 1-已取货 2-跳过 */
    val status: Int = 0,

    @ColumnInfo(name = "supplier_name")
    val supplierName: String = "",

    @ColumnInfo(name = "supplier_code")
    val supplierCode: String = "",

    /** 备注，NOT NULL，默认空字符串 */
    val remark: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)
