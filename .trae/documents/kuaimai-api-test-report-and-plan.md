# 快麦API全量测试报告 & 修复计划

## 测试环境
- APP Key: `1981991413` (2022年4月后申请，必须使用V2环境)
- API Base: `https://gw.superboss.cc/router` (V2正式环境)
- 测试SKU: `B08-12` (sysItemId=5884168438716416, sysSkuId=5884168439067136, itemOuterId=B-08)

---

## 第一部分：接口测试结果

### ✅ 可用接口

| 序号 | 接口方法 | 用途 | 状态 |
|------|---------|------|------|
| 1 | `erp.item.single.sku.get` | 查询单SKU信息 | ✅ **正常** |
| 2 | `item.supplier.list.get` | 查询供应商信息 | ✅ **正常** |
| 3 | `erp.item.general.addorupdate` | 修改商品(备注/供应商) | ⚠️ **需补充必填参数** |
| 4 | `open.token.refresh` | 刷新session | ✅ **正常** |

### ❌ 不可用接口

| 序号 | 接口方法 | 原因 | 当前处理 |
|------|---------|------|---------|
| 5 | `supplier.list.query` | 旧V1已废弃401 | 已改用 `item.supplier.list.get` |
| 6 | `erp.item.sku.list.get` | 不支持按 outerId 查单个 | 已改用 `erp.item.single.sku.get` |

---

## 第二部分：字段映射验证

### SKU查询 `erp.item.single.sku.get`

| 系统字段 | 类型 | SKU API字段 | 值示例 | 验证 |
|---------|------|-----------|--------|-----|
| `properties_name` | str | `propertiesName` | "卡皮巴拉橡皮擦12个" | ✅ |
| `pic_path` | str | `skuPicPath` | "https://img.alicdn.com/..." | ✅ |
| `remark` | str | `skuRemark` | "" (未设置时为空) | ✅ |
| `sys_item_id` | int | `sysItemId` | 5884168438716416 | ✅ |
| `sys_sku_id` | int | `sysSkuId` | 5884168439067136 | ✅ |
| `item_outer_id` | str | `itemOuterId` | "B-08" | ✅ **新增需求** |

> **注意**: 已修改为 V2 camelCase → snake_case 映射，响应 key 是 `itemSku`(单数)

### 供应商查询 `item.supplier.list.get`

| 系统字段 | 类型 | 供应商API字段 | 值示例 | 验证 |
|---------|------|------------|--------|-----|
| `supplier_name` | str | `supplierName` | "备货⑥" | ✅ |
| `supplier_code` | str | `supplierCode` | "" (该供应商未设置) | ⚠️ **可能为空** |

> `supplierCode` 在API文档中有定义，但部分供应商可能未设置编码。系统需要能处理空值。

### 商品修改 `erp.item.general.addorupdate` (Android端直接调用)

测试结果：**"平台规格商家编码不能为空"** — 缺少必需参数。

| 需要补充的参数 | 值来源 | 说明 |
|--------------|--------|------|
| `outerId` | sku_data中的 `itemOuterId` = "B-08" | 平台商家编码 |
| `title` | 商品标题 | 需要从 `erp.item.list.get` 获取 |
| `skus[].skuOuterId` | SKU外部编码 = "B08-12" | 规格商家编码 |

---

## 第三部分：需要修复的问题

### 问题1：`get_sku_by_outer_id` 返回缺少 `itemOuterId`

**影响**: Android端 `OrderSyncWorker` 调用 `erp.item.general.addorupdate` 时需要 `outerId`(主商品编码)。当前 `get_sku_by_outer_id` 返回字典中没有该字段。

**文件**: `backend/app/services/kuaimai_api.py`

**修复**: 在 mapped 字典中添加 `item_outer_id` 字段：
```python
mapped = {
    ...
    "item_outer_id": sku_data.get("itemOuterId", ""),
}
```

### 问题2：`cache.py` 的 sku_cache 表及 `_api_data_to_dict` 缺少 `item_outer_id`

**影响**: sku_cache 缓存中没有 `itemOuterId`，导致离线场景下取不到该值。

**文件**: `backend/app/database.py` (表结构) + `cache.py`

**修复**: 
- `sku_cache` 表新增 `item_outer_id VARCHAR(64)` 列
- `insert or replace` 中新增该字段
- `_api_data_to_dict` 返回中新增该字段
- `_cache_row_to_dict` 返回中新增该字段

### 问题3：`SkuUpdateDto` 缺少 `skuOuterId` 参数

**影响**: V2 `erp.item.general.addorupdate` 要求 `skus[].skuOuterId` 必填。当前 Android 端只传了 `id`+`remark`。

**文件**: `app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateRequest.kt`

**修复**: `SkuUpdateDto` 新增 `@SerializedName("skuOuterId") val skuOuterId: String = ""`

### 问题4：`OrderSyncWorker` syncRemarkUpdate 未传 `itemOuterId`

**影响**: 调用 addorupdate 时缺少 `outerId`(主商品编码) 参数。

**文件**: `app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt`

**修复**: 
- payload 中补充 `item_outer_id`
- `syncRemarkUpdate` 中将 `item_outer_id` 设为 `request.outerId`
- 同样需要补充 `request.title`（商品标题）

### 问题5：`OrderSyncWorker` syncSupplierUpdate 未传 `itemOuterId` 和 `title`

**影响**: 同上，缺少必填参数。

**文件**: `app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt`

**修复**: 与问题4类似。

### 问题6：`supplierCode` 可能为空

**影响**: 部分供应商未设置编码时，系统写入 `supplier_code=""`。在使用 `erp.item.general.addorupdate` 的 `suppliers[].code` 时，传空字符串会导致API错误。

**文件**: `backend/app/services/kuaimai_api.py`

**修复**: `get_sku_by_outer_id` 中 `supplier_code` 正常处理空值即可，系统层面已有默认值处理。

---

## 第四部分：修复执行顺序

1. **后端** `kuaimai_api.py` — 补充 `item_outer_id` 字段
2. **后端** `database.py` — sku_cache 表新增 `item_outer_id` 列
3. **后端** `cache.py` — 三处补充 `item_outer_id`
4. **Android** `ItemUpdateRequest.kt` — SkuUpdateDto 新增 skuOuterId
5. **Android** `ItemUpdateRequest.kt` — ItemUpdateRequest 新增 outerId/title
6. **Android** `OrderSyncWorker.kt` — 同步补充参数 (等待OrderSyncWorker修复完成)

---

## 第五部分：验证步骤

1. `python test_kuaimai_api.py` — 确认所有 API 调用返回正确
2. 后添加一个测试：验证 `erp.item.general.addorupdate` 补充参数后能成功写入备注
3. `./gradlew assembleRelease` — Android 构建验证
4. 同步 docker-deploy
5. Git 提交推送
