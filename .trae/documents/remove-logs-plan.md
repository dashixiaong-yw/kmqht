# 移除 Worker 日志 & 滚动日志系统（第二轮审查后完整版）

## 第二轮审查发现的问题

18 处 `run { appendLog(...); return false }` 中 `appendLog` 是**唯一的错误记录方式**。删除后错误静默消失，违反项目规则 24（禁止静默失败）。

**修复**：将 18 处的 `appendLog(...)` 替换为 `Log.w(TAG, "...")`，保持 error guard 行为不变。

**这 18 处的 pattern**：
```kotlin
// 改前
val userRepo = userRepository ?: run {
    appendLog(applicationContext, "completeItem同步失败: userRepository为null")
    return false
}

// 改后
val userRepo = userRepository ?: run {
    Log.w(TAG, "completeItem同步失败: userRepository为null")
    return false
}
```

其余 47 处独立 `appendLog` 调用直接删除（进度日志，非错误日志）。

---

## 改动清单（7 文件）

### 1. 删除 ScrollLogger.kt（整个文件 -51 行）

### 2. OrderSyncWorker.kt（约 -70 行，+18 行 Log.w）

删除：
- `LOG_FILE` 常量 + `appendLog()` + `clearLogs()` + `trimByAge()`（4个成员）
- 47 处独立 `appendLog` 调用
- 18 处 `run` 块中的 `appendLog` 调用

新增（替换 18 处 `appendLog`）：
- 18 处 `Log.w(TAG, "...")` 错误日志

### 3. PickDetailViewModel.kt（约 -21 行）

删除：
- `import android.content.Context`
- `import dagger.hilt.android.qualifiers.ApplicationContext`
- `import com.kuaimai.pda.util.ScrollLogger`
- `@ApplicationContext private val appContext: Context` 参数
- 14 处 `ScrollLogger.appendLog`
- 日志监听协程块（8 行）

保留：
- `import kotlinx.coroutines.flow.collect`（图片预加载仍用）

### 4. PickDetailScreen.kt（约 -3 行）

删除：
- `import com.kuaimai.pda.util.ScrollLogger`
- 2 处 `ScrollLogger.appendLog`

### 5. SettingsScreen.kt（约 -95 行）

删除：
- `import androidx.compose.ui.platform.LocalContext`
- `showLogDialog` + `showScrollLogDialog` 状态变量
- `val appContext = LocalContext.current`
- 同步日志弹窗 if-block
- 滚动日志弹窗 if-block
- "查看同步日志" + "查看滚动日志" 按钮

### 6. App.kt（约 -4 行）

删除：
- `import com.kuaimai.pda.data.OrderSyncWorker`
- `import com.kuaimai.pda.util.ScrollLogger`
- `OrderSyncWorker.trimByAge(this)` + `ScrollLogger.trimByAge(this)`

### 7. UserRepository.kt（约 -6 行）

删除：
- `import android.content.Context`
- `import com.kuaimai.pda.data.OrderSyncWorker`
- `import com.kuaimai.pda.util.ScrollLogger`
- `import dagger.hilt.android.qualifiers.ApplicationContext`
- `@ApplicationContext private val appContext: Context` 参数
- 2 行 clearLogs 调用

---

## 代码量统计

| 文件 | 删除 | 新增 | 净变化 |
|:-----|:----:|:----:|:------:|
| ScrollLogger.kt | -51 | 0 | -51 |
| OrderSyncWorker.kt | -70 | +18 | -52 |
| PickDetailViewModel.kt | -21 | 0 | -21 |
| SettingsScreen.kt | -95 | 0 | -95 |
| App.kt | -4 | 0 | -4 |
| UserRepository.kt | -6 | 0 | -6 |
| PickDetailScreen.kt | -3 | 0 | -3 |
| **总计** | **-250** | **+18** | **-232** |

---

## 安全确认

| 检查项 | 结果 |
|:-----|:----:|
| 18 处 error guard 静默失败 | ✅ 已修复（+18 `Log.w`） |
| ViewModel 构造函数 Hilt 兼容性 | ✅ 通过 |
| UserRepository 构造函数 Hilt 兼容性 | ✅ 通过 |
| SettingsScreen appContext 无外部引用 | ✅ 通过 |
| 跨文件 clearLogs/trimByAge/ScrollLogger 无遗漏 | ✅ 通过 |
| CHANGELOG 无需要修改的历史条目 | ✅ 通过 |

## 版本号

2.45 → 2.46
