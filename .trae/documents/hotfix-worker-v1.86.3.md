# 修复计划：Worker 改用后端获取 SKU 数据

## 问题

日志显示 `fetchLatestSkuData()` 5次重试全部失败：
```
[06-21 01:02:42.841] Worker启动，共 1 个待处理操作
[06-21 01:02:43.168] 快麦备注同步失败: 获取SKU数据失败, sku=DSGHQC  ← 连续5次
```

## 根因

Worker 的 `fetchLatestSkuData()` 通过 `KuaimaiApiService` **从 Android 直连** `gw.superboss.cc` 调用快麦API：
1. `getSkuInfo()` → `erp.item.single.sku.get`
2. `getItemDetail()` → `item.single.get`

但后端早已实现 `/api/sku/{sku_outer_id}` 端点（backend/app/routers/system.py），且：
- Android 端的 `SystemApiService.getSkuDetail()` 已存在 ✅
- `SkuDetailResponse` DTO 包含 `itemTitle` 字段 ✅
- 后端日志证明该接口正常工作 ✅

## 修改方案

### 改动1：App.kt — 将 systemApiService 注入 Worker 依赖

```kotlin
// OrderSyncWorkerDeps 增加
@Volatile var systemApiService: SystemApiService? = null

// 初始化处增加
systemApiService = this@App.systemApiService
```

### 改动2：OrderSyncWorker.kt — 替换 fetchLatestSkuData 为后端中转

**新增** `systemApiService` 依赖，新增 `fetchLatestSkuDataViaBackend()` 方法：

```kotlin
private val systemApiService: SystemApiService? by lazy {
    com.kuaimai.pda.App.OrderSyncWorkerDeps.systemApiService
}

/**
 * 通过后端中转获取SKU数据（替代直连快麦API）
 * 后端返回 SkuDetailResponse，包含 itemTitle、propertiesName、itemOuterId
 */
private suspend fun fetchLatestSkuDataViaBackend(skuOuterId: String, itemOuterIdFallback: String? = null): SkuSyncData? {
    val userRepo = userRepository ?: return null
    val api = systemApiService ?: return null
    val token = userRepo.getToken()
    return try {
        val detail = api.getSkuDetail(token, skuOuterId)
        val title = if (detail.itemTitle.isNotBlank()) detail.itemTitle else null
        val itemOuterId = if (detail.itemOuterId.isNotBlank()) detail.itemOuterId else itemOuterIdFallback
        if (title == null || itemOuterId == null) {
            appendLog(applicationContext, "后端返回SKU数据不完整: sku=$skuOuterId, title=${title != null}, outerId=${itemOuterId != null}")
            return null
        }
        SkuSyncData(
            title = title,
            itemOuterId = itemOuterId,
            propertiesName = detail.propertiesName
        )
    } catch (e: Exception) {
        Log.w(TAG, "通过后端获取SKU数据失败: $skuOuterId — ${e.message}")
        appendLog(applicationContext, "后端SKU查询失败: sku=$skuOuterId, error=${e.message}")
        null
    }
}
```

**修改** `syncRemarkUpdate` 和 `syncSupplierUpdate` 中将 `fetchLatestSkuData()` 替换为 `fetchLatestSkuDataViaBackend()`：

```kotlin
// 替换
val skuData = fetchLatestSkuData(kmApi, skuOuterId, extractPayloadValue(op.payload, "item_outer_id"))
// 为
val skuData = fetchLatestSkuDataViaBackend(skuOuterId, extractPayloadValue(op.payload, "item_outer_id"))
```

保留 `fetchLatestSkuData()` 和 `apiService`（用于实际的 updateItemRemark/updateItemSupplier 调用）。

## 回归风险

| 风险 | 评估 |
|:-----|:------|
| 后端 `/api/sku/xxx` 不返回 `itemTitle` | ✅ 后端 system.py L253-308 确实查询了 item.single.get 获取 title |
| 后端 `/api/sku/xxx` 失败时回退 | ✅ `catch` 返回 null，Worker 按原有重试机制处理 |
| `SystemApiService` 未初始化 | ✅ `?: return null` 保护 |

## 验证

1. 修改备注/供应商 → 等待 Worker 执行
2. 查看同步日志：应有 `后端SKU查询成功` 或 `后端SKU查询失败`
3. 成功时快麦后台数据已更新
