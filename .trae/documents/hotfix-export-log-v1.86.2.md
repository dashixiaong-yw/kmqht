# 修复计划：导出日志无可用应用

## 问题

PDA 上没有微信/QQ 等社交软件，`Intent.createChooser(ACTION_SEND)` 弹出「没有应用可执行操作」，日志无法导出。

## 方案选择

| 方案 | 描述 | 复杂度 | 权限要求 |
|:-----|:------|:------:|:---------|
| **A. MediaStore Downloads 写入（推荐）** | 日志文件自动保存到系统「下载」目录，用户打开文件管理器即可找到并分享 | 低 | 无（Android 10+） |
| B. SAF 系统文件选择器 | 弹出系统文件选择器让用户选保存位置 | 中 | 无，但需 ActivityResult Launcher |
| C. 外部文件目录 | 保存到 `externalFilesDir/Downloads`，通知用户路径 | 最低 | 无，但部分文件管理器看不到 |

**选择方案 A**：最用户友好，无需用户选择保存位置，直接存到系统下载目录，打开文件管理器即可看到。

## 实现

**文件**：[SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt#L380-L400)

将导出逻辑改为：

```kotlin
// 导出同步日志
TextButton(
    onClick = {
        val srcFile = java.io.File(appContext.cacheDir, "sync_log.txt")
        if (!srcFile.exists()) return@TextButton
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+: 使用 MediaStore 写入 Downloads
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "sync_log_${System.currentTimeMillis()}.txt")
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = appContext.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        srcFile.inputStream().use { it.copyTo(output) }
                    }
                }
            } else {
                // Android 9 及以下: 写入公共 Downloads
                val destDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                destDir.mkdirs()
                val destFile = java.io.File(destDir, "sync_log_${System.currentTimeMillis()}.txt")
                srcFile.copyTo(destFile, overwrite = true)
            }
            // 提示成功（可用 Snackbar 或者简单的 Toast）
            android.widget.Toast.makeText(appContext, "日志已导出到「下载」文件夹", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(appContext, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    },
    modifier = Modifier.fillMaxWidth()
) {
    Text("导出同步日志", color = TextSecondary, fontSize = 12.sp)
}
```

## 执行时序

```
1. 用户点「导出同步日志」
2. 检查 sync_log.txt 是否存在 → 存在才继续
3. Android 10+: 写入 MediaStore.Downloads（系统下载目录）
   Android 9-:  写入公共 Downloads 目录
4. Toast 提示「日志已导出到「下载」文件夹」
5. 用户打开文件管理器 → 进入「下载」文件夹 → 找到 sync_log_xxx.txt → 长按分享
```

## 验证

1. 点击「导出同步日志」
2. 看到 Toast：日志已导出到「下载」文件夹
3. 打开系统文件管理器，进入「下载」文件夹，看到以 `sync_log_` 开头的 txt 文件
4. 长按文件 → 分享 → 可选择任意方式发送

## 回归风险

| 风险 | 评估 |
|:-----|:------|
| `MediaStore.Downloads` API 可用性 | ✅ Android 10+ 原生支持，PDA 通常为 Android 10-13 |
| `WRITE_EXTERNAL_STORAGE` 权限 | ✅ Android 10+ 使用 MediaStore 无需权限 |
| 文件重名覆盖 | ✅ 文件名带时间戳 `sync_log_${currentTime}.txt`，永不覆盖 |
| `Toast` 在 Composable 中的使用 | ✅ `Toast` 是静态方法，不需要 Activity context |
