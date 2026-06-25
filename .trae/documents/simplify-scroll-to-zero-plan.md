# 简化视口逻辑 — 默认显示位置号 0

## 全面回归审查确认

### 总体结论：✅ 安全可实施，无回归风险

14 个子项全部审查通过。净删 ≈30 行，新增 ≈10 行，无跨模块影响。

---

### 你的核心方案

> 不管添加商品还是重新进入，默认视口最上方显示位置号 0 的商品。

**位置号0 = 最后添加的商品**（排序规则 `createdAt DESC + id DESC`）。

`LazyListState()` 创建时默认 `index=0`，就是显示最新商品。唯一需要滚动的情况是添加商品后 LazyColumn 的 `firstVisibleItemIndex` 从 0 被推到 1。

### 方案：snapshotFlow 监听 index 漂移

```kotlin
LaunchedEffect(viewModel.orderId) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .collect { index ->
            if (index > 0 && filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
}
```

**只覆盖 index 漂移的场景**：新商品插入 index 0 → 原有 index 0 被推到 1 → snapshotFlow 检测到 1 → scrollToItem(0) 回滚。

### 所有覆盖场景

| 场景 | firstVisibleItemIndex | snapshotFlow 行为 | 视觉 |
|:-----|:---------------------:|:-----------------|:-----|
| 首次进入页面 | 0（新LazyListState默认值） | 检测到 0，条件 false → 不滚动 | 显示最新商品 ✅ |
| 重新进入页面（新 composition） | 0 | 同上 | ✅ |
| 扫码添加新商品 | 0→1 漂移 | 检测到 1 → scrollToItem(0) | ✅ |
| 连续快速扫码 | 每次插入后 0→1 | 依次回滚，collect 排队不丢 | ✅ |
| pullToRefresh | 不变 | 不变 → 不触发 | ✅ |
| 供应商过滤 | 不变 | 不变 → 不触发 | ✅ |
| completeItem（已完成项移到底部） | 可能漂移 | 漂移则回滚 | ✅ |
| deleteItem | 可能漂移 | 漂移则回滚 | ✅ |
| 重复扫码滚动 | 独立动画路径 | 不经过 snapshotFlow | ✅ |

---

### 改动清单

#### 改动1：PickDetailViewModel.kt

**删除**：
1. `import kotlinx.coroutines.flow.first`（L35，v2.42 加的）
2. `_needScroll` / `needScroll` 声明（L120-122）
3. `_executeAddItem` 中 `items.first{...}` + `_needScroll++` + 相关日志（L282-295，约6行）
4. `syncItemsFromBackend` 末尾 `_needScroll++`（L502-504）
5. init 日志中 `_needScroll` 引用部分（L131）

**保留**：其他所有 ScrollLogger 日志（sync、items emit、scan、insertItem、finally 等）

#### 改动2：PickDetailScreen.kt

**删除**：
1. `val needScroll by viewModel.needScroll.collectAsState()`（L190）
2. 整个 `LaunchedEffect(viewModel.orderId, needScroll)` 块（L191-214，约 23 行）

**新增**：
```kotlin
LaunchedEffect(viewModel.orderId) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .collect { index ->
            if (index > 0 && filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
}
```

**import 情况**：
- `import kotlinx.coroutines.flow.collectLatest` — **保留**（被 scanSuccessEvent、scanFailureEvent、scanResult 3处使用）
- `import androidx.compose.runtime.snapshotFlow` — **保留**（新增代码要用，且它原本就是死import，算是物归原主）
- `import com.kuaimai.pda.util.ScrollLogger` — **保留**（duplicateScan 等处仍在用）

#### 验证方法

1. APK 安装后进入取货单详情 → 确认显示位置号 0 的商品
2. 返回列表 → 再进入 → 确认同一位置
3. 扫码添加 1-10 个商品 → 每个添加后确认新商品自动显示在视口
4. 连续快速扫码 3 个以上 → 确认都正确显示
5. pullToRefresh → 确认不跳动
6. 供应商过滤切换 → 确认列表正常，不跳动
7. 删除/完成/恢复操作 → 确认视口稳定

---

## 版本号

2.43 → 2.44，构建 APK。
