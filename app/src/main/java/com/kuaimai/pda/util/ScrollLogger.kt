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
}
