package com.kuaimai.pda.ui.picklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.kuaimai.pda.data.api.AreaApiService
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.dto.CreateOrderRequest
import com.kuaimai.pda.data.db.entity.PickOrderEntity
import com.kuaimai.pda.data.repository.PickOrderRepository
import com.kuaimai.pda.data.repository.UserRepository
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
 * 取货列表ViewModel
 * 管理取货单列表数据、新建取货单、删除取货单
 */
@HiltViewModel
class PickListViewModel @Inject constructor(
    private val pickOrderRepository: PickOrderRepository,
    private val orderApiService: OrderApiService,
    private val areaApiService: AreaApiService,
    private val userRepository: UserRepository
) : ViewModel() {

    /** 进行中的取货单列表 */
    val activeOrders: StateFlow<List<PickOrderEntity>> =
        pickOrderRepository.getOrdersByStatus(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 已完成的取货单列表（7天内） */
    private val _completedOrders = MutableStateFlow<List<PickOrderEntity>>(emptyList())
    val completedOrders: StateFlow<List<PickOrderEntity>> = _completedOrders.asStateFlow()

    /** 拣货区列表 */
    private val _areas = MutableStateFlow<List<String>>(emptyList())
    val areas: StateFlow<List<String>> = _areas.asStateFlow()

    /** 是否显示新建弹窗 */
    private val _showNewOrderDialog = MutableStateFlow(false)
    val showNewOrderDialog: StateFlow<Boolean> = _showNewOrderDialog.asStateFlow()

    /** 是否显示已完成列表 */
    private val _showCompletedList = MutableStateFlow(false)
    val showCompletedList: StateFlow<Boolean> = _showCompletedList.asStateFlow()

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误消息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 删除确认弹窗 */
    private val _deleteTarget = MutableStateFlow<PickOrderEntity?>(null)
    val deleteTarget: StateFlow<PickOrderEntity?> = _deleteTarget.asStateFlow()

    init {
        loadAreas()
        loadCompletedOrders()
    }

    /**
     * 加载拣货区列表
     */
    private fun loadAreas() {
        viewModelScope.launch {
            try {
                val token = userRepository.getToken()
                val response = areaApiService.getAreas(token)
                _areas.value = response.data.map { it.name }
            } catch (e: Exception) {
                Log.w("PickListViewModel", "加载拣货区失败: ${e.message}")
                _areas.value = listOf("A区", "B区", "C区", "D区")
            }
        }
    }

    /**
     * 加载已完成的取货单（7天内）
     */
    private fun loadCompletedOrders() {
        viewModelScope.launch {
            val sevenDaysAgo = TimeUtils.now() - TimeUtils.COMPLETED_ORDER_RANGE_MS
            pickOrderRepository.getCompletedOrders(sevenDaysAgo)
                .collect { orders ->
                    _completedOrders.value = orders
                }
        }
    }

    /**
     * 显示新建取货单弹窗
     */
    fun showNewOrderDialog() {
        _showNewOrderDialog.value = true
    }

    /**
     * 隐藏新建取货单弹窗
     */
    fun hideNewOrderDialog() {
        _showNewOrderDialog.value = false
    }

    /**
     * 创建新取货单
     * @param areaName 拣货区名称
     */
    fun createOrder(areaName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = userRepository.getToken()
                val response = orderApiService.createOrder(token, CreateOrderRequest(areaName))
                // 同步到本地数据库（使用后端返回的真实时间）
                val order = PickOrderEntity(
                    id = response.id,
                    orderNo = response.orderNo,
                    status = response.status,
                    completionType = response.completionType,
                    totalCount = response.totalCount,
                    completedCount = response.completedCount,
                    createdAt = TimeUtils.parseBeijingTime(response.createdAt).let { if (it > 0) it else TimeUtils.now() },
                    expireAt = TimeUtils.parseBeijingTime(response.expireAt).let { if (it > 0) it else TimeUtils.now() + TimeUtils.DEFAULT_EXPIRE_MS }
                )
                pickOrderRepository.insertOrder(order)
                _showNewOrderDialog.value = false
            } catch (e: Exception) {
                _errorMessage.value = "创建取货单失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 显示已完成列表
     */
    fun showCompletedList() {
        _showCompletedList.value = true
    }

    /**
     * 隐藏已完成列表
     */
    fun hideCompletedList() {
        _showCompletedList.value = false
    }

    /**
     * 请求删除取货单（弹出确认）
     */
    fun requestDelete(order: PickOrderEntity) {
        _deleteTarget.value = order
    }

    /**
     * 取消删除
     */
    fun cancelDelete() {
        _deleteTarget.value = null
    }

    /**
     * 确认删除取货单
     */
    fun confirmDelete() {
        val order = _deleteTarget.value ?: return
        viewModelScope.launch {
            try {
                val token = userRepository.getToken()
                orderApiService.deleteOrder(token, order.id)
                // API成功：直接删除本地
                pickOrderRepository.deleteOrder(order)
            } catch (e: Exception) {
                // API失败：乐观删除+入队
                pickOrderRepository.deleteOrderWithQueue(order)
                _errorMessage.value = "删除取货单失败，将在网络恢复后重试: ${e.message}"
            } finally {
                _deleteTarget.value = null
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
