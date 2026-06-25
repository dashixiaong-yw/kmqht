# 历史取货单详情打开时视口固定在顶部

## 问题

用户反馈：打开已完成的历史取货单详情时，视口显示在列表最底部，需要向上滚动才能到顶部。

---

## 脑暴 — 所有可选方案评估

### 方案 A：`withFrameNanos { }; scrollToItem(0)`（目前提案）

```kotlin
LaunchedEffect(Unit) {
    withFrameNanos { }
    listState.scrollToItem(0)
}
```

- 新增 1 行 import + 3 行代码
- 等待首帧布局完成后强制滚动到顶部
- `withFrameNanos` 比 `delay(50)` 更精确（不是 magic number，而是等真正的帧完成）
- **仅覆盖页面打开**

### 方案 B：`remember(orderId) { LazyListState() }`（一行改动）

```kotlin
val listState = remember(orderId) { LazyListState() }
```

- 只用一行替换 `rememberLazyListState()`
- 每次进入不同订单详情必定创建新状态，scroll=0
- 不再依赖 `rememberSaveable` 可能的状态恢复
- **缺点**：不再使用 `rememberSaveable`，进程杀死后状态丢失（但本来就没用这个功能）
- **优点**：最少的代码改动，一行解决
- **结论**：✅ 这个方案更好

### 方案 C：`delay(50); scrollToItem(0)`

```kotlin
LaunchedEffect(Unit) {
    delay(50)
    listState.scrollToItem(0)
}
```

- magic number 50ms，在慢设备上可能不够
- **结论**：方案 A 更好（`withFrameNanos` 无 magic number）

### 方案 D：`items.isNotEmpty()` 触发

```kotlin
var didInitialScroll by remember { mutableStateOf(false) }
LaunchedEffect(items.size) {
    if (items.isNotEmpty() && !didInitialScroll) {
        didInitialScroll = true
        listState.scrollToItem(0)
    }
}
```

- 代码太多，不够简洁
- **结论**：比方案 A/B 差

### 方案 E：`animateScrollToItem(0)`

```kotlin
LaunchedEffect(Unit) {
    withFrameNanos { }
    listState.animateScrollToItem(0)
}
```

- 页面打开时有不必要的滚动动画，突兀
- **结论**：方案 A 更好（`scrollToItem` 即时生效）

### 方案 F：`LazyColumn(reverseLayout = true)`

- 把列表反向排列，让 index=0 在底部
- 但所有 UI 顺序都颠倒了，需要改大量逻辑
- **结论**：改动太大，不采纳

### 最优选择：方案 B + 方案 A 组合

**方案 B**（`remember(orderId)`）解决了"状态恢复"场景：如果 Compose Navigation 因某些原因复用了之前的 LazyListState，新状态必定为 0。
**方案 A**（`withFrameNanos`）解决了"首帧布局后数据到达"场景：即使数据到达导致布局变化，scrollToItem(0) 在首帧后执行。

**选择方案 B**（一行改动）单独实施，因为：
1. 代码最少（1行替换）
2. 覆盖了大部分场景
3. 如果用户进另一个订单再返回，状态永远是全新的
4. 如果方案 B 不够，再加方案 A 也来得及

---

## 改动

### 文件：[PickDetailScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt)

#### 改 1 行：`rememberLazyListState()` → `remember(orderId) { LazyListState() }`

```kotlin
// 每打开一个订单详情，创建独立的 LazyListState，确保视口始终从顶部开始
val listState = remember(orderId) { LazyListState() }
```

**原理**：
- `rememberLazyListState()` → `rememberSaveable(saver = LazyListState.Saver) { LazyListState() }`
  - 使用 `rememberSaveable`，在某些场景下可能恢复之前保存的 scroll 状态
- `remember(orderId) { LazyListState() }`
  - 以订单 ID 为 key，每次进不同的订单详情必定创建新的 `LazyListState(0, 0)`
  - 进同一个订单详情时也复用已有状态

**改动前**：
```kotlin
val listState = rememberLazyListState()
```

**改动后**：
```kotlin
val listState = remember(orderId) { LazyListState() }
```

### 回归风险分析

| # | 风险项 | 风险等级 | 说明 |
|:-:|:-------|:--------:|:------|
| 1 | **进程杀死后重进** | ✅ 安全 | `remember(orderId)` 在进程恢复后重建 composable，重新创建 LazyListState，初始为 0 |
| 2 | **返回再进同一订单** | ✅ 安全 | composable 被销毁重建，remember(orderId) 创建新状态 |
| 3 | **订单 ID 变更极端情况** | ✅ 安全 | orderId 是导航参数，在 Page 生命周期内不变 |
| 4 | **`LazyListState` import** | ✅ 已导入 | 由 `rememberLazyListState` 带入选，无需额外 import |
| 5 | **扫码添加滚动干扰** | ✅ 安全 | `scrollToTopEvent` 仍然是独立的，不受 listState 创建方式影响 |
| 6 | **下拉刷新** | ✅ 安全 | 不创建新 listState，刷新保持原位 |
| 7 | **`remember` vs `rememberSaveable` 差异** | ✅ 安全 | 本场景不需要 `rememberSaveable` 的进程恢复功能，因为进程恢复后 composable 会重建 |

## 前置条件检查

| # | 检查项 | 状态 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | `orderId` 在 Screen 中可访问 | ✅ | 通过 `viewModel.orderId` 或直接从 ViewModel 获取 |
| 2 | `LazyListState` import 已存在 | ✅ | 由 `rememberLazyListState` 自动导入 |

> 注意：目前 `PickDetailScreen.kt` 没有直接访问 `orderId` 的变量。需要通过 ViewModel 暴露，或者从 `viewModel.orderId` 读取。

验证 Screen 中是否有 `orderId`：

```kotlin
// PickDetailScreen 参数列表中有 viewModel
@Composable
fun PickDetailScreen(
    viewModel: PickDetailViewModel = hiltViewModel(),
    ...
)
```

`viewModel.orderId` 是 `public val orderId: Long`，所以可以直接用。

## 改动清单

| 文件 | 改动 |
|:-----|:------|
| PickDetailScreen.kt | L126: `rememberLazyListState()` → `remember(orderId) { LazyListState() }` |

**仅 1 行改动。**

## 验证

| 场景 | 预期 |
|:-----|:------|
| 打开已完成取货单（50+商品） | 视口在顶部，无需向上滚动 ✅ |
| 打开进行中取货单（10+商品） | 视口在顶部 ✅ |
| 返回列表再进同一订单 | 视口在顶部 ✅ |
| 返回列表再进不同订单 | 视口在顶部 ✅ |
| 扫码添加商品 | `scrollToTopEvent` 保持顶部 ✅ |
| 下拉刷新 | 视口保持原位 ✅ |
| 删除/完成/恢复 | 视口保持原位 ✅ |
| 进程杀死恢复 | 视口在顶部 ✅ |

## 版本号

2.30 → 2.31（不构建 APK，等通知）
