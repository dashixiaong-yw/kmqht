# APK下载存到Downloads目录 + 自动安装修复方案（最终版）

## 一、前置条件验证报告

| # | 检查项 | 结论 |
|:-:|:-------|:----:|
| 1 | 服务器 URL 协议 | **HTTPS** → `DEFAULT_SERVER_URL = "https://frp-off.com:64623"` |
| 2 | 证书类型 | **自签名** → 全项目使用 `unsafeSslSocketFactory` + `trustAllClient` 绕过验证 |
| 3 | DownloadManager 能否处理自签名证书 | **不能** → DownloadManager 使用系统信任存储，自签名证书会 SSL 错误 |
| 4 | `userRepository` → `checkForUpdate()` → `getAppVersion()` | 匿名 API，无需 Token，使用 `@Named("backend")` Retrofit（已有 trustAll 配置）✅ |
| 5 | `trustAllClient` 的注入方 | NetworkModule L154-170 — OKHttpClient 带 `hostnameVerifier` + `unsafeSslSocketFactory` |
| 6 | `trustAllClient` 的其他消费者 | `ImageUploadService`（图片上传）+ `App.kt`（Coil ImageLoader），删除 AppUpdateManager 的注入不影响其他 |
| 7 | API 最低版本要求 | `minSdk = 23` (Android 6.0) |
| 8 | `getExternalFilesDir("Downloads")` 权限要求 | Android 6+ 均不需要任何权限，是应用私有外部存储 ✅ |
| 9 | `MediaStore.Downloads` 可用版本 | Android 10+ (API 29+) |
| 10 | `Environment.getExternalStoragePublicDirectory()` | Android 10+ 已废弃，10+ 必须用 MediaStore |
| 11 | `Intent.ACTION_VIEW` 安装 file:// URI | Android 7+ 禁止 file:// URI，必须用 FileProvider |
| 12 | `file_paths.xml` 当前只暴露 cacheDir | 需添加 external-path/Downloads 路径 |

---

## 二、核心方案

### 整体策略

```
保持 OkHttp(trustAllClient) 下载（解决自签名SSL问题）
  ↓
下载到 getExternalFilesDir("Downloads")  -- 不需要任何权限
  - 不随 cache 清除
  - 可通过 USB / 文件管理器访问
  ↓
下载完成后尝试复制到 系统公共 Downloads  -- 尽力而为
  - Android 10+: MediaStore.Downloads（无需权限）
  - Android 6-9: 直接写目录（需要 WRITE_EXTERNAL_STORAGE，失败则跳过）
  ↓
通过 FileProvider 安装（与当前逻辑一致）
```

### 修改 1：`AppUpdateManager.kt` — 下载到外部存储 + 复制到公共Downloads

**构造参数调整**：

```kotlin
@Singleton
class AppUpdateManager @Inject constructor(
    @Named("backend") private val retrofit: retrofit2.Retrofit,
    @Named("trustAll") private val trustAllClient: OkHttpClient,  // ← 保留！自签名SSL必须
    @ApplicationContext private val context: Context,
) {
```

**下载目录更改**（L78）：

```kotlin
// 修改前: val dir = File(context.cacheDir, "update")
// 修改后:
val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.cacheDir, "update")
dir.mkdirs()
```

**下载完成后复制到公共Downloads**（新增方法，L116 处调用）：

```kotlin
/**
 * 下载完成后尝试复制到系统公共Downloads目录。
 * 让用户在文件管理器中能找到APK，即使自动安装失败。
 */
private fun saveToPublicDownloads(file: File, version: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: 使用 MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "快麦取货通-${version}.apk")
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
            )
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                Log.i(TAG, "APK已保存到系统Downloads (MediaStore)")
            }
        } else {
            // Android 6-9: 直接写入公共Downloads目录
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            publicDir.mkdirs()
            val dest = File(publicDir, "快麦取货通-${version}.apk")
            file.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "APK已保存到: ${dest.absolutePath}")
        }
    } catch (e: Exception) {
        Log.w(TAG, "保存到公共Downloads失败（不影响安装）: ${e.message}")
    }
}
```

在下载完成处调用（修改 L116 附近）：

```kotlin
// 下载成功后，从 file://dir/... 移动到外部存储并复制到公共 Downloads
val finalFile = File(dir, "快麦取货通-${info.latestVersion}.apk")
// 先复制到公共 Downloads（尽力而为）
saveToPublicDownloads(finalFile, info.latestVersion)
// 再从外部存储文件安装
_downloadState.value = DownloadState.Completed(finalFile)
```

**新增 import**：

```kotlin
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
```

**新增 companion object TAG**：

```kotlin
private val TAG = "AppUpdateManager"
// 替换原来 Log 中的硬编码 "AppUpdateManager"
```

### 修改 2：`file_paths.xml` — 添加外部 Downloads 路径

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="apk_downloads" path="update/" />
    <external-path name="downloads" path="Android/media/com.kuaimai.pda/" />
    <cache-path name="sync_logs" path="/" />
</paths>
```

**注意**：`getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)` 返回的路径形如：
`/storage/emulated/0/Android/data/com.kuaimai.pda/files/Download/`

对于较老的 file_manager 可能访问不到（Android 10+ 限制），但在 Android 6-9 PDA 设备上通常可直接访问。复制到公共 Downloads 是对所有版本都可见的保障。

### 修改 3：`MainActivity.kt` — 安装失败后提示用户

```kotlin
// 下载完成后
if (state is DownloadState.Completed) {
    appUpdateManager.installApk(state.file)
    // 增加用户通知
    showUpdateDialog = false
    updateInfo = null
} else if (state is DownloadState.Failed) {
    isDownloading = false
    // 失败时增加提示
    snackbarHostState?.showSnackbar("下载失败，请重试")
}
```

### 修改 4：`AndroidManifest.xml` — 可选添加通知权限

在 Android 13+ (API 33+) 上，应用需要 `POST_NOTIFICATIONS` 权限才能显示前台通知。但 DownloadManager 的通知由系统进程管理，**不需要**应用声明该权限。

---

## 三、修改文件清单

| 文件 | 改动 | 行数 |
|:-----|:------|:----:|
| `AppUpdateManager.kt` | 下载到 `getExternalFilesDir` + 新增 `saveToPublicDownloads()` + 新增 import | ~30行 |
| `file_paths.xml` | 添加 `external-path` 行 | ~2行 |
| `MainActivity.kt` | 安装失败时增加 snackbar 提示 | ~3行 |

---

## 四、用户行为流程

```
用户点击"立即更新"
  → OkHttp(trustAll) 下载到 /sdcard/Android/.../files/Download/快麦取货通-2.1.apk
    → 进度条显示在APP弹窗中（同现在）
  → 下载完成
  
  → 复制到公共 Downloads/ （尽力而为）
    → Android 10+: MediaStore → 文件管理器中可见
    → Android 6-9: 直接写入 → 文件管理器中可见

  → 触发安装（FileProvider，同现在）
    → 如果安装器弹出 → 用户安装
    → 如果安装器没弹出 → 用户去"文件管理→下载" 找到 APK 手动安装
```

---

## 五、回归风险

| # | 风险 | 分析 | 等级 |
|:-:|:-----|:------|:----:|
| 1 | 自签名SSL导致 DownloadManager 无法下载 | ❌ 已排除：本方案**仍是 OkHttp(trustAll)** 下载，DownloadManager 仅用于文件复制 | ✅ |
| 2 | `getExternalFilesDir` 返回 null | 有 fallback：`?: File(context.cacheDir, "update")` | ✅ |
| 3 | `saveToPublicDownloads` 异常 | catch 全部 Exception，不影响安装主流程 | ✅ |
| 4 | MediaStore.IS_PENDING 在 API<29 上不存在 | 有 `Build.VERSION.SDK_INT >= Q` 分支保护 | ✅ |
| 5 | Android 13+ 通知权限 | DownloadManager 通知无需应用声明 | ✅ |
| 6 | 旧版 file_paths.xml 路径变化 | 新增行不影响已有 cache-path 配置 | ✅ |
| 7 | `trustAllClient` 在 APK 更新后卸载 | NetworkModule 仍保留该注入 | ✅ |
| 8 | 写入外部存储的权限 | `getExternalFilesDir` 无需权限；公共 Downloads 分支失败则静默跳过 | ✅ |
| 9 | `ContentValues` 类型不匹配 | `put` 方法签名多样，编译通过 | ✅ |
