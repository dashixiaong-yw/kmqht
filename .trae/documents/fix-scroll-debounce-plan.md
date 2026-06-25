# v2.44 重新进入取货单详情依然有从下往上的滚动 — 分析

## 排查结论

全局搜索 15 项，**没有遗漏的隐藏限制条件**。项目中只有一个地方控制 LazyColumn 滚动位置：

```kotlin
// PickDetailScreen.kt L189-197
LaunchedEffect(viewModel.orderId) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .collect { index ->
            if (index > 0 && filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
}
```

## 你的核心洞察完全正确

> "既然每次进去都能准确的显示最下方的商品，那么一定是有一个限制条件的"

那个"限制条件"就是这一行：

```kotlin
val listState = remember(viewModel.orderId) { LazyListState() }
```

### 为什么重新进入时会先显示底部再滚回顶部

关键在于 `syncItemsFromBackend()` 中 `insertOrder(REPLACE)` 的行为：

```
T1: VM init → items = emptyList()
T2: Room 发射缓存数据 → items = [11条数据]
T3: Compose 重组 → LazyColumn 第一次布局 11 条数据
    → firstVisibleItemIndex = ??? (可能是 0...也可能不是!)
T4: syncItemsFromBackend 执行 → insertOrder(REPLACE)
    → CASCADE 删除全部明细 → items = emptyList()
    → LazyColumn 变空
T5: sync 逐条恢复数据 → items: 1→2→...→11
    → LazyColumn 逐帧膨胀
T6: 某一帧中 firstVisibleItemIndex 可能漂移到 >0
    → snapshotFlow 检测到 → scrollToItem(0) 执行
    → **从下往上的快速滚动**
```

**根因**：T3→T4→T5 的数据剧烈波动（11→0→1→2→...→11），导致 LazyColumn 在 layout 的多帧之间 `firstVisibleItemIndex` 漂移。`snapshotFlow` 检测到后触发 `scrollToItem(0)`，这正是用户看到的"快速滚动"。

**它不是限制条件，是副作用**：数据被人为清空再重建（CASCADE），LazyColumn 在 0↔N 的剧烈波动中位置偏离，然后被 snapshotFlow 拉回来。

## 修复方案

### 本质解决：阻止 CASCADE 乱序的数据波动

将 `insertOrder` 的 REPLACE 改为先更新已有字段，不在 INSERT 时触发 CASCADE：

但更简单的方案是：**syncItemsFromBackend 不要先删所有数据再逐条恢复，改为逐条 upsert，不做 REPLACE 的插入**。

或者更简单的：**snapshotFlow 只在首次布局完成后（items 不再为空时）才启动监听**，或者在 `filteredItems` 从 0>0 的波动中保持静默。

### 推荐方案：snapshotFlow 加 200ms 去抖

```kotlin
LaunchedEffect(viewModel.orderId) {
    snapshotFlow { listState.firstVisibleItemIndex to filteredItems.isNotEmpty() }
        .debounce(200)  // 数据波动稳定后再响应
        .collect { (index, hasItems) ->
            if (index > 0 && hasItems) {
                listState.scrollToItem(0)
            }
        }
}
```

`debounce(200)` 确保 LazyColumn 在 CASCADE 删除→恢复的过程中不会多次触发 scrollToItem。只有 index 在 200ms 内稳定在 >0 时才执行一次滚动。

需要新增 import：
```kotlin
import kotlinx.coroutines.flow.debounce
```

## 版本号

2.44 → 2.45
