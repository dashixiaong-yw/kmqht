package com.kuaimai.pda.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kuaimai.pda.data.db.entity.ProductImageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 商品图片DAO
 */
@Dao
interface ProductImageDao {

    /**
     * 根据SKU外部编码获取图片列表
     */
    @Query("SELECT * FROM product_image WHERE sku_outer_id = :skuOuterId")
    fun getBySkuOuterId(skuOuterId: String): Flow<List<ProductImageEntity>>

    /**
     * 根据SKU和图片类型获取图片
     */
    @Query("SELECT * FROM product_image WHERE sku_outer_id = :skuOuterId AND image_type = :imageType LIMIT 1")
    suspend fun getBySkuOuterIdAndType(skuOuterId: String, imageType: String): ProductImageEntity?

    /**
     * 插入图片
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ProductImageEntity): Long

    /**
     * 批量插入图片
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ProductImageEntity>)

    /**
     * 根据SKU和图片类型删除图片
     */
    @Query("DELETE FROM product_image WHERE sku_outer_id = :skuOuterId AND image_type = :imageType")
    suspend fun deleteBySkuAndType(skuOuterId: String, imageType: String)

    @Query("DELETE FROM product_image WHERE sku_outer_id = :skuOuterId")
    suspend fun deleteBySku(skuOuterId: String)

    /**
     * 删除所有图片
     */
    @Query("DELETE FROM product_image")
    suspend fun deleteAll()

    /** 原子替换指定SKU的图片：先删旧记录再批量插入 */
    @Transaction
    suspend fun replaceImagesForSku(skuOuterId: String, images: List<ProductImageEntity>) {
        deleteBySku(skuOuterId)
        insertAll(images)
    }
}
