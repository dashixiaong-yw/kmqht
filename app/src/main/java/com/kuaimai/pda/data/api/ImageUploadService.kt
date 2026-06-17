package com.kuaimai.pda.data.api

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.PrefsKeys
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import okio.buffer

/**
 * 图片上传服务
 * 使用OkHttp Multipart上传，区分area/box类型
 * 支持进度回调、失败重试（最多3次）
 */
class ImageUploadService @Inject constructor(
    private val client: OkHttpClient,
    private val prefs: SharedPreferences,
    @Named("encrypted") private val encryptedPrefs: SharedPreferences
) {

    companion object {
        private const val TAG = "ImageUploadService"
        private const val DEFAULT_SERVER_URL = AppConstants.DEFAULT_SERVER_URL
        private const val MAX_RETRY = 3
    }

    /**
     * 上传图片（含重试机制）
     * @param imageFile 图片文件
     * @param imageType 图片类型：area（区域图）或 box（货位图）
     * @param skuOuterId 商品外部ID
     * @param onProgress 进度回调 (0-100)
     * @return Pair(remoteId, imageUrl) 上传后的远程ID和图片相对URL路径
     * @throws IOException 网络或上传失败
     */
    @Throws(IOException::class)
    suspend fun uploadImage(
        imageFile: File,
        imageType: String,
        skuOuterId: String,
        onProgress: ((Int) -> Unit)? = null
    ): Pair<Long, String> = withContext(Dispatchers.IO) {
        var lastException: IOException? = null

        repeat(MAX_RETRY) { attempt ->
            try {
                return@withContext doUpload(imageFile, imageType, skuOuterId, onProgress)
            } catch (e: IOException) {
                lastException = e
                Log.w(TAG, "图片上传失败(第${attempt + 1}次): ${e.message}")
                if (attempt < MAX_RETRY - 1) {
                    // 指数退避：1s, 2s, 4s
                    val delayMs = (1L shl attempt) * 1000
                    Thread.sleep(delayMs)
                }
            }
        }

        throw lastException ?: IOException("图片上传失败: 未知错误")
    }

    /**
     * 执行单次上传
     */
    private fun doUpload(
        imageFile: File,
        imageType: String,
        skuOuterId: String,
        onProgress: ((Int) -> Unit)?
    ): Pair<Long, String> {
        val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, DEFAULT_SERVER_URL)
            ?: DEFAULT_SERVER_URL
        val uploadUrl = "$serverUrl/api/upload"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("skuOuterId", skuOuterId)
            .addFormDataPart("imageType", imageType)
            .addFormDataPart(
                "file",
                imageFile.name,
                imageFile.asRequestBody(guessMediaType(imageFile.name).toMediaType())
            )
            .build()

        // 包装请求体以支持进度回调
        val progressBody = if (onProgress != null) {
            ProgressRequestBody(requestBody) { bytesWritten, contentLength ->
                val progress = (bytesWritten * 100 / contentLength).toInt()
                onProgress(progress)
            }
        } else {
            requestBody
        }

        val token = encryptedPrefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
        val request = Request.Builder()
            .url(uploadUrl)
            .post(progressBody)
            .addHeader("X-User-Token", token)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("图片上传失败: HTTP ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw IOException("上传响应为空")

        // 解析响应JSON获取图片URL
        return parseImageUrlFromResponse(responseBody)
    }

    /**
     * 删除图片（F22）
     * @param imageId 图片ID
     */
    suspend fun deleteImage(imageId: Long) = withContext(Dispatchers.IO) {
        val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, DEFAULT_SERVER_URL)
            ?: DEFAULT_SERVER_URL
        val deleteUrl = "$serverUrl/api/images/$imageId"

        val token = encryptedPrefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
        val request = Request.Builder()
            .url(deleteUrl)
            .delete()
            .addHeader("X-User-Token", token)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("删除图片失败: HTTP ${response.code}")
        }
    }

    /**
     * 从后端查询SKU的图片列表（多PDA同步用）
     * @param skuOuterId SKU外部编码
     * @return 图片列表JSON字符串
     */
    suspend fun fetchImages(skuOuterId: String): String = withContext(Dispatchers.IO) {
        val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, DEFAULT_SERVER_URL)
            ?: DEFAULT_SERVER_URL
        val queryUrl = "$serverUrl/api/images/$skuOuterId"

        val token = encryptedPrefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
        val request = Request.Builder()
            .url(queryUrl)
            .get()
            .addHeader("X-User-Token", token)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("查询图片失败: HTTP ${response.code}")
        }

        response.body?.string() ?: throw IOException("查询图片响应为空")
    }

    /**
     * 根据文件扩展名推断MediaType
     */
    private fun guessMediaType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "image/jpeg" // 默认jpeg
        }
    }

    /**
     * 从响应JSON中解析图片URL和远程ID
     * 响应格式: {"id":1,"skuOuterId":"...","imageType":"area","imageUrl":"/images/...","filePath":"...","createdAt":"..."}
     * 使用JSONObject解析，正确处理转义字符
     * @return Pair(remoteId, imageUrl)
     */
    fun parseImageUrlFromResponse(responseBody: String): Pair<Long, String> {
        return try {
            val json = JSONObject(responseBody)
            val remoteId = json.optLong("id", 0L)
            val imageUrl = json.optString("imageUrl", "")
            if (remoteId == 0L || imageUrl.isEmpty()) {
                throw IOException("响应中未找到id或imageUrl字段")
            }
            Pair(remoteId, imageUrl)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("解析上传响应失败: ${e.message}")
        }
    }

    /**
     * 带进度回调的RequestBody包装
     */
    private class ProgressRequestBody(
        private val delegate: okhttp3.RequestBody,
        private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit
    ) : okhttp3.RequestBody() {

        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()

        override fun writeTo(sink: okio.BufferedSink) {
            val countingSink = object : okio.ForwardingSink(sink) {
                private var bytesWritten: Long = 0L

                override fun write(source: okio.Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    onProgress(bytesWritten, contentLength())
                }
            }
            val bufferedSink = countingSink.buffer()
            delegate.writeTo(bufferedSink)
            bufferedSink.flush()
        }
    }
}
