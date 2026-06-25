# 添加取货单详情视口滚动日志系统

## 目的

记录所有视口滚动相关事件的精确时间戳，用户运行复现两个 bug 后将日志发回，直接分析根因。

## 改动方案

### 改动1：PickDetailViewModel — 添加 `appendLog` 方法 + 关键事件打点

**在 `companion object` 中添加 `appendLog`**（与 OrderSyncWorker 完全相同的模式）：

```kotlin
companion object {
    private const val TAG = "PickDetailVM"
    private const val LOG_FILE = "scroll_log.txt"

    fun appendLog(context: Context, message: String) {
        try {
            val file = File(context.cacheDir, LOG_FILE)
            val now = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$now] $message\n"
            val existing = if (file.exists()) file.readLines() else emptyList()
            val lines = if (existing.size >= 500) existing.drop(existing.size - 250) else existing
            file.writeText(lines.joinToString("\n") + "\n" + line)
        } catch (e: Exception) { Log.w(TAG, "appendLog失败: ${e.message}") }
    }
}
```

**在以下关键位置加入打点日志：**

| 位置 | 日志内容 | 行号 |
|:-----|:---------|:----:|
| `init { }` 开头 | `"VM init, orderId=$orderId, needScroll初始值=${_needScroll.value}"` | ~L124 |
| `items` StateFlow 定义后，新增一个 emit 监听 | `"items emit, size=${items.value.size}"` — 用 `snapshotFlow` | ~L71 之后 |
| `loadOrder()` 中获取到 order 后 | `"loadOrder完成, items size=${items.value.size}"` | ~L150 |
| `syncItemsFromBackend()` 开始 | `"syncItemsFromBackend 开始"` | ~L447 |
| `syncItemsFromBackend()` 结束 | `"syncItemsFromBackend 完成, items size=${items.value.size}"` | ~L473 |
| `pickOrderRepository.insertItem()` 之前 | `"insertItem 开始, sku=$barcode"` | ~L263 |
| `pickOrderRepository.insertItem()` 之后 | `"insertItem 完成, sku=$barcode"` | ~L263 后 |
| `_needScroll.value++` 之前 | `"needScroll 准备递增: ${_needScroll.value} → ${_needScroll.value + 1}"` | ~L271 |
| `_needScroll.value++` 之后 | `"needScroll 已递增: ${_needScroll.value}"` | ~L271 后 |
| 新增：items.first{...} 完成 | 暂不添加（是本次修复的内容） | — |

**新增 import：**
```kotlin
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

### 改动2：PickDetailScreen — LaunchedEffect 关键点打点

在 LaunchedEffect 内部加入：

```kotlin
LaunchedEffect(viewModel.orderId, needScroll) {
    val appContext = context.applicationContext
    PickDetailViewModel.appendLog(appContext, "LaunchedEffect触发, orderId=${viewModel.orderId}, needScroll=$needScroll")
    PickDetailViewModel.appendLog(appContext, "filteredItems size=${filteredItems.size}, isNotEmpty=${filteredItems.isNotEmpty()}")
    PickDetailViewModel.appendLog(appContext, "listState.firstVisibleItemIndex=${listState.firstVisibleItemIndex}")
    
    if (filteredItems.isNotEmpty()) {
        delay(1)
        PickDetailViewModel.appendLog(appContext, "scrollToItem(0) 即将执行")
        listState.scrollToItem(0)
        PickDetailViewModel.appendLog(appContext, "scrollToItem(0) 执行完成, firstVisibleItemIndex=${listState.firstVisibleItemIndex}")
    } else {
        PickDetailViewModel.appendLog(appContext, "filteredItems为空, 跳过scroll")
    }
}
```

### 改动3：PickDetailViewModel — 监听 items 每次发射

在 `init` 块中新增一个日志监听协程：

```kotlin
// 日志：监听 items 每次发射
viewModelScope.launch {
    items.collect { itemList ->
        appendLog(appContext, "items StateFlow emit, size=${itemList.size}")
    }
}
```

需要 ViewModel 持有 `Context` 或 `Application`：

```kotlin
@HiltViewModel
class PickDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val application: Application,  // ← 新增
    ...
```

或者直接用 `androidx.lifecycle.viewModelScope` 已可访问 Application，用 `getApplication()`：

Actually in Hilt `@HiltViewModel` ViewModels can inject `Application`:

```kotlin
class PickDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val application: Application,
    ...
```

Wait, but the ViewModel constructor already has many parameters. Let me check...

Actually, I can just pass `context` from the Screen to the ViewModel's log method, similar to how OrderSyncWorker does it (it receives `context` as a parameter). So I don't need to inject Application.

The ViewModel's `appendLog(context, message)` is called from the Screen's `LaunchedEffect` where `context` is available via `LocalContext.current`.

But for logging in the ViewModel's `init` block (where there's no `context`), I can just use `Log.d` (Android Logcat). That's simpler and doesn't require injection.

Actually, the best approach: use Android `Log.d` (Logcat) in the ViewModel's coroutines (since we can see it with `adb logcat`), and use the `appendLog` file-based logging only in the Screen where context is available.

Wait no, the user wants to run the app on the PDA, reproduce the bug, and send the log. They can't run `adb logcat` on a PDA. So the file-based log is necessary.

Let me provide the context from the Screen. I'll add an `appContext` field to the ViewModel.

Actually, looking at the code structure again, the ViewModel doesn't need the application context injected. I can just pass context from the Screen to static `appendLog`. But for ViewModel-side logging (init block, Flow collectors), I need context. 

The simplest approach: pass context from Screen's `context.applicationContext` to a ViewModel method that stores it, then use it for logging.

Or better: Use a static/companion object approach. Have `appendLog` be in a separate utility class. The Screen calls it, and the ViewModel calls it via a callback.

Actually the simplest clean approach: make `appendLog` a top-level function in a new file `ScrollLogger.kt`:

```kotlin
package com.kuaimai.pda.util

object ScrollLogger {
    private const val LOG_FILE = "scroll_log.txt"
    
    fun appendLog(context: Context, message: String) {
        // same as OrderSyncWorker's appendLog
    }
}
```

Then both ViewModel and Screen can call `ScrollLogger.appendLog(context, message)`.

The ViewModel needs context. I can pass it: In the Screen, after creating the ViewModel, set the context on it. Or, I can make the ViewModel accept an `Application` parameter.

Actually, the ViewModel receives `context` as `@ApplicationContext` in Hilt. Let me check if any existing ViewModel does this...

Actually, let me think about this differently. The cleanest approach: **modify `PickDetailViewModel` to inject `Application`** and store a reference for logging.

Or even simpler: Don't modify the ViewModel constructor. Instead, have the Screen's `LaunchedEffect` pass the context to a ViewModel method that stores it, e.g.:

Wait, ViewModel shouldn't hold references to Context that can leak. But `Application` is fine.

OK, let me just go with the simplest approach that works: a standalone `ScrollLogger` object. The Screen passes `context.applicationContext` to it from `LaunchedEffect`. For ViewModel-side logging, I'll add `context` as a parameter to the methods that need it (like `onBarcodeScanned` which already exists, and I can capture context from there).

Actually, looking at this more carefully, `onBarcodeScanned` doesn't receive context. The simplest approach: just pass context from Screen to a ViewModel init method.

Let me just write a clean plan. I'll use a `ScrollLogger` object and pass context from the Screen.

Actually, the USER's request is pretty simple — just add detailed logging. Let me not overcomplicate this. I'll:

1. Create a `ScrollLogger.kt` in `util/` package
2. Add logging calls to PickDetailViewModel at key points (using a context that's stored when the ViewModel first starts — I can pass it from the Screen via an `init` method or use `applicationContext` from a `@ApplicationContext` injection)
3. Add logging calls to PickDetailScreen's LaunchedEffect

For the ViewModel context issue, the cleanest solution: inject `Application` into the ViewModel. Let me check if Hilt supports this...

```kotlin
class PickDetailViewModel @Inject constructor(
    ...,
    private val application: Application
) : ViewModel()
```

Yes, Hilt can inject `Application` into ViewModels. Let me just do this.

Actually, that might break the existing Hilt binding. Let me check if `Application` is available as a default binding in Hilt... Yes, `Application` is a default component in Hilt's `@SingletonComponent`.

But `@HiltViewModel` uses `@ViewModelComponent` which also has access to `Application` via `@ApplicationContext`. So:

```kotlin
@HiltViewModel
class PickDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val orderApiService: OrderApiService,
    private val pickOrderRepository: PickOrderRepository,
    private val scannerManager: ScannerManager,
    private val imageRepository: ImageRepository,
    private val userRepository: UserRepository,
    @Named("encrypted") private val prefs: SharedPreferences,
    @ApplicationContext private val appContext: Context  // ← 新增
) : ViewModel()
```

But wait, this changes the constructor signature which will break Hilt unless a binding for `@ApplicationContext Context` exists. It should, since Hilt for Android provides `@ApplicationContext Context` by default.

Let me just go ahead and write the plan.<｜end▁of▁thinking｜>好，现在写方案：

<｜｜DSML｜｜tool_calls>
<｜｜DSML｜｜invoke name="Write">
<｜｜DSML｜｜parameter name="content" string="true"># 添加取货单详情视口滚动日志系统

## 目的

记录所有视口滚动相关事件的精确时间戳+状态，用户运行复现两个 bug 后将日志发回，直接分析根因。

## 改动

### 改动1：新增 `ScrollLogger.kt`

**文件**：`app/src/main/java/com/kuaimai/pda/util/ScrollLogger.kt`

与 `OrderSyncWorker.appendLog` 完全相同的模式，日志文件独立为 `scroll_log.txt`：

```kotlin
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
```

### 改动2：PickDetailViewModel — 关键事件打点 + 注入 Application

**注入 Application**：

```kotlin
@HiltViewModel
class PickDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    ...
    private val userRepository: UserRepository,
    @Named("encrypted") private val prefs: SharedPreferences,
    @ApplicationContext private val appContext: Context  // ← 新增
) : ViewModel() {
```

**新增 import**：
```kotlin
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kuaimai.pda.util.ScrollLogger
```

首先需要确认 `@ApplicationContext` 在 Hilt 中默认可用。检查项目是否用了 Hilt...

项目用了 `@HiltViewModel` + `@Inject constructor`，且已有 `@Named("encrypted")`，说明 Hilt 已正确配置。`@ApplicationContext` 是 Hilt 的默认 qualifier，无需额外配置。

**打点位置**：

| 位置 | 日志内容 |
|:-----|:---------|
| `init { }` 开头 | `"VM init, orderId=$orderId, needScroll初始值=${_needScroll.value}"` |
| `init { }` — 监听 items 每次发射 | `"items emit: size=${itemList.size}"` |
| `loadOrder()` 完成 | `"loadOrder完成, orderNo=${_order.value?.orderNo}"` |
| `insertItem()` 之前 | `"insertItem 开始, sku=$barcode"` |
| `insertItem()` 之后 | `"insertItem 返回"` |
| `_needScroll.value++` 之前 | `"needScroll 即将++: ${_needScroll.value} → ${_needScroll.value + 1}"` |
| `_needScroll.value++` 之后 | `"needScroll 已++: ${_needScroll.value}"` |
| `syncItemsFromBackend()` 开始 | `"syncFromBackend 开始"` |
| `syncItemsFromBackend()` 结束 | `"syncFromBackend 完成"` |
| `finally` 清理 pending | `"finally: pending移除, sku=$barcode"` |

### 改动3：PickDetailScreen — LaunchedEffect 精确打点

```kotlin
LaunchedEffect(viewModel.orderId, needScroll) {
    val ctx = context.applicationContext
    ScrollLogger.appendLog(ctx, "=== LaunchedEffect 触发, orderId=${viewModel.orderId}, needScroll=$needScroll ===")
    ScrollLogger.appendLog(ctx, "filteredItems: size=${filteredItems.size}, isNotEmpty=${filteredItems.isNotEmpty()}")
    ScrollLogger.appendLog(ctx, "listState: index=${listState.firstVisibleItemIndex}, offset=${listState.firstVisibleItemScrollOffset}")
    
    if (filteredItems.isNotEmpty()) {
        delay(1)
        ScrollLogger.appendLog(ctx, "scrollToItem(0) 即将执行")
        listState.scrollToItem(0)
        ScrollLogger.appendLog(ctx, "scrollToItem(0) 完成, index=${listState.firstVisibleItemIndex}")
    } else {
        ScrollLogger.appendLog(ctx, "filteredItems为空, 跳过scroll")
    }
}
```

### 改动4：SettingsScreen — 添加"查看滚动日志"入口

在"查看同步日志"按钮旁边新增一个按钮：

```kotlin
// 查看滚动日志
TextButton(
    onClick = { showScrollLogDialog = true },
    modifier = Modifier.fillMaxWidth()
) {
    Text("查看滚动日志", color = TextSecondary, fontSize = 12.sp)
}
```

和对应的弹窗（复用同步日志弹窗的代码逻辑，仅日志文件名不同）：

```kotlin
if (showScrollLogDialog) {
    val logContent = try {
        java.io.File(appContext.cacheDir, "scroll_log.txt").readText()
    } catch (_: Exception) { "暂无滚动日志" }

    AlertDialog(
        onDismissRequest = { showScrollLogDialog = false },
        shape = RoundedCornerShape(16.dp),
        title = { Text("滚动日志") },
        text = {
            Column {
                Text(
                    text = logContent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("可复制后发送", fontSize = 12.sp, color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = { showScrollLogDialog = false }) { Text("关闭") }
        }
    )
}
```

### 预期日志输出示例

假设用户操作：进入详情页 → 扫码添加 3 个商品 → 返回列表 → 再进入 → 扫码第 4 个商品

```
[06-25 10:00:01.000] VM init, orderId=123, needScroll初始值=2147483647
[06-25 10:00:01.005] loadOrder完成, orderNo=CG20250625001
[06-25 10:00:01.010] syncFromBackend 开始
[06-25 10:00:01.050] items emit: size=0
[06-25 10:00:01.080] items emit: size=5
[06-25 10:00:01.081] syncFromBackend 完成
[06-25 10:00:01.082] === LaunchedEffect 触发, orderId=123, needScroll=2147483647 ===
[06-25 10:00:01.083] filteredItems: size=5, isNotEmpty=true
[06-25 10:00:01.083] listState: index=4, offset=0          ← 异常！数据到达后 index=4 不是 0
[06-25 10:00:01.084] scrollToItem(0) 即将执行
[06-25 10:00:01.100] scrollToItem(0) 完成, index=0
...
[06-25 10:00:15.000] insertItem 开始, sku=ITEM006
[06-25 10:00:15.200] insertItem 返回
[06-25 10:00:15.201] needScroll 即将++: 2147483647 → -2147483648
[06-25 10:00:15.202] needScroll 已++: -2147483648
[06-25 10:00:15.203] === LaunchedEffect 触发, orderId=123, needScroll=-2147483648 ===
[06-25 10:00:15.204] filteredItems: size=5, isNotEmpty=true  ← 此时还是5个！新商品未到！
[06-25 10:00:15.205] scrollToItem(0) 即将执行
[06-25 10:00:15.206] scrollToItem(0) 完成, index=0
[06-25 10:00:15.250] items emit: size=6                     ← 新商品到达，但已错过 scroll
```

## 使用流程

1. 安装 APK → 打开 PDA
2. 进入取货单详情 → 操作（正常浏览、扫码添加） → 尽可能复现两个 bug
3. 进入「设置」→ 拉到最下方 → 点击「查看滚动日志」
4. 点击「复制」→ 粘贴给我

## 版本号

2.40 → 2.41（日志版，构建 APK）
