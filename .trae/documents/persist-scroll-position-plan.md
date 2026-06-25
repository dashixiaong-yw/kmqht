# 取货单详情滚动位置持久化方案

## 方案

进入详情时保存滚动位置 → 再次进入时直接恢复到该位置 → 完成/删除时清除。

### 核心机制

**PickDetailViewModel 的 companion object 中维护一个静态 Map**：

```kotlin
companion object {
    private val savedPositions = mutableMapOf<Long, Int>()  // orderId → firstVisibleItemIndex
}
```

因为是 companion object，不会随 ViewModel 销毁而丢失。App 进程重启后自动清零（无持久化需求——纯内存缓存）。

### 改动1：PickDetailScreen.kt — 退出时保存位置

```kotlin
// 在现有 DisposableEffect(Unit) { ... } 中增加保存逻辑
DisposableEffect(viewModel.orderId) {
    onDispose {
        PickDetailViewModel.saveScrollPosition(viewModel.orderId, listState.firstVisibleItemIndex)
    }
}
```

### 改动2：PickDetailViewModel.kt — 保存/恢复/清除方法

```kotlin
companion object {
    private const val TAG = "PickDetailVM"
    private val savedPositions = mutableMapOf<Long, Int>()

    fun saveScrollPosition(orderId: Long, index: Int) {
        savedPositions[orderId] = index
    }

    fun getSavedPosition(orderId: Long): Int? = savedPositions[orderId]

    fun clearSavedPosition(orderId: Long) {
        savedPositions.remove(orderId)
    }
}
```

### 改动3：PickDetailViewModel.kt — syncItemsFromBackend 末尾提供恢复信号

利用一个 StateFlow 标识 "数据已就绪可以恢复位置"：

不再需要，改用更简单的方式：见改动4。

### 改动4：PickDetailScreen.kt — 恢复时机

删除现有的 `snapshotFlow` scrollToItem 逻辑，替换为：

```kotlin
// 数据就绪后恢复滚动位置
LaunchedEffect(viewModel.orderId) {
    // 等待数据到达
    snapshotFlow { filteredItems.size }
        .first { it > 0 }
    // 恢复保存的位置
    val savedPos = PickDetailViewModel.getSavedPosition(viewModel.orderId)
    if (savedPos != null && savedPos < filteredItems.size) {
        listState.scrollToItem(savedPos)
    } else {
        listState.scrollToItem(0)
    }
}
```

### 改动5：PickDetailViewModel.kt — 完成/删除操作时清除

在 `completeItem`、`completeAllItems`、`deleteItem` 中：

```kotlin
// completeItem → 清除缓存（取货单状态变化，用户下次进入看到新状态）
PickDetailViewModel.clearSavedPosition(orderId)

// completeAllItems → 同上
PickDetailViewModel.clearSavedPosition(orderId)

// deleteItem → 同上
PickDetailViewModel.clearSavedPosition(orderId)
```

### 改动6：PickDetailViewModel.kt — refresh 时不清除

`refresh()` 是下拉刷新，不改变取货单完成状态，保留位置。

## 时序验证

**首次进入订单 38，添加 5 个商品 → 退出**：
```
saveScrollPosition(38, 0)  ← 保存
```

**重新进入订单 38**：
```
VM init → syncFromBackend 开始
  ↓ CASCADE 删除 → 恢复 → 数据到达
  ↓ snapshotFlow { filteredItems.size }.first { it > 0 } → 数据就绪
  ↓ getSavedPosition(38) → 0
  ↓ scrollToItem(0) → 直接在顶部，无可见滚动 ✅
```

**扫码添加 → 感觉不到区别**：
```
新商品插入 index 0 → LazyColumn 从 0 漂移到 1
  ↓ LaunchedEffect 没有被触发（因为 orderId 没变，LaunchedEffect 只执行一次）
  ↓ ...等等，这不对
```

问题：`LaunchedEffect(viewModel.orderId)` 只执行一次。扫码添加后 LazyColumn 漂移到 index 1，但 LaunchedEffect 不会再触发。

**修正**：恢复位置后，仍然需要监听 index 漂移。

### 最终方案：恢复 + 监听双保险

```kotlin
// 数据就绪后恢复滚动位置（仅一次）
var restored by remember { mutableStateOf(false) }
LaunchedEffect(viewModel.orderId) {
    snapshotFlow { filteredItems.size }
        .first { it > 0 }
    val savedPos = PickDetailViewModel.getSavedPosition(viewModel.orderId)
    if (savedPos != null && savedPos < filteredItems.size) {
        listState.scrollToItem(savedPos)
    } else {
        listState.scrollToItem(0)
    }
    restored = true
    PickDetailViewModel.clearSavedPosition(viewModel.orderId)
}

// 新商品插入导致的 index 漂移 → 回滚
LaunchedEffect(restored) {
    if (!restored) return@LaunchedEffect
    snapshotFlow { listState.firstVisibleItemIndex }
        .collect { index ->
            if (index > 0 && filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
}
```

- 第一个 LaunchedEffect：数据就绪后恢复保存的位置（无可见滚动，因为 LazyColumn 默认就在 index=0）
- 第二个 LaunchedEffect：`restored=true` 后激活，监听后续扫码添加导致的 index 漂移

## 影响范围

| 场景 | 行为 |
|:-----|:-----|
| 首次进入 | 无保存位置 → scrollToItem(0) |
| 退出再进入 | 已保存位置 0 → scrollToItem(0) → 无滚动 ✅ |
| 滚动到位置 5 → 退出 → 再进入 | save(5) → 恢复 → scrollToItem(5) ✅ |
| 扫码添加商品 | 新商品插入 → index 0→1 → 监听回滚 ✅ |
| 取货单完成后退出再进入 | clearSavedPosition → scrollToItem(0)（取货单已关闭，从顶部开始） |
| 删除商品后退出再进入 | clearSavedPosition → scrollToItem(0) |

## 版本号

2.44 → 2.45
