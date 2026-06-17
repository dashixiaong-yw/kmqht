package com.kuaimai.pda.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import kotlinx.coroutines.flow.Flow

/**
 * 取货单DAO
 */
@Dao
interface PickOrderDao {

    /**
     * 获取所有取货单，按创建时间倒序
     */
    @Query("SELECT * FROM pick_order ORDER BY created_at DESC")
    fun getAllOrders(): Flow<List<PickOrderEntity>>

    /**
     * 获取已完成的取货单（7天内），按完成时间倒序
     */
    @Query("SELECT * FROM pick_order WHERE status = 1 AND completed_at >= :sevenDaysAgo ORDER BY completed_at DESC")
    fun getCompletedOrders(sevenDaysAgo: Long): Flow<List<PickOrderEntity>>

    /**
     * 根据ID获取取货单（挂起版本）
     */
    @Query("SELECT * FROM pick_order WHERE id = :id")
    suspend fun getById(id: Long): PickOrderEntity?

    /**
     * 根据单号获取取货单
     */
    @Query("SELECT * FROM pick_order WHERE order_no = :orderNo")
    suspend fun getByOrderNo(orderNo: String): PickOrderEntity?

    /**
     * 根据状态获取取货单
     */
    @Query("SELECT * FROM pick_order WHERE status = :status ORDER BY created_at DESC")
    fun getByStatus(status: Int): Flow<List<PickOrderEntity>>

    /**
     * 插入取货单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: PickOrderEntity): Long

    /**
     * 批量插入取货单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<PickOrderEntity>)

    /**
     * 更新取货单
     */
    @Update
    suspend fun update(order: PickOrderEntity)

    /**
     * 删除取货单
     */
    @Delete
    suspend fun delete(order: PickOrderEntity)

    /**
     * 更新取货单状态
     */
    @Query("UPDATE pick_order SET status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, completedAt: Long? = null)

    /**
     * 更新完成数量
     */
    @Query("UPDATE pick_order SET completed_count = :count WHERE id = :id")
    suspend fun updateCompletedCount(id: Long, count: Int)

    /**
     * 根据ID删除取货单
     */
    @Query("DELETE FROM pick_order WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有取货单
     */
    @Query("DELETE FROM pick_order")
    suspend fun deleteAll()
}
