package com.kuaimai.pda.util

/**
 * SharedPreferences Key统一常量
 * 所有模块引用此类，避免key名称重复定义
 */
object PrefsKeys {
    // 用户相关（EncryptedSharedPreferences）
    const val KEY_USER_TOKEN = "user_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_PERMISSIONS = "user_permissions"
    const val KEY_SESSION_EXPIRE = "session_expire_time"

    // 快麦凭证相关（EncryptedSharedPreferences）
    const val KEY_API_KEY = "api_key"
    const val KEY_APP_KEY = "app_key"
    const val KEY_APP_SECRET = "app_secret"
    const val KEY_SESSION = "session"

    // 服务器配置相关（EncryptedSharedPreferences）
    const val KEY_BASE_URL = "base_url"
    const val KEY_SERVER_URL = "server_url"
}
