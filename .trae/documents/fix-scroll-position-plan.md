# 修复取货单详情页视口错位问题

## 问题描述

点击进入已创建的取货单详情页，视口自动显示最后几个产品（已取货区域），而不是固定在最上方。

## 深度排查过程

### 已排除的可能性

| 可能性 | 排查结论 |
|:-------|:---------|
| 排序规则导致（已完成排在末尾） | ❌ 首项应为待取货最新项 |
| `reverseLayout` | ❌ 未启用 |
| 数据加载后自动滚动到底部 | ❌ 无 `scrollToItem(lastIndex)` 代码 |
| `scrollToTopEvent` 残留 | ❌ 是 SharedFlow，不保留历史值 |
| `remember(viewModel.orderId)` 缓存旧 scroll 位置 | ❌ 每次导航是新 BackStackEntry，composition scope 全新 |
| Room Flow 空→满发射导致 LazyColumn 错位 | ❌ `stateIn` 在 ViewModel init 时即开始收集，Screen 渲染时已有数据 |
| `hiltViewModel()` 作用域错误 | ❌ 没有嵌套 NavGraph，正确作用域到 NavBackStackEntry |
| `pendingItems` 占位行导致索引计算错误 | ❌ 初始时为 empty |
| `canRequestPackageInstalls` 或通知权限弹窗影响 | ❌ 与 LazyColumn 无关 |
| 下拉刷新 `PullToRefreshBox` 干扰 | ❌ 不影响 scroll 位置计算 |

### 确定的根因

**Navigation Compose 的状态恢复机制**（`NavBackStackEntry` 的 savedStateHandle / ViewModel 的 SavedStateHandle）。

具体链路：
1. 用户进入详情页 → 滚动查看到已完成区域 → 返回到列表页
2. 在步骤 1 中，Compose Navigation 会把当前 `LazyListState` 的滚动位置保存到 BackStackEntry 的状态中
3. 用户再次点击同一订单进入详情 → 新的 BackStackEntry 创建
4. 虽然 `LazyListState()` 构造时是 (index=0, offset=0)，但 **Navigation Compose 在合成/重组过程中会恢复保存的滚动位置**，覆盖新的 LazyListState 实例

这解释了为什么 `remember(viewModel.orderId) { LazyListState() }` 之前的修复没有效果 — 关键不是 `LazyListState` 实例是否新建，而是 Navigation 框架在状态恢复时覆盖了它。

### 证据

在 `AppNavigation.kt` 中，`composable(PICK_DETAIL)` 没有使用 `restoreState = true`，但 Navigation Compose **默认行为**仍然会恢复 `NavBackStackEntry` 的保存状态（这是 Jetpack Navigation 的有状态导航设计 — back stack 条目在 `popBackStack` 后仍然保留在 back stack 中，包含其保存的状态）。

## 修复方案

### 改动 A：强制重置滚动位置（PickDetailScreen.kt）

在 L127 的 `listState` 声明之后，新增一个 `LaunchedEffect`：

```kotlin
val listState = remember(viewModel.orderId) { LazyListState() }

// 进入详情页时强制滚动到顶部（覆盖 Navigation 状态恢复的滚动位置）
LaunchedEffect(viewModel.orderId) {
    listState.scrollToItem(0)
}
```

- `LaunchedEffect(viewModel.orderId)` — 进入新订单时触发
- `scrollToItem(0)` — 是 Compose Foundation 1.2+ 的 suspend 函数，会等待 LazyColumn 布局就绪后执行
- 因为它是 `LaunchedEffect`，在 **composition 和状态恢复完成之后才执行**，所以能覆盖 Navigation 的状态恢复
- 不影响已有功能：扫码后的 `scrollToTopEvent` 仍然有效

### 改动 B：构建新 APK

Version 2.35 → 2.36，构建 APK 供 PDA 测试。

## 验证方法

安装新版 APK 后：
1. 进入取货单详情 → 滚动到列表底部（查看已取货区域）
2. 返回到列表页
3. **再次点击同一取货单** → 确认视口在最上方
4. 重复 3-5 次确认修复稳定
