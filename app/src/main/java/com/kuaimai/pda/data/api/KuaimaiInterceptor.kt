package com.kuaimai.pda.data.api

import android.content.SharedPreferences
import com.kuaimai.pda.util.SignUtils
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * 快麦API签名拦截器
 * HMAC-MD5签名流程：
 * 1. 参数按key字母排序
 * 2. 拼接 key1value1key2value2...
 * 3. 前后追加appSecret
 * 4. HMAC-MD5(appSecret, 拼接串)
 */
class KuaimaiInterceptor @Inject constructor(
    private val prefs: SharedPreferences
) : Interceptor {

    companion object {
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_APP_SECRET = "app_secret"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val appKey = prefs.getString(KEY_APP_KEY, "") ?: ""
        val appSecret = prefs.getString(KEY_APP_SECRET, "") ?: ""

        // 从请求体中提取参数并签名
        val originalBody = originalRequest.body
        // TODO: 解析请求体参数，计算签名，追加sign参数后重新构建请求

        val newRequest = originalRequest.newBuilder()
            .addHeader("app_key", appKey)
            .build()

        return chain.proceed(newRequest)
    }

    /**
     * 计算快麦API签名
     * @param params 请求参数
     * @param appSecret 应用密钥
     * @return 签名字符串
     */
    private fun calculateSign(params: Map<String, String>, appSecret: String): String {
        return SignUtils.sign(params, appSecret)
    }
}
