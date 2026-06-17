package com.kuaimai.pda.di

import android.content.Context
import androidx.room.Room
import com.kuaimai.pda.data.db.AppDatabase
import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.PickOrderDao
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.dao.ProductImageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库层依赖注入：Room Database + DAO
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Room数据库实例，禁止fallbackToDestructiveMigration */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "kuaimai_pda.db"
        )
            // 后续版本升级时在此添加 .addMigrations(MIGRATION_X_Y)
            .build()
    }

    /** 取货单DAO */
    @Provides
    fun providePickOrderDao(db: AppDatabase): PickOrderDao {
        return db.pickOrderDao()
    }

    /** 取货明细DAO */
    @Provides
    fun providePickItemDao(db: AppDatabase): PickItemDao {
        return db.pickItemDao()
    }

    /** 商品图片DAO */
    @Provides
    fun provideProductImageDao(db: AppDatabase): ProductImageDao {
        return db.productImageDao()
    }

    /** 待操作队列DAO */
    @Provides
    fun providePendingOperationDao(db: AppDatabase): PendingOperationDao {
        return db.pendingOperationDao()
    }
}
