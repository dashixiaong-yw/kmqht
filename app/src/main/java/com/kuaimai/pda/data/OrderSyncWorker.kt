package com.kuaimai.pda.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.dto.AddOrderItemRequest
import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.SkuUpdateDto
import com.kuaimai.pda.data.api.dto.SupplierUpdateDto
import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.entity.PendingOperationEntity
import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.PickOrderRepository
import com.kuaimai.pda.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 离线操作同步Worker
 * 使用WorkManager在后台同步待操作队列
 * 按orderId分组，不同订单间并行，同订单内串行
 * 成功后从Room删除，失败递增retryCount，最多3次后标记冲突
 *
 * 支持的操作类型（统一小写下划线风格）：
 * - complete_item: 完成取货明细 → 后端API
 * - restore_item: 恢复取货明细 → 后端API
 * - update_remark: 更新备注 → 快麦API
 * - update_supplier: 更新供应商 → 快麦API
 * - upload_image: 上传图片 → 后端图片服务
 * - add_item: 添加取货明细 → 后端API
 * - complete_all: 批量完成 → 后端API
 * - delete_item: 删除取货明细 → 后端API
 * - delete_order: 删除取货单 → 后端API
 */
class OrderSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val pendingOperationDao: PendingOperationDao,
    private val apiService: KuaimaiApiService,
    private val orderApiService: OrderApiService,
    private val authRepository: AuthRepository,
    private val imageUploadService: ImageUploadService,
    private val userRepository: UserRepository
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
                        // 重新查询当前retryCount，防止syncOperation已设置-1被覆盖
                        val current = pendingOperationDao.getById(op.id)
                        if (current?.retryCount == -1) {
                            // 已标记为冲突，不覆盖
                            Log.w(TAG, "操作已标记冲突: ${op.operationType} orderId=$orderId")
                        } else {
                            val newRetryCount = op.retryCount + 1
                            if (newRetryCount >= MAX_RETRY) {
                                Log.e(TAG, "操作同步失败超过${MAX_RETRY}次，标记冲突: ${op.operationType}")
                                pendingOperationDao.updateRetryCount(op.id, -1)
                            } else {
                                pendingOperationDao.updateRetryCount(op.id, newRetryCount)
                            }
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
                // 后端API同步
                "complete_item" -> syncCompleteItem(op)
                "restore_item" -> syncRestoreItem(op)
                "add_item" -> syncAddItem(op)
                "complete_all" -> syncCompleteAll(op)
                "delete_item" -> syncDeleteItem(op)
                "delete_order" -> syncDeleteOrder(op)
                // 快麦API同步
                "update_remark" -> syncRemarkUpdate(op)
                "update_supplier" -> syncSupplierUpdate(op)
                // 图片服务同步
                "upload_image" -> syncImageUpload(op)
                else -> {
                    Log.e(TAG, "未知操作类型: ${op.operationType}，标记为冲突而非静默删除")
                    false
                }
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() in 400..499) {
                Log.w(TAG, "客户端错误${e.code()}，标记冲突: ${op.operationType}")
                pendingOperationDao.updateRetryCount(op.id, -1)
                false  // 客户端错误，标记冲突但保留记录，用户可查看/解决
            } else {
                Log.e(TAG, "服务端错误${e.code()}，将重试: ${op.operationType}")
                false  // 服务端错误，重试
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步操作失败: ${op.operationType}, error=${e.message}")
            false
        }
    }

    /**
     * 同步完成取货明细 - 调用后端API
     */
    private suspend fun syncCompleteItem(op: PendingOperationEntity): Boolean {
        val orderId = op.orderId
        val itemId = op.targetId
        val token = userRepository.getToken()
        orderApiService.completeItem(token, orderId, itemId)
        Log.d(TAG, "完成明细同步完成: orderId=$orderId itemId=$itemId")
        return true
    }

    /**
     * 同步恢复取货明细 - 调用后端API
     */
    private suspend fun syncRestoreItem(op: PendingOperationEntity): Boolean {
        val orderId = op.orderId
        val itemId = op.targetId
        val token = userRepository.getToken()
        orderApiService.restoreItem(token, orderId, itemId)
        return true
    }

    /**
     * 同步添加取货明细 - 调用后端API
     */
    private suspend fun syncAddItem(op: PendingOperationEntity): Boolean {
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: return false
        val orderId = op.orderId
        val token = userRepository.getToken()
        orderApiService.addItem(token, orderId, AddOrderItemRequest(skuOuterId = skuOuterId))
        Log.d(TAG, "添加明细同步完成: orderId=$orderId skuOuterId=$skuOuterId")
        return true
    }

    /**
     * 同步批量完成 - 调用后端API
     */
    private suspend fun syncCompleteAll(op: PendingOperationEntity): Boolean {
        val orderId = op.orderId
        val token = userRepository.getToken()
        orderApiService.completeAllItems(token, orderId)
        Log.d(TAG, "批量完成同步完成: orderId=$orderId")
        return true
    }

    /**
     * 同步删除取货明细 - 调用后端API
     */
    private suspend fun syncDeleteItem(op: PendingOperationEntity): Boolean {
        val orderId = op.orderId
        val itemId = op.targetId
        val token = userRepository.getToken()
        orderApiService.deleteItem(token, orderId, itemId)
        Log.d(TAG, "删除明细同步完成: orderId=$orderId itemId=$itemId")
        return true
    }

    /**
     * 同步删除取货单 - 调用后端API
     */
    private suspend fun syncDeleteOrder(op: PendingOperationEntity): Boolean {
        val orderId = op.orderId
        val token = userRepository.getToken()
        orderApiService.deleteOrder(token, orderId)
        Log.d(TAG, "删除取货单同步完成: orderId=$orderId")
        return true
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

        val (remoteId, imageUrl) = imageUploadService.uploadImage(imageFile, imageType, skuOuterId)
        Log.d(TAG, "图片上传同步完成: skuOuterId=$skuOuterId remoteId=$remoteId imageUrl=$imageUrl")
        return true
    }

    /**
     * 从JSON payload中提取值
     * 使用JSONObject解析，正确处理转义字符
     */
    private fun extractPayloadValue(payload: String, key: String): String? {
        return try {
            val json = JSONObject(payload)
            if (json.has(key)) json.getString(key) else null
        } catch (e: Exception) {
            Log.w(TAG, "解析payload失败: key=$key, error=${e.message}")
            null
        }
    }
}
