package com.kuaimai.pda.data.api

import android.content.SharedPreferences
import android.util.Log
import com.kuaimai.pda.util.PrefsKeys
import com.kuaimai.pda.util.SignUtils
import com.kuaimai.pda.util.TimeUtils
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject

/**
 * 快麦API签名拦截器
 * MD5签名流程（与快麦开放平台一致）：
 * 1. 添加公共参数（app_key、timestamp、session、method等）
 * 2. 所有参数按key字母排序
 * 3. 拼接 key1value1key2value2...
 * 4. 前后追加appSecret
 * 5. MD5(拼接串) 转大写
 */
class KuaimaiInterceptor @Inject constructor(
    private val prefs: SharedPreferences
) : Interceptor {

    companion object {
        private const val TAG = "KuaimaiInterceptor"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 仅对快麦API请求添加签名，后端API请求跳过
        val host = originalRequest.url.host
        if (!host.contains("kuaimai.com")) {
            return chain.proceed(originalRequest)
        }

        val appKey = prefs.getString(PrefsKeys.KEY_APP_KEY, "") ?: ""
        val appSecret = prefs.getString(PrefsKeys.KEY_APP_SECRET, "") ?: ""
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""

        // 从请求体中提取参数
        val bodyString = extractBodyString(originalRequest)
        val params = mutableMapOf<String, String>()

        // 解析原始请求体中的参数
        if (bodyString.isNotEmpty()) {
            try {
                val json = JSONObject(bodyString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    params[key] = json.getString(key)
                }
            } catch (e: Exception) {
                Log.w(TAG, "解析请求体失败: ${e.message}")
            }
        }

        // 添加公共参数（与后端 _build_common_params 保持一致）
        params["app_key"] = appKey
        params["timestamp"] = TimeUtils.formatTimestamp(TimeUtils.now())
        params["session"] = accessToken
        params["format"] = "json"
        params["v"] = "2.0"
        params["sign_method"] = "md5"

        // 计算签名
        val sign = SignUtils.sign(params, appSecret)
        params["sign"] = sign

        // 重建请求体
        val newBodyJson = JSONObject(params as Map<Any?, Any?>).toString()
        val newBody = newBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val newRequest = originalRequest.newBuilder()
            .method(originalRequest.method, newBody)
            .build()

        return chain.proceed(newRequest)
    }

    /**
     * 从请求中提取body字符串
     */
    private fun extractBodyString(request: Request): String {
        val body = request.body ?: return ""
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }
}
