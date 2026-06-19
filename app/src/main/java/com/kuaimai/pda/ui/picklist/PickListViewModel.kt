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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 取货列表ViewModel
 * 管理取货单列表数据、新建取货单、删除取货单、发布/领取
 */
@HiltViewModel
class PickListViewModel @Inject constructor(
    private val pickOrderRepository: PickOrderRepository,
    private val orderApiService: OrderApiService,
    private val areaApiService: AreaApiService,
    private val userRepository: UserRepository
) : ViewModel() {

    /** 进行中的取货单列表（从后端API获取） */
    private val _activeOrders = MutableStateFlow<List<PickOrderEntity>>(emptyList())
    val activeOrders: StateFlow<List<PickOrderEntity>> = _activeOrders.asStateFlow()

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

    /** 创建成功后导航到详情页的事件 */
    private val _navigateToOrderEvent = MutableSharedFlow<Long>()
    val navigateToOrderEvent: SharedFlow<Long> = _navigateToOrderEvent.asSharedFlow()

    init {
        loadAreas()
        loadActiveOrders()
        loadCompletedOrders()
    }

    /**
     * 加载进行中的取货单列表（从后端API获取）
     */
    fun loadActiveOrders() {
        viewModelScope.launch {
            try {
                val token = userRepository.getToken()
                val response = orderApiService.listOrders(token, status = 0)
                _activeOrders.value = response.data.map { it.toOrderEntity() }
            } catch (e: Exception) {
                Log.e("PickListViewModel", "加载取货单列表失败: ${e.message}", e)
                _errorMessage.value = "加载取货单列表失败: ${e.message?.take(80) ?: "未知错误"}"
                _activeOrders.value = emptyList()
            }
        }
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
                Log.e("PickListViewModel", "加载拣货区失败: ${e.message}", e)
                _errorMessage.value = "加载拣货区失败: ${e.message?.take(80) ?: "未知错误"}"
                _areas.value = emptyList()
            }
        }
    }

    /**
     * 加载已完成的取货单（7天内）
     */
    private fun loadCompletedOrders() {
        viewModelScope.launch {
            try {
                val token = userRepository.getToken()
                val response = orderApiService.listOrders(token, status = 1)
                _completedOrders.value = response.data.map { it.toOrderEntity() }
            } catch (e: Exception) {
                Log.e("PickListViewModel", "加载已完成取货单失败: ${e.message}", e)
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
                pickOrderRepository.insertOrder(response.toOrderEntity())
                _showNewOrderDialog.value = false
                loadActiveOrders()
                _navigateToOrderEvent.emit(response.id)
            } catch (e: Exception) {
                _errorMessage.value = "创建取货单失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 发布取货单到公共列表
     */
    fun publishOrder(orderId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = userRepository.getToken()
                orderApiService.publishOrder(token, orderId)
                loadActiveOrders()
            } catch (e: Exception) {
                _errorMessage.value = "发布取货单失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 领取公开取货单
     */
    fun claimOrder(orderId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = userRepository.getToken()
                orderApiService.claimOrder(token, orderId)
                loadActiveOrders()
            } catch (e: Exception) {
                _errorMessage.value = "领取取货单失败: ${e.message}"
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
                pickOrderRepository.deleteOrder(order)
                loadActiveOrders()
            } catch (e: Exception) {
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
