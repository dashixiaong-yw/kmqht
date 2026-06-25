# 修复重新进入取货单详情视口在底部（Bug1）

## 日志证据

```
[04.441] syncFromBackend 开始
[04.529] LaunchedEffect触发 → filteredSize=0 → 跳过scroll     ← needScroll=MAX_VALUE
[04.589] items 发射: size=11  (已有数据)
[04.790] items emit: size=0  ← insertOrder(REPLACE)导致CASCADE删除全部明细
[04.846] items emit: size=1
[04.886] items emit: size=2
...
[05.343] items emit: size=11  ← sync完成，数据恢复
```

## 根因

`syncItemsFromBackend()` 调用 `pickOrderRepository.insertOrder(orderEntity)` 使用 **REPLACE** 策略。REPLACE = DELETE + INSERT。由于 `pick_item` 的外键声明了 `onDelete = ForeignKey.CASCADE`，**父订单被 REPLACE 时会级联删除所有明细**，然后通过 `upsertItemFromResponse` 逐条重新插入。

导致的数据流：
1. 初始：Room 发射已有数据 (size=11) — LaunchedEffect 尚未执行
2. LaunchedEffect 执行时 `filteredSize=0`（数据被 CASCADE 删除）→ **跳过 scroll**
3. sync 逐条恢复数据 (size=1→2→...→11)
4. sync 完成，但 **needScroll 从未再次变化** → LaunchedEffect 永不重启
5. 最终：列表有 11 条数据，scrollToItem(0) 从未在真实数据上执行过 → 视口在底部

**两个层面都需要修复**：
- **syncItemsFromBackend**：完成后触发 needScroll++
- **_executeAddItem**：insertItem 后等待 Room 数据就绪再触发（防止异步发射竞态）

## 修复方案

### 改动1：PickDetailViewModel.kt — syncItemsFromBackend 末尾触发 needScroll

```kotlin
loadSuppliers()
ScrollLogger.appendLog(appContext, "syncItemsFromBackend 完成, items size=${items.value.size}")
// 数据已就绪，触发滚动
_needScroll.value = _needScroll.value + 1
```

### 改动2：PickDetailViewModel.kt — _executeAddItem 等待 Room 数据就绪

```kotlin
pickOrderRepository.insertItem(item)
ScrollLogger.appendLog(appContext, "insertItem 返回, sku=${r.skuOuterId}")
// 等待 Room Flow 发射包含新商品的数据，再触发滚动
items.first { itemList -> itemList.any { it.skuOuterId == r.skuOuterId } }
```

需要新增 import：
```kotlin
import kotlinx.coroutines.flow.first  // 从 Screen 移到 ViewModel
```

(检查 PickDetailScreen.kt L60 已经有这个 import，可以删掉 Screen 端的并移到 ViewModel)

### 时序验证

**Bug1（重新进入）**：
```
VM init → syncFromBackend 开始
  ↓ (insertOrder REPLACE → CASCADE 删除所有明细 → items 为空)
LaunchedEffect(orderId, MAX_VALUE) 触发
  ↓ filteredSize=0 → 跳过 scroll
  ↓ syncFromBackend 恢复数据完成
  ↓ _needScroll.value++  ← 新增
LaunchedEffect 以新 key 重启
  ↓ filteredSize=11 → scrollToItem(0) → ✅ 顶部
```

**Bug2（扫码添加）**：
```
insertItem(Room) → Room 写入完成
  ↓
items.first { it.contains(sku) } → 挂起等待  ← 新增
  ↓ (Room Flow 异步发射 → items 包含新 sku → 条件满足)
  ↓
_needScroll++ → LaunchedEffect 重启
  ↓
filteredItems 已包含新商品在 position 0
  ↓ scrollToItem(0) → ✅ 新商品可见
```

## 版本号

2.41 → 2.42，构建 APK。
