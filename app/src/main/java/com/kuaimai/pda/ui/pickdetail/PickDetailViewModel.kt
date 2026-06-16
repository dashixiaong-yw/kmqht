package com.kuaimai.pda.ui.pickdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.dto.AddOrderItemRequest
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.data.repository.PickOrderRepository
import com.kuaimai.pda.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 取货单详情ViewModel
 * 管理取货明细数据、扫码添加、完成/恢复、供应商过滤
 */
@HiltViewModel
class PickDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pickOrderRepository: PickOrderRepository,
    private val orderApiService: OrderApiService
) : ViewModel() {

    /** 取货单ID */
    val orderId: Long = savedStateHandle["orderId"] ?: 0L

    /** 取货单信息 */
    private val _order = MutableStateFlow<PickOrderEntity?>(null)
    val order: StateFlow<PickOrderEntity?> = _order.asStateFlow()

    /** 取货明细列表 */
    val items: StateFlow<List<PickItemEntity>> =
        pickOrderRepository.getItemsByOrderId(orderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 供应商列表（从明细中提取） */
    private val _suppliers = MutableStateFlow<List<String>>(emptyList())
    val suppliers: StateFlow<List<String>> = _suppliers.asStateFlow()

    /** 当前供应商过滤 */
    private val _currentSupplier = MutableStateFlow("全部")
    val currentSupplier: StateFlow<String> = _currentSupplier.asStateFlow()

    /** 连续扫码模式 */
    private val _continuousScanMode = MutableStateFlow(false)
    val continuousScanMode: StateFlow<Boolean> = _continuousScanMode.asStateFlow()

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 刷新状态 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 错误消息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 重复扫码提示 */
    private val _duplicateScan = MutableStateFlow(false)
    val duplicateScan: StateFlow<Boolean> = _duplicateScan.asStateFlow()

    init {
        loadOrder()
        loadSuppliers()
    }

    /**
     * 加载取货单信息
     */
    private fun loadOrder() {
        viewModelScope.launch {
            val order = pickOrderRepository.getOrderById(orderId)
            _order.value = order
        }
    }

    /**
     * 加载供应商列表
     */
    private fun loadSuppliers() {
        viewModelScope.launch {
            try {
                val result = orderApiService.getSuppliers(orderId)
                _suppliers.value = listOf("全部") + result
            } catch (e: Exception) {
                _suppliers.value = listOf("全部")
            }
        }
    }

    /**
     * 扫码添加取货明细
     * @param barcode 扫描到的条码
     */
    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 检查重复扫码（精确查询当前订单下的SKU）
                val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, barcode)
                if (existing != null) {
                    _duplicateScan.value = true
                    return@launch
                }

                val response = orderApiService.addItem(
                    orderId,
                    AddOrderItemRequest(barcode)
                )
                // 同步到本地数据库
                val item = PickItemEntity(
                    id = response.id,
                    orderId = orderId,
                    skuOuterId = response.skuOuterId,
                    sysItemId = response.sysItemId,
                    sysSkuId = response.sysSkuId,
                    propertiesName = response.propertiesName,
                    picPath = response.picPath,
                    status = response.status,
                    supplierName = response.supplierName,
                    supplierCode = response.supplierCode,
                    remark = response.remark,
                    createdAt = TimeUtils.parseBeijingTime(response.createdAt).let { if (it > 0) it else TimeUtils.now() }
                )
                pickOrderRepository.insertItem(item)
                loadSuppliers()
            } catch (e: Exception) {
                _errorMessage.value = "添加明细失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 完成取货明细
     * @param itemId 明细ID
     */
    fun completeItem(itemId: Long) {
        viewModelScope.launch {
            try {
                orderApiService.completeItem(orderId, itemId)
                pickOrderRepository.updateItemStatus(itemId, 1, TimeUtils.now())
            } catch (e: Exception) {
                _errorMessage.value = "完成明细失败: ${e.message}"
            }
        }
    }

    /**
     * 恢复取货明细
     * @param itemId 明细ID
     */
    fun restoreItem(itemId: Long) {
        viewModelScope.launch {
            try {
                orderApiService.restoreItem(orderId, itemId)
                pickOrderRepository.updateItemStatus(itemId, 0, null)
            } catch (e: Exception) {
                _errorMessage.value = "恢复明细失败: ${e.message}"
            }
        }
    }

    /**
     * 批量完成所有明细
     */
    fun completeAllItems() {
        viewModelScope.launch {
            try {
                orderApiService.completeAllItems(orderId)
                // 更新本地所有未完成明细状态
                val currentItems = items.value
                val now = TimeUtils.now()
                currentItems.filter { it.status == 0 }.forEach { item ->
                    pickOrderRepository.updateItemStatus(item.id, 1, now)
                }
                pickOrderRepository.updateOrderStatus(orderId, 1, now)
                loadOrder()
            } catch (e: Exception) {
                _errorMessage.value = "批量完成失败: ${e.message}"
            }
        }
    }

    /**
     * 设置供应商过滤
     * @param supplier 供应商名称
     */
    fun setSupplierFilter(supplier: String) {
        _currentSupplier.value = supplier
    }

    /**
     * 切换连续扫码模式
     */
    fun toggleContinuousScanMode() {
        _continuousScanMode.value = !_continuousScanMode.value
    }

    /**
     * 下拉刷新
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val detail = orderApiService.getOrderDetail(orderId)
                // 更新本地取货单信息（使用后端返回的真实时间）
                val orderEntity = PickOrderEntity(
                    id = detail.id,
                    orderNo = detail.orderNo,
                    status = detail.status,
                    completionType = detail.completionType,
                    totalCount = detail.totalCount,
                    completedCount = detail.completedCount,
                    createdAt = TimeUtils.parseBeijingTime(detail.createdAt).let { if (it > 0) it else TimeUtils.now() },
                    completedAt = TimeUtils.parseBeijingTimeOrNull(detail.completedAt),
                    expireAt = TimeUtils.parseBeijingTime(detail.expireAt).let { if (it > 0) it else TimeUtils.now() + 12 * 60 * 60 * 1000L }
                )
                pickOrderRepository.updateOrder(orderEntity)
                loadSuppliers()
            } catch (e: Exception) {
                _errorMessage.value = "刷新失败: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 清除重复扫码提示
     */
    fun clearDuplicateScan() {
        _duplicateScan.value = false
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
