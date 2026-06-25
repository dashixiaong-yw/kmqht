package com.kuaimai.pda

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.kuaimai.pda.data.OrderSyncWorker
import com.kuaimai.pda.data.api.ImageUploadService
import com.kuaimai.pda.data.api.KuaimaiApiService
import com.kuaimai.pda.data.api.OrderApiService
import com.kuaimai.pda.data.api.SystemApiService
import com.kuaimai.pda.data.db.dao.PickItemDao
import com.kuaimai.pda.data.db.dao.PickOrderDao
import com.kuaimai.pda.data.db.dao.PendingOperationDao
import com.kuaimai.pda.data.db.dao.ProductImageDao
import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.util.ScrollLogger
import com.kuaimai.pda.util.TimeUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Named

/**
 * 快麦取货通 Application
 * Hilt 依赖注入入口
 * 集成ACRA崩溃上报 + ANR检测
 */
@HiltAndroidApp
class App : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "App"
        private const val ANR_THRESHOLD_MS = 5000L
        private const val ANR_CHECK_INTERVAL_MS = 1000L
    }

    /**
     * OrderSyncWorker 的依赖容器（WorkManager 通过无参构造函数实例化 Worker）
     */
    object OrderSyncWorkerDeps {
        @Volatile var pendingOperationDao: PendingOperationDao? = null
        @Volatile var apiService: KuaimaiApiService? = null
        @Volatile var orderApiService: OrderApiService? = null
        @Volatile var authRepository: AuthRepository? = null
        @Volatile var imageUploadService: ImageUploadService? = null
        @Volatile var userRepository: UserRepository? = null
        @Volatile var productImageDao: ProductImageDao? = null
        @Volatile var systemApiService: SystemApiService? = null
        @Volatile var pickOrderDao: PickOrderDao? = null
        @Volatile var pickItemDao: PickItemDao? = null
    }

    @Inject lateinit var pendingOperationDao: PendingOperationDao
    @Inject lateinit var apiService: KuaimaiApiService
    @Inject lateinit var orderApiService: OrderApiService
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var imageUploadService: ImageUploadService
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var productImageDao: ProductImageDao
    @Inject lateinit var systemApiService: SystemApiService
    @Inject lateinit var pickOrderDao: PickOrderDao
    @Inject lateinit var pickItemDao: PickItemDao

    @Inject
    @field:Named("trustAll")
    lateinit var trustAllClient: OkHttpClient

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { trustAllClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.1)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }

    // ANR检测
    private val mainHandler = Handler(Looper.getMainLooper())
    private var anrCheckRunnable: Runnable? = null
    private var lastMainThreadTick: Long = System.currentTimeMillis()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 初始化 OrderSyncWorker 的依赖容器
        OrderSyncWorkerDeps.apply {
            pendingOperationDao = this@App.pendingOperationDao
            apiService = this@App.apiService
            orderApiService = this@App.orderApiService
            authRepository = this@App.authRepository
            imageUploadService = this@App.imageUploadService
            userRepository = this@App.userRepository
            productImageDao = this@App.productImageDao
            systemApiService = this@App.systemApiService
            pickOrderDao = this@App.pickOrderDao
            pickItemDao = this@App.pickItemDao
        }
        startAnrDetection()

        // 启动时清理超过30天的旧图片记录
        ioScope.launch {
            try {
                productImageDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
            } catch (_: Exception) { }
        }

        // 启动时清理超过7天的日志文件
        OrderSyncWorker.trimByAge(this)
        ScrollLogger.trimByAge(this)
    }

    /**
     * 启动ANR检测
     * 主线程阻塞超过5秒则记录到本地文件
     */
    private fun startAnrDetection() {
        val checkRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val diff = now - lastMainThreadTick
                if (diff > ANR_THRESHOLD_MS) {
                    // 主线程阻塞超过5秒，记录ANR
                    logAnr(diff)
                }
                lastMainThreadTick = now
                mainHandler.postDelayed(this, ANR_CHECK_INTERVAL_MS)
            }
        }
        anrCheckRunnable = checkRunnable
        mainHandler.postDelayed(checkRunnable, ANR_CHECK_INTERVAL_MS)
    }

    override fun onTerminate() {
        super.onTerminate()
        anrCheckRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    /**
     * 记录ANR到本地文件
     * 文件I/O在IO线程执行，不阻塞主线程
     * @param blockDurationMs 阻塞时长（毫秒）
     */
    private fun logAnr(blockDurationMs: Long) {
        ioScope.launch {
            try {
                val timestamp = TimeUtils.formatTimestamp(System.currentTimeMillis())

                // 获取主线程堆栈（在检测线程中已执行，此处直接使用循环外捕获的值）
                val stackTrace = Looper.getMainLooper().thread.stackTrace
                    .take(20)
                    .joinToString("\n") { "    at $it" }

                val logEntry = """
                    |[$timestamp] ANR detected (blocked ${blockDurationMs}ms)
                    |Main thread stack:
                    |$stackTrace
                    |
                """.trimMargin()

                // 写入文件
                val logDir = File(getExternalFilesDir(null), "anr_logs")
                if (!logDir.exists()) logDir.mkdirs()
                // 清理超过7天的旧ANR日志
                val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                logDir.listFiles()?.forEach { if (it.lastModified() < sevenDaysAgo) it.delete() }
                val dateStr = TimeUtils.formatDate(System.currentTimeMillis())
                val logFile = File(logDir, "anr_$dateStr.log")
                FileWriter(logFile, true).use { it.write(logEntry) }

                Log.w(TAG, "ANR detected: blocked ${blockDurationMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "记录ANR日志失败: ${e.message}")
            }
        }
    }
}
