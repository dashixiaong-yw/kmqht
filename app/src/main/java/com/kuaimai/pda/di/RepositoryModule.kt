package com.kuaimai.pda.di

import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.AuthRepositoryImpl
import com.kuaimai.pda.data.repository.ImageRepository
import com.kuaimai.pda.data.repository.ImageRepositoryImpl
import com.kuaimai.pda.data.repository.PickOrderRepository
import com.kuaimai.pda.data.repository.PickOrderRepositoryImpl
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.data.repository.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository绑定模块：接口→实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPickOrderRepository(impl: PickOrderRepositoryImpl): PickOrderRepository

    @Binds
    @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
