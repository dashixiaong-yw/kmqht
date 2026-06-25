package com.kuaimai.pda.data.repository

import android.util.Log

import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.db.dao.ProductImageDao
import com.kuaimai.pda.data.db.entity.ProductImageEntity
import com.kuaimai.pda.util.ImageCompressor
import com.kuaimai.pda.util.TimeUtils
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/** 图片仓库接口 */
interface ImageRepository {
    suspend fun getImageBySkuAndType(skuOuterId: String, imageType: String): ProductImageEntity?
    suspend fun uploadImage(imageFile: File, imageType: String, skuOuterId: String): ProductImageEntity
    suspend fun deleteImage(skuOuterId: String, imageType: String)
    /** 从后端同步图片到本地（多PDA数据共享），返回同步后的图片列表 */
    suspend fun syncImagesFromBackend(skuOuterId: String): List<ProductImageEntity>
}

/**
 * 图片仓库实现
 */
class ImageRepositoryImpl @Inject constructor(
    private val uploadService: ImageUploadService,
    private val productImageDao: ProductImageDao
) : ImageRepository {

    override suspend fun getImageBySkuAndType(skuOuterId: String, imageType: String): ProductImageEntity? {
        return productImageDao.getBySkuOuterIdAndType(skuOuterId, imageType)
    }

    override suspend fun uploadImage(
        imageFile: File,
        imageType: String,
        skuOuterId: String
    ): ProductImageEntity {
        val compressedFile = ImageCompressor.compress(imageFile)
        val fileToUpload = if (compressedFile !== imageFile) compressedFile else imageFile
        return try {
            val (remoteId, imageUrl) = uploadService.uploadImage(fileToUpload, imageType, skuOuterId)
            val entity = ProductImageEntity(
                skuOuterId = skuOuterId,
                imageType = imageType,
                imageUrl = imageUrl,
                remoteId = remoteId,
                createdAt = TimeUtils.now()
            )
            productImageDao.insert(entity)
            entity
        } finally {
            if (compressedFile !== imageFile && compressedFile.exists()) {
                compressedFile.delete()
            }
        }
    }

    override suspend fun deleteImage(skuOuterId: String, imageType: String) {
        val image = productImageDao.getBySkuOuterIdAndType(skuOuterId, imageType)
        if (image != null) {
            val deleteId = if (image.remoteId > 0) image.remoteId else image.id
            uploadService.deleteImage(deleteId)
            productImageDao.deleteBySkuAndType(skuOuterId, imageType)
        }
    }

    override suspend fun syncImagesFromBackend(skuOuterId: String): List<ProductImageEntity> {
        try {
            val responseBody = uploadService.fetchImages(skuOuterId)
            val jsonArray = JSONObject(responseBody).getJSONArray("data")
            if (jsonArray.length() == 0) return productImageDao.getBySkuOuterId(skuOuterId).first()
            val now = TimeUtils.now()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val imageType = json.getString("imageType")
                val remoteId = json.getLong("id")
                val existing = productImageDao.getBySkuOuterIdAndType(skuOuterId, imageType)
                if (existing?.remoteId == remoteId) continue
                productImageDao.insert(ProductImageEntity(
                    skuOuterId = skuOuterId,
                    imageType = imageType,
                    imageUrl = json.getString("imageUrl"),
                    remoteId = remoteId,
                    createdAt = now
                ))
            }
        } catch (e: Exception) {
            Log.w("ImageRepository", "同步后端图片列表失败: ${e.message}")
        }
        return productImageDao.getBySkuOuterId(skuOuterId).first()
    }
}
