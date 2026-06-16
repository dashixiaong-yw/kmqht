package com.kuaimai.pda.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话过期事件总线
 * TokenAuthenticator检测到401且刷新失败时通知UI
 * UI层监听此事件显示不可关闭的"会话已过期"对话框
 */
object SessionExpiredEvent {

    private val _isExpired = MutableStateFlow(false)
    val isExpired: StateFlow<Boolean> = _isExpired.asStateFlow()

    /**
     * 通知会话已过期
     */
    fun notifyExpired() {
        _isExpired.value = true
    }

    /**
     * 重置过期状态（用户重新授权后调用）
     */
    fun reset() {
        _isExpired.value = false
    }
}
