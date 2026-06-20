package com.kuaimai.pda.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.SystemApiService
import com.kuaimai.pda.data.api.dto.AddOrderItemRequest
import com.kuaimai.pda.data.api.dto.ItemGetRequest
import com.kuaimai.pda.data.api.dto.ItemUpdateRequest
import com.kuaimai.pda.data.api.dto.SkuQueryRequest
import com.kuaimai.pda.data.api.dto.SkuUpdateDto
import com.kuaimai.pda.data.api.dto.SupplierUpdateDto
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.PickOrderDao
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
        private const val LOG_FILE = "sync_log.txt"

        fun appendLog(context: Context, message: String) {
            try {
                val file = File(context.cacheDir, LOG_FILE)
                val now = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                val line = "[$now] $message\n"
                val existing = if (file.exists()) file.readLines() else emptyList()
                val lines = if (existing.size >= 500) existing.drop(existing.size - 250) else existing
                file.writeText(lines.joinToString("\n") + "\n" + line)
            } catch (_: Exception) { }
        }
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
    private val systemApiService: SystemApiService? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.systemApiService
    }
    private val pickOrderDao: PickOrderDao? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.pickOrderDao
    }
    private val pickItemDao: PickItemDao? by lazy {
        com.kuaimai.pda.App.OrderSyncWorkerDeps.pickItemDao
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
                appendLog(applicationContext, "Worker启动，共 ${operations.size} 个待处理操作")

                val grouped = operations.groupBy { it.orderId }
                var loopFailure = false

                for ((orderId, orderOps) in grouped) {
                    for (op in orderOps) {
                        val success = syncOperation(op, dao)
                        if (success) {
                            dao.deleteById(op.id)
                            Log.d(TAG, "操作同步成功: ${op.operationType} orderId=$orderId")
                            appendLog(applicationContext, "操作同步成功: type=${op.operationType}, orderId=$orderId")
                        } else {
                            val current = dao.getById(op.id)
                            if (current == null) continue
                            if (current.retryCount == -1) {
                                Log.w(TAG, "操作已标记冲突，删除: ${op.operationType} orderId=$orderId")
                                dao.deleteById(op.id)
                            } else if (current.retryCount >= MAX_RETRY) {
                                if (op.operationType == "upload_image") {
                                    val filePath = extractPayloadValue(op.payload, "file_path")
                                    filePath?.let { File(it).delete() }
                                }
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

            if (hasFailure) {
                 appendLog(applicationContext, "Worker本轮结束: 有失败操作，返回retry")
                 Result.retry()
             } else {
                 // 清理超过7天的冲突记录
                 try {
                     val oneWeekAgo = java.lang.System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                     val cleaned = dao.deleteConflictsOlderThan(oneWeekAgo)
                     if (cleaned > 0) appendLog(applicationContext, "清理冲突记录: ${cleaned}条")
                 } catch (_: Exception) { }
                 // 清理超过7天的已完成取货单
                 try {
                     val oneWeekAgo = java.lang.System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                     val orderDao = pickOrderDao
                     if (orderDao != null) {
                         val cleaned = orderDao.deleteCompletedOlderThan(oneWeekAgo)
                         if (cleaned > 0) {
                             pickItemDao?.deleteOrphanItems()
                             appendLog(applicationContext, "清理已完成取货单: ${cleaned}单")
                         }
                     }
                 } catch (_: Exception) { }
                 appendLog(applicationContext, "Worker本轮结束: 全部成功")
                 Result.success()
             }
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
                appendLog(applicationContext, "操作${op.operationType}客户端错误${e.code()}，标记冲突")
                dao.updateRetryCount(op.id, -1)
                false
            } else {
                Log.e(TAG, "服务端错误${e.code()}，将重试: ${op.operationType}")
                appendLog(applicationContext, "操作${op.operationType}服务端错误${e.code()}，将重试")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步操作失败: ${op.operationType}, error=${e.message}")
            appendLog(applicationContext, "操作${op.operationType}异常: ${e.message}")
            false
        }
    }

    private suspend fun syncCompleteItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: run {
            appendLog(applicationContext, "completeItem同步失败: userRepository为null")
            return false
        }
        val api = orderApiService ?: run {
            appendLog(applicationContext, "completeItem同步失败: orderApiService为null")
            return false
        }
        val token = userRepo.getToken()
        appendLog(applicationContext, "completeItem开始同步: orderId=${op.orderId}, itemId=${op.targetId}")
        return try {
            val resp = api.completeItem(token, op.orderId, op.targetId)
            if (!resp.success) {
                Log.w(TAG, "completeItem业务拒绝: ${resp.message}")
                appendLog(applicationContext, "completeItem业务拒绝: ${resp.message}")
                return false
            }
            appendLog(applicationContext, "completeItem同步成功: orderId=${op.orderId}, itemId=${op.targetId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "completeItem同步失败: ${e.message}")
            appendLog(applicationContext, "completeItem同步异常: ${e.message}")
            false
        }
    }

    private suspend fun syncRestoreItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: run {
            appendLog(applicationContext, "restoreItem同步失败: userRepository为null")
            return false
        }
        val api = orderApiService ?: run {
            appendLog(applicationContext, "restoreItem同步失败: orderApiService为null")
            return false
        }
        val token = userRepo.getToken()
        appendLog(applicationContext, "restoreItem开始同步: orderId=${op.orderId}, itemId=${op.targetId}")
        return try {
            val resp = api.restoreItem(token, op.orderId, op.targetId)
            if (!resp.success) {
                Log.w(TAG, "restoreItem业务拒绝: ${resp.message}")
                appendLog(applicationContext, "restoreItem业务拒绝: ${resp.message}")
                return false
            }
            appendLog(applicationContext, "restoreItem同步成功: orderId=${op.orderId}, itemId=${op.targetId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "restoreItem同步失败: ${e.message}")
            appendLog(applicationContext, "restoreItem同步异常: ${e.message}")
            false
        }
    }

    private suspend fun syncAddItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: run {
            appendLog(applicationContext, "addItem同步失败: userRepository为null")
            return false
        }
        val api = orderApiService ?: run {
            appendLog(applicationContext, "addItem同步失败: orderApiService为null")
            return false
        }
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: run {
            appendLog(applicationContext, "addItem同步失败: payload缺少sku_outer_id")
            return false
        }
        val token = userRepo.getToken()
        appendLog(applicationContext, "addItem开始同步: orderId=${op.orderId}, sku=$skuOuterId")
        return try {
            api.addItem(token, op.orderId, AddOrderItemRequest(skuOuterId = skuOuterId))
            appendLog(applicationContext, "addItem同步成功: orderId=${op.orderId}, sku=$skuOuterId")
            true
        } catch (e: Exception) {
            Log.w(TAG, "addItem同步失败: ${e.message}")
            appendLog(applicationContext, "addItem同步异常: orderId=${op.orderId}, sku=$skuOuterId, error=${e.message}")
            false
        }
    }

    private suspend fun syncCompleteAll(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: run {
            appendLog(applicationContext, "completeAll同步失败: userRepository为null")
            return false
        }
        val api = orderApiService ?: run {
            appendLog(applicationContext, "completeAll同步失败: orderApiService为null")
            return false
        }
        val token = userRepo.getToken()
        appendLog(applicationContext, "completeAll开始同步: orderId=${op.orderId}")
        return try {
            val resp = api.completeAllItems(token, op.orderId)
            if (!resp.success) {
                Log.w(TAG, "completeAll业务拒绝: ${resp.message}")
                appendLog(applicationContext, "completeAll业务拒绝: ${resp.message}")
                return false
            }
            appendLog(applicationContext, "completeAll同步成功: orderId=${op.orderId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "completeAll同步失败: ${e.message}")
            appendLog(applicationContext, "completeAll同步异常: ${e.message}")
            false
        }
    }

    private suspend fun syncDeleteItem(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: run {
            appendLog(applicationContext, "deleteItem同步失败: userRepository为null")
            return false
        }
        val api = orderApiService ?: run {
            appendLog(applicationContext, "deleteItem同步失败: orderApiService为null")
            return false
        }
        val token = userRepo.getToken()
        appendLog(applicationContext, "deleteItem开始同步: orderId=${op.orderId}, itemId=${op.targetId}")
        return try {
            val resp = api.deleteItem(token, op.orderId, op.targetId)
            if (!resp.success) {
                Log.w(TAG, "deleteItem业务拒绝: ${resp.message}")
                appendLog(applicationContext, "deleteItem业务拒绝: ${resp.message}")
                return false
            }
            appendLog(applicationContext, "deleteItem同步成功: orderId=${op.orderId}, itemId=${op.targetId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteItem同步失败: ${e.message}")
            appendLog(applicationContext, "deleteItem同步异常: ${e.message}")
            false
        }
    }

    private suspend fun syncDeleteOrder(op: PendingOperationEntity): Boolean {
        val userRepo = userRepository ?: run {
            appendLog(applicationContext, "deleteOrder同步失败: userRepository为null")
            return false
        }
        val api = orderApiService ?: run {
            appendLog(applicationContext, "deleteOrder同步失败: orderApiService为null")
            return false
        }
        val token = userRepo.getToken()
        appendLog(applicationContext, "deleteOrder开始同步: orderId=${op.orderId}")
        return try {
            val resp = api.deleteOrder(token, op.orderId)
            if (!resp.success) {
                Log.w(TAG, "deleteOrder业务拒绝: ${resp.message}")
                appendLog(applicationContext, "deleteOrder业务拒绝: ${resp.message}")
                return false
            }
            appendLog(applicationContext, "deleteOrder同步成功: orderId=${op.orderId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteOrder同步失败: ${e.message}")
            appendLog(applicationContext, "deleteOrder同步异常: ${e.message}")
            false
        }
    }

    private suspend fun syncRemarkUpdate(op: PendingOperationEntity): Boolean {
        val kmApi = apiService ?: run {
            Log.e(TAG, "syncRemarkUpdate: apiService为null")
            appendLog(applicationContext, "快麦备注同步失败: apiService未初始化")
            return false
        }
        val remark = extractPayloadValue(op.payload, "remark") ?: run {
            Log.e(TAG, "syncRemarkUpdate: payload缺少remark字段")
            appendLog(applicationContext, "快麦备注同步失败: payload缺少remark字段")
            return false
        }
        val skuId = extractPayloadValue(op.payload, "sys_sku_id")?.toLongOrNull() ?: run {
            Log.e(TAG, "syncRemarkUpdate: sys_sku_id无效")
            return false
        }
        val itemId = extractPayloadValue(op.payload, "sys_item_id")?.toLongOrNull() ?: run {
            Log.e(TAG, "syncRemarkUpdate: sys_item_id无效")
            return false
        }
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: run {
            Log.e(TAG, "syncRemarkUpdate: sku_outer_id无效")
            return false
        }
        val skuData = fetchLatestSkuDataViaBackend(skuOuterId, extractPayloadValue(op.payload, "item_outer_id"))
        if (skuData == null) {
            Log.w(TAG, "无法获取SKU数据，跳过备注同步: skuOuterId=$skuOuterId")
            appendLog(applicationContext, "快麦备注同步失败: 获取SKU数据失败, sku=$skuOuterId")
            return false
        }
        val propertiesName = skuData.propertiesName.ifBlank {
            extractPayloadValue(op.payload, "properties_name") ?: ""
        }
        val request = ItemUpdateRequest(
            id = itemId,
            method = "erp.item.general.addorupdate",
            outerId = skuData.itemOuterId,
            title = skuData.title,
            skus = listOf(SkuUpdateDto(skuId = skuId, skuOuterId = skuOuterId, skuRemark = remark, skuPropertiesName = propertiesName))
        )
        val response = kmApi.updateItemRemark(request)
        if (!response.success) {
            Log.w(TAG, "快麦备注更新失败: code=${response.code} msg=${response.msg}")
            appendLog(applicationContext, "快麦备注同步失败: code=${response.code}, msg=${response.msg}")
            return false
        }
        appendLog(applicationContext, "快麦备注同步成功: sku=$skuOuterId")
        return true
    }

    private suspend fun syncSupplierUpdate(op: PendingOperationEntity): Boolean {
        val kmApi = apiService ?: run {
            Log.e(TAG, "syncSupplierUpdate: apiService为null")
            appendLog(applicationContext, "快麦供应商同步失败: apiService未初始化")
            return false
        }
        val supplierName = extractPayloadValue(op.payload, "supplier_name") ?: run {
            Log.e(TAG, "syncSupplierUpdate: payload缺少supplier_name")
            return false
        }
        val supplierCode = extractPayloadValue(op.payload, "supplier_code") ?: run {
            Log.e(TAG, "syncSupplierUpdate: payload缺少supplier_code")
            return false
        }
        val itemId = extractPayloadValue(op.payload, "sys_item_id")?.toLongOrNull() ?: run {
            Log.e(TAG, "syncSupplierUpdate: sys_item_id无效")
            return false
        }
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: run {
            Log.e(TAG, "syncSupplierUpdate: sku_outer_id无效")
            return false
        }
        val skuId = extractPayloadValue(op.payload, "sys_sku_id")?.toLongOrNull() ?: run {
            Log.e(TAG, "syncSupplierUpdate: sys_sku_id无效")
            return false
        }
        val skuData = fetchLatestSkuDataViaBackend(skuOuterId, extractPayloadValue(op.payload, "item_outer_id"))
        if (skuData == null) {
            Log.w(TAG, "无法获取SKU数据，跳过供应商同步: skuOuterId=$skuOuterId")
            appendLog(applicationContext, "快麦供应商同步失败: 获取SKU数据失败, sku=$skuOuterId")
            return false
        }
        val skuPropertiesName = skuData.propertiesName.ifBlank {
            extractPayloadValue(op.payload, "properties_name") ?: ""
        }
        val skuSuppliers = listOf(SupplierUpdateDto(supplierCode = supplierCode, supplierName = supplierName))
        val request = ItemUpdateRequest(
            id = itemId,
            method = "erp.item.general.addorupdate",
            outerId = skuData.itemOuterId,
            title = skuData.title,
            skus = listOf(SkuUpdateDto(
                skuId = skuId, skuOuterId = skuOuterId,
                skuPropertiesName = skuPropertiesName,
                skuSuppliers = skuSuppliers
            ))
        )
        val response = kmApi.updateItemSupplier(request)
        if (!response.success) {
            Log.w(TAG, "快麦供应商更新失败: code=${response.code} msg=${response.msg}")
            appendLog(applicationContext, "快麦供应商同步失败: code=${response.code}, msg=${response.msg}")
            return false
        }
        appendLog(applicationContext, "快麦供应商同步成功: sku=$skuOuterId, supplier=$supplierName")
        return true
    }

    private data class SkuSyncData(
        val title: String,
        val itemOuterId: String,
        val propertiesName: String
    )

    private suspend fun fetchLatestSkuData(kmApi: KuaimaiApiService, skuOuterId: String, itemOuterIdFallback: String? = null): SkuSyncData? {
        try {
            val skuResp = kmApi.getSkuInfo(SkuQueryRequest(skuOuterId = skuOuterId))
            val skuList = skuResp.response?.itemSku ?: emptyList()
            val sku = skuList.firstOrNull()
            val itemOuterId = sku?.itemOuterId
            if (itemOuterId.isNullOrBlank()) {
                if (itemOuterIdFallback.isNullOrBlank()) {
                    Log.w(TAG, "fetchLatestSkuData: 未从API获取到itemOuterId，且无fallback: $skuOuterId")
                    return null
                }
                // 使用 fallback 跳过 getSkuInfo，直接调 getItemDetail
                val fallbackResp = kmApi.getItemDetail(ItemGetRequest(outerId = itemOuterIdFallback))
                val fallbackTitle = fallbackResp.response?.item?.title ?: ""
                val effectiveFallbackTitle = if (fallbackTitle.isNotBlank()) fallbackTitle else sku?.title ?: ""
                if (effectiveFallbackTitle.isBlank()) {
                    Log.w(TAG, "fetchLatestSkuData: 使用fallback后仍无title，且sku.title为空: $skuOuterId")
                    return null
                }
                Log.d(TAG, "fetchLatestSkuData fallback路径成功: sku=$skuOuterId, title来源=${if (fallbackTitle.isNotBlank()) "getItemDetail" else "sku.title"}")
                return SkuSyncData(
                    title = effectiveFallbackTitle,
                    itemOuterId = itemOuterIdFallback,
                    propertiesName = sku?.propertiesName ?: ""
                )
            }
            val itemResp = kmApi.getItemDetail(ItemGetRequest(outerId = itemOuterId))
            val title = itemResp.response?.item?.title ?: ""
            val effectiveTitle = if (title.isNotBlank()) title else sku?.title ?: ""
            if (effectiveTitle.isBlank()) {
                Log.w(TAG, "fetchLatestSkuData: getItemDetail和getSkuInfo均无title: $skuOuterId")
                return null
            }
            Log.d(TAG, "fetchLatestSkuData成功: sku=$skuOuterId, title来源=${if (title.isNotBlank()) "getItemDetail" else "sku.title"}")
            return SkuSyncData(
                title = effectiveTitle,
                itemOuterId = itemOuterId,
                propertiesName = sku.propertiesName
            )
        } catch (e: Exception) {
            Log.w(TAG, "获取SKU数据失败: $skuOuterId — ${e.message}")
            appendLog(applicationContext, "获取SKU数据失败: sku=$skuOuterId, error=${e.message}")
            return null
        }
    }

    private suspend fun fetchLatestSkuDataViaBackend(skuOuterId: String, itemOuterIdFallback: String? = null): SkuSyncData? {
        val userRepo = userRepository ?: return null
        val api = systemApiService ?: return null
        val token = userRepo.getToken()
        return try {
            val detail = api.getSkuDetail(token, skuOuterId)
            val title = if (detail.itemTitle.isNotBlank()) detail.itemTitle else return null
            val itemOuterId = if (detail.itemOuterId.isNotBlank()) detail.itemOuterId else itemOuterIdFallback ?: return null
            appendLog(applicationContext, "后端SKU查询成功: sku=$skuOuterId, title=$title")
            SkuSyncData(title = title, itemOuterId = itemOuterId, propertiesName = detail.propertiesName)
        } catch (e: Exception) {
            Log.w(TAG, "通过后端获取SKU数据失败: $skuOuterId — ${e.message}")
            appendLog(applicationContext, "后端SKU查询失败: sku=$skuOuterId, error=${e.message}")
            null
        }
    }

    private suspend fun syncImageUpload(op: PendingOperationEntity): Boolean {
        val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: run {
            appendLog(applicationContext, "imageUpload同步失败: payload缺少sku_outer_id")
            return false
        }
        val imageType = extractPayloadValue(op.payload, "image_type") ?: run {
            appendLog(applicationContext, "imageUpload同步失败: payload缺少image_type")
            return false
        }
        val filePath = extractPayloadValue(op.payload, "file_path") ?: run {
            appendLog(applicationContext, "imageUpload同步失败: payload缺少file_path")
            return false
        }
        val imageFile = File(filePath)
        if (!imageFile.exists()) {
            Log.w(TAG, "图片文件不存在，放弃同步: $filePath")
            appendLog(applicationContext, "imageUpload放弃同步: 图片文件不存在, path=$filePath")
            return true
        }
        val uploader = imageUploadService ?: run {
            appendLog(applicationContext, "imageUpload同步失败: imageUploadService为null")
            return false
        }
        val imageDao = productImageDao ?: run {
            appendLog(applicationContext, "imageUpload同步失败: productImageDao为null")
            return false
        }
        appendLog(applicationContext, "imageUpload开始上传: sku=$skuOuterId, type=$imageType")
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
            appendLog(applicationContext, "imageUpload上传成功: sku=$skuOuterId, remoteId=$remoteId")
            true
        } catch (e: Exception) {
            Log.w(TAG, "上传图片失败，保留文件以重试: ${e.message}")
            appendLog(applicationContext, "imageUpload上传异常: sku=$skuOuterId, error=${e.message}")
            false
        }
    }

    private fun extractPayloadValue(payload: String, key: String): String? {
        return try {
            val json = JSONObject(payload)
            if (json.has(key)) json.getString(key) else {
                Log.w(TAG, "提取payload失败: key=$key 不存在")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析payload失败: key=$key, error=${e.message}")
            null
        }
    }
}
