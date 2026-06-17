package com.kuaimai.pda.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaimai.pda.data.api.dto.AppVersionResponse
import com.kuaimai.pda.update.AppUpdateManager
import com.kuaimai.pda.update.CheckResult
import com.kuaimai.pda.update.DownloadState
import com.kuaimai.pda.util.PrefsKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人设置ViewModel
 * 管理扫码方式、反馈开关等个人偏好设置
 * 系统管理功能已迁移到Web管理后台（浏览器访问 /admin）
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {

    companion object {
        const val KEY_SCAN_METHOD = "scan_method"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_GUIDE_SHOWN = "guide_shown"
    }

    private var isDownloadingUpdate = false

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

    /** 更新检查结果 */
    private val _updateCheckResult = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val updateCheckResult: StateFlow<UpdateCheckUiState> = _updateCheckResult.asStateFlow()

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
    fun toggleSound(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    /**
     * 切换振动开关
     */
    fun toggleVibration(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    /**
     * 手动检查更新
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            _updateCheckResult.value = UpdateCheckUiState.Checking
            when (val result = appUpdateManager.checkForUpdate()) {
                is CheckResult.HasUpdate -> {
                    _updateCheckResult.value = UpdateCheckUiState.HasUpdate(result.info)
                }
                is CheckResult.NoUpdate -> {
                    _updateCheckResult.value = UpdateCheckUiState.NoUpdate
                }
                is CheckResult.CheckError -> {
                    _updateCheckResult.value = UpdateCheckUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * 开始下载更新，完成后自动安装
     */
    fun startDownload(info: AppVersionResponse) {
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        appUpdateManager.downloadApk(info)
        viewModelScope.launch {
            appUpdateManager.downloadState.collect { state ->
                when (state) {
                    is DownloadState.Completed -> {
                        appUpdateManager.installApk(state.file)
                        isDownloadingUpdate = false
                    }
                    is DownloadState.Failed -> {
                        isDownloadingUpdate = false
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 关闭更新检查弹窗，重置状态
     */
    fun dismissUpdateCheck() {
        _updateCheckResult.value = UpdateCheckUiState.Idle
    }
}

sealed class UpdateCheckUiState {
    data object Idle : UpdateCheckUiState()
    data object Checking : UpdateCheckUiState()
    data class HasUpdate(val info: AppVersionResponse) : UpdateCheckUiState()
    data object NoUpdate : UpdateCheckUiState()
    data class Error(val message: String) : UpdateCheckUiState()
}
