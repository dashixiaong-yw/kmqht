package com.kuaimai.pda.data.api

import android.content.SharedPreferences
import android.util.Log
import com.kuaimai.pda.util.PrefsKeys
import com.kuaimai.pda.util.SignUtils
import com.kuaimai.pda.util.TimeUtils
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject

/**
 * 快麦API签名拦截器
 * MD5签名流程（与快麦开放平台一致）：
 * 1. 添加公共参数（appKey、timestamp、session、method等）
 * 2. 所有参数按key字母排序
 * 3. 拼接 key1value1key2value2...
 * 4. 前后追加appSecret
 * 5. MD5(拼接串) 转大写
 * 请求格式：form-urlencoded（与后端 httpx data= 和快麦官方一致）
 */
class KuaimaiInterceptor @Inject constructor(
    private val prefs: SharedPreferences
) : Interceptor {

    companion object {
        private const val TAG = "KuaimaiInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 仅对快麦API请求添加签名，后端API请求跳过
        val host = originalRequest.url.host
        if (!host.contains("kuaimai.com") && !host.contains("superboss.cc")) {
            return chain.proceed(originalRequest)
        }

        val appKey = prefs.getString(PrefsKeys.KEY_APP_KEY, "") ?: ""
        val appSecret = prefs.getString(PrefsKeys.KEY_APP_SECRET, "") ?: ""
        val accessToken = prefs.getString(PrefsKeys.KEY_SESSION, "") ?: ""

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

        // 添加公共参数（与快麦开放平台官方文档一致，与后端 _build_common_params 保持一致）
        params["appKey"] = appKey
        params["timestamp"] = TimeUtils.formatTimestamp(TimeUtils.now())
        params["session"] = accessToken
        params["format"] = "json"
        params["version"] = "1.0"
        params["sign_method"] = "md5"

        // 计算签名
        val sign = SignUtils.sign(params, appSecret)
        params["sign"] = sign

        // 重建请求体（form-urlencoded格式，与后端httpx data=一致）
        val formBody = okhttp3.FormBody.Builder()
        for ((key, value) in params) {
            formBody.add(key, value)
        }
        val newBody = formBody.build()

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
