# Android 6.0 PDA OTA 更新深入根因分析

## 问题复现

用户在 Android 6.0 PDA 上：
1. 管理后台分发新版本
2. PDA 启动弹窗提示更新 → 点击「立即更新」
3. 下载弹窗显示「正在下载更新...」→ **几秒后弹窗消失**
4. 通知栏无任何提示（看不到进度/完成/失败）
5. 再次进入 App → 依然提示更新 → 再次点击 → **无反应**

---

## 根因清单（4 个问题链）

### 根因 1：下载后没有任何用户可见的反馈（首要问题）

| 反馈机制 | Android 6.0 PDA 行为 |
|:---------|:---------------------|
| 📱 **弹窗内进度条** | 正常显示，但下载完成后弹窗关闭 |
| 💬 **Toast 提示** | Android 6.0 部分 PDA ROM 屏蔽了 Toast 或用户未察觉 |
| 🔔 **通知栏通知** | **Android 6.0 PDA 定制 ROM 通知系统可能有 bug**，通知不会显示或被系统清除 |
| ❌ **无持久化日志** | 下载完成后/失败后没有留下可追查的痕迹 |

**结论**：**用户的 App 确实下载失败了，但没有任何地方能看到错误信息**。用户以为自己点了没反应。

### 根因 2：SSL/TLS 握手失败导致下载立即报错（最可能的技术原因）

**链路追踪**：

```
PDA 点击"立即更新"
  → backend 返回 downloadUrl = "https://frp-off.com:64623/api/app-version/download"
  → Android 6.0 OkHttp 用 SSLContext("TLS") 连接
  → 服务端（FRP nginx）要求 TLS 1.2，但 cipher suite 不兼容
  → SSLException!
  → 被 catch 捕获 → _downloadState = Failed(msg) → Toast + 通知
  → Toast 用户没看到，通知栏不显示
  → isDownloading 恢复为 false → 按钮恢复"立即更新"
  → 用户再次点击 → 同样的流程 → 同样的失败
```

- `SSLContext.getInstance("TLS")` 在 Android 6.0 上：**默认不启用 TLS 1.2**（需调用 `setEnabledProtocols()`）
- FRP 公网域名 `frp-off.com` 使用 Let's Encrypt 或类似证书，Android 6.0 的 CA 证书列表可能过期
- 即使 `hostnameVerifier { _, _ -> true }` + `unsafeTrustManager`，**SSL 协议协商本身仍然可能失败**

**证据**：如果 HTTPS 走通，就算证书不匹配也能下载（trustAll 已绕过验证）。但如果协议层协商失败（TLS version mismatch / cipher mismatch / SSLHandshakeException），trustAll 也无能为力。

### 根因 3：`lifecycleScope` 生命周期窗口问题

**位置**：[MainActivity.kt#L128-L151](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/MainActivity.kt#L128-L151)

```kotlin
lifecycleScope.launch {
    appUpdateManager.downloadState.collect { state ->
        when (state) {
            is DownloadState.Completed -> { installApk() }
            is DownloadState.Failed -> { isDownloading = false; Toast }
        }
    }
}
```

- `lifecycleScope` 绑定到 Activity 生命周期
- 如果用户在下载过程中按下 Home 键/切换 App/息屏 → Activity 进入 `onStop` → `lifecycleScope` 被取消
- 下载完成后 `emit(Completed)` → **但协程已取消，无人接收**
- 安装和错误提示都不会触发
- 重新进入 App → `LaunchedEffect(Unit)` 重新触发 → 提示更新 → 再次下载 → 再次失败

### 根因 4：`showNotificationFailed` 在 Android 6.0 自定义 ROM 上表现不稳定

```kotlin
// 通知发送成功，但可能在 PDA 的深度定制 ROM 上：
// 1. 通知被 ROM 拦截（PDA 厂商去掉了 NotificationManager 的某些功能）
// 2. 通知短暂闪现后自动清除（系统清理）
// 3. 通知无法弹出（ROM 关了通知灯/屏显通知）
```

---

## 对比：为什么高版本 Android 正常？

| 因素 | Android 6.0 PDA | Android 10+ |
|:-----|:----------------|:------------|
| TLS 协议 | 仅 TLS 1.2（且需手动启用） | TLS 1.2/1.3 默认启用 |
| CA 证书 | 证书列表可能过期 | 证书列表自动更新 |
| 通知系统 | 定制 ROM 可能有 bug | 标准 AOSP 实现 |
| Toast | 可能被 ROM 屏蔽 | 正常显示 |
| Compose Dialog | 正常 | 正常 |
| 用户反馈渠道 | ❌ 仅通知（不可靠）+ Toast（不可靠） | ✅ Toast + 通知都正常 |

---

## 改动方案

### 改动 1：AppUpdateManager — 显式启用 TLS 1.2（解决最可能的下载失败原因）

**文件**：[AppUpdateManager.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt)

在 downloadApk 方法开头，构造 OkHttp 请求时，如果 SDK < 26，尝试启用 TLS 1.2：

```kotlin
fun downloadApk(info: AppVersionResponse) {
    if (!_isDownloading.compareAndSet(false, true)) return
    _downloadState.value = DownloadState.Idle
    Thread {
        try {
            try {
                val dir = File(context.cacheDir, "update")
                dir.mkdirs()
                val apkFile = File(dir, "快麦取货通-${info.latestVersion}.apk")
                // ...缓存检查...

                // 【新增】对 Android 6.0 显式启用 TLS 1.2
                val client = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    enableTls12(trustAllClient)
                } else {
                    trustAllClient
                }
                val request = Request.Builder().url(info.downloadUrl).build()
                val response = client.newCall(request).execute()
                // ...后续代码不变...
            }
        }
    }
}
```

新增方法：

```kotlin
private fun enableTls12(client: OkHttpClient): OkHttpClient {
    return try {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, null)
        val socketFactory = sslContext.socketFactory
        // 显式启用 TLS 1.2（Android 6.0 默认不启用）
        val tls12Factory = object : DelegatingSSLSocketFactory(socketFactory) {
            override fun configureSocket(socket: javax.net.ssl.SSLSocket): javax.net.ssl.SSLSocket {
                socket.enabledProtocols = arrayOf("TLSv1.2")
                return socket
            }
        }
        client.newBuilder()
            .sslSocketFactory(tls12Factory, unsafeTrustManager)
            .build()
    } catch (e: Exception) {
        Log.w(TAG, "启用TLS 1.2失败，使用默认配置: ${e.message}")
        client
    }
}
```

注意：`DelegatingSSLSocketFactory` 是 OkHttp 4.x 的一个内部类，或者可以用更简单的方式——在 `unsafeSslSocketFactory` 基础上包装。

**更简单的替代方案**：直接在 NetworkModule.kt 中修改 `unsafeSslSocketFactory`，让它在所有版本上都显式启用 TLS 1.2：

```kotlin
private val unsafeSslSocketFactory: SSLSocketFactory by lazy {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(unsafeTrustManager), java.security.SecureRandom())
    val defaultFactory = sslContext.socketFactory
    // 显式启用 TLS 1.2 以兼容 Android 6.0
    try {
        object : SSLSocketFactory() {
            // 委托给默认工厂，但启用 TLS 1.2
            private val delegate = defaultFactory
            override fun getDefaultCipherSuites() = delegate.defaultCipherSuites
            override fun getSupportedCipherSuites() = delegate.supportedCipherSuites
            override fun createSocket() = (delegate.createSocket() as javax.net.ssl.SSLSocket).apply {
                enabledProtocols = arrayOf("TLSv1.2", "TLSv1.1", "TLSv1")
            }
            // ...其他 createSocket 重载类似...
        }
    } catch (e: Exception) {
        defaultFactory
    }
}
```

但实际上这个方案更复杂。最好的方案是在 OkHttpClient 的 `connectionSpecs` 中配置 TLS 版本。

### 改动 2：添加持久化下载失败日志

**文件**：[AppUpdateManager.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt)

在 catch 块中记录错误到本地文件，供后续查看：

```kotlin
} catch (e: Exception) {
    Log.e(TAG, "下载APK失败", e)
    val msg = e.message ?: "未知错误"
    // 【新增】持久化错误日志到文件，供排查
    logDownloadError(msg, e)
    _downloadState.value = DownloadState.Failed(msg)
    showNotificationFailed(msg)
}
```

新增方法：

```kotlin
private fun logDownloadError(message: String, e: Exception) {
    try {
        val logFile = File(context.cacheDir, "update/ota_error.log")
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val detail = "$time | ERROR | $message | ${e.javaClass.simpleName}: ${e.message}"
        logFile.appendText(detail + "\n")
    } catch (_: Exception) {}
}
```

### 改动 3：Dialog 增加持久化状态——下载失败后不关闭弹窗（v2.17 已部分实现）

v2.17 中已经改了：当 `installApk` 返回 false 时，对话框不关闭、`isDownloading` 重置。但**下载失败时弹窗仍然允许用户点击"稍后再说"关闭**。我们可以让弹窗在下载失败后显示错误信息，而不是消失后用户需要靠 Toast 感知。

### 改动 4：MainActivity 弹窗增加下载失败错误展示

**文件**：[MainActivity.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/MainActivity.kt)

新增一个 `downloadErrorMessage` 状态变量，下载失败时设置并展示：

```kotlin
var downloadErrorMessage by remember { mutableStateOf<String?>(null) }

// 在弹窗的 text 部分：
if (downloadErrorMessage != null) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "下载失败: $downloadErrorMessage",
        color = Color(0xFFDC2626),
        fontSize = 13.sp
    )
    Text(
        "请检查网络连接后重新尝试",
        color = Color(0xFF666666),
        fontSize = 12.sp
    )
}
```

在 collect Failed 状态时：

```kotlin
is DownloadState.Failed -> {
    isDownloading = false
    downloadErrorMessage = state.message
}
```

---

## 改动清单

| 文件 | 改动 | 影响 |
|:-----|:-----|:-----|
| AppUpdateManager.kt | TrustAllClient 包装启用 TLS 1.2 | 解决最可能的 Android 6.0 SSL 握手失败 |
| AppUpdateManager.kt | 增加 `logDownloadError()` 持久化错误日志 | 方便排查，写入 cacheDir/update/ota_error.log |
| MainActivity.kt | 弹窗内显示下载错误信息（红色文字） | 用户能直接看到错误原因，不再无感知 |

**涉及文件数**：2 个（只改 Android 端，不改后端）

---

## 验证

| 场景 | 预期 |
|:-----|:-----|
| 正常下载 + 安装 | 弹窗消失，调用系统安装器（成功率不变） |
| SSL 握手失败（Android 6.0） | 弹窗不关闭，显示红色错误文字：「下载失败: SSL handshake failed」 |
| 网络不可达 | 弹窗不关闭，显示红色错误文字 + 操作指南 |
| 任何下载异常 | 错误写入 `ota_error.log`，管理后台可查看（未来可扩展此文件导出） |

---

## 不涉及的

- 不改后端代码
- 不引入新依赖
- 不修改版本号管理
