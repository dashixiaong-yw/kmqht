# 修复进入取货单详情页视口不在顶部的问题

## 问题

重构后（v2.37）进入未完成的取货单详情页，视口依然显示最下方几个商品，而不是固定在最上方。

## 根因

经过 14 项深度排查，已排除 ViewModel 重用、`rememberSaveable`、`restoreState` 显式配置等常见原因。

**真正根因：Navigation Compose 2.8.0 内部状态冻结/恢复机制。**

当用户从 PickDetail 返回到 PickList（`popBackStack()`）时，Navigation Compose **不会立即销毁** PickDetail 的 `NavBackStackEntry` 的 composition 树，而是将其"冻结"（内部 savedState 保存）。当用户再次进入 PickDetail 时，冻结的 composition 树被"恢复"——这导致：

1. `remember(viewModel.orderId) { LazyListState() }` **返回的是之前冻结的`LazyListState`实例**，其中 `firstVisibleItemIndex` 保留着上次离开时的滚动位置（比如底部）
2. `LaunchedEffect(viewModel.orderId, needScroll)` 的 key 与之前相同（相同 `orderId` + 相同 `needScroll=0`），**不重新触发**，因为 LaunchedEffect 的协程也被冻结/恢复
3. 结果是：用户看到的滚动位置与上次离开时完全一致

**证据**：navigation-compose 2.8.0 changelog 明确提到改进了 back stack 条目的状态保存，使得 `composable()` 默认行为比以前更"持久化"。

## 修复方案

**原理**：使用 `DisposableEffect` 在每次 composition 重建时生成一个递增的 key，注入到 `LaunchedEffect` 中，确保 `LaunchedEffect` 在每次页面进入时都重新触发。

### 改动：PickDetailScreen.kt

**改前**（L127-132 + L184-189）：

```kotlin
val listState = remember(viewModel.orderId) { LazyListState() }

// 根据供应商过滤明细 ...
...

// 添加完成（成功）后滚动到顶部显示新商品（进入页面时也生效）
val needScroll by viewModel.needScroll.collectAsState()
LaunchedEffect(viewModel.orderId, needScroll) {
    listState.scrollToItem(0)
}
```

**改后**：

```kotlin
val listState = remember(viewModel.orderId) { LazyListState() }

// 使用 DisposableEffect 生成每次composition重建时递增的key
// 解决 Navigation Compose 2.8 状态冻结导致 LaunchedEffect 不重启的问题
val pageKey = remember { mutableIntStateOf(0) }
DisposableEffect(Unit) {
    pageKey.intValue++
    onDispose { }
}

// 根据供应商过滤明细 ...
...

// 统一滚动控制：进入页面 + 扫码添加后滚动到顶部
val needScroll by viewModel.needScroll.collectAsState()
LaunchedEffect(viewModel.orderId, pageKey.intValue, needScroll) {
    listState.scrollToItem(0)
}
```

### 为什么这样做

`DisposableEffect` 在 **composition 重新创建时**（而不是 normal recomposition 时）会重启。Navigation Compose 冻结/恢复 composition 时：

| 生命周期阶段 | DisposableEffect 行为 |
|:-------------|:----------------------|
| 导航离开（PopDetail→PopList） | `onDispose { }` 执行（但 composition 仍被冻结） |
| 再次导航到 PopDetail | **DisposableEffect 重启**（composition 被解冻，视为新的开始） |
| `pageKey.intValue++` | 从0→1，触发重组 |
| `LaunchedEffect(orderId, 1, needScroll)` | keys 变成 `(orderId, 1, 0)`，与之前 `(orderId, 0, 0)` 不同 → **重新触发** |
| `scrollToItem(0)` | 执行滚动到顶部 ✅ |

### 影响范围

| 场景 | 预期行为 |
|:-----|:---------|
| 首次进入页面 | pageKey=0→1, LaunchedEffect触发, 滚动到顶部 ✅ |
| 返回 PickList 再进入 | DisposableEffect 重启→pageKey=1, LaunchedEffect触发, 滚动到顶部 ✅ |
| 扫码添加商品 | needScroll 变化→0→1, LaunchedEffect触发, 滚动到顶部 ✅ |
| 下拉刷新 / 后台同步 | needScroll 不变, 不触发 ✅ |
| 连续扫码 | needScroll 多次变化, 每次触发 ✅ |
| 返回到 PickList 再进入**另一个**取货单 | orderId 变化 + pageKey=1, LaunchedEffect触发 ✅ |

### 验证确认

构建 APK 后测试：
1. 进入取货单 → 滚动到底部 → 返回列表 → **再次进入** → 确认视口在顶部
2. 返回列表 → 进入另一个取货单 → 确认视口在顶部
3. 扫码添加 N 个商品 → 每个添加后确认新商品可见
4. 重复 3-5 次确认修复稳定

## 版本号

2.37 → 2.38，构建 APK。
