package com.kuaimai.pda.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.dto.AddOrderItemRequest
import com.kuaimai.pda.data.api.dto.ItemGetRequest
import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.SkuQueryRequest
import com.kuaimai.pda.data.api.dto.SkuUpdateDto
import com.kuaimai.pda.data.api.dto.SupplierUpdateDto
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.entity.PendingOperationEntity
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
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "OrderSyncWorker"
        private const val MAX_RETRY = 3
    }

    private val pendingOperationDao: PendingOperationDao? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.pendingOperationDao
    }
    private val apiService: KuaimaiApiService? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.apiService
    }
    private val orderApiService: OrderApiService? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.orderApiService
    }
    private val imageUploadService: ImageUploadService? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.imageUploadService
    }
    private val userRepository: UserRepository? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.userRepository
    }
    private val productImageDao: com.kuaimai.pda.data.db.dao.ProductImageDao? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.productImageDao
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = pendingOperationDao ?: run {
                Log.e(TAG, "依赖未初始化")
                return@withContext Result.failure()
            }

            var hasFailure = false
            var hasWork = true
            while (hasWork) {
                val operations = dao.getAllPending()
                if (operations.isEmpty()) {
                    hasWork = false
                    break
                }

                val grouped = operations.groupBy { it.orderId }
                var loopFailure = false

                for ((orderId, orderOps) in grouped) {
                    for (op in orderOps) {
                        val success = syncOperation(op, dao)
                        if (success) {
                            dao.deleteById(op.id)
                            Log.d(TAG, "操作同步成功: ${op.operationType} orderId=$orderId")
                        } else {
                            val current = dao.getById(op.id)
                            if (current == null) continue
                            if (current.retryCount == -1) {
                                Log.w(TAG, "操作已标记冲突，删除: ${op.operationType} orderId=$orderId")
                                dao.deleteById(op.id)
                            } else if (current.retryCount >= MAX_RETRY) {
                                Log.e(TAG, "操作同步失败超过${MAX_RETRY}次，标记冲突: ${op.operationType}")
                                dao.updateRetryCount(op.id, -1)
                            } else {
                                dao.updateRetryCount(op.id, current.retryCount + 1)
                            }
                            loopFailure = true
                        }
                    }
                }

                if (loopFailure) hasFailure = true
                if (!loopFailure && dao.getAllPending().isNotEmpty()) {
                    Log.d(TAG, "仍有待处理操作，继续下一轮")
                }
            }

            if (hasFailure) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "同步Worker异常: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun syncOperation(op: PendingOperationEntity, dao: PendingOperationDao): Boolean {
        return try {
            when (op.operationType) {
                "complete_item" -> syncCompleteItem(op)
                "restore_item" -> syncRestoreItem(op)
                "add_item" -> syncAddItem(op)
                "complete_all" -> syncCompleteAll(op)
                "delete_item" -> syncDeleteItem(op)
                "delete_order" -> syncDeleteOrder(op)
                "update_remark" -> syncRemarkUpdate(op)
                "update_supplier" -> syncSupplierUpdate(op)
                "upload_image" -> syncImageUpload(op)
                else -> {
                    Log.e(TAG, "未知操作类型: ${op.operationType}，标记为冲突")
                    false
                }
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() in 400..499) {
                Log.w(TAG, "客户端错误${e.code()}，标记冲突: ${op.operationType}")
                dao.updateRetryCount(op.id, -1)
                false
            } else {
                Log.e(TAG, "服务端错误${e.code()}，将重试: ${op.operationType}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步操作失败: ${op.operationType}, error=${e.message}")
            false
        }
    }

    private suspend fun syncCompleteItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: return false
        val api = orderApiService ?: return false
        val token = userRepo.getToken()
        return try {
            api.completeItem(token, op.orderId, op.targetId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "completeItem同步失败: ${e.message}")
            false
        }
    }

    private suspend fun syncRestoreItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: return false
        val api = orderApiService ?: return false
        val token = userRepo.getToken()
        return try {
            api.restoreItem(token, op.orderId, op.targetId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "restoreItem同步失败: ${e.message}")
            false
        }
    }

    private suspend fun syncAddItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: return false
        val api = orderApiService ?: return false
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: return false
        val token = userRepo.getToken()
        return try {
            api.addItem(token, op.orderId, AddOrderItemRequest(skuOuterId = skuOuterId))
            true
        } catch (e: Exception) {
            Log.w(TAG, "addItem同步失败: ${e.message}")
            false
        }
    }

    private suspend fun syncCompleteAll(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: return false
        val api = orderApiService ?: return false
        val token = userRepo.getToken()
        return try {
            api.completeAllItems(token, op.orderId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "completeAll同步失败: ${e.message}")
            false
        }
    }

    private suspend fun syncDeleteItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: return false
        val api = orderApiService ?: return false
        val token = userRepo.getToken()
        return try {
            api.deleteItem(token, op.orderId, op.targetId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteItem同步失败: ${e.message}")
            false
        }
    }

    private suspend fun syncDeleteOrder(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: return false
        val api = orderApiService ?: return false
        val token = userRepo.getToken()
        return try {
            api.deleteOrder(token, op.orderId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteOrder同步失败: ${e.message}")
            false
        }
    }

    private suspend fun syncRemarkUpdate(op: PendingOperationEntity): Boolean {
        val kmApi = apiService ?: return false
        val remark = extractPayloadValue(op.payload, "remark") ?: return false
        val skuId = extractPayloadValue(op.payload, "sys_sku_id")?.toLongOrNull() ?: return false
        val itemId = extractPayloadValue(op.payload, "sys_item_id")?.toLongOrNull() ?: return false
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: return false
        val propertiesName = extractPayloadValue(op.payload, "properties_name") ?: return false
        val outerId = skuOuterId.substringBefore("-")
        val title = getLatestTitle(kmApi, skuOuterId)
        if (title == null) {
            Log.w(TAG, "无法获取商品标题，跳过备注同步: skuOuterId=$skuOuterId")
            return false
        }
        val request = ItemUpdateRequest(
            id = itemId,
            method = "erp.item.general.addorupdate",
            outerId = outerId,
            title = title,
            skus = listOf(SkuUpdateDto(skuId = skuId, skuOuterId = skuOuterId, skuRemark = remark, skuPropertiesName = propertiesName))
        )
        val wrapper = kmApi.updateItemRemark(request)
        val response = wrapper.response
        if (response == null || !response.success) {
            Log.w(TAG, "快麦备注更新失败: code=${response?.code} msg=${response?.msg}")
            return false
        }
        return true
    }

    private suspend fun syncSupplierUpdate(op: PendingOperationEntity): Boolean {
        val kmApi = apiService ?: return false
        val supplierName = extractPayloadValue(op.payload, "supplier_name") ?: return false
        val supplierCode = extractPayloadValue(op.payload, "supplier_code") ?: return false
        val itemId = extractPayloadValue(op.payload, "sys_item_id")?.toLongOrNull() ?: return false
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: return false
        val skuId = extractPayloadValue(op.payload, "sys_sku_id")?.toLongOrNull() ?: return false
        val skuPropertiesName = extractPayloadValue(op.payload, "properties_name") ?: ""
        val outerId = skuOuterId.substringBefore("-")
        val title = getLatestTitle(kmApi, skuOuterId)
        if (title == null) {
            Log.w(TAG, "无法获取商品标题，跳过供应商同步: skuOuterId=$skuOuterId")
            return false
        }
        val skuSuppliers = listOf(SupplierUpdateDto(supplierCode = supplierCode, supplierName = supplierName))
        val request = ItemUpdateRequest(
            id = itemId,
            method = "erp.item.general.addorupdate",
            outerId = outerId,
            title = title,
            skus = listOf(SkuUpdateDto(
                skuId = skuId, skuOuterId = skuOuterId,
                skuPropertiesName = skuPropertiesName,
                skuSuppliers = skuSuppliers
            ))
        )
        val wrapper = kmApi.updateItemSupplier(request)
        val response = wrapper.response
        if (response == null || !response.success) {
            Log.w(TAG, "快麦供应商更新失败: code=${response?.code} msg=${response?.msg}")
            return false
        }
        return true
    }

    private suspend fun getLatestTitle(kmApi: KuaimaiApiService, skuOuterId: String): String? {
        try {
            val skuResp = kmApi.getSkuInfo(SkuQueryRequest(skuOuterId = skuOuterId))
            val skuList = skuResp.response?.itemSku ?: emptyList()
            val itemOuterId = skuList.firstOrNull()?.itemOuterId ?: ""
            if (itemOuterId.isBlank()) return null
            val itemResp = kmApi.getItemDetail(ItemGetRequest(outerId = itemOuterId))
            val title = itemResp.response?.item?.title ?: ""
            return title.ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "获取最新title失败: ${e.message}")
            return null
        }
    }

    private suspend fun syncImageUpload(op: PendingOperationEntity): Boolean {
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: return false
        val imageType = extractPayloadValue(op.payload, "image_type") ?: return false
        val filePath = extractPayloadValue(op.payload, "file_path") ?: return false
        val imageFile = File(filePath)
        if (!imageFile.exists()) {
            Log.w(TAG, "图片文件不存在，放弃同步: $filePath")
            return true
        }
        val uploader = imageUploadService ?: return false
        val imageDao = productImageDao ?: return false
        return try {
            val (remoteId, imageUrl) = uploader.uploadImage(imageFile, imageType, skuOuterId)
            imageDao.insert(com.kuaimai.pda.data.db.entity.ProductImageEntity(
                skuOuterId = skuOuterId,
                imageType = imageType,
                imageUrl = imageUrl,
                remoteId = remoteId,
                createdAt = com.kuaimai.pda.util.TimeUtils.now()
            ))
            imageFile.delete()
            true
        } catch (e: Exception) {
            Log.w(TAG, "上传图片失败，保留文件以重试: ${e.message}")
            false
        }
    }

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
