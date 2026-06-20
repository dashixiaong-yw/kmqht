# 快麦开放平台 API 接口规范

> **目的**：记录快麦 ERP 接口的实际调用方式、请求/响应格式、编码差异，避免因格式假设错误导致的回归 bug（如 v1.79 ItemUpdateWrapper 误判）。
>
> **最后更新**：v1.81 (2026-06-20)

---

## 一、调用方式总览

| 接口 | HTTP Method | 编码方式 | 响应结构 |
|------|:--:|:--:|------|
| `erp.item.single.sku.get` | POST | `data=` (form-urlencoded) | **包裹层** |
| `item.supplier.list.get` | POST | `data=` (form-urlencoded) | **包裹层** |
| `item.single.get` | POST | `data=` (form-urlencoded) | **包裹层** |
| `erp.item.general.addorupdate` | POST | `data=` (form-urlencoded) | **扁平** ❗ |
| `supplier.list.query` | POST | `files=` **(multipart)** | **扁平** ❗ |
| `open.token.refresh` | POST | `files=` **(multipart)** | **扁平** |

> **关键规律**：`data=`（form-urlencoded）编码的请求，响应可能有包裹层也可能扁平。`files=`（multipart）编码的请求，响应**一律扁平**。

---

## 二、接口详细规范

### 2.1 `erp.item.single.sku.get` — SKU 详情查询

**编码**：`data=` (form-urlencoded)

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `skuOuterId` | String | ✅ | SKU 外部编码，如 `"B08-24"` |

**响应结构**（**有包裹层**）：

```json
{
  "erp_item_single_sku_get_response": {
    "itemSku": [{
      "sysItemId": 5884168438716416,
      "sysSkuId": 5884168439175169,
      "itemOuterId": "B-08",
      "skuOuterId": "B08-24",
      "propertiesName": "卡皮巴拉橡皮擦24个",
      "skuRemark": "API测试-114700",
      "skuPicPath": "https://img.alicdn.com/...",
      "hasSupplier": 1,
      "supplierName": "备货①",
      "supplierCode": "0001",
      "title": "卡皮巴拉橡皮擦"
    }]
  }
}
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sysItemId` | Long | 商品系统 ID |
| `sysSkuId` | Long | SKU 系统 ID |
| `itemOuterId` | String | 商品外部编码（用于查标题） |
| `skuOuterId` | String | SKU 外部编码 |
| `propertiesName` | String | 规格名称 |
| `skuRemark` | String | **规格备注**（驼峰 `skuRemark` ❗） |
| `skuPicPath` | String | 规格图 URL |
| `hasSupplier` | int/str | 是否有供应商（1=有） |
| `supplierName` | String | 供应商名称 |
| `supplierCode` | String | 供应商编码 |
| `title` | String | 商品标题 |

**后端字段映射**（`kuaimai_api.py` `get_sku_by_outer_id`）：

| 快麦字段 (camelCase) | 内部字段 (snake_case) |
|------|------|
| `propertiesName` | `properties_name` |
| `skuPicPath` | `pic_path` |
| `skuRemark` | `remark` |
| `sysItemId` | `sys_item_id` |
| `sysSkuId` | `sys_sku_id` |
| `itemOuterId` | `item_outer_id` |
| `supplierName` | `supplier_name` |
| `supplierCode` | `supplier_code` |

---

### 2.2 `item.single.get` — 商品标题查询

**编码**：`data=` (form-urlencoded)

**说明**：SKU 查询返回的 `title` 字段可能为空，需要通过 `itemOuterId` 调用此接口获取真实标题。

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `outerId` | String | ✅ | 商品外部编码，如 `"B-08"` |

**响应结构**（**有包裹层**）：

```json
{
  "item_single_get_response": {
    "item": {
      "title": "卡皮巴拉橡皮擦",
      "outerId": "B-08",
      "sysItemId": 5884168438716416
    }
  }
}
```

**关键字段**：`item.title` — 商品标题，用于 `erp.item.general.addorupdate` 的必填 `title` 参数。

---

### 2.3 `erp.item.general.addorupdate` — 商品更新（备注/供应商）⚠️ 写操作

**编码**：`data=` (form-urlencoded)

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `id` | Long | ✅ | 商品系统 ID (`sysItemId`) |
| `outerId` | String | ✅ | 商品外部编码 (`itemOuterId`) |
| `title` | String | ✅ | **必须传真实标题**，传 `"."` 会覆盖原有标题为 `"."` ❗ |
| `skus` | JSON String | ✅ | SKU 数组，JSON 序列化后的字符串 |

`skus` 数组项结构：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `id` | Long | ✅ | SKU 系统 ID (`sysSkuId`) |
| `outerId` | String | ✅ | SKU 外部编码 (`skuOuterId`) |
| `propertiesName` | String | ✅ | 规格名称 |
| `remark` | String | — | 规格备注（仅备注更新时传） |
| `suppliers` | JSON Array | — | 供应商列表（仅供应商更新时传） |

**供应商子项**：`{"code": "0002", "itemTitle": "备货②"}`

**响应结构**（**扁平，无包裹层** ❗）：

```json
{
  "traceId": "5788120279655612819",
  "success": true,
  "skus": [
    {"sysSkuId": 5884168438936065, "skuOuterId": "B08-06"},
    {"sysSkuId": 5884168439067136, "skuOuterId": "B08-12"},
    {"sysSkuId": 5884168439175169, "skuOuterId": "B08-24"}
  ],
  "outerId": "B-08",
  "id": 5884168438716416
}
```

**Android 端解析规则**（v1.81 已验证）：

```kotlin
// ✅ 正确：直接解析扁平 JSON
data class ItemUpdateResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val msg: String = ""
)
val response = kmApi.updateItemRemark(request)  // ItemUpdateResponse
if (!response.success) return false

// ❌ 错误：假设有包裹层（v1.79 的 bug）
// Gson 找不到 "erp_item_general_addorupdate_response" 键 → response 永远为 null
```

**关键约束**：

- ❌ **禁止传 `title = "."`** — 会覆盖商品标题
- ✅ `title` 必须从 `item.single.get` 实时获取
- ✅ `title == null` → 拒绝同步（v1.78 保障）
- ✅ 响应用 `success` 字段判定（不是 `code`）
- ✅ 响应是**扁平 JSON，无包裹层**

---

### 2.4 `supplier.list.query` — 供应商列表

**编码**：`files=` **(multipart/form-data)**

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `pageNo` | String | ✅ | 页码 |
| `pageSize` | String | ✅ | 每页数量 |

**响应结构**（**扁平** ❗）：

```json
{
  "total": 6,
  "success": true,
  "list": [
    {"id": 6520902, "code": "0001", "name": "备货①"},
    {"id": 6520903, "code": "0002", "name": "备货②"},
    {"id": 6520904, "code": "0003", "name": "备货③"}
  ]
}
```

**后端解析**（`kuaimai_api.py` `get_supplier_list`）：

```python
# multipart 响应可能是扁平结构，也可能有包裹层，需要兼容
result = response.json()
return result.get("supplier_list_query_response", result).get("list", [])
```

> v1.77 修复：原来用 `result.get("supplier_list_query_response", {})` 导致扁平响应永远返回空列表。

---

### 2.5 `item.supplier.list.get` — 商品供应商关系

**编码**：`data=` (form-urlencoded)

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `sysSkuIds` | String | ✅ | SKU 系统 ID，如 `"5884168439175169"` |

**响应结构**（**有包裹层**）：

```json
{
  "item_supplier_list_get_response": {
    "suppliers": [{
      "supplierName": "备货①",
      "supplierId": 6520902,
      "sysSkuId": 5884168439175169,
      "sysItemId": 5884168438716416
    }]
  }
}
```

---

### 2.6 `open.token.refresh` — Session 刷新

**编码**：`files=` **(multipart/form-data)**

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `refreshToken` | String | ✅ | 刷新令牌 |

**响应结构**（**扁平**）：

```json
{
  "success": true,
  "code": 0
}
```

**高频刷新**：`{"success": false, "code": "refresh_frequently"}` — 这是**正常限流**，session 仍有效，不应视为错误。

---

### 2.7 商品标题获取完整链路（Worker 中使用）

```
1. kmApi.getSkuInfo(skuOuterId)
   → 响应中取 itemOuterId
2. kmApi.getItemDetail(outerId = itemOuterId)
   → 响应中取 item.title
3. title 用于 erp.item.general.addorupdate 的 title 参数
```

失败处理：`getLatestTitle` 任一环节失败 → 返回 `null` → Worker 拒绝同步 → `return false`。

---

### 2.8 供应商编码说明

供应商 `code` 是唯一业务标识符（如 `"0001"`=`备货①`、`"0002"`=`备货②`）。`item.supplier.list.get` 返回的 `supplierName` 是实际供应商关联名。

---

## 三、Android 端 Retrofit 接口清单

### 3.1 直接调用快麦（`KuaimaiApiService`，BaseURL `https://gw.superboss.cc/`）

| 方法 | HTTP | 快麦 method | 返回类型 | 用途 |
|------|:--:|------|------|------|
| `updateItemRemark()` | POST | `erp.item.general.addorupdate` | `ItemUpdateResponse` | 备注更新 |
| `updateItemSupplier()` | POST | `erp.item.general.addorupdate` | `ItemUpdateResponse` | 供应商更新 |
| `getSkuInfo()` | POST | `erp.item.single.sku.get` | `SkuQueryWrapper` | SKU 查询 |
| `getItemDetail()` | POST | `item.single.get` | `ItemGetWrapper` | 商品标题 |

> 注意：仅 `updateItemRemark`/`updateItemSupplier` 的返回类型是扁平 `ItemUpdateResponse`（无包裹层）。`getSkuInfo`/`getItemDetail` 有 `*Wrapper` 包裹层。

### 3.2 通过后端中转（`SystemApiService`）

| 方法 | HTTP | 端点 | 返回类型 |
|------|:--:|------|------|
| `getSkuDetail()` | GET | `/api/sku/{skuOuterId}` | `SkuDetailResponse` |
| `getKuaimaiSuppliers()` | GET | `/api/kuaimai/suppliers` | `KuaimaiSuppliersResponse` |
| `getSessionStatus()` | GET | `/api/kuaimai/session-status` | — |
| `refreshSession()` | POST | `/api/kuaimai/refresh-session` | — |

---

## 四、编码方式差异速查

| 编码 | 何时使用 | 响应结构 | 示例 API |
|------|------|:--:|------|
| `data=` (form-urlencoded) | Android FormBody / httpx data | **包裹层或有包裹层** | `erp.item.single.sku.get`, `item.single.get`, `erp.item.general.addorupdate` |
| `files=` (multipart) | httpx files | **一律扁平** | `supplier.list.query`, `open.token.refresh` |

> Android 端 KuaimaiApiService 全部用 `@Body` + `@POST("router")` + FormBody → 对应快麦 `data=` 编码。

---

## 五、常见错误与避免

| 错误 | 版本 | 根因 | 修复 |
|------|:--:|------|------|
| 备注/供应商同步全部失败 | v1.79 | `ItemUpdateWrapper` 假设有包裹层，实际扁平 | 直接用 `ItemUpdateResponse` 解析扁平 JSON |
| 供应商列表为空 | v1.77 | multipart 响应扁平，误用 `{}` 兜底 | 改用 `result` 兜底 |
| 标题被覆盖为 `"."` | v1.78 | 传 `title = "."` 给 addorupdate | `getLatestTitle` 失败返回 null 拒绝同步 |
| `open.token.refresh` 被判定失败 | — | 未处理 `refresh_frequently` 码 | 视为正常（session 仍有效） |
| SKU 查询字段名不匹配 | — | 快麦返回 `skuRemark`（驼峰），不是 `remark` | 后端映射 `"remark": sku_data.get("skuRemark", "")` |

---

## 六、新增/修改 DTO 时的检查清单

- [ ] 确认接口响应是**包裹层**还是**扁平**（对照本文档）
- [ ] 扁平响应用 `data class XxxResponse(...)`
- [ ] 包裹层响应用 `data class XxxWrapper(@SerializedName("xxx_response") val response: XxxResponse?)`
- [ ] 字段名与快麦 API 实际返回一致（camelCase：`skuRemark` 不是 `remark`，`itemSku` 不是 `skus`）
- [ ] `erp.item.general.addorupdate` 的返回类型**必须是 `ItemUpdateResponse`（扁平）**，禁止加 wrapper
- [ ] 写操作必须验证不修改无关字段（title/propertiesName/supplier）
