package com.kuaimai.pda.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.SkuUpdateDto
import com.kuaimai.pda.data.api.dto.SupplierUpdateDto
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.entity.PendingOperationEntity
import com.kuaimai.pda.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 离线操作同步Worker
 * 使用WorkManager在后台同步待操作队列
 * 按orderId分组，不同订单间并行，同订单内串行
 * 成功后从Room删除，失败递增retryCount，最多3次后标记冲突
 */
class OrderSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val pendingOperationDao: PendingOperationDao,
    private val apiService: KuaimaiApiService,
    private val authRepository: AuthRepository,
    private val imageUploadService: ImageUploadService
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "OrderSyncWorker"
        private const val MAX_RETRY = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val operations = pendingOperationDao.getAllPending()
            if (operations.isEmpty()) {
                Log.d(TAG, "无待同步操作")
                return@withContext Result.success()
            }

            // 按orderId分组
            val grouped = operations.groupBy { it.orderId }
            var hasFailure = false

            for ((orderId, orderOps) in grouped) {
                // 同订单内串行执行
                for (op in orderOps) {
                    val success = syncOperation(op)
                    if (success) {
                        pendingOperationDao.deleteById(op.id)
                        Log.d(TAG, "操作同步成功: ${op.operationType} orderId=$orderId")
                    } else {
                        // 递增重试计数
                        val newRetryCount = op.retryCount + 1
                        if (newRetryCount >= MAX_RETRY) {
                            // 超过最大重试次数，标记为冲突（保留记录）
                            Log.e(TAG, "操作同步失败超过${MAX_RETRY}次，标记冲突: ${op.operationType}")
                            pendingOperationDao.updateRetryCount(op.id, -1)
                        } else {
                            pendingOperationDao.updateRetryCount(op.id, newRetryCount)
                        }
                        hasFailure = true
                    }
                }
            }

            if (hasFailure) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步Worker异常: ${e.message}")
            Result.retry()
        }
    }

    /**
     * 同步单个操作
     * @return 是否成功
     */
    private suspend fun syncOperation(op: PendingOperationEntity): Boolean {
        return try {
            when (op.operationType) {
                "update_remark" -> syncRemarkUpdate(op)
                "update_supplier" -> syncSupplierUpdate(op)
                "upload_image" -> syncImageUpload(op)
                else -> {
                    Log.w(TAG, "未知操作类型: ${op.operationType}")
                    true // 未知类型直接删除
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步操作失败: ${op.operationType}, error=${e.message}")
            false
        }
    }

    /**
     * 同步备注更新 - 调用快麦API更新SKU备注
     */
    private suspend fun syncRemarkUpdate(op: PendingOperationEntity): Boolean {
        val remark = extractPayloadValue(op.payload, "remark") ?: return false
        val skuId = extractPayloadValue(op.payload, "sys_sku_id")?.toLongOrNull() ?: return false
        val itemId = extractPayloadValue(op.payload, "sys_item_id")?.toLongOrNull() ?: return false

        val request = ItemUpdateRequest(
            id = itemId,
            method = "erp.item.general.addorupdate",
            skus = listOf(SkuUpdateDto(skuId = skuId, skuRemark = remark))
        )
        val result = apiService.updateItemRemark(request)
        Log.d(TAG, "备注同步完成: skuId=$skuId remark=$remark result=$result")
        return true
    }

    /**
     * 同步供应商更新 - 调用快麦API更新商品供应商
     */
    private suspend fun syncSupplierUpdate(op: PendingOperationEntity): Boolean {
        val supplierName = extractPayloadValue(op.payload, "supplier_name") ?: return false
        val supplierCode = extractPayloadValue(op.payload, "supplier_code") ?: return false
        val itemId = extractPayloadValue(op.payload, "sys_item_id")?.toLongOrNull() ?: return false

        val request = ItemUpdateRequest(
            id = itemId,
            method = "erp.item.general.addorupdate",
            suppliers = listOf(SupplierUpdateDto(supplierCode = supplierCode, supplierName = supplierName))
        )
        val result = apiService.updateItemSupplier(request)
        Log.d(TAG, "供应商同步完成: code=$supplierCode name=$supplierName result=$result")
        return true
    }

    /**
     * 同步图片上传 - 调用ImageUploadService上传图片到后端
     */
    private suspend fun syncImageUpload(op: PendingOperationEntity): Boolean {
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: return false
        val imageType = extractPayloadValue(op.payload, "image_type") ?: return false
        val filePath = extractPayloadValue(op.payload, "file_path") ?: return false

        val imageFile = File(filePath)
        if (!imageFile.exists()) {
            Log.e(TAG, "图片文件不存在: $filePath")
            return false
        }

        val imageUrl = imageUploadService.uploadImage(imageFile, imageType, skuOuterId)
        Log.d(TAG, "图片上传同步完成: skuOuterId=$skuOuterId imageUrl=$imageUrl")
        return true
    }

    /**
     * 从JSON payload中提取值
     * 简单解析，避免引入JSON库
     */
    private fun extractPayloadValue(payload: String, key: String): String? {
        val keyPattern = "\"$key\":"
        val keyIndex = payload.indexOf(keyPattern) ?: return null
        val startIndex = payload.indexOf("\"", keyIndex + keyPattern.length)
        val endIndex = payload.indexOf("\"", startIndex + 1)
        return if (startIndex != -1 && endIndex != -1) {
            payload.substring(startIndex + 1, endIndex)
        } else null
    }
}
