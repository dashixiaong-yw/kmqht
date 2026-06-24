package com.kuaimai.pda.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 从通知栏点击"安装"时触发
 * 注册在 AndroidManifest.xml 中
 */
class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val filePath = intent.getStringExtra("apk_path") ?: return
        val apkFile = File(filePath)
        if (!apkFile.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            android.util.Log.e("ApkInstallReceiver", "安装失败", e)
        }
    }
}
