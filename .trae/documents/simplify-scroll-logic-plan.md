# 简化视口滚动逻辑 — 用 LazyColumn 默认行为

## 你提出的核心洞察

> 位置号0是最新添加的商品，列表默认就在位置0，不需要滚动。

完全正确。LazyColumn 本就是从 index 0 开始渲染，排序后 index 0 就是最新商品。

## 当前代码的冗余

v2.43 的 LaunchedEffect：

```kotlin
LaunchedEffect(viewModel.orderId, needScroll) {
    if (filteredItems.isNotEmpty() && listState.firstVisibleItemIndex != 0) {
        listState.scrollToItem(0)
    }
}
```

`needScroll` 变化会触发这个 effect，但 `firstVisibleItemIndex != 0` 守卫已经确保：**本来就在顶部就不滚动**。也就是说 `needScroll` 变化本身已经不需要了——因为即使没有 `needScroll` 事件驱动，Compose 重组后 LazyColumn 自然地保持 index 0。

所有滚动机制的冗余追踪：

| 组件 | 当前状态 | 建议 |
|:-----|:---------|:-----|
| `_needScroll` StateFlow | 控制 LaunchedEffect 重启 | **可删除** |
| `needScroll` collectAsState | StateFlow 监听 | **可删除** |
| LaunchedEffect(orderId, needScroll) | 用于触发 scroll | **可简化** |
| `syncItemsFromBackend` 末尾的 `_needScroll++` | v2.42 加的 | **可删除** |
| `_executeAddItem` 的 `items.first{...}` | v2.42 加的 | **可删除** |
| `_executeAddItem` 的 `_needScroll++` | v2.37 加的 | **可删除** |

## 唯一需要滚动的情况

扫码添加新商品后，新商品插入 position 0，原有的 position 0 被推到 position 1。LazyColumn 的布局管理器为了保持"视觉稳定"，会把 `firstVisibleItemIndex` 从 0 变为 1。**这时**才需要 `scrollToItem(0)`。

## 最终方案

**用 `snapshotFlow` 监听 `firstVisibleItemIndex` 变化，只有在确实偏离了 index 0 时才回滚**：

```kotlin
// 监听列表项在顶部被插入时的自动偏移，回滚到顶部
LaunchedEffect(viewModel.orderId) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .collect { index ->
            if (index > 0 && filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
}
```

需要原有 import（`snapshotFlow` 已在 L59，`collectLatest` 已在 import 中），不需要 `kotlinx.coroutines.flow.first`（已删）。

**引用**：`collect` 而不是 `collectLatest`，确保每个偏移变化都被处理。

### VM 侧可以清理的内容

从 PickDetailViewModel.kt 移除：
- `_needScroll` / `needScroll` 声明（L120-122）
- `import kotlinx.coroutines.flow.first`（L35，v2.42 加的）
- `_executeAddItem` 中的 `items.first{...}`（L283-284）
- `_executeAddItem` 中的 `_needScroll++`（L291）
- `syncItemsFromBackend` 末尾的 `_needScroll++`（L503-504）
- 相关日志（needScroll 准备++ / 已++）

### Screen 侧可以清理的内容

从 PickDetailScreen.kt 移除：
- `needScroll` 的 collectAsState
- 旧的 LaunchedEffect(orderId, needScroll) + 滚动日志
- `import kotlinx.coroutines.flow.collectLatest` → 改为 `import kotlinx.coroutines.flow.collect`
- `import androidx.compose.runtime.snapshotFlow`（已有）
- 删除 `ScrollLogger.appendLog` 在 LaunchedEffect 内的调用（日志简化）

### 为什么 `snapshotFlow` 不会引起循环

`listState.firstVisibleItemIndex` 变化 → `scrollToItem(0)` 将 index 设为 0 → Compose 重组后 `snapshotFlow` 再次检查 → index=0，条件不满足 → 不滚动。一气呵成。

### 清理日志

v2.41 加的日志系统保留（滚动日志 + 同步日志 + 退出清理），但移除 no-op 落点日志。保留 VM init、sync、items emit、scan 等关键日志。

## 版本号

2.43 → 2.44，构建 APK。
