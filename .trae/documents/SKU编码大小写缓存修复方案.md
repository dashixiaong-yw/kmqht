# SKU 编码大小写缓存修复方案

## 一、问题分析

### 症状

用户手动输入小写编码（如 `"b08-24"`）后，回传到快麦时使用了小写编码，导致快麦侧的商家编码被改为小写。

### 根因

`cache.py` L66 的 `INSERT OR REPLACE INTO sku_cache` 将**函数参数** `sku_outer_id`（用户输入的大小写）存入缓存，而非快麦 API 返回的正确大小写。

### 完整数据流

```
用户输入 "b08-24" (小写)
    → /api/sku/b08-24 (system.py)
        → cache.py: get_sku_info("b08-24")
            → kuaimai_api.py: get_sku_by_outer_id("b08-24")
                → 快麦 API 返回 {"skuOuterId": "B08-24", ...}
                → kuaimai_api.py 返回 {"sku_outer_id": "B08-24", ...} ✅ (API 返回的正确大小写)
            → cache.py L66: INSERT INTO sku_cache VALUES (sku_outer_id="b08-24", ...) ❌
              # 存入的是函数参数 "b08-24"，不是 API 返回的 "B08-24"
            → cache.py L85: _api_data_to_dict("b08-24", sku_data)
              # 返回 {"sku_outer_id": "B08-24", ...} ✅ (用 data.get 优先取 API 值)
            → system.py 返回 skuOuterId="B08-24" ✅
```

**下次请求 "b08-24" 命中缓存**：
```
→ /api/sku/b08-24 (system.py)
    → cache.py: get_sku_info("b08-24")
        → 缓存命中 → _cache_row_to_dict(cached)
          # 返回 {"sku_outer_id": "b08-24", ...} ❌ (缓存中是小写)
        → system.py 返回 skuOuterId="b08-24" ❌
→ Android ProductScreen: detail.skuOuterId = "b08-24"
→ OfflineOperation payload: {"sku_outer_id": "b08-24"}
→ Worker syncRemarkUpdate: fetchLatestSkuDataViaBackend("b08-24")
    → 又命中缓存 → skuData.skuOuterId = "b08-24"
    → correctSkuOuterId = "b08-24" ❌
    → 快麦 API 收到小写编码
```

### 前置条件验证

| 检查项 | 状态 |
|:-------|:----:|
| `kuaimai_api.py` L173 `sku_outer_id` | ✅ 从 API 响应 `skuOuterId` 取值，始终正确 |
| `cache.py` L85 `_api_data_to_dict` | ✅ 使用 `data.get("sku_outer_id", param)` 优先取 API 值 |
| `cache.py` L113-120 `_cache_row_to_dict` | ❌ 返回 `row["sku_outer_id"]`（缓存中的值，可能小写） |
| `system.py` L293 返回 | ✅ `sku_info.get("sku_outer_id", sku_outer_id)` 优先取结果值 |
| `OrderSyncWorker.kt` L407/461 `correctSkuOuterId` | ✅ 优先取 `skuData.skuOuterId` |
| `OrderSyncWorker.kt` `fetchLatestSkuDataViaBackend` | ✅ 走后端 API 获取缓存/API 值 |

**唯一需要修改的地方是 `cache.py` L66**：将函数参数 `sku_outer_id` 改为 API 响应值 `sku_data.get("sku_outer_id", sku_outer_id)`。

## 二、修改内容

### 唯一改动：`cache.py` L66

**文件**：`backend/app/services/cache.py`

**改动前**：
```python
sku_outer_id,                # 函数参数（用户输入的大小写）
```

**改动后**：
```python
sku_data.get("sku_outer_id", sku_outer_id),  # 快麦API返回的正确大小写
```

### 原理

`sku_data` 来自 `get_sku_by_outer_id()` 的返回值，其中 `"sku_outer_id"` 字段（[kuaimai_api.py L173](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py#L173)）取自知麦 API 响应的 `skuOuterId`，始终为正确大小写。

修改后：
- 缓存始终保存 API 返回的正确大小写
- 缓存命中时，`_cache_row_to_dict` 返回正确大小写
- 后端 `/api/sku/{id}` 返回正确大小写
- Worker 获取到的 `skuData.skuOuterId` 为正确大小写
- 回传快麦时使用正确大小写 ✅

## 三、回归风险分析

| 风险 | 分析 | 等级 |
|:-----|:-----|:----:|
| 缓存 key 不变 | `sku_outer_id` 列是 PRIMARY KEY，存入 API 大小写后，下次用小写查询仍会是 cache-miss（key 不同），多一次 API 调用后命中。不影响正确性 | 低 |
| `INSERT OR REPLACE` 行为 | 已存在的 record 会被 `ON CONFLICT` 替换，无数据冗余 | 低 |
| `orders.py` 不涉及 | `add_order_item` 的 duplicate check（L206-207）使用用户输入 case 做匹配，与 cache 无关 | 无影响 |
| Android 端不涉及 | 仅后端缓存修复，Android 端代码不变 | 无影响 |
| `get_sku_info` 的其他调用者 | 仅 system.py L268 和 orders.py L214 调用，均消费 `sku_info["sku_outer_id"]` 字段，修复后均受益 | 正面影响 |

## 四、验证步骤

1. 部署后端：NAS 执行 `docker-compose up -d --build`
2. 手动测试：
   - 手动输入小写编码 `"b08-24"` 查询 → 后台/API 应返回大写 `"B08-24"`
   - 再次查询同编码（命中缓存）→ 仍应返回大写 `"B08-24"`
   - 修改备注 → Worker 应使用正确大写编码回传快麦
   - 切换供应商 → Worker 应使用正确大写编码回传快麦
3. 确认 CHANGELOG.md 中 v1.90 的记录已覆盖此问题（v1.90 本身已有部分修复，此次为补全缓存路径）