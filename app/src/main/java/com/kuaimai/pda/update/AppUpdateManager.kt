package com.kuaimai.pda.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.kuaimai.pda.BuildConfig
import com.kuaimai.pda.data.api.SystemApiService
import com.kuaimai.pda.data.api.dto.AppVersionResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class CheckResult {
    data class HasUpdate(val info: AppVersionResponse) : CheckResult()
    data object NoUpdate : CheckResult()
    data class CheckError(val message: String) : CheckResult()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

@Singleton
class AppUpdateManager @Inject constructor(
    @Named("backend") private val retrofit: retrofit2.Retrofit,
    @Named("trustAll") private val trustAllClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val systemApi = retrofit.create(SystemApiService::class.java)

    private val _isDownloading = AtomicBoolean(false)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    suspend fun checkForUpdate(): CheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = systemApi.getAppVersion()
                if (!response.success || response.latestVersion.isBlank()) {
                    return@withContext CheckResult.NoUpdate
                }
                val currentVersion = BuildConfig.VERSION_NAME
                if (compareVersions(response.latestVersion, currentVersion) > 0) {
                    CheckResult.HasUpdate(response)
                } else {
                    CheckResult.NoUpdate
                }
            } catch (e: Exception) {
                Log.w("AppUpdateManager", "检查更新失败: ${e.message}")
                CheckResult.CheckError(e.message ?: "检查更新失败")
            }
        }
    }

    fun downloadApk(info: AppVersionResponse) {
        if (!_isDownloading.compareAndSet(false, true)) return
        _downloadState.value = DownloadState.Idle
        Thread {
            try {
                try {
                    val dir = File(context.cacheDir, "update")
                    dir.mkdirs()
                    val apkFile = File(dir, "快麦取货通-${info.latestVersion}.apk")
                    if (apkFile.exists() && info.apkSize > 0 && apkFile.length() == info.apkSize) {
                        _downloadState.value = DownloadState.Completed(apkFile)
                        return@Thread
                    }
                    val request = Request.Builder().url(info.downloadUrl).build()
                    val response = trustAllClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        _downloadState.value = DownloadState.Failed("下载失败: HTTP ${response.code}")
                        return@Thread
                    }
                    val body = response.body ?: run {
                        _downloadState.value = DownloadState.Failed("响应体为空")
                        return@Thread
                    }
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    val buffer = ByteArray(8192)
                    FileOutputStream(apkFile).use { output ->
                        body.byteStream().use { input ->
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    val progress = downloadedBytes.toFloat() / totalBytes
                                    _downloadState.value = DownloadState.Downloading(progress, downloadedBytes, totalBytes)
                                }
                            }
                        }
                    }
                    if (info.apkSize > 0 && apkFile.length() != info.apkSize) {
                        apkFile.delete()
                        _downloadState.value = DownloadState.Failed("文件大小不匹配")
                        return@Thread
                    }
                    _downloadState.value = DownloadState.Completed(apkFile)
                } catch (e: IOException) {
                    Log.e("AppUpdateManager", "下载APK失败", e)
                    _downloadState.value = DownloadState.Failed("下载失败: ${e.message}")
                }
            } finally {
                _isDownloading.set(false)
            }
        }.start()
    }

    fun installApk(apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "安装APK失败", e)
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
