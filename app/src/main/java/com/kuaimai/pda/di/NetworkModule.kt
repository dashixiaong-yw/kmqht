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
import java.io.File
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Named
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
        return try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("NetworkModule", "加密存储打开失败，删除重建: ${e.message}")
            File(context.applicationInfo.dataDir, "shared_prefs/${PREFS_NAME}.xml").delete()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
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

    /** Token刷新认证器 */
    @Provides
    @Singleton
    fun provideTokenAuthenticator(): TokenAuthenticator {
        return TokenAuthenticator()
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

    /** 信任所有证书的OkHttp客户端（图片上传等服务端自签证书用） */
    @Provides
    @Singleton
    @Named("trustAll")
    fun provideTrustAllOkHttpClient(
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
            .hostnameVerifier { _, _ -> true }
            .sslSocketFactory(unsafeSslSocketFactory, unsafeTrustManager)
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
 * 检测401响应 → 通知UI"会话已过期"对话框
 * 用户重新登录后可恢复，不走自动刷新（用户token 7天到期，重新登录即可）
 */
class TokenAuthenticator() : okhttp3.Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    override fun authenticate(
        route: okhttp3.Route?,
        response: okhttp3.Response
    ): okhttp3.Request? {
        if (response.code != 401) return null
        SessionExpiredEvent.notifyExpired()
        return null
    }
}
