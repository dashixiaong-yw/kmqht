# 取货单详情库存不显示 - 终版

## 根因

`loadStocks()` 在 init L146 与 `syncItemsFromBackend()` L130 并发 → `items.value` 为 `emptyList` → `forEach` 空 → `stockMap` 永远为空。

## 前置验证

| 条件 | 状态 |
|:-----|:----:|
| `OrderItemResponse.skuOuterId: String` | ✅ [OrderDto.kt L90](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/OrderDto.kt#L90) |
| `SystemApiService` 已注入 | ✅ [L13](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L13) + [L55](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L55) |
| `kotlinx.coroutines.launch` 已 import | ✅ [L35](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L35) |
| `_executeAddItem` 中有 `r.skuOuterId` | ✅ 来自 `OrderItemResponse`，[L255](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L255) |
| `syncItemsFromBackend` 中有 `detail.items` | ✅ 来自 `orderApiService.getOrderDetail` |
| 新增字段 `_stockMap` 已定义 | ✅ [L63-64](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L63-L64) |

---

## 改动：PickDetailViewModel.kt（5 处）

### ① 删除 L146 的无效调用

```kotlin
// 删除
viewModelScope.launch { loadStocks() }
```

### ② syncItemsFromBackend 末尾新增

在 L479 `loadSuppliers()` 之后（try 块内）：

```kotlin
loadSuppliers()
// 实时获取库存
viewModelScope.launch {
    loadStocksForSkus(detail.items.map { it.skuOuterId }.distinct())
}
```

### ③ _executeAddItem 成功路径新增

在 L267 `insertItem(item)` 之后：

```kotlin
pickOrderRepository.insertItem(item)
viewModelScope.launch {
    val token = userRepository.getToken()
    if (token.isEmpty()) return@launch
    try {
        val resp = systemApiService.getSkuStock(token, r.skuOuterId)
        resp.totalStock?.let { _stockMap.value = _stockMap.value + (r.skuOuterId to it) }
    } catch (e: Exception) { Log.w(TAG, "库存查询失败: ${r.skuOuterId}", e) }
}
```

### ④ refresh() 作用域修复

`detail` 提升到 try 外部：

```kotlin
fun refresh() {
    viewModelScope.launch {
        _isRefreshing.value = true
        var detail: OrderDetailResponse? = null
        try {
            ...
            detail = orderApiService.getOrderDetail(token, orderId)
            ...
            detail.items.forEach { upsertItemFromResponse(it) }
            loadSuppliers()
        } catch (e: Exception) { ... } finally {
            if (detail != null) {
                viewModelScope.launch {
                    loadStocksForSkus(detail.items.map { it.skuOuterId }.distinct())
                }
            }
            _isRefreshing.value = false
        }
    }
}
```

### ⑤ loadStocks() 重写为 loadStocksForSkus

```kotlin
private suspend fun loadStocksForSkus(skuList: List<String>) {
    val token = userRepository.getToken()
    if (token.isEmpty()) return
    _stockMap.value = emptyMap()
    coroutineScope {
        skuList.forEach { sku ->
            launch {
                try {
                    val resp = systemApiService.getSkuStock(token, sku)
                    resp.totalStock?.let { _stockMap.value = _stockMap.value + (sku to it) }
                } catch (e: Exception) { Log.w(TAG, "库存查询失败: $sku", e) }
            }
        }
    }
}
```

新增 import：`import kotlinx.coroutines.coroutineScope`

---

## 回归分析

| 场景 | 行为 | 回归？ |
|:-----|:-----|:----:|
| 首次进入 | syncItemsFromBackend → 数据就绪 → loadStocksForSkus 从 API 响应提取 SKU 列表 | ✅ 修复 |
| 重新进入(Room有缓存) | items.value 立即有数据，stockMap 同步初始化 | ✅ 无变化 |
| 扫码添加 | 成功 → 单 SKU 查库存（不覆盖已有的其他 SKU 库存） | ✅ 新增 |
| 扫码添加 409 | syncItemsFromBackend → 末尾 loadStocksForSkus | ✅ 修复 |
| 下拉刷新 | detail 提升到 try 外 → finally 中 loadStocksForSkus | ✅ 修复 |
| 供应商过滤切换 | items 不变 → 库存不变 | ✅ 无影响 |
| 并发写 stockMap | MutableStateFlow.value 原子操作 | ✅ 安全 |
| coroutineScope import | 新增 `import kotlinx.coroutines.coroutineScope` | ✅ 无冲突 |
| loadStocks 旧签名 | 全替换为 loadStocksForSkus，无残留引用 | ✅ 已全量替换 |

## 版本号

2.49 → 2.50
