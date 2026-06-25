# 添加日志文件清理机制

## 需求
1. 退出登录时删除 `sync_log.txt` 和 `scroll_log.txt`
2. 固定时间自动删除（保留最近 7 天的日志）

## 改动方案

### 改动1：ScrollLogger.kt — 添加 clearLogs + trimOldLogs

```kotlin
object ScrollLogger {
    private const val TAG = "ScrollLogger"
    private const val LOG_FILE = "scroll_log.txt"

    fun appendLog(context: Context, message: String) { ... }

    /** 退出登录或主动清理时删除日志文件 */
    fun clearLogs(context: Context) {
        try {
            val file = File(context.cacheDir, LOG_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "clearLogs失败: ${e.message}")
        }
    }

    /** 按天清理：删除超过 maxAgeDays 天的日志 */
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
```

### 改动2：OrderSyncWorker.kt — 添加 clearLogs + trimOldLogs

```kotlin
companion object {
    private const val TAG = "OrderSyncWorker"
    private const val MAX_RETRY = 3
    private const val LOG_FILE = "sync_log.txt"

    fun appendLog(context: Context, message: String) { ... }

    /** 退出登录或主动清理时删除日志文件 */
    fun clearLogs(context: Context) {
        try {
            val file = File(context.cacheDir, LOG_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) { Log.w(TAG, "clearLogs失败: ${e.message}") }
    }

    /** 按天清理：删除超过 maxAgeDays 天的日志 */
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
        } catch (e: Exception) { Log.w(TAG, "trimByAge失败: ${e.message}") }
    }
}
```

### 改动3：UserRepositoryImpl — 退出登录时清理日志

**注入 Application Context**：

```kotlin
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val apiService: UserApiService,
    private val systemApiService: SystemApiService,
    @Named("encrypted") private val prefs: SharedPreferences,
    @ApplicationContext private val appContext: Context     // ← 新增
) : UserRepository {
```

**新增 import**：
```kotlin
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kuaimai.pda.data.OrderSyncWorker
import com.kuaimai.pda.util.ScrollLogger
```

**在 `logout()` 末尾添加清理**：

```kotlin
override suspend fun logout() {
    val token = getToken()
    clearLocalUser()
    if (token.isNotEmpty()) {
        try {
            withTimeout(5000L) {
                apiService.logout(token)
            }
        } catch (e: Exception) {
            Log.w(TAG, "退出登录API调用失败: ${e.message}")
        }
    }
    // 退出登录时清理日志文件
    OrderSyncWorker.clearLogs(appContext)
    ScrollLogger.clearLogs(appContext)
}
```

### 改动4：App.kt — 启动时清理过期日志

在 `Application.onCreate()` 中调用 trimByAge，需要注入 Context：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 启动时清理超过 7 天未使用的日志文件
        OrderSyncWorker.trimByAge(this)
        ScrollLogger.trimByAge(this)
    }
}
```

## 验证方法

### 退出登录验证
1. 检查是否存在日志文件 → 退出登录 → 确认文件被删除

### 自动清理验证
1. 将 App 系统时间回调 8 天 → 重启 App → 确认日志文件被删除

## 版本号

2.42 → 2.43，构建 APK。
