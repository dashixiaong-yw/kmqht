package com.kuaimai.pda.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE product_image ADD COLUMN remote_id INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pick_order ADD COLUMN created_by TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE pick_order ADD COLUMN assigned_to TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE pick_order ADD COLUMN visibility TEXT NOT NULL DEFAULT 'private'")
    }
}

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    /** 取货单DAO */
    @Provides @Singleton
    fun providePickOrderDao(db: AppDatabase): PickOrderDao {
        return db.pickOrderDao()
    }

    /** 取货明细DAO */
    @Provides @Singleton
    fun providePickItemDao(db: AppDatabase): PickItemDao {
        return db.pickItemDao()
    }

    /** 商品图片DAO */
    @Provides @Singleton
    fun provideProductImageDao(db: AppDatabase): ProductImageDao {
        return db.productImageDao()
    }

    /** 待操作队列DAO */
    @Provides @Singleton
    fun providePendingOperationDao(db: AppDatabase): PendingOperationDao {
        return db.pendingOperationDao()
    }
}
