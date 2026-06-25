package com.kuaimai.pda.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
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
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "apk_download"
        private const val INSTALL_REQUEST_CODE = 1002
    }

    private val systemApi = retrofit.create(SystemApiService::class.java)

    private val _isDownloading = AtomicBoolean(false)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用更新下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "新版本 APK 下载进度通知"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

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
                Log.w(TAG, "检查更新失败: ${e.message}")
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
                        showNotificationCompleted(apkFile, info.latestVersion)
                        return@Thread
                    }
                    val request = Request.Builder().url(info.downloadUrl).build()
                    val response = trustAllClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        val msg = "下载失败: HTTP ${response.code}"
                        _downloadState.value = DownloadState.Failed(msg)
                        showNotificationFailed(msg)
                        return@Thread
                    }
                    val body = response.body ?: run {
                        _downloadState.value = DownloadState.Failed("响应体为空")
                        showNotificationFailed("响应体为空")
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
                                    showNotificationProgress(progress, info.latestVersion)
                                }
                            }
                        }
                    }
                    if (info.apkSize > 0 && apkFile.length() != info.apkSize) {
                        apkFile.delete()
                        _downloadState.value = DownloadState.Failed("文件大小不匹配")
                        showNotificationFailed("文件大小不匹配")
                        return@Thread
                    }
                    _downloadState.value = DownloadState.Completed(apkFile)
                    showNotificationCompleted(apkFile, info.latestVersion)
                } catch (e: Exception) {
                    Log.e(TAG, "下载APK失败", e)
                    val msg = e.message ?: "未知错误"
                    _downloadState.value = DownloadState.Failed(msg)
                    showNotificationFailed(msg)
                }
            } finally {
                _isDownloading.set(false)
            }
        }.start()
    }

    fun installApk(apkFile: File): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    return false
                }
            }
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
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "FileProvider URI 不被识别，尝试 file:// URI 降级", e)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                try {
                    apkFile.setReadable(true, false)
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    return true
                } catch (e2: Exception) {
                    Log.e(TAG, "file:// URI 降级也失败", e2)
                }
            }
            showNotificationFailed("未找到安装器，请到系统文件管理器手动安装")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "安装APK失败", e)
            return false
        }
    }

    private fun showNotificationProgress(progress: Float, version: String) {
        mainHandler.post {
            try {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("正在下载快麦取货通 v${version}")
                    .setContentText("${(progress * 100).toInt()}%")
                    .setProgress(100, (progress * 100).toInt(), false)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun showNotificationCompleted(apkFile: File, version: String) {
        mainHandler.post {
            try {
                val installIntent = Intent(context, ApkInstallReceiver::class.java).apply {
                    putExtra("apk_path", apkFile.absolutePath)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    INSTALL_REQUEST_CODE,
                    installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("快麦取货通 v${version} 下载完成")
                    .setContentText("点击安装更新")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setSilent(false)
                    .build()
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun showNotificationFailed(message: String) {
        mainHandler.post {
            try {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("下载失败")
                    .setContentText(message.take(60))
                    .setAutoCancel(true)
                    .setSilent(false)
                    .build()
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
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
