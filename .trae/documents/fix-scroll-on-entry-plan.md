# 修复进入取货单详情页视口在底部的问题

## 关键线索

| 操作路径 | 视口行为 | 含义 |
|:---------|:---------|:-----|
| PickList → PickDetail | ❌ 最底部 | Composition **重建**，LaunchedEffect 在空列表时执行 |
| Product → 返回 PickDetail | ✅ 最上方 | Composition **存活**，LaunchedEffect 不执行，数据已在 |

**结论**：composition 重建时 `LaunchedEffect(orderId, needScroll=0)` 触发 `scrollToItem(0)`，但此时 LazyColumn 无数据（Room Flow 还没发射），`scrollToItem(0)` 是空操作。数据到达后初始滚动位置偏差导致视口在底部。

## 修复方案

### 改动1：PickDetailViewModel.kt — needScroll 初始值从 0 改为 `Int.MAX_VALUE`

```kotlin
// 改前
private val _needScroll = MutableStateFlow(0)
// 改后
private val _needScroll = MutableStateFlow(Int.MAX_VALUE)
```

原因：composition 恢复（Navigation 2.8 内部机制）时 `needScroll=0` 与之前相同，LaunchedEffect 不重启。`Int.MAX_VALUE` 确保每次新 ViewModel 的值都与之前不同，LaunchedEffect 以新 key 重启。

### 改动2：PickDetailScreen.kt — 用 snapshotFlow 等待数据就绪

```kotlin
val needScroll by viewModel.needScroll.collectAsState()
LaunchedEffect(viewModel.orderId, needScroll) {
    // 等待 LazyColumn 有数据后再滚动（避免空列表时 scrollToItem 无效）
    snapshotFlow { filteredItems.size }
        .first { it > 0 }
    listState.scrollToItem(0)
}
```

### 原因

| 场景 | LaunchedEffect 是否重启 | snapshotFlow 行为 | scrollToItem |
|:-----|:----------------------:|:-----------------:|:------------:|
| 首次进入 | ✅ 新 composition | 等待数据到达 → 返回 | ✅ 有数据时执行 |
| 返回再进入 | ✅ needScroll=MAX_VALUE 不同 | 数据已在 → 立即返回 | ✅ 有数据时执行 |
| Product 返回 | ❌ composition 存活 | 不经过此路径 | （保持在顶部） |
| 扫码添加 | ✅ needScroll++ | 数据已在 → 立即返回 | ✅ 有数据时执行 |
| 下拉刷新 | ❌ needScroll 不变 | 不触发 | （不滚动） |

## 版本号

2.37 → 2.38，构建 APK。
