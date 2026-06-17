package com.kuaimai.pda.data.repository

import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.PickOrderDao
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.data.db.entity.PendingOperationEntity
import com.kuaimai.pda.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 取货单仓库接口
 */
interface PickOrderRepository {
    fun getAllOrders(): Flow<List<PickOrderEntity>>
    fun getOrdersByStatus(status: Int): Flow<List<PickOrderEntity>>
    fun getCompletedOrders(sevenDaysAgo: Long): Flow<List<PickOrderEntity>>
    suspend fun getOrderById(id: Long): PickOrderEntity?
    suspend fun insertOrder(order: PickOrderEntity): Long
    suspend fun updateOrder(order: PickOrderEntity)
    suspend fun updateOrderStatus(id: Long, status: Int, completedAt: Long? = null)
    suspend fun updateCompletedCount(id: Long, count: Int)
    fun getItemsByOrderId(orderId: Long): Flow<List<PickItemEntity>>
    fun getItemsByOrderIdAndStatus(orderId: Long, status: Int): Flow<List<PickItemEntity>>
    suspend fun getItemBySkuOuterId(skuOuterId: String): PickItemEntity?
    suspend fun getItemByOrderIdAndSkuOuterId(orderId: Long, skuOuterId: String): PickItemEntity?
    suspend fun insertItem(item: PickItemEntity): Long
    suspend fun updateItemStatus(id: Long, status: Int, completedAt: Long? = null)
    /** 直接更新明细状态（不入队，用于在线模式API成功后） */
    suspend fun updateItemStatusDirect(id: Long, status: Int, completedAt: Long? = null)
    suspend fun deleteOrder(order: PickOrderEntity)
    /** 获取冲突操作列表 */
    suspend fun getConflicts(): List<PendingOperationEntity>
    /** 更新备注（乐观更新本地+写入离线队列） */
    suspend fun updateRemarkWithQueue(id: Long, remark: String)
    /** 更新供应商（乐观更新本地+写入离线队列） */
    suspend fun updateSupplierWithQueue(id: Long, supplierName: String, supplierCode: String)
    /** 删除取货明细（乐观更新本地+写入离线队列） */
    suspend fun deleteItemWithQueue(id: Long)
    /** 直接删除取货明细（不入队，用于在线模式API成功后） */
    suspend fun deleteItemDirect(id: Long)
    /** 图片上传入队（离线模式下图片上传失败时调用） */
    suspend fun enqueueUploadImage(skuOuterId: String, payload: String)
}

/**
 * 取货单仓库实现
 * 在线：调用API → 更新本地缓存
 * 离线：写入pending_operations + 更新本地缓存（乐观UI）
 */
class PickOrderRepositoryImpl @Inject constructor(
    private val pickOrderDao: PickOrderDao,
    private val pickItemDao: PickItemDao,
    private val pendingOperationDao: PendingOperationDao
) : PickOrderRepository {

    override fun getAllOrders(): Flow<List<PickOrderEntity>> {
        return pickOrderDao.getAllOrders()
    }

    override fun getOrdersByStatus(status: Int): Flow<List<PickOrderEntity>> {
        return pickOrderDao.getByStatus(status)
    }

    override fun getCompletedOrders(sevenDaysAgo: Long): Flow<List<PickOrderEntity>> {
        return pickOrderDao.getCompletedOrders(sevenDaysAgo)
    }

    override suspend fun getOrderById(id: Long): PickOrderEntity? {
        return pickOrderDao.getById(id)
    }

    override suspend fun insertOrder(order: PickOrderEntity): Long {
        return pickOrderDao.insert(order)
    }

    override suspend fun updateOrder(order: PickOrderEntity) {
        pickOrderDao.update(order)
    }

    override suspend fun updateOrderStatus(id: Long, status: Int, completedAt: Long?) {
        pickOrderDao.updateStatus(id, status, completedAt)
    }

    override suspend fun updateCompletedCount(id: Long, count: Int) {
        pickOrderDao.updateCompletedCount(id, count)
    }

    override fun getItemsByOrderId(orderId: Long): Flow<List<PickItemEntity>> {
        return pickItemDao.getByOrderId(orderId)
    }

    override fun getItemsByOrderIdAndStatus(orderId: Long, status: Int): Flow<List<PickItemEntity>> {
        return pickItemDao.getByOrderIdAndStatus(orderId, status)
    }

    override suspend fun getItemBySkuOuterId(skuOuterId: String): PickItemEntity? {
        return pickItemDao.getBySkuOuterId(skuOuterId)
    }

    override suspend fun getItemByOrderIdAndSkuOuterId(orderId: Long, skuOuterId: String): PickItemEntity? {
        return pickItemDao.getByOrderIdAndSkuOuterId(orderId, skuOuterId)
    }

    override suspend fun insertItem(item: PickItemEntity): Long {
        return pickItemDao.insert(item)
    }

    override suspend fun updateItemStatus(id: Long, status: Int, completedAt: Long?) {
        // 乐观更新本地
        pickItemDao.updateStatus(id, status, completedAt)
        // 写入离线队列
        val item = pickItemDao.getById(id)
        if (item != null) {
            val operationType = if (status == 1) "complete_item" else "restore_item"
            enqueueOperation(operationType, item.orderId, id)
        }
    }

    override suspend fun updateItemStatusDirect(id: Long, status: Int, completedAt: Long?) {
        // 直接更新本地数据库（不入队，用于在线模式API成功后）
        pickItemDao.updateStatus(id, status, completedAt)
    }

    override suspend fun getConflicts(): List<PendingOperationEntity> {
        return pendingOperationDao.getConflicts()
    }

    override suspend fun updateRemarkWithQueue(id: Long, remark: String) {
        // 乐观更新本地
        pickItemDao.updateRemark(id, remark)
        // 写入离线队列
        val item = pickItemDao.getById(id)
        if (item != null) {
            enqueueOperation(
                operationType = "update_remark",
                orderId = item.orderId,
                targetId = id,
                payload = """{"remark":"${TimeUtils.escapeJson(remark)}","sys_sku_id":${item.sysSkuId},"sys_item_id":${item.sysItemId}}"""
            )
        }
    }

    override suspend fun updateSupplierWithQueue(id: Long, supplierName: String, supplierCode: String) {
        // 乐观更新本地
        pickItemDao.updateSupplier(id, supplierName, supplierCode)
        // 写入离线队列
        val item = pickItemDao.getById(id)
        if (item != null) {
            enqueueOperation(
                operationType = "update_supplier",
                orderId = item.orderId,
                targetId = id,
                payload = """{"supplier_name":"${TimeUtils.escapeJson(supplierName)}","supplier_code":"${TimeUtils.escapeJson(supplierCode)}","sys_item_id":${item.sysItemId}}"""
            )
        }
    }

    override suspend fun deleteItemWithQueue(id: Long) {
        val item = pickItemDao.getById(id)
        if (item != null) {
            // 乐观更新本地
            pickItemDao.deleteById(id)
            // 写入离线队列
            enqueueOperation(
                operationType = "delete_item",
                orderId = item.orderId,
                targetId = id
            )
        }
    }

    override suspend fun deleteItemDirect(id: Long) {
        // 直接删除本地记录（不入队，用于在线模式API成功后）
        pickItemDao.deleteById(id)
    }

    override suspend fun enqueueUploadImage(skuOuterId: String, payload: String) {
        enqueueOperation(
            operationType = "upload_image",
            orderId = 0L,
            targetId = 0L,
            payload = payload
        )
    }

    override suspend fun deleteOrder(order: PickOrderEntity) {
        pickOrderDao.delete(order)
    }

    /**
     * 写入离线操作队列
     */
    private suspend fun enqueueOperation(
        operationType: String,
        orderId: Long,
        targetId: Long,
        payload: String = "{}"
    ) {
        val operation = PendingOperationEntity(
            operationType = operationType,
            orderId = orderId,
            targetId = targetId,
            payload = payload,
            createdAt = TimeUtils.now(),
            retryCount = 0
        )
        pendingOperationDao.insert(operation)
    }
}
