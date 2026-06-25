package com.kuaimai.pda.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScrollLogger {
    private const val TAG = "ScrollLogger"
    private const val LOG_FILE = "scroll_log.txt"

    fun appendLog(context: Context, message: String) {
        try {
            val file = File(context.cacheDir, LOG_FILE)
            val now = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$now] $message\n"
            val existing = if (file.exists()) file.readLines() else emptyList()
            val lines = if (existing.size >= 500) existing.drop(existing.size - 250) else existing
            file.writeText(lines.joinToString("\n") + "\n" + line)
        } catch (e: Exception) {
            Log.w(TAG, "appendLog失败: ${e.message}")
        }
    }

    fun clearLogs(context: Context) {
        try {
            val file = File(context.cacheDir, LOG_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "clearLogs失败: ${e.message}")
        }
    }

    fun trimByAge(context: Context, maxAgeDays: Int = 7) {
        try {
            val file = File(context.cacheDir, LOG_FILE)
            if (file.exists()) {
                val lastModified = file.lastModified()
                val cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 60 * 60 * 1000
                if (lastModified < cutoff) {
                    file.delete()
                    Log.i(TAG, "日志文件超过${maxAgeDays}天，已自动清理")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "trimByAge失败: ${e.message}")
        }
    }
}
