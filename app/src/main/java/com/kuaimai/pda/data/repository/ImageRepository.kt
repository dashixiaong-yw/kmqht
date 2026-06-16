package com.kuaimai.pda.data.repository

import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.db.dao.ProductImageDao
import com.kuaimai.pda.data.db.entity.ProductImageEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

/**
 * 图片仓库接口
 */
interface ImageRepository {
    suspend fun uploadImage(imageFile: File, imageType: String, skuOuterId: String): String
    fun getImagesBySkuOuterId(skuOuterId: String): Flow<List<ProductImageEntity>>
    suspend fun getImageBySkuAndType(skuOuterId: String, imageType: String): ProductImageEntity?
    suspend fun saveImage(image: ProductImageEntity): Long
}

/**
 * 图片仓库实现
 */
class ImageRepositoryImpl @Inject constructor(
    private val uploadService: ImageUploadService,
    private val productImageDao: ProductImageDao
) : ImageRepository {

    override suspend fun uploadImage(
        imageFile: File,
        imageType: String,
        skuOuterId: String
    ): String {
        return uploadService.uploadImage(imageFile, imageType, skuOuterId)
    }

    override fun getImagesBySkuOuterId(skuOuterId: String): Flow<List<ProductImageEntity>> {
        return productImageDao.getBySkuOuterId(skuOuterId)
    }

    override suspend fun getImageBySkuAndType(
        skuOuterId: String,
        imageType: String
    ): ProductImageEntity? {
        return productImageDao.getBySkuOuterIdAndType(skuOuterId, imageType)
    }

    override suspend fun saveImage(image: ProductImageEntity): Long {
        return productImageDao.insert(image)
    }
}
