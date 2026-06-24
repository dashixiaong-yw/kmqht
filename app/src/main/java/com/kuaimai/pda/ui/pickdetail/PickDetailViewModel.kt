package com.kuaimai.pda.ui.pickdetail

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.dto.AddOrderItemRequest
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.data.repository.ImageRepository
import com.kuaimai.pda.data.repository.PickOrderRepository
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.scanner.ScanFeedbackType
import com.kuaimai.pda.scanner.ScannerManager
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.PrefsKeys
import com.kuaimai.pda.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Named

/**
 * 取货单详情ViewModel
 * 管理取货明细数据、扫码添加、完成/恢复、供应商过滤
 */
@HiltViewModel
class PickDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pickOrderRepository: PickOrderRepository,
    private val orderApiService: OrderApiService,
    val scannerManager: ScannerManager,
    private val imageRepository: ImageRepository,
    private val userRepository: UserRepository,
    @Named("encrypted") private val prefs: SharedPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "PickDetailVM"
    }

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
    private val _suppliers = MutableStateFlow<List<String>>(listOf(AppConstants.SUPPLIER_ALL_LABEL))
    val suppliers: StateFlow<List<String>> = _suppliers.asStateFlow()

    /** 当前供应商过滤 */
    private val _currentSupplier = MutableStateFlow(AppConstants.SUPPLIER_ALL_LABEL)
    val currentSupplier: StateFlow<String> = _currentSupplier.asStateFlow()

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

    /** 最近扫码的SKU编码（用于重复扫码时滚动定位） */
    var lastScannedSku: String = ""
        private set

    /** 扫码成功事件 */
    private val _scanSuccessEvent = MutableSharedFlow<Unit>()
    val scanSuccessEvent = _scanSuccessEvent.asSharedFlow()

    /** 扫码失败事件 */
    private val _scanFailureEvent = MutableSharedFlow<String>()
    val scanFailureEvent = _scanFailureEvent.asSharedFlow()

    init {
        loadOrder()
        loadSuppliers()
        viewModelScope.launch {
            syncItemsFromBackend()
        }
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
                val token = userRepository.getToken()
                val result = orderApiService.getSuppliers(token, orderId)
                _suppliers.value = listOf(AppConstants.SUPPLIER_ALL_LABEL) + result
            } catch (e: Exception) {
                Log.e("PickDetailViewModel", "加载供应商列表失败: ${e.message}", e)
                _errorMessage.value = "加载供应商列表失败: ${e.message?.take(80) ?: "未知错误"}"
                _suppliers.value = emptyList()
            }
        }
    }

    /**
     * 从本地已入库的明细中提取供应商列表（无网络依赖）
     */
    private fun loadSuppliersFromLocal() {
        try {
            val itemList = items.value
            val suppliers = itemList.map { it.supplierName }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            _suppliers.value = listOf(AppConstants.SUPPLIER_ALL_LABEL) + suppliers
        } catch (e: Exception) {
            Log.w(TAG, "从本地提取供应商列表失败: ${e.message}")
        }
    }

    /**
     * 扫码添加取货明细
     * @param barcode 扫描到的条码
     */
    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            lastScannedSku = barcode
            try {
                // 检查重复扫码（精确查询当前订单下的SKU）
                val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, barcode)
                if (existing != null) {
                    _duplicateScan.value = true
                    return@launch
                }

                val token = userRepository.getToken()
                val response = orderApiService.addItem(
                    token,
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
                    itemOuterId = response.itemOuterId,
                    createdAt = TimeUtils.parseBeijingTime(response.createdAt).let { if (it > 0) it else TimeUtils.now() }
                )
                pickOrderRepository.insertItem(item)
                val newSupplier = response.supplierName
                if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
                    _suppliers.value = _suppliers.value + newSupplier
                }
                loadOrder()
                _order.value = _order.value?.copy(totalCount = (_order.value?.totalCount ?: 0) + 1)
                _scanSuccessEvent.emit(Unit)
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 409) {
                    _errorMessage.value = null
                    syncItemsFromBackend()
                    _duplicateScan.value = true
                } else {
                    _errorMessage.value = "添加明细失败: ${e.message}"
                    _scanFailureEvent.emit("添加明细失败: ${e.message}")
                }
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
            _isLoading.value = true
            try {
                val token = userRepository.getToken()
                orderApiService.completeItem(token, orderId, itemId)
                pickOrderRepository.updateItemStatusDirect(itemId, 1, TimeUtils.now())
                val count = pickOrderRepository.getCompletedCount(orderId, 1)
                pickOrderRepository.updateCompletedCount(orderId, count)
                loadOrder()
            } catch (e: Exception) {
                pickOrderRepository.updateItemStatus(itemId, 1, TimeUtils.now())
                val count = pickOrderRepository.getCompletedCount(orderId, 1)
                pickOrderRepository.updateCompletedCount(orderId, count)
                _errorMessage.value = "完成明细失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 恢复取货明细
     * @param itemId 明细ID
     */
    fun restoreItem(itemId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = userRepository.getToken()
                orderApiService.restoreItem(token, orderId, itemId)
                pickOrderRepository.updateItemStatusDirect(itemId, 0, null)
                val count = pickOrderRepository.getCompletedCount(orderId, 1)
                pickOrderRepository.updateCompletedCount(orderId, count)
                loadOrder()
            } catch (e: Exception) {
                pickOrderRepository.updateItemStatus(itemId, 0, null)
                val count = pickOrderRepository.getCompletedCount(orderId, 1)
                pickOrderRepository.updateCompletedCount(orderId, count)
                _errorMessage.value = "恢复明细失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 批量完成所有明细
     */
    fun completeAllItems() {
        viewModelScope.launch {
            _isLoading.value = true
            val now = TimeUtils.now()
            try {
                val token = userRepository.getToken()
                orderApiService.completeAllItems(token, orderId)
                // 使用原子操作直接更新DB（避免并发竞态）
                pickOrderRepository.completeAllItemsDirect(orderId, now)
                loadOrder()
            } catch (e: Exception) {
                pickOrderRepository.enqueueCompleteAll(orderId, now)
                loadOrder()
                _errorMessage.value = "批量完成失败: ${e.message}"
            } finally {
                _isLoading.value = false
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
     * 下拉刷新（同步取货单信息+明细数据）
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val token = userRepository.getToken()
                val detail = orderApiService.getOrderDetail(token, orderId)
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
                    expireAt = TimeUtils.parseBeijingTime(detail.expireAt).let { if (it > 0) it else TimeUtils.now() + TimeUtils.DEFAULT_EXPIRE_MS },
                    createdBy = detail.createdBy,
                    assignedTo = detail.assignedTo,
                    visibility = detail.visibility
                )
                pickOrderRepository.updateOrder(orderEntity)

                // 同步明细数据：将后端明细upsert到本地数据库
                detail.items.forEach { itemResponse ->
                    val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, itemResponse.skuOuterId)
                    if (existing == null) {
                        // 新明细（其他PDA添加的），插入本地
                        val item = PickItemEntity(
                            id = itemResponse.id,
                            orderId = orderId,
                            skuOuterId = itemResponse.skuOuterId,
                            sysItemId = itemResponse.sysItemId,
                            sysSkuId = itemResponse.sysSkuId,
                            propertiesName = itemResponse.propertiesName,
                            picPath = itemResponse.picPath,
                            status = itemResponse.status,
                            supplierName = itemResponse.supplierName,
                            supplierCode = itemResponse.supplierCode,
                            remark = itemResponse.remark,
                            itemOuterId = itemResponse.itemOuterId,
                            createdAt = TimeUtils.parseBeijingTime(itemResponse.createdAt).let { if (it > 0) it else TimeUtils.now() },
                            completedAt = TimeUtils.parseBeijingTimeOrNull(itemResponse.completedAt)
                        )
                        pickOrderRepository.insertItem(item)
                    } else {
                        // 已有明细，同步快麦字段（仅不可变字段，防止覆盖用户修改）
                        pickOrderRepository.updateItemFieldsDirect(existing.id, itemResponse.propertiesName, itemResponse.picPath, itemResponse.itemOuterId)
                        if (existing.status != itemResponse.status) {
                            val completedAt = TimeUtils.parseBeijingTimeOrNull(itemResponse.completedAt)
                            pickOrderRepository.updateItemStatusDirect(existing.id, itemResponse.status, completedAt)
                        }
                    }
                }

                loadSuppliers()
            } catch (e: Exception) {
                _errorMessage.value = "刷新失败: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 删除取货明细（GAP-06）
     */
    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 在线模式：先API，成功后直接删除本地（不入队）
                val token = userRepository.getToken()
                orderApiService.deleteItem(token, orderId, itemId)
                pickOrderRepository.deleteItemDirect(itemId)
                loadSuppliers()
            } catch (e: Exception) {
                // API失败，使用乐观更新+入队（离线模式自动走此路径）
                pickOrderRepository.deleteItemWithQueue(itemId)
                _errorMessage.value = "删除失败: ${e.message}"
            } finally {
                _isLoading.value = false
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

    /**
     * 从后端同步最新明细到本地（不含UI加载状态）
     */
    private suspend fun syncItemsFromBackend() {
        try {
            val token = userRepository.getToken()
            val detail = orderApiService.getOrderDetail(token, orderId)

            // 同步order信息（_order是MutableStateFlow，需同时保存Room和设置StateFlow）
            val orderEntity = PickOrderEntity(
                id = detail.id,
                orderNo = detail.orderNo,
                status = detail.status,
                completionType = detail.completionType,
                totalCount = detail.totalCount,
                completedCount = detail.completedCount,
                createdAt = TimeUtils.parseBeijingTime(detail.createdAt).let { if (it > 0) it else TimeUtils.now() },
                completedAt = TimeUtils.parseBeijingTimeOrNull(detail.completedAt),
                expireAt = TimeUtils.parseBeijingTime(detail.expireAt).let { if (it > 0) it else TimeUtils.now() + TimeUtils.DEFAULT_EXPIRE_MS },
                createdBy = detail.createdBy,
                assignedTo = detail.assignedTo,
                visibility = detail.visibility
            )
            pickOrderRepository.insertOrder(orderEntity) // insertOrder (REPLACE) 兼容不存在和已存在
            _order.value = orderEntity // 直接设置StateFlow，让UI立即响应

            // 同步明细数据（items由Room Flow自动驱动，无需手动设置）
            detail.items.forEach { itemResponse ->
                val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, itemResponse.skuOuterId)
                if (existing == null) {
                    pickOrderRepository.insertItem(
                        PickItemEntity(
                            id = itemResponse.id,
                            orderId = orderId,
                            skuOuterId = itemResponse.skuOuterId,
                            sysItemId = itemResponse.sysItemId,
                            sysSkuId = itemResponse.sysSkuId,
                            propertiesName = itemResponse.propertiesName,
                            picPath = itemResponse.picPath,
                            status = itemResponse.status,
                            supplierName = itemResponse.supplierName,
                            supplierCode = itemResponse.supplierCode,
                            remark = itemResponse.remark,
                            itemOuterId = itemResponse.itemOuterId,
                            createdAt = TimeUtils.parseBeijingTime(itemResponse.createdAt).let { if (it > 0) it else TimeUtils.now() }
                        )
                    )
                } else {
                    pickOrderRepository.updateItemFieldsDirect(existing.id, itemResponse.propertiesName, itemResponse.picPath, itemResponse.itemOuterId)
                    val completedAt = TimeUtils.parseBeijingTimeOrNull(itemResponse.completedAt)
                    if (existing.status != itemResponse.status) {
                        pickOrderRepository.updateItemStatusDirect(existing.id, itemResponse.status, completedAt)
                    }
                }
            }
            loadSuppliers()
        } catch (e: Exception) {
            Log.w(TAG, "syncItemsFromBackend失败: ${e.message}")
        }
    }

    /**
     * 触发扫码反馈（振动+声音）
     * @param context 上下文
     * @param type 反馈类型
     */
    fun provideFeedback(context: android.content.Context, type: ScanFeedbackType) {
        scannerManager.provideFeedback(context, type)
    }

    /**
     * 获取SKU的库区图和装箱图URL（含缩略图URL）
     * @param skuOuterId SKU外部编码
     * @return ImageUrls(完整URL + 缩略图URL)
     */
    suspend fun getImageUrls(skuOuterId: String): ImageUrls {
        return try {
            val areaImage = imageRepository.getImageBySkuAndType(skuOuterId, AppConstants.IMAGE_TYPE_AREA)
            val boxImage = imageRepository.getImageBySkuAndType(skuOuterId, AppConstants.IMAGE_TYPE_BOX)
            val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, AppConstants.DEFAULT_SERVER_URL)?.trim() ?: AppConstants.DEFAULT_SERVER_URL
            val areaUrl = areaImage?.let { url -> if (serverUrl.isNotEmpty()) "${serverUrl.trimEnd('/')}/${url.imageUrl}" else url.imageUrl }
            val boxUrl = boxImage?.let { url -> if (serverUrl.isNotEmpty()) "${serverUrl.trimEnd('/')}/${url.imageUrl}" else url.imageUrl }
            ImageUrls(
                areaUrl = areaUrl,
                boxUrl = boxUrl,
                areaThumbUrl = buildThumbUrl(areaUrl),
                boxThumbUrl = buildThumbUrl(boxUrl)
            )
        } catch (e: Exception) {
            Log.w("PickDetailViewModel", "获取图片URL失败: ${e.message}")
            ImageUrls(null, null, null, null)
        }
    }

    /** 从完整URL构造缩略图URL（在扩展名前插入 _thumb） */
    private fun buildThumbUrl(fullUrl: String?): String? {
        if (fullUrl == null) return null
        val dot = fullUrl.lastIndexOf('.')
        return if (dot > 0) "${fullUrl.substring(0, dot)}_thumb${fullUrl.substring(dot)}" else fullUrl
    }
}

/** SKU图片URL集合（完整URL + 缩略图URL） */
data class ImageUrls(
    val areaUrl: String?,
    val boxUrl: String?,
    val areaThumbUrl: String?,
    val boxThumbUrl: String?
)
