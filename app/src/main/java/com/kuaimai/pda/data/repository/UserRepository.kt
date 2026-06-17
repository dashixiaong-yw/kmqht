package com.kuaimai.pda.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.kuaimai.pda.data.api.UserApiService
import com.kuaimai.pda.data.api.dto.CreateUserRequest
import com.kuaimai.pda.data.api.dto.LoginRequest
import com.kuaimai.pda.data.api.dto.UpdateUserRequest
import com.kuaimai.pda.data.api.dto.UserListResponse
import com.kuaimai.pda.data.api.dto.UserResponse
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
    @Named("encrypted") private val prefs: SharedPreferences
) : UserRepository {

    /** 应用级协程作用域，用于handleAuthError发送事件 */
    private val appScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "UserRepository"
        private const val KEY_USER_TOKEN = "user_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PERMISSIONS = "user_permissions"
    }

    private val _currentUser = MutableStateFlow<UserResponse?>(null)
    override val currentUser: StateFlow<UserResponse?> = _currentUser

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
                val user = UserResponse(
                    id = response.userId,
                    username = response.username,
                    permissions = response.permissions
                )
                // 保存到本地
                prefs.edit()
                    .putString(KEY_USER_TOKEN, response.token)
                    .putLong(KEY_USER_ID, response.userId)
                    .putString(KEY_USER_NAME, response.username)
                    .putStringSet(KEY_USER_PERMISSIONS, response.permissions.toSet())
                    .apply()
                _currentUser.value = user
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
        return prefs.getString(KEY_USER_TOKEN, "") ?: ""
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
                        putStringSet(KEY_USER_PERMISSIONS, permissions.toSet())
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
                .putLong(KEY_USER_ID, user.id)
                .putString(KEY_USER_NAME, user.username)
                .putStringSet(KEY_USER_PERMISSIONS, user.permissions.toSet())
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
        val message = e.message ?: ""
        // Retrofit的HTTP 401异常消息包含"401"
        if (message.contains("401") || message.contains("Unauthorized")) {
            clearLocalUser()
            appScope.launch {
                _loginRequired.emit(Unit)
            }
        }
    }

    /** 从本地缓存恢复用户状态 */
    private fun restoreFromCache() {
        val token = prefs.getString(KEY_USER_TOKEN, "") ?: ""
        val userId = prefs.getLong(KEY_USER_ID, 0L)
        val username = prefs.getString(KEY_USER_NAME, "") ?: ""
        val permissions = prefs.getStringSet(KEY_USER_PERMISSIONS, emptySet()) ?: emptySet()

        if (token.isNotEmpty() && username.isNotEmpty()) {
            _currentUser.value = UserResponse(
                id = userId,
                username = username,
                permissions = permissions.toList()
            )
        }
    }

    /** 清除本地用户数据 */
    private fun clearLocalUser() {
        prefs.edit()
            .remove(KEY_USER_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_PERMISSIONS)
            .apply()
        _currentUser.value = null
        Log.i(TAG, "本地用户数据已清除")
    }
}
