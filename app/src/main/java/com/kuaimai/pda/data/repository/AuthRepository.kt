package com.kuaimai.pda.data.repository

import android.content.SharedPreferences
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.util.AppConstants
import javax.inject.Inject

/**
 * 认证仓库接口
 */
interface AuthRepository {
    suspend fun refreshSession(): Boolean
    fun getApiKey(): String
    fun setApiKey(key: String)
    fun getAppKey(): String
    fun setAppKey(key: String)
    fun getAppSecret(): String
    fun setAppSecret(secret: String)
    fun getBaseUrl(): String
    fun setBaseUrl(url: String)
}

/**
 * 认证仓库实现
 * 敏感配置存储在EncryptedSharedPreferences中
 */
class AuthRepositoryImpl @Inject constructor(
    private val apiService: KuaimaiApiService,
    private val prefs: SharedPreferences
) : AuthRepository {

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_APP_SECRET = "app_secret"
        private const val KEY_BASE_URL = "base_url"
        private const val DEFAULT_BASE_URL = AppConstants.KUAIMAI_API_URL
    }

    override suspend fun refreshSession(): Boolean {
        return try {
            val result = apiService.refreshSession(emptyMap())
            result.containsKey("session")
        } catch (e: Exception) {
            false
        }
    }

    override fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    override fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    override fun getAppKey(): String {
        return prefs.getString(KEY_APP_KEY, "") ?: ""
    }

    override fun setAppKey(key: String) {
        prefs.edit().putString(KEY_APP_KEY, key).apply()
    }

    override fun getAppSecret(): String {
        return prefs.getString(KEY_APP_SECRET, "") ?: ""
    }

    override fun setAppSecret(secret: String) {
        prefs.edit().putString(KEY_APP_SECRET, secret).apply()
    }

    override fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    override fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }
}
