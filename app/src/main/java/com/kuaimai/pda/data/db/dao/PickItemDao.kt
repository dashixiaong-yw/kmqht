package com.kuaimai.pda.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kuaimai.pda.data.db.entity.PickItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 取货明细DAO
 */
@Dao
interface PickItemDao {

    /**
     * 根据ID获取明细（挂起版本）
     */
    @Query("SELECT * FROM pick_item WHERE id = :id")
    suspend fun getById(id: Long): PickItemEntity?

    /**
     * 获取取货单下的明细（按ID排序）
     */
    @Query("SELECT * FROM pick_item WHERE order_id = :orderId ORDER BY id ASC")
    fun getByOrderId(orderId: Long): Flow<List<PickItemEntity>>

    /**
     * 获取取货单下指定状态的明细
     */
    @Query("SELECT * FROM pick_item WHERE order_id = :orderId AND status = :status ORDER BY id ASC")
    fun getByOrderIdAndStatus(orderId: Long, status: Int): Flow<List<PickItemEntity>>

    /**
     * 根据SKU外部编码获取明细（全局查询，可能返回其他订单的记录）
     */
    @Query("SELECT * FROM pick_item WHERE sku_outer_id = :skuOuterId LIMIT 1")
    suspend fun getBySkuOuterId(skuOuterId: String): PickItemEntity?

    /**
     * 根据订单ID和SKU外部编码获取明细（精确查询当前订单）
     */
    @Query("SELECT * FROM pick_item WHERE order_id = :orderId AND sku_outer_id = :skuOuterId LIMIT 1")
    suspend fun getByOrderIdAndSkuOuterId(orderId: Long, skuOuterId: String): PickItemEntity?

    /**
     * 插入明细
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PickItemEntity): Long

    /**
     * 批量插入明细（事务）
     */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PickItemEntity>)

    /**
     * 更新明细
     */
    @Update
    suspend fun update(item: PickItemEntity)

    /**
     * 更新明细状态
     */
    @Query("UPDATE pick_item SET status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, completedAt: Long? = null)

    /**
     * 更新备注
     */
    @Query("UPDATE pick_item SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: Long, remark: String)

    /**
     * 更新供应商
     */
    @Query("UPDATE pick_item SET supplier_name = :supplierName, supplier_code = :supplierCode WHERE id = :id")
    suspend fun updateSupplier(id: Long, supplierName: String, supplierCode: String)

    /**
     * 删除取货单下的所有明细
     */
    @Query("DELETE FROM pick_item WHERE order_id = :orderId")
    suspend fun deleteByOrderId(orderId: Long)

    /**
     * 删除单条明细
     */
    @Query("DELETE FROM pick_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有明细
     */
    @Query("DELETE FROM pick_item")
    suspend fun deleteAll()
}
