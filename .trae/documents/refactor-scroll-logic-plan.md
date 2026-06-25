# 重构取货单详情视口滚动逻辑

## 问题

取货单详情页存在两个视口问题，之前都是"打补丁"式修复，这次全面重构：

1. **进入页面时**：Navigation 状态恢复导致视口不在顶部（v2.36 用 LaunchedEffect 修复了，但不够根本）
2. **扫码添加后**：`scrollToItem(0)` 在 Room 数据之前执行，新商品不显示在视口内（本次核心问题）

## 根因

两个问题都由同一个架构缺陷导致：**事件驱动的滚动机制**。

当前架构：
```
ViewModel (协程上下文):
  insertItem(Room DB) → _scrollToTopEvent.emit(Unit)  [立即执行，Room数据未到]
                                              ↓
Screen (Compose 上下文):
  scrollToTopEvent.collectLatest { scrollToItem(0) }  [用旧数据滚动]
```

固有缺陷：
1. ViewModel 的协程与 Compose 的重组周期不同步
2. `SharedFlow` 不保留历史值，且 `emit()` 立即触发收集器
3. Room Flow 的发射需要跨协程调度，比 `emit()` 慢至少 1 帧

## 重构方案

### 原则

- **用 Compose 的 LaunchedEffect 替代事件总线**：LaunchedEffect 在 Compose 重组后执行，天然保证数据已就绪
- **用 StateFlow 替代 SharedFlow**：保留最新值，便于多个触发条件合并
- **单一滚动入口**：合并"进入页面"和"扫码添加后"两个滚动场景

### 具体改动

#### 改动1：ViewModel — 替换滚动事件机制

**删掉**：
```kotlin
// L121-122: 删除整个 scrollToTopEvent
private val _scrollToTopEvent = MutableSharedFlow<Unit>()
val scrollToTopEvent: SharedFlow<Unit> = _scrollToTopEvent.asSharedFlow()
```

**新增**：
```kotlin
// 用 StateFlow 替代 SharedFlow，值递增表示需要滚动到顶部
private val _needScroll = MutableStateFlow(0)
val needScroll: StateFlow<Int> = _needScroll.asStateFlow()
```

**删除 import**：
```kotlin
import kotlinx.coroutines.flow.MutableSharedFlow   // ← 如果不再被其他地方使用
import kotlinx.coroutines.flow.SharedFlow           // ← 同上
import kotlinx.coroutines.flow.asSharedFlow          // ← 同上
```

**改动2：`_executeAddItem()` — 只在成功时触发滚动**

**改前**（L279-283, finally 块）：
```kotlin
} finally {
    _pendingItems.value = _pendingItems.value - barcode
    _scrollToTopEvent.emit(Unit)
}
```

**改后**（L267, try 块末尾，loadOrder() 之后）：
```kotlin
_needScroll.value = _needScroll.value + 1
```
```kotlin
// finally 块只清理 pending 占位
} finally {
    _pendingItems.value = _pendingItems.value - barcode
}
```

#### 改动3：Screen — 合并两个 LaunchedEffect 为一个

**删除**（L129-132）：
```kotlin
// 进入详情页时强制滚动到顶部（覆盖 Navigation 状态恢复导致的视口错位）
LaunchedEffect(viewModel.orderId) {
    listState.scrollToItem(0)
}
```

**删除**（L190-195）：
```kotlin
// 添加完成（成功/失败）后滚动到顶部（数据已就绪）
LaunchedEffect(Unit) {
    viewModel.scrollToTopEvent.collectLatest {
        listState.scrollToItem(0)
    }
}
```

**新增**（在 L127 `val listState` 之后）：
```kotlin
// 统一滚动控制：
// 1. orderId 变化 → 进入新取货单页面（覆盖 Navigation 状态恢复）
// 2. needScroll 变化 → 扫码添加完成（Compose 重组后执行，数据已就绪）
LaunchedEffect(viewModel.orderId, viewModel.needScroll) {
    listState.scrollToItem(0)
}
```

**删除未使用 import**：
```kotlin
import kotlinx.coroutines.flow.collectLatest  // ← 如果不再被其他地方使用
```

### 验证

| 场景 | 触发条件 | 预期行为 |
|:-----|:---------|:---------|
| 首次进入详情页 | init 时 needScroll=0，LaunchedEffect 执行 | 滚动到顶部 ✅ |
| 返回后重新进入 | orderId 未变，needScroll 未变 → LaunchedEffect 不执行 | 但是新 BackStackEntry 意味着新的 composition，LaunchedEffect 会重新执行 ✅ |
| 扫码添加第 N 个产品 | _executeAddItem 成功后 needScroll++ | LaunchedEffect 重新执行 → 新商品在 index 0 → 滚动到顶部 → 用户看到新商品 ✅ |
| 后台同步 | syncItemsFromBackend 不触发 needScroll | 不滚动，用户当前视口不受影响 ✅ |
| 扫码添加失败 | 异常抛出，needScroll 不变 | 不滚动，用户看到错误消息 ✅ |
| 下拉刷新 | refresh() 不触发 needScroll | 不滚动，用户看到刷新后的当前区域 ✅ |

## 版本号

2.36 → 2.37，构建 APK。
