# 供应商 + 备注功能修复计划（已验根因，无缓存策略）

## 验证结果汇总

| 问题 | 验证方式 | 结论 |
|:--|:--|:--|
| 供应商列表获取失败 | 直接调 `supplier.list.query` → 成功返回6个供应商 | ✅ 权限不匹配 |
| 备注未同步到快麦 | 代码追踪 | ✅ Worker 只触发一次 |
| title="." 覆盖标题 | Python 穷举测试 | ✅ `item.single.get` 可获取最新 title |
| 扫码无备注 | 代码追踪 | ✅ loadSkuInfo() 只查 Room |

### 核心设计原则

> **Room = 图片（服务器存储的库区图/装箱图）+ 本地取货状态。其余所有数据（标题、备注、供应商、规格等）必须从快麦 API 实时获取，禁止 Room 缓存。**
>
> 原因：快麦 ERP 端可能随时修改商品信息，Room 缓存会过时导致数据混乱。

### 关键 API

```
supplier.list.query          → [{code, name, id}]  (供应商列表，已确认)
erp.item.single.sku.get      → itemOuterId         (SKU→商品外码，已有)
item.single.get              → title               (最新标题，已确认)
erp.item.general.addorupdate →                     (更新备注/供应商，已有)
```

### Room 数据用途定义

| 表 | 存什么 | 为什么 |
|:--|:--|:--|
| `pick_order` | 订单元数据(status/total/...) | 本地订单状态 |
| `pick_item` | SKU绑定关系 + **本地完成状态** | 仅状态是本地权威数据 |
| `product_image` | 库区图/装箱图 URL | 图片存在服务器，Room 缓存URL |
| `pending_operation` | 离线操作队列 | 离线到在线过渡 |

> **禁止 Room 缓存**：remark、supplierName、supplierCode、propertiesName、title 等快麦数据。这些字段存在于 PickItemEntity 中仅因为历史原因，修复后逐步移除。新代码不从 Room 读取这些字段。

---

## 一、供应商列表获取失败

### 根因

后端 `GET /api/kuaimai/suppliers` 要求 `settings` 权限，Android 检查 `update_supplier`。

### 修复（P0）

[system.py:L174](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L174)：`check_permission("settings")` → `check_permission("update_supplier")`

---

## 二、备注未同步 + title 污染

### 根因A：Worker 只触发一次

`OneTimeWorkRequest + KEEP` → 只在启动时运行一次。

### 根因B：title="." 覆盖标题

`title = "."` 永久覆盖快麦原文标题。

### 修复方案

#### P0-A：每次新增 pending 操作后触发 Worker

[PickOrderRepository.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/PickOrderRepository.kt) `enqueueOperation()` 末尾触发 `WorkManager.beginUniqueWork("order_sync", KEEP, ...)`。

#### P0-B：Worker 同步时实时获取 title（不缓存）

```
Worker syncRemarkUpdate / syncSupplierUpdate:
  skuOuterId (从 payload)
    → erp.item.single.sku.get  → itemOuterId
    → item.single.get          → title (最新！)
    → erp.item.general.addorupdate (title=最新title)
```

需修改文件：

| # | 层 | 文件 | 改动 |
|---|:--:|------|------|
| 1 | Android | `KuaimaiApiService.kt` | 新增 `getSkuInfo()` + `getItemDetail()` 方法 |
| 2 | Android | DTO 目录 | 新增 `SkuQueryRequest`/`SkuQueryResponse`/`ItemGetRequest`/`ItemGetResponse` |
| 3 | Android | `PickOrderRepository.kt` | payload 增加 `item_outer_id`（已有 `sku_outer_id`，Worker 用 sku_outer_id 查也可以） |
| 4 | Android | `OrderSyncWorker.kt` | `syncRemarkUpdate()` + `syncSupplierUpdate()` 增加实时 title 获取逻辑 |

**Worker 修改后的 syncRemarkUpdate**：

```kotlin
private suspend fun syncRemarkUpdate(op: PendingOperationEntity): Boolean {
    val kmApi = apiService ?: return false
    val remark = extractPayloadValue(op.payload, "remark") ?: return false
    val skuId = extractPayloadValue(op.payload, "sys_sku_id")?.toLongOrNull() ?: return false
    val itemId = extractPayloadValue(op.payload, "sys_item_id")?.toLongOrNull() ?: return false
    val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: return false
    val propertiesName = extractPayloadValue(op.payload, "properties_name") ?: return false
    val outerId = skuOuterId.substringBefore("-")

    // 实时获取最新 title（不缓存，不依赖 Room）
    val title = getLatestTitle(kmApi, skuOuterId, propertiesName)

    val request = ItemUpdateRequest(
        id = itemId, method = "erp.item.general.addorupdate",
        outerId = outerId, title = title,
        skus = listOf(SkuUpdateDto(skuId = skuId, skuOuterId = skuOuterId,
            skuRemark = remark, skuPropertiesName = propertiesName))
    )
    val response = kmApi.updateItemRemark(request)
    return response.success
}

// 实时获取 title：三重降级
private suspend fun getLatestTitle(kmApi: KuaimaiApiService, skuOuterId: String, fallback: String): String {
    try {
        // Step 1: SKU查询 → 获 itemOuterId
        val skuResp = kmApi.getSkuInfo(SkuQueryRequest(skuOuterId = skuOuterId))
        val itemOuterId = skuResp.itemOuterId
        if (itemOuterId.isBlank()) return fallback
        
        // Step 2: item查询 → 获 title
        val itemResp = kmApi.getItemDetail(ItemGetRequest(outerId = itemOuterId))
        return itemResp.title.ifBlank { fallback }
    } catch (e: Exception) {
        Log.w(TAG, "获取最新title失败，使用propertiesName: ${e.message}")
        return fallback  // 降级到 propertiesName
    }
}
```

> **三重降级**：`item.single.get` 失败 → `propertiesName` → 永不使用 `"."`

**安全特性**：
- ✅ 每次同步都获取最新 title，不会用过期缓存覆盖快麦
- ✅ API 调用失败时降级到 propertiesName（仍比 `"."` 好）
- ✅ 不需要 DB 迁移、不需要新增 Room 字段
- ✅ 不需要修改后端

---

## 三、商品详情页扫码无法获取备注

### 根因

`loadSkuInfo()` 只查 Room `pickItemDao`，不调任何 API。Room 中的 remark/supplier 是添加明细时的快照，早已过时。

### 修复（P0）

**API 优先**：`loadSkuInfo()` 改为先调后端 API（后端实时查快麦返回最新数据），网络失败时才降级到本地 Room。

| # | 文件 | 改动 |
|---|------|------|
| 1 | `backend/app/routers/system.py` | 新增 `GET /api/sku/{sku_outer_id}`（调 `get_sku_info()` 实时从快麦获取 remark/supplier/title） |
| 2 | `app/.../SystemApiService.kt` | 新增 `getSkuDetail()` |
| 3 | `app/.../ProductViewModel.kt` | `loadSkuInfo()` API 优先，Room 仅离线降级（try-catch 静默处理） |

```kotlin
// loadSkuInfo() 修改后逻辑：
// 1. 先调后端 /api/sku/{sku_outer_id}（实时快麦数据）
// 2. 成功 → 显示最新 remark/supplier/title
// 3. 网络失败 → 降级到 Room 本地数据（显示过时但有总比没有好）
// 4. Room 也没有 → 只显示 SKU ID
```

---

## 四、修改清单（精简后 — 12 项）

### P0

| # | 层 | 文件 | 改动 |
|---|:--:|------|------|
| 1 | Backend | `routers/system.py` L174 | `settings` → `update_supplier` |
| 2 | Backend | `routers/system.py` | 新增 `GET /api/sku/{sku_outer_id}` |
| 3 | Android | `KuaimaiApiService.kt` | 新增 `getSkuInfo()` + `getItemDetail()` |
| 4 | Android | DTO（新文件） | `SkuQueryRequest/Response` + `ItemGetRequest/Response` |
| 5 | Android | `OrderSyncWorker.kt` | `getLatestTitle()` + 两处 `title` 改为实时获取 |
| 6 | Android | `PickOrderRepository.kt` | `enqueueOperation()` 触发 Worker |
| 7 | Android | `SystemApiService.kt` | 新增 `getSkuDetail()` + DTO |
| 8 | Android | `ProductViewModel.kt` | `loadSkuInfo()` Room 未命中→后端 API |

### P1

| # | 文件 | 改动 |
|---|------|------|
| 9 | `ProductViewModel.kt` | `isLoadingSuppliers` + `supplierError` |
| 10 | `SupplierSelectDialog.kt` | 错误UI + 重试 |
| 11 | `ProductScreen.kt` | 传参 |

---

## 五、安全审查

### 5.1 无缓存策略优势

| 对比 | 旧方案（Room 缓存 title） | 新方案（实时获取） |
|:--|:--|:--|
| 快麦端修改 title 后 | Worker 发送过期 title，覆盖新 title ❌ | Worker 获取最新 title ✅ |
| DB 迁移 | 需要后端 SQLite + Room 共 2 处迁移 ❌ | 不需要 ✅ |
| 代码改动量 | 14 项 | 11 项 |
| API 调用次数 | 1 次/同步 | 3 次/同步（可接受） |

### 5.2 实时获取 title 的 API 调用于是在 Worker 后台线程，不影响 UI。

### 5.3 Room 缓存的数据（remark/supplierName 等）

remark 和 supplier 本身也需要时实性。但 remark 只能在 PDA 端修改（当前功能），所以 Room 缓存是安全的。supplier 同理——当前只有 PDA 端修改供应商功能。如果将来快麦端也能修改这些，再考虑实时获取。

---

## 六、验证步骤

1. 供应商列表：`update_supplier` 权限 → 点"切换" → 显示列表
2. 备注同步：保存备注 → Worker 触发 → 快麦 ERP 验证
3. title 回传：Worker 从 `item.single.get` 获取最新 title → 快麦 ERP 中 title 不被覆盖
4. title 时实性：先在快麦 ERP 修改商品标题 → PDA 修改备注触发同步 → 快麦 ERP 中 title 保持修改后的值
5. 扫码加载：扫未入单 SKU → 后端 API 显示；扫已入单 SKU → Room 优先
6. `./gradlew lint` 通过
