package com.kuaimai.pda.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.kuaimai.pda.data.api.SystemApiService
import com.kuaimai.pda.data.api.UserApiService
import com.kuaimai.pda.data.api.dto.LoginResponse
import com.kuaimai.pda.data.api.dto.CreateUserRequest
import com.kuaimai.pda.data.api.dto.LoginRequest
import com.kuaimai.pda.data.api.dto.UpdateUserRequest
import com.kuaimai.pda.data.api.dto.UserListResponse
import com.kuaimai.pda.data.api.dto.UserResponse
import com.kuaimai.pda.util.PrefsKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 用户仓库接口
 */
interface UserRepository {
    /** 当前用户信息 */
    val currentUser: StateFlow<UserResponse?>

    /** 登录失效事件（token过期/被禁用等），UI层监听此事件跳转登录页 */
    val loginRequired: SharedFlow<Unit>

    /** 是否已登录 */
    fun isLoggedIn(): Boolean

    /** 登录 */
    suspend fun login(username: String, password: String): Result<UserResponse>

    /** 退出登录 */
    suspend fun logout()

    /** 检查权限 */
    fun hasPermission(perm: String): Boolean

    /** 获取用户token */
    fun getToken(): String

    /** 获取最近一次登录结果（含mustChangePassword标志） */
    fun getLoginResult(): LoginResponse?

    // ↓↓↓ 记住密码相关（全部本地加密存储） ↓↓↓

    /** 是否启用了"记住密码" */
    fun isSavePasswordEnabled(): Boolean

    /** 设置"记住密码"开关 */
    fun setSavePasswordEnabled(enabled: Boolean)

    /** 获取已保存的用户名 */
    fun getSavedUsername(): String

    /** 获取已保存的密码 */
    fun getSavedPassword(): String

    /** 保存账号密码（给当前已勾选记住密码的用户） */
    fun saveCredentials(username: String, password: String)

    /** 清除已保存的账号密码 */
    fun clearSavedCredentials()

    // ↓↓↓ 登录历史相关（全部本地加密存储） ↓↓↓

    /** 获取登录历史（按最近使用降序，最近10条） */
    fun getLoginHistory(): List<String>

    /** 将登录成功的用户名加入历史（去重、移到列首、限10条） */
    fun saveToLoginHistory(username: String)

    /** 获取用户列表（需settings权限） */
    suspend fun getUsers(): Result<UserListResponse>

    /** 创建用户（需settings权限） */
    suspend fun createUser(username: String, password: String, permissions: List<String>): Result<Unit>

    /** 更新用户（需settings权限） */
    suspend fun updateUser(userId: Long, password: String?, permissions: List<String>?, isActive: Boolean?): Result<Unit>

    /** 删除用户（需settings权限） */
    suspend fun deleteUser(userId: Long): Result<Unit>

    /** 验证token有效性 */
    suspend fun validateToken(): Boolean
}

/**
 * 用户仓库实现
 * 管理登录状态、权限校验
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val apiService: UserApiService,
    private val systemApiService: SystemApiService,
    @Named("encrypted") private val prefs: SharedPreferences
) : UserRepository {

    /** 应用级协程作用域，用于handleAuthError发送事件 */
    private val appScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "UserRepository"
        /** Token有效期7天（毫秒） */
        private const val TOKEN_EXPIRE_MS = 7L * 24 * 60 * 60 * 1000
    }

    private val _currentUser = MutableStateFlow<UserResponse?>(null)
    override val currentUser: StateFlow<UserResponse?> = _currentUser

    /** 最近一次登录结果 */
    private var _lastLoginResult: LoginResponse? = null

    private val _loginRequired = MutableSharedFlow<Unit>()
    override val loginRequired: SharedFlow<Unit> = _loginRequired

    init {
        // 从本地缓存恢复用户状态
        restoreFromCache()
    }

    override fun isLoggedIn(): Boolean {
        return getToken().isNotEmpty() && _currentUser.value != null
    }

    override suspend fun login(username: String, password: String): Result<UserResponse> {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.success && response.token.isNotEmpty()) {
                _lastLoginResult = response
                val user = UserResponse(
                    id = response.userId,
                    username = response.username,
                    permissions = response.permissions
                )
                // 保存到本地（含session过期时间，供HomeScreen预警使用）
                val expireTime = System.currentTimeMillis() + TOKEN_EXPIRE_MS
                prefs.edit()
                    .putString(PrefsKeys.KEY_USER_TOKEN, response.token)
                    .putLong(PrefsKeys.KEY_USER_ID, response.userId)
                    .putString(PrefsKeys.KEY_USER_NAME, response.username)
                    .putStringSet(PrefsKeys.KEY_USER_PERMISSIONS, response.permissions.toSet())
                    .putLong(PrefsKeys.KEY_SESSION_EXPIRE, expireTime)
                    .apply()
                _currentUser.value = user
                // 登录成功后同步快麦凭证到本地
                syncKuaimaiCredentials(response.token)
                Log.i(TAG, "登录成功: $username")
                Result.success(user)
            } else {
                Log.w(TAG, "登录失败: ${response.message}")
                Result.failure(Exception(response.message.ifEmpty { "登录失败" }))
            }
        } catch (e: Exception) {
            Log.e(TAG, "登录异常: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        val token = getToken()
        if (token.isNotEmpty()) {
            try {
                apiService.logout(token)
            } catch (e: Exception) {
                Log.w(TAG, "退出登录API调用失败: ${e.message}")
            }
        }
        clearLocalUser()
    }

    override fun hasPermission(perm: String): Boolean {
        return _currentUser.value?.permissions?.contains(perm) == true
    }

    override fun getToken(): String {
        return prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
    }

    override fun getLoginResult(): LoginResponse? {
        return _lastLoginResult
    }

    override suspend fun getUsers(): Result<UserListResponse> {
        return try {
            val response = apiService.getUsers(getToken())
            Result.success(response)
        } catch (e: Exception) {
            handleAuthError(e)
            Log.e(TAG, "获取用户列表失败: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createUser(username: String, password: String, permissions: List<String>): Result<Unit> {
        return try {
            apiService.createUser(getToken(), CreateUserRequest(username, password, permissions))
            Result.success(Unit)
        } catch (e: Exception) {
            handleAuthError(e)
            Log.e(TAG, "创建用户失败: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateUser(userId: Long, password: String?, permissions: List<String>?, isActive: Boolean?): Result<Unit> {
        return try {
            apiService.updateUser(getToken(), userId, UpdateUserRequest(password, permissions, isActive))
            // 如果修改的是当前用户，刷新本地缓存
            val currentId = _currentUser.value?.id ?: 0L
            if (userId == currentId) {
                val updatedUser = UserResponse(
                    id = currentId,
                    username = _currentUser.value?.username ?: "",
                    isActive = isActive ?: _currentUser.value?.isActive ?: true,
                    permissions = permissions ?: _currentUser.value?.permissions ?: emptyList()
                )
                _currentUser.value = updatedUser
                prefs.edit().apply {
                    if (permissions != null) {
                        putStringSet(PrefsKeys.KEY_USER_PERMISSIONS, permissions.toSet())
                    }
                }.apply()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleAuthError(e)
            Log.e(TAG, "更新用户失败: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteUser(userId: Long): Result<Unit> {
        return try {
            apiService.deleteUser(getToken(), userId)
            Result.success(Unit)
        } catch (e: Exception) {
            handleAuthError(e)
            Log.e(TAG, "删除用户失败: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun validateToken(): Boolean {
        val token = getToken()
        if (token.isEmpty()) return false

        return try {
            val user = apiService.getCurrentUser(token)
            _currentUser.value = user
            // 更新本地缓存（包含id）
            prefs.edit()
                .putLong(PrefsKeys.KEY_USER_ID, user.id)
                .putString(PrefsKeys.KEY_USER_NAME, user.username)
                .putStringSet(PrefsKeys.KEY_USER_PERMISSIONS, user.permissions.toSet())
                .apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Token验证失败: ${e.message}")
            clearLocalUser()
            handleAuthError(e)
            false
        }
    }

    /**
     * 处理认证错误：检测401响应并触发登录失效事件
     */
    private fun handleAuthError(e: Exception) {
        if ((e as? retrofit2.HttpException)?.code() == 401) {
            clearLocalUser()
            appScope.launch {
                _loginRequired.emit(Unit)
            }
        }
    }

    /** 从本地缓存恢复用户状态 */
    private fun restoreFromCache() {
        val token = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
        val userId = prefs.getLong(PrefsKeys.KEY_USER_ID, 0L)
        val username = prefs.getString(PrefsKeys.KEY_USER_NAME, "") ?: ""
        val permissions = prefs.getStringSet(PrefsKeys.KEY_USER_PERMISSIONS, emptySet()) ?: emptySet()

        if (token.isNotEmpty() && username.isNotEmpty()) {
            _currentUser.value = UserResponse(
                id = userId,
                username = username,
                permissions = permissions.toList()
            )
        }
    }

    /** 登录成功后同步快麦凭证到本地 */
    private fun syncKuaimaiCredentials(token: String) {
        appScope.launch {
            try {
                val creds = systemApiService.getKuaimaiCredentials(token)
                if (creds.appKey.isNotEmpty()) {
                    prefs.edit()
                        .putString(PrefsKeys.KEY_APP_KEY, creds.appKey)
                        .putString(PrefsKeys.KEY_APP_SECRET, creds.appSecret)
                        .putString(PrefsKeys.KEY_SESSION, creds.session)
                        .apply()
                    Log.i(TAG, "快麦凭证同步成功: appKey=${creds.appKey}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "快麦凭证同步失败（登录后重试）: ${e.message}")
            }
        }
    }

    /** 清除本地用户数据 */
    private fun clearLocalUser() {
        prefs.edit()
            .remove(PrefsKeys.KEY_USER_TOKEN)
            .remove(PrefsKeys.KEY_USER_ID)
            .remove(PrefsKeys.KEY_USER_NAME)
            .remove(PrefsKeys.KEY_USER_PERMISSIONS)
            .remove(PrefsKeys.KEY_SESSION_EXPIRE)
            .apply()
        _currentUser.value = null
        Log.i(TAG, "本地用户数据已清除")
    }

    // ↓↓↓ 记住密码实现 ↓↓↓

    override fun isSavePasswordEnabled(): Boolean {
        return prefs.getBoolean(PrefsKeys.KEY_SAVE_PASSWORD, false)
    }

    override fun setSavePasswordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PrefsKeys.KEY_SAVE_PASSWORD, enabled).apply()
    }

    override fun getSavedUsername(): String {
        return prefs.getString(PrefsKeys.KEY_SAVED_USERNAME, "") ?: ""
    }

    override fun getSavedPassword(): String {
        return prefs.getString(PrefsKeys.KEY_SAVED_PASSWORD, "") ?: ""
    }

    override fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(PrefsKeys.KEY_SAVED_USERNAME, username)
            .putString(PrefsKeys.KEY_SAVED_PASSWORD, password)
            .apply()
    }

    override fun clearSavedCredentials() {
        prefs.edit()
            .remove(PrefsKeys.KEY_SAVED_USERNAME)
            .remove(PrefsKeys.KEY_SAVED_PASSWORD)
            .apply()
    }

    // ↓↓↓ 登录历史实现 ↓↓↓

    private val gson = Gson()

    override fun getLoginHistory(): List<String> {
        val json = prefs.getString(PrefsKeys.KEY_LOGIN_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "解析登录历史失败: ${e.message}")
            emptyList()
        }
    }

    override fun saveToLoginHistory(username: String) {
        val json = prefs.getString(PrefsKeys.KEY_LOGIN_HISTORY, null)
        val list = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<String>>() {}.type
                gson.fromJson<MutableList<String>>(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                Log.w(TAG, "解析登录历史失败，重置: ${e.message}")
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
        list.remove(username)
        list.add(0, username)
        if (list.size > 10) list.removeAt(list.size - 1)
        prefs.edit().putString(PrefsKeys.KEY_LOGIN_HISTORY, gson.toJson(list)).apply()
    }
}
