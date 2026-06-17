package com.kuaimai.pda.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaimai.pda.data.api.AreaApiService
import com.kuaimai.pda.data.api.dto.AreaResponse
import com.kuaimai.pda.util.AppConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置ViewModel
 * 管理拣货区、服务器地址、API Key、扫码方式、反馈开关
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val areaApiService: AreaApiService,
    private val prefs: SharedPreferences
) : ViewModel() {

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_SCAN_METHOD = "scan_method"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_GUIDE_SHOWN = "guide_shown"
    }

    /** 拣货区列表 */
    private val _areas = MutableStateFlow<List<AreaResponse>>(emptyList())
    val areas: StateFlow<List<AreaResponse>> = _areas.asStateFlow()

    /** 新拣货区名称输入 */
    private val _newAreaName = MutableStateFlow("")
    val newAreaName: StateFlow<String> = _newAreaName.asStateFlow()

    /** 服务器地址 */
    private val _serverUrl = MutableStateFlow(
        prefs.getString(KEY_SERVER_URL, AppConstants.DEFAULT_SERVER_URL) ?: AppConstants.DEFAULT_SERVER_URL
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    /** API Key */
    private val _apiKey = MutableStateFlow(
        prefs.getString(KEY_API_KEY, "") ?: ""
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    /** 扫码方式 (0=PDA硬件, 1=相机, 2=手动) */
    private val _scanMethod = MutableStateFlow(
        prefs.getInt(KEY_SCAN_METHOD, 0)
    )
    val scanMethod: StateFlow<Int> = _scanMethod.asStateFlow()

    /** 声音开关 */
    private val _soundEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SOUND_ENABLED, true)
    )
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    /** 振动开关 */
    private val _vibrationEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    )
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误消息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 成功消息 */
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        loadAreas()
    }

    /**
     * 加载拣货区列表
     */
    fun loadAreas() {
        viewModelScope.launch {
            try {
                val response = areaApiService.getAreas()
                _areas.value = response.data
            } catch (e: Exception) {
                _errorMessage.value = "加载拣货区失败: ${e.message}"
            }
        }
    }

    /**
     * 更新新拣货区名称输入
     */
    fun updateNewAreaName(name: String) {
        _newAreaName.value = name
    }

    /**
     * 添加拣货区
     */
    fun addArea() {
        val name = _newAreaName.value.trim()
        if (name.isEmpty()) {
            _errorMessage.value = "请输入拣货区名称"
            return
        }
        if (_areas.value.any { it.name == name }) {
            _errorMessage.value = "该拣货区已存在"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 后端暂无添加拣货区API，先本地添加
                val newArea = AreaResponse(id = System.currentTimeMillis(), name = name)
                _areas.value = _areas.value + newArea
                _newAreaName.value = ""
                _successMessage.value = "已添加拣货区: $name"
            } catch (e: Exception) {
                _errorMessage.value = "添加拣货区失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 删除拣货区
     */
    fun deleteArea(area: AreaResponse) {
        viewModelScope.launch {
            try {
                _areas.value = _areas.value.filter { it.id != area.id }
                _successMessage.value = "已删除拣货区: ${area.name}"
            } catch (e: Exception) {
                _errorMessage.value = "删除拣货区失败: ${e.message}"
            }
        }
    }

    /**
     * 更新服务器地址
     */
    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    /**
     * 保存服务器地址
     */
    fun saveServerUrl() {
        val url = _serverUrl.value.trim()
        if (url.isEmpty() || !url.startsWith("http")) {
            _errorMessage.value = "请输入有效的服务器地址（以http开头）"
            return
        }
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        _successMessage.value = "服务器地址已保存"
    }

    /**
     * 更新API Key
     */
    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    /**
     * 保存API Key
     */
    fun saveApiKey() {
        prefs.edit().putString(KEY_API_KEY, _apiKey.value.trim()).apply()
        _successMessage.value = "API Key已保存"
    }

    /**
     * 设置扫码方式
     */
    fun setScanMethod(method: Int) {
        _scanMethod.value = method
        prefs.edit().putInt(KEY_SCAN_METHOD, method).apply()
    }

    /**
     * 切换声音开关
     */
    fun toggleSound() {
        val newValue = !_soundEnabled.value
        _soundEnabled.value = newValue
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, newValue).apply()
    }

    /**
     * 切换振动开关
     */
    fun toggleVibration() {
        val newValue = !_vibrationEnabled.value
        _vibrationEnabled.value = newValue
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, newValue).apply()
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 清除成功消息
     */
    fun clearSuccess() {
        _successMessage.value = null
    }
}
