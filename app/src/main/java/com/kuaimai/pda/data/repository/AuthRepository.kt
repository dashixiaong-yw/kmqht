package com.kuaimai.pda.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.kuaimai.pda.data.api.SystemApiService
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.PrefsKeys
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Named

/**
 * 认证仓库接口
 */
interface AuthRepository {
    /** Token刷新失败事件 */
    val tokenRefreshFailed: SharedFlow<Unit>

    suspend fun refreshSession(): Boolean
    fun getAppKey(): String
    fun setAppKey(key: String)
    fun getAppSecret(): String
    fun setAppSecret(secret: String)
    fun getBaseUrl(): String
    fun setBaseUrl(url: String)
    fun getSessionExpireTime(): Long
}

/**
 * 认证仓库实现
 * 敏感配置存储在EncryptedSharedPreferences中
 */
class AuthRepositoryImpl @Inject constructor(
    private val systemApiService: SystemApiService,
    @Named("encrypted") private val prefs: SharedPreferences
) : AuthRepository {

    companion object {
        private const val DEFAULT_BASE_URL = AppConstants.KUAIMAI_API_URL
    }

    /** Token刷新失败事件流 */
    private val _tokenRefreshFailed = MutableSharedFlow<Unit>()
    override val tokenRefreshFailed: SharedFlow<Unit> = _tokenRefreshFailed

    override suspend fun refreshSession(): Boolean {
        return try {
            val userToken = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
            val result = systemApiService.refreshSession(userToken)
            if (result.success) {
                true
            } else {
                _tokenRefreshFailed.emit(Unit)
                false
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "刷新session失败: ${e.message}")
            _tokenRefreshFailed.emit(Unit)
            false
        }
    }

    override fun getAppKey(): String {
        return prefs.getString(PrefsKeys.KEY_APP_KEY, "") ?: ""
    }

    override fun setAppKey(key: String) {
        prefs.edit().putString(PrefsKeys.KEY_APP_KEY, key).apply()
    }

    override fun getAppSecret(): String {
        return prefs.getString(PrefsKeys.KEY_APP_SECRET, "") ?: ""
    }

    override fun setAppSecret(secret: String) {
        prefs.edit().putString(PrefsKeys.KEY_APP_SECRET, secret).apply()
    }

    override fun getBaseUrl(): String {
        return prefs.getString(PrefsKeys.KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    override fun setBaseUrl(url: String) {
        prefs.edit().putString(PrefsKeys.KEY_BASE_URL, url).apply()
    }

    override fun getSessionExpireTime(): Long {
        return prefs.getLong(PrefsKeys.KEY_SESSION_EXPIRE, 0L)
    }
}
