# 修复取货单详情视口两个 Bug

## Bug 确认

| Bug | 现象 | 当前代码（v2.40） |
|:----|:-----|:-----------------|
| 1 | 从列表进入详情，视口在最下方 | `LaunchedEffect` 在 LazyColumn 未布局时执行 `scrollToItem(0)`，静默失败 |
| 2 | 添加第7/10/12个商品时不显示在视口中 | `_needScroll++` 先于 Room Flow 发射 → `scrollToItem(0)` 在旧列表上执行 → 新商品到达后被推至视口上方 |

## 根因

**Bug 1**：`LaunchedEffect(orderId, needScroll=MAX_VALUE)` 在首次 composition 时触发，LazyColumn 尚未布局完毕。`scrollToItem(0)` 作为 suspend 函数需要 LazyColumn 完成第一轮布局测量后才能生效，但此时 `layoutInfo.visibleItemsInfo` 为空，scroll 被静默忽略。后续 Room 数据到达、LazyColumn 渲染后初始滚动位置不在顶部。

**Bug 2**：`_executeAddItem` 中 `insertItem(Room)` → `_needScroll++` 之间 Room Flow 尚未异步发射。LaunchedEffect 看到 `needScroll` 变化后立即执行 `scrollToItem(0)`，但此时 `filteredItems` 还是旧数据（新商品不在列表中）。之后 Room Flow 异步发射，新商品插入 `filteredItems` 头部（position 0），LazyLayout 保持视口稳定 → 新商品被推到视口上方不可见。

## 修复方案（2 处改动）

### 改动1：ViewModel (`PickDetailViewModel.kt`) — `_executeAddItem` 等待 Room 数据就绪

在 `insertItem` 之后、`_needScroll++` 之前，等待 `items` StateFlow 包含新商品：

```kotlin
// 改前（L263-271）：
pickOrderRepository.insertItem(item)
val newSupplier = r.supplierName
if (...) { ... }
loadOrder()
_order.value = ...
_needScroll.value = _needScroll.value + 1

// 改后：
pickOrderRepository.insertItem(item)
// 等待 Room Flow 发射包含新商品的数据，再触发滚动
items.first { itemList -> itemList.any { it.skuOuterId == r.skuOuterId } }
val newSupplier = r.supplierName
if (...) { ... }
loadOrder()
_order.value = ...
_needScroll.value = _needScroll.value + 1
```

新增 import：
```kotlin
import kotlinx.coroutines.flow.first
```

> `items.first { ... }` 是挂起函数，会挂起直到 StateFlow 当前值满足条件。Room Flow 异步发射后，items 包含新 sku，条件满足，继续执行后续的 `_needScroll++`。此时 `filteredItems` 已包含新商品在 position 0，`scrollToItem(0)` 才能正确滚动到新商品位置。

### 改动2：Screen (`PickDetailScreen.kt`) — 延迟 1 帧等 LazyColumn 布局

```kotlin
// 改前（L189-193）：
LaunchedEffect(viewModel.orderId, needScroll) {
    if (filteredItems.isNotEmpty()) {
        listState.scrollToItem(0)
    }
}

// 改后：
LaunchedEffect(viewModel.orderId, needScroll) {
    if (filteredItems.isNotEmpty()) {
        delay(1)
        listState.scrollToItem(0)
    }
}
```

> `delay(1)` 让出当前帧，Compose 在下一帧完成 LazyColumn 的初始布局测量。此时 `scrollToItem(0)` 调用时 LazyColumn 已有正确的 `layoutInfo`，scroll 能正常执行。

### 删除无用 import

PickDetailScreen.kt L59-60 的 `snapshotFlow` 和 `first` 不再需要（已被 `if (isNotEmpty())` 替代）：
```kotlin
import androidx.compose.runtime.snapshotFlow  // ← 删除
import kotlinx.coroutines.flow.first           // ← 删除
```

## 时序验证

### Bug 1（首次进入 / 重新进入）

```
Composition 创建 → LaunchedEffect(orderId, MAX_VALUE) 启动
  ↓
if (filteredItems.isNotEmpty()) → false（Room 尚未发射）
  ↓ 跳过 scroll
  ↓ (Room 异步发射，数据到达，重组)
if (filteredItems.isNotEmpty()) → true
  ↓ delay(1) → 让 LazyColumn 完成布局
  ↓ scrollToItem(0) → ✅ 正确滚动到顶部
```

### Bug 2（扫码添加）

```
insertItem(Room) → Room 写入完成
  ↓
items.first { it.contains(newSku) } → 挂起等待
  ↓ (Room 异步发射 → items 包含新 sku → 条件满足)
  ↓
_needScroll++ → LaunchedEffect 重启
  ↓
if (filteredItems.isNotEmpty()) → true（已包含新商品）
  ↓ delay(1)
  ↓ scrollToItem(0) → ✅ 正确滚动到新商品位置
```

## 影响范围

| 场景 | 当前行为 | 修复后 |
|:-----|:---------|:-------|
| 从列表**首次**进入详情 | 底部 | **顶部** ✅ |
| 返回列表**再次**进入同一详情 | 底部 | **顶部** ✅ |
| 扫码添加商品（第1-50个） | 不定（第7/10/12失败） | **全部显示** ✅ |
| 从 Product 返回详情 | 正常（composition 存活） | 不受影响 ✅ |
| 下拉刷新 / 后台同步 | 不触发 needScroll | 不受影响 ✅ |
| 重复扫码滚动 | 独立路径 | 不受影响 ✅ |

## 版本号

2.40 → 2.41，构建 APK。
