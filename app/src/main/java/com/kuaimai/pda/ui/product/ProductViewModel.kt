package com.kuaimai.pda.ui.product

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.dto.SupplierDto
import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.ProductImageDao
import com.kuaimai.pda.data.db.entity.PickItemEntity
import com.kuaimai.pda.data.db.entity.ProductImageEntity
import com.kuaimai.pda.data.repository.ImageRepository
import com.kuaimai.pda.data.repository.PickOrderRepository
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.PrefsKeys
import com.kuaimai.pda.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 商品详情UI状态
 */
data class ProductUiState(
    val isLoading: Boolean = false,
    val skuOuterId: String = "",
    val propertiesName: String = "",
    val picPath: String = "",
    val supplierName: String = "",
    val supplierCode: String = "",
    val remark: String = "",
    val areaImageUrl: String? = null,
    val boxImageUrl: String? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0,
    val error: String? = null,
    val isSavingRemark: Boolean = false,
    val isSavingSupplier: Boolean = false,
    val showSupplierDialog: Boolean = false,
    val showConfirmDialog: ConfirmType? = null,
    val scanInput: String = ""
)

/** 确认对话框类型 */
sealed class ConfirmType {
    data class Remark(val remark: String) : ConfirmType()
    data class Supplier(val name: String, val code: String) : ConfirmType()
}

/**
 * 商品详情ViewModel
 * 管理SKU信息加载、扫码切换、备注编辑、供应商切换、图片上传
 */
@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pickItemDao: PickItemDao,
    private val productImageDao: ProductImageDao,
    private val pickOrderRepository: PickOrderRepository,
    private val imageRepository: ImageRepository,
    private val apiService: KuaimaiApiService,
    private val prefs: SharedPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val DEFAULT_SERVER_URL = AppConstants.DEFAULT_SERVER_URL
    }

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    private val _suppliers = MutableStateFlow<List<SupplierDto>>(emptyList())
    val suppliers: StateFlow<List<SupplierDto>> = _suppliers.asStateFlow()

    /** 当前SKU对应的PickItem（可能为null，如果不在取货单中） */
    private var currentItem: PickItemEntity? = null

    /** 当前取货单ID（从导航参数获取，用于精确查询当前订单下的SKU） */
    private var currentOrderId: Long = 0L

    /** 图片加载协程Job，用于取消旧的Flow收集 */
    private var imagesJob: Job? = null

    init {
        val skuOuterId: String = savedStateHandle["skuOuterId"] ?: ""
        val orderId: Long = savedStateHandle["orderId"] ?: 0L
        currentOrderId = orderId
        if (skuOuterId.isNotEmpty()) {
            loadSkuInfo(skuOuterId)
        }
    }

    /**
     * 加载SKU信息
     * @param skuOuterId SKU外部编码
     */
    fun loadSkuInfo(skuOuterId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // 先从本地数据库查询（优先按当前订单精确查询）
                val item = if (currentOrderId > 0) {
                    pickItemDao.getByOrderIdAndSkuOuterId(currentOrderId, skuOuterId)
                } else {
                    pickItemDao.getBySkuOuterId(skuOuterId)
                }
                if (item != null) {
                    currentItem = item
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        skuOuterId = skuOuterId,
                        propertiesName = item.propertiesName,
                        picPath = item.picPath,
                        supplierName = item.supplierName,
                        supplierCode = item.supplierCode,
                        remark = item.remark
                    )
                } else {
                    // 本地无数据，仅设置ID等待扫码
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        skuOuterId = skuOuterId
                    )
                }
                // 加载图片
                loadImages(skuOuterId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载SKU信息失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载SKU图片（启动新协程收集，取消旧的避免泄漏）
     */
    private fun loadImages(skuOuterId: String) {
        imagesJob?.cancel()
        imagesJob = viewModelScope.launch {
            try {
                // 从后端同步图片（多PDA数据共享）
                imageRepository.syncImagesFromBackend(skuOuterId)
            } catch (e: Exception) {
                Log.w("ProductViewModel", "同步后端图片失败: ${e.message}")
            }
            try {
                val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
                productImageDao.getBySkuOuterId(skuOuterId).collectLatest { images ->
                    val areaImage = images.find { it.imageType == "area" }
                    val boxImage = images.find { it.imageType == "box" }
                    _uiState.value = _uiState.value.copy(
                        areaImageUrl = areaImage?.let { "$serverUrl${it.imageUrl}" },
                        boxImageUrl = boxImage?.let { "$serverUrl${it.imageUrl}" }
                    )
                }
            } catch (e: Exception) {
                Log.w("ProductViewModel", "加载SKU图片失败: ${e.message}")
            }
        }
    }

    /**
     * 扫码切换SKU
     * @param barcode 扫描到的条码
     */
    fun onScanBarcode(barcode: String) {
        if (barcode.isBlank()) return
        _uiState.value = _uiState.value.copy(scanInput = barcode)
        loadSkuInfo(barcode)
    }

    /**
     * 更新扫码输入框
     */
    fun updateScanInput(input: String) {
        _uiState.value = _uiState.value.copy(scanInput = input)
    }

    /**
     * 确认扫码输入
     */
    fun confirmScanInput() {
        val input = _uiState.value.scanInput.trim()
        if (input.isNotEmpty()) {
            loadSkuInfo(input)
        }
    }

    /**
     * 更新备注
     */
    fun updateRemark(remark: String) {
        _uiState.value = _uiState.value.copy(remark = remark)
    }

    /**
     * 请求保存备注（弹出确认对话框）
     */
    fun requestSaveRemark() {
        val remark = _uiState.value.remark
        _uiState.value = _uiState.value.copy(
            showConfirmDialog = ConfirmType.Remark(remark)
        )
    }

    /**
     * 确认保存备注
     */
    fun confirmSaveRemark() {
        val state = _uiState.value
        val confirmType = state.showConfirmDialog as? ConfirmType.Remark ?: return
        _uiState.value = state.copy(isSavingRemark = true, showConfirmDialog = null)

        viewModelScope.launch {
            try {
                val item = currentItem
                if (item != null) {
                    // 通过Repository更新本地+写入离线队列
                    pickOrderRepository.updateRemarkWithQueue(item.id, confirmType.remark)
                }
                _uiState.value = _uiState.value.copy(isSavingRemark = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingRemark = false,
                    error = "保存备注失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 显示供应商选择对话框
     */
    fun showSupplierDialog() {
        _uiState.value = _uiState.value.copy(showSupplierDialog = true)
        loadSuppliers()
    }

    /**
     * 隐藏供应商选择对话框
     */
    fun hideSupplierDialog() {
        _uiState.value = _uiState.value.copy(showSupplierDialog = false)
    }

    /**
     * 加载供应商列表
     */
    private fun loadSuppliers() {
        viewModelScope.launch {
            try {
                val result = apiService.querySupplierList(mapOf("method" to "supplier.list.query"))
                _suppliers.value = result.suppliers
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "加载供应商列表失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 选择供应商（弹出确认对话框）
     */
    fun selectSupplier(supplier: SupplierDto) {
        _uiState.value = _uiState.value.copy(
            showSupplierDialog = false,
            showConfirmDialog = ConfirmType.Supplier(supplier.supplierName, supplier.supplierCode)
        )
    }

    /**
     * 确认切换供应商
     */
    fun confirmChangeSupplier() {
        val state = _uiState.value
        val confirmType = state.showConfirmDialog as? ConfirmType.Supplier ?: return
        _uiState.value = state.copy(isSavingSupplier = true, showConfirmDialog = null)

        viewModelScope.launch {
            try {
                val item = currentItem
                if (item != null) {
                    // 通过Repository更新本地+写入离线队列
                    pickOrderRepository.updateSupplierWithQueue(item.id, confirmType.name, confirmType.code)
                }
                _uiState.value = _uiState.value.copy(
                    isSavingSupplier = false,
                    supplierName = confirmType.name,
                    supplierCode = confirmType.code
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingSupplier = false,
                    error = "切换供应商失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 取消确认对话框
     */
    fun dismissConfirmDialog() {
        _uiState.value = _uiState.value.copy(showConfirmDialog = null)
    }

    /**
     * 上传图片
     * @param imageFile 图片文件
     * @param imageType 图片类型 area/box
     */
    fun uploadImage(imageFile: File, imageType: String) {
        val skuOuterId = _uiState.value.skuOuterId
        if (skuOuterId.isBlank()) return

        _uiState.value = _uiState.value.copy(isUploading = true, uploadProgress = 0)
        viewModelScope.launch {
            try {
                val (remoteId, imageUrl) = imageRepository.uploadImage(imageFile, imageType, skuOuterId)
                val entity = ProductImageEntity(
                    skuOuterId = skuOuterId,
                    imageType = imageType,
                    imageUrl = imageUrl,
                    remoteId = remoteId,
                    createdAt = TimeUtils.now()
                )
                imageRepository.saveImage(entity)

                val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
                val fullUrl = "$serverUrl$imageUrl"
                _uiState.value = if (imageType == "area") {
                    _uiState.value.copy(areaImageUrl = fullUrl)
                } else {
                    _uiState.value.copy(boxImageUrl = fullUrl)
                }
                _uiState.value = _uiState.value.copy(isUploading = false, uploadProgress = 100)
            } catch (e: Exception) {
                // 离线支持：将图片复制到持久目录并入队等待网络恢复后上传
                try {
                    val pendingDir = File(appContext.filesDir, "pending_images")
                    pendingDir.mkdirs()
                    val pendingFile = File(pendingDir, "${skuOuterId}_${imageType}_${System.currentTimeMillis()}.jpg")
                    imageFile.copyTo(pendingFile, overwrite = true)

                    val payload = """{"sku_outer_id":"${TimeUtils.escapeJson(skuOuterId)}","image_type":"${imageType}","file_path":"${pendingFile.absolutePath}"}"""
                    pickOrderRepository.enqueueUploadImage(skuOuterId, payload)

                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "图片将在网络恢复后自动上传"
                    )
                } catch (queueError: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "上传图片失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 删除图片（F22）
     * @param imageType 图片类型 area/box
     */
    fun deleteImage(imageType: String) {
        val skuOuterId = _uiState.value.skuOuterId
        if (skuOuterId.isBlank()) return

        viewModelScope.launch {
            try {
                imageRepository.deleteImage(skuOuterId, imageType)
                // 更新UI状态
                _uiState.value = if (imageType == "area") {
                    _uiState.value.copy(areaImageUrl = null)
                } else {
                    _uiState.value.copy(boxImageUrl = null)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "删除图片失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
