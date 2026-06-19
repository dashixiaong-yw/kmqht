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
import com.kuaimai.pda.data.api.SystemApiService
import com.kuaimai.pda.data.api.UserApiService
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.PrefsKeys
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
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Named
import javax.inject.Provider
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import javax.inject.Singleton

/**
 * 网络层依赖注入：OkHttp + Retrofit + Token刷新
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PREFS_NAME = "kuaimai_encrypted_prefs"
    private const val DEFAULT_BASE_URL = AppConstants.KUAIMAI_API_URL
    private const val DEFAULT_SERVER_URL = AppConstants.DEFAULT_SERVER_URL
    private const val TAG = "NetworkModule"

    private val unsafeTrustManager: X509TrustManager by lazy {
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    private val unsafeSslSocketFactory: SSLSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(unsafeTrustManager), java.security.SecureRandom())
        sslContext.socketFactory
    }

    /** 加密SharedPreferences，存储API密钥等敏感配置 */
    @Provides
    @Singleton
    @Named("encrypted")
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

    /** 普通SharedPreferences，存储非敏感配置（引导标记等） */
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("kuaimai_prefs", Context.MODE_PRIVATE)
    }

    /** 快麦API签名拦截器 */
    @Provides
    @Singleton
    fun provideKuaimaiInterceptor(
        @Named("encrypted") prefs: SharedPreferences
    ): KuaimaiInterceptor {
        return KuaimaiInterceptor(prefs)
    }

    /** 日志拦截器 */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    /** Token刷新认证器：使用Provider避免循环依赖 */
    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        systemApiServiceProvider: Provider<SystemApiService>,
        @Named("encrypted") prefs: SharedPreferences
    ): TokenAuthenticator {
        return TokenAuthenticator(systemApiServiceProvider, prefs)
    }

    /** OkHttp客户端 */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        kuaimaiInterceptor: KuaimaiInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(kuaimaiInterceptor)
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
        @Named("encrypted") prefs: SharedPreferences
    ): Retrofit {
        val baseUrl = prefs.getString(PrefsKeys.KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
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
    fun provideBackendRetrofit(client: OkHttpClient, @Named("encrypted") prefs: SharedPreferences): Retrofit {
        var serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        // 未配置服务器地址时使用占位URL，避免Retrofit.baseUrl()抛异常
        // 占位URL的请求会失败但不崩溃，用户配置后会重新创建Retrofit
        if (serverUrl.isBlank()) {
            serverUrl = "http://localhost:1/"
            Log.w(TAG, "服务器地址未配置，使用占位URL。请在引导页或设置页配置服务器地址")
        }
        val trustAllClient = client.newBuilder()
            .hostnameVerifier { _, _ -> true }
            .sslSocketFactory(unsafeSslSocketFactory, unsafeTrustManager)
            .build()
        return Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(trustAllClient)
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

    /** 后端系统API服务（快麦session状态和刷新） */
    @Provides
    @Singleton
    fun provideSystemApiService(@Named("backend") backendRetrofit: Retrofit): SystemApiService {
        return backendRetrofit.create(SystemApiService::class.java)
    }
}

/**
 * Token刷新认证器
 * 检测401响应 → 通过后端API刷新快麦session → 成功后重试原请求
 * 失败则通知UI显示"会话已过期"对话框
 * 使用Provider<SystemApiService>避免循环依赖，通过后端中转刷新
 */
class TokenAuthenticator(
    private val systemApiServiceProvider: Provider<SystemApiService>,
    private val prefs: SharedPreferences
) : okhttp3.Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
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
            val systemApiService = systemApiServiceProvider.get()
            val userToken = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""

            // 通过后端中转刷新快麦session
            // runBlocking 在 OkHttp 分发线程中执行，PDA 单用户场景风险可控
            val refreshResult = kotlinx.coroutines.runBlocking {
                try {
                    systemApiService.refreshSession(userToken)
                } catch (e: Exception) {
                    Log.e(TAG, "刷新session失败: ${e.message}")
                    null
                }
            }

            if (refreshResult != null && refreshResult.success) {
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
