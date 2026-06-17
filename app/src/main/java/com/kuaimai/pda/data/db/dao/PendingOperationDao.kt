package com.kuaimai.pda.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kuaimai.pda.data.db.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 待操作队列DAO（离线操作缓存）
 */
@Dao
interface PendingOperationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity): Long

    @Query("SELECT * FROM pending_operation ORDER BY created_at ASC")
    fun getAll(): Flow<List<PendingOperationEntity>>

    /** 获取所有待同步操作（非Flow，用于Worker） */
    @Query("SELECT * FROM pending_operation ORDER BY created_at ASC")
    suspend fun getAllPending(): List<PendingOperationEntity>

    /** 获取指定订单的待同步操作 */
    @Query("SELECT * FROM pending_operation WHERE order_id = :orderId ORDER BY created_at ASC")
    suspend fun getPendingByOrder(orderId: Long): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operation WHERE operation_type = :type ORDER BY created_at ASC")
    fun getByType(type: String): Flow<List<PendingOperationEntity>>

    @Query("UPDATE pending_operation SET retry_count = :retryCount WHERE id = :id")
    suspend fun updateRetryCount(id: Long, retryCount: Int)

    @Query("DELETE FROM pending_operation WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_operation")
    suspend fun deleteAll()

    /** 获取冲突操作（retryCount = -1） */
    @Query("SELECT * FROM pending_operation WHERE retry_count = -1 ORDER BY created_at ASC")
    suspend fun getConflicts(): List<PendingOperationEntity>

    /** 按ID查询操作记录 */
    @Query("SELECT * FROM pending_operation WHERE id = :id")
    suspend fun getById(id: Long): PendingOperationEntity?
}
