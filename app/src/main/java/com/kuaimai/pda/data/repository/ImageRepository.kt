package com.kuaimai.pda.data.repository

import android.util.Log

import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.db.dao.ProductImageDao
import com.kuaimai.pda.data.db.entity.ProductImageEntity
import com.kuaimai.pda.util.ImageCompressor
import com.kuaimai.pda.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.io.File
import javax.inject.Inject

/**
 * 图片仓库接口
 */
interface ImageRepository {
    suspend fun uploadImage(imageFile: File, imageType: String, skuOuterId: String): Pair<Long, String>
    fun getImagesBySkuOuterId(skuOuterId: String): Flow<List<ProductImageEntity>>
    suspend fun getImageBySkuAndType(skuOuterId: String, imageType: String): ProductImageEntity?
    suspend fun saveImage(image: ProductImageEntity): Long
    suspend fun deleteImage(skuOuterId: String, imageType: String)
    /** 从后端同步图片到本地（多PDA数据共享） */
    suspend fun syncImagesFromBackend(skuOuterId: String)
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
    ): Pair<Long, String> {
        val compressedFile = ImageCompressor.compress(imageFile)
        val fileToUpload = if (compressedFile !== imageFile) compressedFile else imageFile
        return try {
            uploadService.uploadImage(fileToUpload, imageType, skuOuterId)
        } finally {
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
            val deleteId = if (image.remoteId > 0) image.remoteId else image.id
            uploadService.deleteImage(deleteId)
            productImageDao.deleteBySkuAndType(skuOuterId, imageType)
        }
    }

    override suspend fun syncImagesFromBackend(skuOuterId: String) {
        try {
            val responseBody = uploadService.fetchImages(skuOuterId)
            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() == 0) return
            val now = TimeUtils.now()
            val entities = mutableListOf<ProductImageEntity>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                entities.add(ProductImageEntity(
                    skuOuterId = skuOuterId,
                    imageType = json.getString("imageType"),
                    imageUrl = json.getString("imageUrl"),
                    remoteId = json.getLong("id"),
                    createdAt = now
                ))
            }
            productImageDao.replaceImagesForSku(skuOuterId, entities)
        } catch (e: Exception) {
            Log.w("ImageRepository", "同步后端图片列表失败: ${e.message}")
        }
    }
}
