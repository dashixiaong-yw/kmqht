package com.kuaimai.pda.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.PickOrderDao
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.dao.ProductImageDao
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.data.db.entity.PendingOperationEntity
import com.kuaimai.pda.data.db.entity.ProductImageEntity

/**
 * 快麦取货通 Room 数据库
 * version = 4，禁止 fallbackToDestructiveMigration
 */
@Database(
    entities = [
        PickOrderEntity::class,
        PickItemEntity::class,
        ProductImageEntity::class,
        PendingOperationEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pickOrderDao(): PickOrderDao
    abstract fun pickItemDao(): PickItemDao
    abstract fun productImageDao(): ProductImageDao
    abstract fun pendingOperationDao(): PendingOperationDao
}
