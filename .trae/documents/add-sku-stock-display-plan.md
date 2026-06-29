# 商品详情与取货单详情增加实际总库存显示（头脑风暴修正版）

## 头脑风暴发现

3 个方案级遗漏，已全部纳入修正：

| # | 问题 | 修正 |
|:--:|------|------|
| 1 | `_call_api` 已处理签名+重试+包裹层剥离，新函数应复用 | 用 `_call_api` 而非 raw HTTP |
| 2 | Vervollständigungen refresh() 和 SKU 切换时不重置 totalStock | 追加覆盖 |
| 3 | `erp.item.warehouse.list.get` 端响应字段未经验证 | 实施前先验证 |

---

## 数据放置位置

### 商品详情 — SkuInfoCard

```
┌──────────────────────────────────────────────┐
│ [72dp]  规格名称(18sp 半粗)                   │
│  图      SKU编码(14sp 辅助色)                  │
│         供应商名(20sp 粗 Red) [切换]           │
│         实际库存: 1280          ← 14sp 辅助色   │
└──────────────────────────────────────────────┘
```

### 取货单详情 — PickItemRow

```
┌──────────────────────────────────────────────┐
│ [90dp]  规格名称(16sp)          [库区][箱图]   │
│  图      供应商名(20sp Bold Red)  [完成]      │
│          实际库存: 1280        ← 12sp 灰文字    │
└──────────────────────────────────────────────┘
```

行高不增：中列 51dp+18dp=69dp 仍小于左列 90dp。

---

## 改动清单（7 文件 + 2 新文件）

### 1. `backend/app/services/kuaimai_api.py`

修复预存 bug：顶部加 `import asyncio`。

新增函数（复用 `_call_api`）：
```python
async def get_sku_stock(sku_outer_id: str) -> Optional[int]:
    result = await _call_api("erp.item.warehouse.list.get", {
        "skuOuterId": sku_outer_id,
        "pageNo": 1,
        "pageSize": 1,
    })
    stock_list = result.get("stockStatusVoList", [])
    if stock_list:
        return stock_list[0].get("totalAvailableStockSum") or 0
    return None
```

### 2. `backend/app/routers/system.py` — 新增端点

```python
@router.get("/api/sku/{sku_outer_id}/stock")
async def get_sku_stock_endpoint(sku_outer_id: str, user=Depends(get_current_user)):
    from app.services.kuaimai_api import get_sku_stock
    try:
        stock = await get_sku_stock(sku_outer_id)
        return {"skuOuterId": sku_outer_id, "totalStock": stock}
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"查询库存失败: {e}")
```

### 3. `backend/app/models.py` — 新增 SkuStockResponse

### 4. `app/.../api/dto/SkuStockResponse.kt` — **新文件**

### 5. `app/.../api/SystemApiService.kt` — 新增 getSkuStock()

### 6. `ProductViewModel.kt` — 3 处改动

**6a. ProductUiState 加字段：**
```kotlin
val totalStock: Long? = null
```

**6b. ⚠️ loadSkuInfo() 开头重置 totalStock（P0 修复）：**
```kotlin
_uiState.value = _uiState.value.copy(
    isLoading = false,
    error = null,
    skuOuterId = skuOuterId,
    totalStock = null  // ← 新 SKU，重置库存
)
```

**6c. API 成功路径中异步查询库存：**
```kotlin
viewModelScope.launch {
    try {
        val token = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
        val resp = systemApiService.getSkuStock(token, skuOuterId)
        _uiState.value = _uiState.value.copy(totalStock = resp.totalStock)
    } catch (e: Exception) {
        Log.w(TAG, "查询库存失败: ${e.message}")
    }
}
```

### 7. `ProductScreen.kt` — SkuInfoCard + 库存行

参数签名加 `totalStock: Long? = null`，供应商行**下方**：
```kotlin
if (totalStock != null) {
    Spacer(modifier = Modifier.height(4.dp))
    Text("实际库存: $totalStock", fontSize = 14.sp, color = TextSecondary)
}
```

调用处传 `totalStock = uiState.totalStock`。

### 8. `PickDetailViewModel.kt` — 4 处改动

**8a. 构造函数新增注入：**
```kotlin
private val systemApiService: SystemApiService
```

**8b. 新增 stockMap：**
```kotlin
private val _stockMap = MutableStateFlow<Map<String, Long>>(emptyMap())
val stockMap: StateFlow<Map<String, Long>> = _stockMap.asStateFlow()
```

**8c. init 块末尾新增独立协程：**
```kotlin
viewModelScope.launch { loadStocks() }
```

**8d. 新增 loadStocks() + ⚠️ refresh() 也有覆盖：**
```kotlin
private suspend fun loadStocks() {
    val token = userRepository.getToken()
    if (token.isEmpty()) return
    items.value.forEach { item ->
        try {
            val resp = systemApiService.getSkuStock(token, item.skuOuterId)
            resp.totalStock?.let {
                _stockMap.value = _stockMap.value + (item.skuOuterId to it)
            }
        } catch (_: Exception) { }
    }
}
```

refresh() 末尾加 `loadStocks()`。

### 9. `PickDetailScreen.kt` — 传递 stock

```kotlin
stockMap[item.skuOuterId]
```

### 10. `PickItemRow.kt` — 新增参数 + 库存文本

`totalStock: Long? = null`，中间列供应商名下方：
```kotlin
if (totalStock != null) {
    Spacer(modifier = Modifier.height(2.dp))
    Text("实际库存: $totalStock", fontSize = 12.sp, color = TextMuted)
}
```

---

## 遗漏覆盖一览

| 漏项 | 覆盖 |
|:-----|:----:|
| `_call_api` 直接复用 | ✅ 修改 kuaimai_api.py |
| refresh() 需重新查库存 | ✅ refresh() 末尾加 loadStocks() |
| SKU 切换时状态不重置 | ✅ L122 copy(totalStock=null) |
| API 响应格式未验证 | ⚠️ 实施前需先测试确保字段名正确 |

## 版本号

2.46 → 2.47
