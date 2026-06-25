# 取货单详情扫码添加商品 — 视口固定整体重构

## 目标

新添加的商品（包括占位符和成功后的真实商品）始终出现在列表**最上方**，视口恒定固定在列表最上方。

```
     ┌──── 视口固定在顶部 ────┐
     │  新占位符 (扫码后立即)  │  ← index 0
     │  新商品 (API完成后)     │  ← index 1 (占位符消失后变index 0)
     │  旧商品 1              │  ← 自动下移
     │  旧商品 2              │
     │  旧商品 3              │
     └────────────────────────┘
          ↓ 用户往下滚动
     旧商品 N
```

## 根本解决方案

### 核心改动链路（改进版）

```
用户扫码
  → 查重（无锁）
  → _pendingItems += barcode  ← 立即显示占位符（无锁）
  → _scanSuccessEvent.emit(Unit)  ← 清空输入框+聚焦（无锁）
  → addItemMutex.withLock {        ← 等待锁（API串行化）
        API调用（带404重试）
        insertItem(Room)            ← 数据写入Room
        _pendingItems -= barcode   ← 移除占位符
        _scrollToTopEvent.emit(Unit)  ← 数据就绪后滚动到顶部
    }
```

**关键设计点**：
- 占位符显示在 **Mutex 外** → 用户扫码立即看到反馈，不会被前面的 API 调用阻塞
- API 调用 + 数据写入在 **Mutex 内** → 串行化，不会同时多个 in-flight
- 滚动在数据写入 **之后** → 视口恒固定在顶部

### 问题 1：视口不固定

**旧方案**：`animateScrollToItem(0)` 在 API 返回前执行 → 数据就绪后 Compose 重算 → 偏移。

**新方案**：占位符在外（无锁）→ API 串行化（Mutex）→ 数据就绪后 `scrollToItem(0)`。

### 问题 2：HTTP 404 首次添加失败

**旧方案**：不处理 404，直接显示错误。

**新方案**：一次自动重试（delay 200ms 后重试）。

### 问题 3：连续扫码并发

**旧方案**：每个扫码启动独立协程，多个 API 同时 in-flight。

**新方案**：`Mutex.withLock` API 调用串行化，一个完成再处理下一个。占位符显示不受影响。

### 问题 4：重复扫码滚动索引错误

**旧方案**：`items.indexOfFirst`（Room id ASC 顺序）与 LazyColumn 显示顺序不符。

**新方案**：使用 `filteredItems.indexOfFirst` 匹配显示顺序。

## 改动

### 文件：[PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt)

#### 新增 import

```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

#### 新增成员变量

```kotlin
/** 添加取货明细的串行化锁，避免并发导致视口跳动 */
private val addItemMutex = Mutex()

/** 添加成功后滚动到顶部的事件（数据已就绪） */
private val _scrollToTopEvent = MutableSharedFlow<Unit>()
val scrollToTopEvent: SharedFlow<Unit> = _scrollToTopEvent.asSharedFlow()
```

#### 重写 `onBarcodeScanned()` 和新增 `_executeAddItem()`

```kotlin
fun onBarcodeScanned(barcode: String) {
    viewModelScope.launch {
        lastScannedSku = barcode
        try {
            // 查重（无锁，直接响应）
            val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, barcode)
            val pending = _pendingItems.value.contains(barcode)
            if (existing != null || pending) {
                _duplicateScan.value = true
                return@launch
            }

            // 立即显示占位符 + 清空输入框（无锁，即时反馈）
            _pendingItems.value = _pendingItems.value + barcode
            _scanSuccessEvent.emit(Unit)

            // API 调用串行化（等待前面的扫码处理完成）
            addItemMutex.withLock {
                _executeAddItem(barcode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加取货明细异常", e)
            _errorMessage.value = "添加明细失败: ${e.message}"
            _scanFailureEvent.emit("添加明细失败: ${e.message}")
        }
    }
}

/**
 * 实际执行添加取货明细（由 Mutex 串行化保护）
 * 注意：调用前 _pendingItems 已包含 barcode，调用后已移除
 */
private suspend fun _executeAddItem(barcode: String) {
    try {
        val token = userRepository.getToken()
        var response: OrderItemResponse? = null
        var retries = 0
        while (response == null && retries < 2) {
            try {
                response = orderApiService.addItem(
                    token, orderId, AddOrderItemRequest(barcode)
                )
            } catch (e: HttpException) {
                if (e.code() == 404 && retries == 0) {
                    retries++
                    delay(200)
                } else {
                    throw e
                }
            }
        }

        val item = PickItemEntity(
            id = response.id, orderId = orderId,
            skuOuterId = response.skuOuterId,
            sysItemId = response.sysItemId,
            sysSkuId = response.sysSkuId,
            propertiesName = response.propertiesName,
            picPath = response.picPath,
            status = response.status,
            supplierName = response.supplierName,
            supplierCode = response.supplierCode,
            remark = response.remark,
            itemOuterId = response.itemOuterId,
            createdAt = TimeUtils.parseBeijingTime(response.createdAt).let { if (it > 0) it else TimeUtils.now() }
        )
        pickOrderRepository.insertItem(item)
        val newSupplier = response.supplierName
        if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
            _suppliers.value = _suppliers.value + newSupplier
        }
        loadOrder()
        _order.value = _order.value?.copy(totalCount = (_order.value?.totalCount ?: 0) + 1)
    } catch (e: Exception) {
        if (e is HttpException && e.code() == 409) {
            _errorMessage.value = null
            syncItemsFromBackend()
            _duplicateScan.value = true
        } else {
            if (e is HttpException && e.code() == 401) {
                SessionExpiredEvent.notifyExpired()
            }
            _errorMessage.value = "添加明细失败: ${e.message}"
            _scanFailureEvent.emit("添加明细失败: ${e.message}")
        }
    } finally {
        // 无论成功失败，从待处理列表中移除 + 通知UI滚动到顶部
        _pendingItems.value = _pendingItems.value - barcode
        _scrollToTopEvent.emit(Unit)
    }
}
```

> **注意**：`_scrollToTopEvent.emit(Unit)` 放在 `_executeAddItem` 的 `finally` 块中，只在实际添加完成后触发。重复扫码（在 `onBarcodeScanned` 中 return）不会触发 `_scrollToTopEvent`，不会覆盖重复扫码弹窗的滚动效果。

### 文件：[PickDetailScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt)

#### scanSuccessEvent — 移除滚动，只保留清空输入框+聚焦

```kotlin
// 扫码成功反馈 + 清空输入框并重新聚焦（不再滚动，数据还没就绪）
LaunchedEffect(Unit) {
    viewModel.scanSuccessEvent.collectLatest {
        viewModel.provideFeedback(context, ScanFeedbackType.SUCCESS)
        scanInput = ""
        focusRequester.requestFocus()
    }
}
```

#### 新增 scrollToTopEvent — 数据就绪后滚动到顶部

```kotlin
// 添加完成（成功/失败）后滚动到顶部（数据已就绪）
LaunchedEffect(Unit) {
    viewModel.scrollToTopEvent.collectLatest {
        listState.scrollToItem(0)
    }
}
```

> 使用 `scrollToItem`（非动画）而不是 `animateScrollToItem`，因为数据就绪后需要立即固定视口在顶部，不需要过渡动画。

#### 重复扫码滚动索引改为 `filteredItems`

```kotlin
val duplicateSku = viewModel.lastScannedSku
if (duplicateSku.isNotEmpty()) {
    val duplicateIndex = filteredItems.indexOfFirst { it.skuOuterId == duplicateSku }
    if (duplicateIndex >= 0) {
        listState.animateScrollToItem(duplicateIndex)
    }
}
```

### 后端：[orders.py](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py)

#### 检查后端 addItem 路由，确认 404 原因

```python
@router.post("/orders/{order_id}/items")
def add_order_item(order_id: int, request: AddOrderItemRequest, user: dict = Depends(get_current_user)):
    """向取货单添加明细"""
    # ... 查询order是否存在
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    order = cursor.fetchone()
    if not order:
        raise HTTPException(status_code=404, detail="取货单不存在")
    # ... 继续处理
```

**如果前端用 Mutex 串行化 + 404 自动重试，后端不需要额外改动。** 但需要检查是否还有除此之外的 404 来源。

---

## 脑暴审查 — 15 项关联点

| # | 检查项 | 结论 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | **Mutex 死锁风险** | ✅ 安全 | `_executeAddItem` 内的 `syncItemsFromBackend()` 不回调 `onBarcodeScanned`，无递归入锁路径 |
| 2 | **占位符被 Mutex 阻塞** | ✅ 已设计 | 占位符显示在 Mutex 外（`onBarcodeScanned` 内先 `_pendingItems +=` 再 `withLock`），用户扫码即时看见 |
| 3 | **重复扫码滚动被覆盖** | ✅ 已设计 | `_scrollToTopEvent.emit()` 在 `_executeAddItem.finally` 中发射；重复扫码走 `return@launch` 不进入 `_executeAddItem`，两者互斥 |
| 4 | **404 重试失败兜底** | ✅ 安全 | 两次 404 后 `throw e` 进 catch → 显示错误 + Snackbar，与现有行为一致 |
| 5 | **重试期间 duplicate 检查绕过** | ✅ 安全 | 重试在 Mutex 内，`_pendingItems` 一直包含该 barcode，后续同 barcode 扫码被拦 |
| 6 | **`scrollToItem` suspend 不阻塞 Mutex** | ✅ 安全 | `SharedFlow.emit()` 是 suspend 函数，但目标 collector 在 `LaunchedEffect`（不同协程），实际不等待。Mutex 上的 `withLock` 在 emit 后立即释放 |
| 7 | **`_scrollToTopEvent` 的 MutableSharedFlow 无 replay** | ✅ 符合预期 | `replay = 0`，旧事件不会重放给新订阅者。但 `LaunchedEffect(Unit)` 在 Screen 整个生命周期内一直存在，不需要 replay |
| 8 | **弱网下 Mutex 队列堆积** | ✅ 已考虑 | 扫码越多，等待越久。但视口始终固定在顶部，用户看到旧的占位符逐渐消失，新占位符出现。总比现在的视口跳动好 |
| 9 | **多次 `_scrollToTopEvent` 快速发射** | ✅ 安全 | `LaunchedEffect.collectLatest` 会取消前一个处理协程，但每次发射只有一次 `scrollToItem(0)`，取消不影响 |
| 10 | **页面离开后事件丢失** | ✅ 无害 | `LaunchedEffect` 随 Composable 销毁，`emit()` 无订阅者 → 值丢弃，无副作用 |
| 11 | **`response` 在 while 后非空保证** | ✅ 已验证 | 两次重试后仍 404 则 `throw e` 跳出，不会出现 `response!!` null |
| 12 | **`TAG` 常量可用** | ✅ 已确认 | PickDetailViewModel 已有 `companion object { const val TAG = "PickDetailVM" }` (L50-L51) |
| 13 | **`SharedFlow.emit()` 并发安全** | ✅ 已保证 | Mutex 确保同一时间只有一个协程调用 `_scrollToTopEvent.emit()`，不会丢失事件 |
| 14 | **`OrderItemResponse` import 缺失** | ⚠️ 需新增 | 需加 `import com.kuaimai.pda.data.api.dto.OrderItemResponse` |
| 15 | **`SharedFlow` import 缺失** | ⚠️ 需新增 | 当前只有 `MutableSharedFlow` 导入，需加 `import kotlinx.coroutines.flow.SharedFlow` |

### 前置条件检查

| # | 检查项 | 状态 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | `OrderItemResponse` 类型存在 | ✅ | [OrderDto.kt:L88-L102](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/OrderDto.kt#L88-L102) 已定义 |
| 2 | `MutableSharedFlow`/`SharedFlow` 已导入 | ✅ | PickDetailViewModel L22 已有 `import kotlinx.coroutines.flow.MutableSharedFlow` |
| 3 | `Mutex` 可用 | ✅ | `kotlinx.coroutines.sync.Mutex` 标准库，无额外依赖 |
| 4 | `delay` 可用 | ✅ | `kotlinx.coroutines.delay` 标准库，无额外依赖 |
| 5 | `filteredItems` 在 Screen 中可访问 | ✅ | 已在 Screen 内 defined as computed val (L195-L200) |
| 6 | `AddOrderItemRequest` 已导入 | ✅ | PickDetailViewModel L9 已有 import |

### 回归风险审计

| # | 风险项 | 风险等级 | 防护措施 |
|:-:|:-------|:--------:|:---------|
| 1 | **Mutex 死锁** | 低 | `_executeAddItem` 内的 `syncItemsFromBackend()` 不调用 `onBarcodeScanned`，不存在递归锁。Mutex 非可重入锁，但本场景无非重入路径 |
| 2 | **占位符被阻塞** | ✅ 已解决 | 占位符显示在 Mutex 外，不会被前面的 API 调用阻塞 |
| 3 | **重复扫码滚动被覆盖** | ✅ 已解决 | `_scrollToTopEvent` 在 `_executeAddItem` 的 `finally` 中发射，重复扫码走 `return@launch` 不会进入 `_executeAddItem`，两者互斥 |
| 4 | **400ms 重试后仍失败** | 低 | 两次重试（首次+重试1次）后仍抛异常，走 catch 分支显示错误。与现有行为一致 |
| 5 | **重试导致 duplicate 检查绕过** | 低 | 重试在 Mutex 内执行，pending 在此期间一直存在，后续的扫码（相同barcode）会被 `_pendingItems.contains(barcode)` 拦下 |
| 6 | **多次 scrollToItem(0) 冲突** | ✅ 安全 | `LaunchedEffect` + `SharedFlow` 收集，每次发射执行一次。串行执行，不会并发 |
| 7 | **`scrollToItem` suspend 在 `finally` 内** | ✅ 安全 | `finally` 内的 `emit` 是 runBlocking 吗？不是——`_executeAddItem` 是 suspend function，`finally` 内可以调用 `SharedFlow.emit()`。且 `emit(Unit)` 是非阻塞的 |
| 8 | **`_scanSuccessEvent` 在 Mutex 外，但用于清空输入框** | ✅ 安全 | 输入框由 UI 层（Main 线程）直接处理，不依赖任何数据状态。重复调用无害 |
| 9 | **旧代码的 `_scanSuccessEvent` 用于清空输入框+滚动** | ✅ 已解耦 | `_scanSuccessEvent` 现仅用于清空输入框+聚焦。滚动职责已委托给 `_scrollToTopEvent` |
| 10 | **`OrderItemResponse` import 缺失** | ✅ 需新增 | 需新增 `import com.kuaimai.pda.data.api.dto.OrderItemResponse` |

### 新增 import 汇总

```kotlin
import com.kuaimai.pda.data.api.dto.OrderItemResponse     // 需要新增
import kotlinx.coroutines.delay                            // 需要新增
import kotlinx.coroutines.flow.SharedFlow                  // 需要新增
import kotlinx.coroutines.sync.Mutex                       // 需要新增
import kotlinx.coroutines.sync.withLock                    // 需要新增
```

### 原始流程前置条件检查

| 条件 | 状态 | 说明 |
|:-----|:----:|:------|
| `lastScannedSku` 已设置 | ✅ | `onBarcodeScanned` 开头设置 |
| `userRepository.getToken()` 可获取 | ✅ | 登录后已有有效token |
| `pickOrderRepository.getItemByOrderIdAndSkuOuterId()` 可查询 | ✅ | Room DAO 层已实现 |
| `pickOrderRepository.insertItem()` 可用 | ✅ | Room DAO 层已实现 |
| `_duplicateScan` 有 collector | ✅ | PickDetailScreen L141-L157 |
| `_scanSuccessEvent` 有 collector | ✅ | PickDetailScreen L160-L168 |
| `_scanFailureEvent` 有 collector | ✅ | PickDetailScreen L170-L175 |
| `orderApiService.addItem()` 后端路由 | ✅ | 后端 `POST /api/orders/{orderId}/items` |

## 改动清单

| 文件 | 改动 |
|:-----|:------|
| PickDetailViewModel.kt | 新增 `import kotlinx.coroutines.delay`, `Mutex`, `withLock` |
| PickDetailViewModel.kt | 新增 `addItemMutex`、`_scrollToTopEvent`/`scrollToTopEvent` 成员 |
| PickDetailViewModel.kt | 新增 `_executeAddItem()` 私有方法，用 Mutex 串行化保护 |
| PickDetailViewModel.kt | `onBarcodeScanned` 改为委托给 `addItemMutex.withLock` |
| PickDetailViewModel.kt | `_executeAddItem` 内增加 404 自动重试 |
| PickDetailViewModel.kt | `finally` 中加 `_scrollToTopEvent.emit(Unit)` |
| PickDetailScreen.kt | `scanSuccessEvent` 移除 `animateScrollToItem(0)` |
| PickDetailScreen.kt | 新增 `scrollToTopEvent` collector 用 `scrollToItem(0)` |
| PickDetailScreen.kt | 重复扫码滚动索引改为 `filteredItems` |

## 验证

| 场景 | 预期 |
|:-----|:------|
| 连续扫码 10+ 商品 | 视口始终固定在顶部，每个新商品直接可见 |
| 弱网环境连续扫码 | 串行化执行，一次只处理一个扫码，视口不跳动 |
| 偶发 404 | 自动重试一次成功，用户无感知 |
| 重复扫码 | 滚动到该商品位置（匹配显示顺序） |
| 添加失败 | 顶部显示占位符→移除，视口固定不动，Snackbar 出现 |

## 版本号

2.25 → 2.26（不构建 APK，等通知）
