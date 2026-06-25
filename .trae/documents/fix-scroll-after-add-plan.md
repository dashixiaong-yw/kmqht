# 修复扫码添加后新商品不自动显示的问题

## 问题描述

PDA 一屏显示 3 个商品。添加第 4 个时，第 3 个（最早添加的已取货商品）消失；添加第 5 个时，第 4 个可见但第 5 个需向上滚动。**新添加的商品不能自动显示在屏幕上。**

## 根因排查

### 链路分析

```
扫码添加 → insertItem(Room DB) → finally: _scrollToTopEvent.emit(Unit)
                                      ↓
                                  scrollToItem(0)  ← 用**旧数据**滚动
                                      ↓  (<1ms)
                                  Room Flow emit → items StateFlow 更新
                                      ↓
                                  Compose 重组 → filteredItems 排序
                                      ↓
                                  新 item 插入 index 0
```

核心问题：`scrollToItem(0)` 在 `_executeAddItem()` 的 `finally` 块中立即执行，但此时 Room Flow **尚未发射新数据**。`scrollToItem(0)` 滚动到的是**旧数据列表**的 index 0。

当 Room 数据最终到达、新 item 被插入到 index 0 时，LazyColumn **保持当前视口不动**，把旧 item（之前在第 0 位的商品）固定在视口上，新 item 就出现在视口上方 — 用户需要手动向上滚动才能看到。

### 关键证据

- `items` StateFlow 的 `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())` — Room 的 Flow 发射需要：InvalidationTracker 调度 → 协程切换 → re-query → distinctUntilChanged → stateIn emit → Compose collectAsState → 下一帧重组
- `_scrollToTopEvent.emit(Unit)` → `scrollToItem(0)` 在**同一协程、同一线程**立即执行，比 Room Flow 到达快至少 1 帧

### 之前 LaunchedEffect 修复为什么不够

之前新增的 `LaunchedEffect(viewModel.orderId) { scrollToItem(0) }` 解决了**首次进入页面**时 Navigation 状态恢复导致的错位，但**不能解决本次问题** — 本问题发生在页面已打开时的**扫码添加过程**中，与 Navigation 状态恢复无关。

## 修复方案

**改动**：[PickDetailViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt) — `_executeAddItem()` 方法

### 原理

在 `insertItem()` 之后，**等待 Room Flow 将新数据发射到 `items` StateFlow**，**再**触发滚动。这样 `scrollToItem(0)` 执行时，新 item 已经出现在 index 0，LazyColumn 正确滚动到新商品位置。

### 具体改动

**当前代码**（L260-283）：
```kotlin
pickOrderRepository.insertItem(item)  // ← Room INSERT
val newSupplier = r.supplierName
if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
    _suppliers.value = _suppliers.value + newSupplier
}
loadOrder()
_order.value = _order.value?.copy(totalCount = (_order.value?.totalCount ?: 0) + 1)
// ... 接着是 catch ... finally ...

// 在 finally 中：
_pendingItems.value = _pendingItems.value - barcode
_scrollToTopEvent.emit(Unit)
```

**改为**：
```kotlin
pickOrderRepository.insertItem(item)
val newSupplier = r.supplierName
if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
    _suppliers.value = _suppliers.value + newSupplier
}
loadOrder()
_order.value = _order.value?.copy(totalCount = (_order.value?.totalCount ?: 0) + 1)
// 等待 Room Flow 将新数据发射到 UI 层，再安全地滚动
val newSku = r.skuOuterId
items.first { itemList -> itemList.any { it.skuOuterId == newSku } }
_scrollToTopEvent.emit(Unit)
// ... 接着是 catch ...

// catch 中改为不触发滚动：
} catch (e: Exception) {
    // ... 处理错误 ...
    throw e  // 确保外层 Mutex 能感知异常
}

// finally 只清理 pending：
} finally {
    _pendingItems.value = _pendingItems.value - barcode
}
```

### 为什么这样做

- **`items.first { ... }`** — 挂起直到 `items` StateFlow 的当前值包含新插入的 SKU。此时 Room Flow 已经发射、Compose 已经重组、新 item 在 filteredItems 的 index 0
- **`_scrollToTopEvent.emit(Unit)` 在 `first{}` 之后** — 确保 `scrollToItem(0)` 执行时新数据已在 LazyColumn 中
- **`finally` 只清理 `_pendingItems`** — 不 emit 滚动事件，因为失败时不需要滚动

### 影响范围

| 功能 | 影响 |
|:-----|:-----|
| 扫码添加成功 | ✅ `first{}` 等待 Room 数据 → 滚动 → 新商品自动可见 |
| 扫码添加失败 | ✅ `throw e` 到外层 catch，finally 只清理 pending，无多余滚动 |
| 重复扫码 | ✅ 409 异常转到 `_executeAddItem` 外层 catch，syncItemsFromBackend 后不触发 `first{}` |
| 连续快速扫码 | ✅ Mutex 串行化，前一个完成 `first{}` + scroll 后才开始下一个 |

## 版本号

2.36 → 2.37，构建 APK。
