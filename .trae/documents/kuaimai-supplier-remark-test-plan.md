# 供应商+备注修改功能测试与修复计划

## 已知问题

### 已确认的问题（依据之前V2 API测试结果）

1. **`erp.item.general.addorupdate` 需要以下参数才能成功**：
   - 顶层: `id`(sys_item_id), `outerId`(itemOuterId), `title`(商品标题)
   - SKU (备注更新): `id`(sys_sku_id), `outerId`(skuOuterId), `remark`
   - 供应商: `code`(supplier_code), `itemTitle`(supplier_name)

2. **当前缺失的参数**：
   - `PickOrderRepository` payload → 缺 `item_outer_id`, `sku_outer_id`
   - `OrderSyncWorker` → 不构造 `outerId`, `title`, `skus[].outerId`
   - `PickItemEntity` → 没有 `itemOuterId` 字段

## 测试计划

### 测试1: `item.supplier.list.get` 供应商列表

测试 `sysSkuIds` 和 `sysItemIds` 两种参数，确认返回字段是否满足 UI 供应商选择需求。

### 测试2: `erp.item.general.addorupdate` 参数最小集

逐步尝试：
- Test 2a: 只传 `id` + `skus[].id` + `skus[].remark`（当前代码状态）
- Test 2b: 补充 `skus[].outerId`
- Test 2c: 补充顶层 `outerId` + `skuOuterId`
- Test 2d: 补充 `title`
找到能成功的最小必需参数集。

### 测试3: 供应商更新的参数最小集

类似测试2，针对 `suppliers[].code` + `suppliers[].itemTitle`。

### 测试4: 修复代码（基于测试结果）

| 修复点 | 文件 | 修改内容 |
|--------|------|---------|
| A | `PickOrderRepository.kt` | payload 补充 `sku_outer_id`、`item_outer_id`（从 PickItemEntity 获取） |
| B | `OrderSyncWorker.kt` | `syncRemarkUpdate` 补充 `outerId`、`skus[].outerId`；提取 payload 新字段 |
| C | `OrderSyncWorker.kt` | `syncSupplierUpdate` 补充 `outerId`；提取 payload 新字段 |

### 验证

1. `python` 测试脚本逐项验证 API 调用
2. `./gradlew assembleRelease` 构建通过
