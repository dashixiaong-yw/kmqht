package com.kuaimai.pda.data.repository

import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.dto.ItemListResponse
import com.kuaimai.pda.data.api.dto.SkuListResponse
import com.kuaimai.pda.data.api.dto.SupplierListResponse
import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.PickOrderDao
import com.kuaimai.pda.data.db.dao.ProductImageDao
import javax.inject.Inject

/**
 * 商品数据仓库接口
 */
interface ItemRepository {
    suspend fun queryItemList(params: Map<String, String>): ItemListResponse
    suspend fun getSkuList(params: Map<String, String>): SkuListResponse
    suspend fun getSupplierList(params: Map<String, String>): SupplierListResponse
    suspend fun querySupplierList(params: Map<String, String>): SupplierListResponse
}

/**
 * 商品数据仓库实现
 */
class ItemRepositoryImpl @Inject constructor(
    private val apiService: KuaimaiApiService,
    private val pickOrderDao: PickOrderDao,
    private val pickItemDao: PickItemDao,
    private val productImageDao: ProductImageDao
) : ItemRepository {

    override suspend fun queryItemList(params: Map<String, String>): ItemListResponse {
        return apiService.queryItemList(params)
    }

    override suspend fun getSkuList(params: Map<String, String>): SkuListResponse {
        return apiService.getSkuList(params)
    }

    override suspend fun getSupplierList(params: Map<String, String>): SupplierListResponse {
        return apiService.getSupplierList(params)
    }

    override suspend fun querySupplierList(params: Map<String, String>): SupplierListResponse {
        return apiService.querySupplierList(params)
    }
}
