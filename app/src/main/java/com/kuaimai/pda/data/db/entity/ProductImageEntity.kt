package com.kuaimai.pda.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 商品图片实体
 * 唯一索引：skuOuterId + imageType
 */
@Entity(
    tableName = "product_image",
    indices = [
        Index(value = ["sku_outer_id", "image_type"], unique = true)
    ]
)
data class ProductImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sku_outer_id")
    val skuOuterId: String,

    /** 图片类型：area（区域图）或 box（货位图） */
    @ColumnInfo(name = "image_type")
    val imageType: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String,

    @ColumnInfo(name = "remote_id")
    val remoteId: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
