# v1.86 完整审计报告

## 各 Bug 状态总览

| Bug | 修复 | 当前状态 |
|:----|:-----|:--------:|
| Bug 3. httpx 404 | 连接池 + TransportError 重试 ✅ | **正确** |
| Bug 4. 供应商筛选刷新 | 删 `loadSuppliersFromLocal()` 调用 ✅ | **正确** |
| 日志导出 | 改为弹窗显示+复制 ✅ | **正确** |
| **Bug 1/2. Worker 同步** | **DTO + 降级链** | ❌ **修复不完整** |

---

## Bug 1/2 审计：之前的修复不充分（致命）

### 之前做了什么

1. `SkuItemInfo` 增加 `title` 字段 → ✅ 正确，但问题不在反序列化
2. `fetchLatestSkuData` 增加降级链 → ✅ 正确，但底层 API 调用已失败
3. Worker 全路径加日志 → ✅ 正确，已证明调用失败

### 日志证明

```
[06-21 01:02:42.841] Worker启动，共 1 个待处理操作
[06-21 01:02:43.168] 快麦备注同步失败: 获取SKU数据失败, sku=DSGHQC
[06-21 01:02:43.174] Worker启动，共 1 个待处理操作  ← 重试
[06-21 01:02:43.337] 快麦备注同步失败: 获取SKU数据失败, sku=DSGHQC
...（共5次）
```

### 根因

Worker 的 `fetchLatestSkuData()` 通过 KuaimaiApiService 从 Android **直连** `gw.superboss.cc` 调用：
- `erp.item.single.sku.get` → 获取 itemOuterId + propertiesName
- `item.single.get` → 获取 title

**两者全部失败**。原因可能是 Android 设备到快麦 API 的网络问题、响应格式不匹配、或凭证问题。

### 解决方案：改走后端中转

后端已有完整接口 `GET /api/sku/{sku_outer_id}`（[system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L261-L308)），该接口：
- 通过后端 httpx 调用快麦 API（已修复连接池 ✅）
- 有缓存降级机制 ✅
- 后端日志已证明正常工作 ✅
- Android 端 `SystemApiService.getSkuDetail()` 已存在 ✅
- 返回 `SkuDetailResponse`（含 `itemTitle`）DTO 已存在 ✅

### 改动清单（最小改动）

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| [App.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/App.kt) | `OrderSyncWorkerDeps` 增加 `systemApiService` 字段 + 初始化赋值 | +3 |
| [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | 新增 `fetchLatestSkuDataViaBackend()` 方法，`syncRemarkUpdate`/`syncSupplierUpdate` 中替换调用 | ~25 |

**不需要改**：后端 system.py、SkuDetailResponse DTO、NetworkModule — 都已就绪。

### 改动详情

**App.kt**：
```kotlin
// OrderSyncWorkerDeps 增加
@Volatile var systemApiService: SystemApiService? = null

// onCreate 中初始化
systemApiService = this@App.systemApiService
```

**OrderSyncWorker.kt**：
```kotlin
private val systemApiService: SystemApiService? by lazy {
    com.kuaimai.pda.App.OrderSyncWorkerDeps.systemApiService
}

/** 通过后端中转获取SKU数据 */
private suspend fun fetchLatestSkuDataViaBackend(skuOuterId: String, itemOuterIdFallback: String? = null): SkuSyncData? {
    val userRepo = userRepository ?: return null
    val api = systemApiService ?: return null
    val token = userRepo.getToken()
    return try {
        val detail = api.getSkuDetail(token, skuOuterId)
        val title = if (detail.itemTitle.isNotBlank()) detail.itemTitle else return null
        val itemOuterId = if (detail.itemOuterId.isNotBlank()) detail.itemOuterId 
                          else itemOuterIdFallback ?: return null
        SkuSyncData(title = title, itemOuterId = itemOuterId, propertiesName = detail.propertiesName)
    } catch (e: Exception) {
        Log.w(TAG, "通过后端获取SKU数据失败: $skuOuterId — ${e.message}")
        appendLog(applicationContext, "后端SKU查询失败: sku=$skuOuterId, error=${e.message}")
        null
    }
}
```

两处替换：
```kotlin
// syncRemarkUpdate L293: 替换
val skuData = fetchLatestSkuData(kmApi, skuOuterId, ...)
// 为
val skuData = fetchLatestSkuDataViaBackend(skuOuterId, ...)

// syncSupplierUpdate L345: 同样替换
```

`fetchLatestSkuData()` 和 `apiService` 保留不动（用于其他可能的调用场景）。

### 回归风险检查

| 风险 | 评估 |
|:-----|:------|
| 后端 `itemTitle` 为空 | ✅ 后端先拿 properties_name 兜底，再尝试获取真实 title。不会为空 |
| 后端 `/api/sku/xxx` 需要 token | ✅ Worker 已有 userRepository.getToken() |
| `SystemApiService` 注入失败 | ✅ `?: return null` 保护，Worker 进入重试 |
| 后端 `/api/sku/xxx` 返回值与之前 SDK 不一致 | ✅ 字段名完全匹配：`itemTitle`→title, `itemOuterId`→itemOuterId, `propertiesName`→propertiesName |

### 验证

1. 修改备注/供应商
2. 去设置页 → 查看同步日志
3. 应该看到 `后端SKU查询成功`（或失败时明确错误原因）
4. 成功时快麦后台数据已更新
