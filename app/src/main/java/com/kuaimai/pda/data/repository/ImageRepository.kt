package com.kuaimai.pda.data.repository

import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.db.dao.ProductImageDao
import com.kuaimai.pda.data.db.entity.ProductImageEntity
import com.kuaimai.pda.util.ImageCompressor
import android.util.Log
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
    suspend fun deleteImage(skuOuterId: String, imageType: String)
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
        // 上传前压缩图片（F10: 压缩到1024px宽/质量80%约200KB）
        val compressedFile = ImageCompressor.compress(imageFile)
        val fileToUpload = if (compressedFile !== imageFile) compressedFile else imageFile
        return try {
            uploadService.uploadImage(fileToUpload, imageType, skuOuterId)
        } finally {
            // 清理临时压缩文件
            if (compressedFile !== imageFile && compressedFile.exists()) {
                compressedFile.delete()
            }
        }
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

    override suspend fun deleteImage(skuOuterId: String, imageType: String) {
        val image = productImageDao.getBySkuOuterIdAndType(skuOuterId, imageType)
        if (image != null) {
            productImageDao.deleteBySkuAndType(skuOuterId, imageType)
            try {
                uploadService.deleteImage(image.id)
            } catch (e: Exception) {
                Log.w("ImageRepository", "远程删除图片失败: ${e.message}")
            }
        }
    }
}
