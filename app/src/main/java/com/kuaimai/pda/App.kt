package com.kuaimai.pda

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kuaimai.pda.util.TimeUtils
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileWriter

/**
 * 快麦取货通 Application
 * Hilt 依赖注入入口
 * 集成ACRA崩溃上报 + ANR检测
 */
@HiltAndroidApp
class App : Application() {

    companion object {
        private const val TAG = "App"
        private const val ANR_THRESHOLD_MS = 5000L
        private const val ANR_CHECK_INTERVAL_MS = 1000L
    }

    // ANR检测
    private val mainHandler = Handler(Looper.getMainLooper())
    private var anrCheckRunnable: Runnable? = null
    private var lastMainThreadTick: Long = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        // 仅初始化Hilt，不做耗时操作（冷启动优化）
        startAnrDetection()
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
     * @param blockDurationMs 阻塞时长（毫秒）
     */
    private fun logAnr(blockDurationMs: Long) {
        try {
            val timestamp = TimeUtils.formatTimestamp(System.currentTimeMillis())

            // 获取主线程堆栈
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
            val dateStr = TimeUtils.formatDate(System.currentTimeMillis())
            val logFile = File(logDir, "anr_$dateStr.log")
            FileWriter(logFile, true).use { it.write(logEntry) }

            Log.w(TAG, "ANR detected: blocked ${blockDurationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "记录ANR日志失败: ${e.message}")
        }
    }
}
