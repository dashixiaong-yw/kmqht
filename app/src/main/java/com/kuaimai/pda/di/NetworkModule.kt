package com.kuaimai.pda.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kuaimai.pda.data.api.AreaApiService
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.KuaimaiInterceptor
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.UserApiService
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.SessionExpiredEvent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 网络层依赖注入：OkHttp + Retrofit + API Key拦截器 + Token刷新
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PREFS_NAME = "kuaimai_secure_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val DEFAULT_BASE_URL = AppConstants.KUAIMAI_API_URL
    private const val KEY_SERVER_URL = "server_url"
    private const val DEFAULT_SERVER_URL = AppConstants.DEFAULT_SERVER_URL
    private const val TAG = "NetworkModule"

    /** 加密SharedPreferences，存储API密钥等敏感配置 */
    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** API Key拦截器：添加X-API-Key请求头 */
    @Provides
    @Singleton
    fun provideApiKeyInterceptor(
        prefs: SharedPreferences
    ): ApiKeyInterceptor {
        return ApiKeyInterceptor(prefs)
    }

    /** 快麦API签名拦截器 */
    @Provides
    @Singleton
    fun provideKuaimaiInterceptor(
        prefs: SharedPreferences
    ): KuaimaiInterceptor {
        return KuaimaiInterceptor(prefs)
    }

    /** 限流拦截器：令牌桶算法，5次/秒 */
    @Provides
    @Singleton
    fun provideRateLimitInterceptor(): RateLimitInterceptor {
        return RateLimitInterceptor(maxRequests = 5, perSeconds = 1)
    }

    /** 日志拦截器 */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    /** Token刷新认证器：使用Provider避免循环依赖 */
    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        apiServiceProvider: Provider<KuaimaiApiService>,
        prefs: SharedPreferences
    ): TokenAuthenticator {
        return TokenAuthenticator(apiServiceProvider, prefs)
    }

    /** OkHttp客户端 */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        apiKeyInterceptor: ApiKeyInterceptor,
        kuaimaiInterceptor: KuaimaiInterceptor,
        rateLimitInterceptor: RateLimitInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(kuaimaiInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
            .build()
    }

    /** 快麦Retrofit实例 */
    @Provides
    @Singleton
    @Named("kuaimai")
    fun provideRetrofit(
        client: OkHttpClient,
        prefs: SharedPreferences
    ): Retrofit {
        val baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** 快麦API服务 */
    @Provides
    @Singleton
    fun provideKuaimaiApiService(@Named("kuaimai") retrofit: Retrofit): KuaimaiApiService {
        return retrofit.create(KuaimaiApiService::class.java)
    }

    /** 后端Retrofit实例（指向本地后端服务） */
    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendRetrofit(client: OkHttpClient, prefs: SharedPreferences): Retrofit {
        val serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        return Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** 后端取货单API服务 */
    @Provides
    @Singleton
    fun provideOrderApiService(@Named("backend") backendRetrofit: Retrofit): OrderApiService {
        return backendRetrofit.create(OrderApiService::class.java)
    }

    /** 后端拣货区API服务 */
    @Provides
    @Singleton
    fun provideAreaApiService(@Named("backend") backendRetrofit: Retrofit): AreaApiService {
        return backendRetrofit.create(AreaApiService::class.java)
    }

    /** 后端用户管理API服务 */
    @Provides
    @Singleton
    fun provideUserApiService(@Named("backend") backendRetrofit: Retrofit): UserApiService {
        return backendRetrofit.create(UserApiService::class.java)
    }
}

/**
 * API Key拦截器：为请求添加X-API-Key头
 */
class ApiKeyInterceptor(
    private val prefs: SharedPreferences
) : okhttp3.Interceptor {

    companion object {
        private const val KEY_API_KEY = "api_key"
    }

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val request = chain.request().newBuilder()
            .addHeader("X-API-Key", apiKey)
            .build()
        return chain.proceed(request)
    }
}

/**
 * 限流拦截器：令牌桶算法
 */
class RateLimitInterceptor(
    private val maxRequests: Int,
    private val perSeconds: Int
) : okhttp3.Interceptor {

    private val intervalMs: Long = (perSeconds * 1000L) / maxRequests
    private var lastRequestTime: Long = 0L

    @Synchronized
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < intervalMs) {
            Thread.sleep(intervalMs - elapsed)
        }
        lastRequestTime = System.currentTimeMillis()
        return chain.proceed(chain.request())
    }
}

/**
 * Token刷新认证器
 * 检测401响应 → 自动调用session.refresh → 成功后重试原请求
 * 失败则通知UI显示"会话已过期"对话框
 * 使用Provider<KuaimaiApiService>避免循环依赖
 */
class TokenAuthenticator(
    private val apiServiceProvider: Provider<KuaimaiApiService>,
    private val prefs: SharedPreferences
) : okhttp3.Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val KEY_SESSION = "session"
    }

    /** 防止并发刷新 */
    private val isRefreshing = AtomicBoolean(false)

    override fun authenticate(
        route: okhttp3.Route?,
        response: okhttp3.Response
    ): okhttp3.Request? {
        // 只对401响应处理
        if (response.code != 401) return null

        // 防止无限重试
        val retryCount = response.request.header("X-Retry-Count")?.toIntOrNull() ?: 0
        if (retryCount >= 2) {
            Log.w(TAG, "Token刷新重试次数已达上限")
            notifySessionExpired()
            return null
        }

        // 防止并发刷新
        if (!isRefreshing.compareAndSet(false, true)) {
            return null
        }

        try {
            val apiService = apiServiceProvider.get()
            // 尝试刷新session
            val refreshResult = kotlinx.coroutines.runBlocking {
                try {
                    apiService.refreshSession(emptyMap())
                } catch (e: Exception) {
                    Log.e(TAG, "刷新session失败: ${e.message}")
                    null
                }
            }

            if (refreshResult != null && refreshResult.containsKey("session")) {
                // 更新本地session
                val newSession = refreshResult["session"] as? String ?: ""
                prefs.edit().putString(KEY_SESSION, newSession).apply()
                Log.i(TAG, "Token刷新成功")

                // 重试原请求
                return response.request.newBuilder()
                    .header("X-Retry-Count", (retryCount + 1).toString())
                    .build()
            } else {
                // 刷新失败，通知UI
                Log.w(TAG, "Token刷新失败，通知UI")
                notifySessionExpired()
                return null
            }
        } finally {
            isRefreshing.set(false)
        }
    }

    /**
     * 通知UI会话已过期
     */
    private fun notifySessionExpired() {
        SessionExpiredEvent.notifyExpired()
    }
}
