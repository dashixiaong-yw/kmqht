# 快麦API V2版本审计报告（最终版）

## 前提
- appKey `1981991413` 申请于 2022年4月1日 **之后**
- 必须使用 **V2 正式环境**：`https://gw.superboss.cc/router`（当前已正确配置）
- V2 接口 method 命名规则：**`erp.` 前缀**

## 对照官方文档逐项核实

### ✅ 正确的调用

| 实际 method | 官方文档 method | 来源 | 状态 |
|------------|----------------|------|------|
| `erp.item.general.addorupdate` | `erp.item.general.addorupdate` | [修改/新增商品V2](https://open.kuaimai.com/docs/api/API%E6%96%87%E6%A1%A3/%E5%95%86%E5%93%81/%E4%BF%AE%E6%94%B9%E6%96%B0%E5%A2%9E%E5%95%86%E5%93%81V2/) | ✅ **正确** |
| `open.token.refresh` | `open.token.refresh` | 快麦开放平台通用刷新接口 | ✅ **正确** |

### ❌ 需要修复的调用

| 位置 | 当前 method | 官方 V2 method | 差异 |
|------|------------|----------------|------|
| **后端** `kuaimai_api.py:109` | `kuaimai.item.sku.get` ❌ | `erp.item.sku.list.get` ✅ | `kuaimai.` 是非标准前缀，官方 V2 是 `erp.`，且方法名为 `erp.item.sku.list.get` |
| **Android端** `ProductViewModel.kt:268` | `supplier.list.query` ❌ 或 `item.supplier.list.get` | `item.supplier.list.get` ✅ | V1 文档中方法名就是 `item.supplier.list.get`（无 `erp.`），不是 `supplier.list.query` |

### 官方文档确认的关键信息

**1. 查询商品SKU列表V2 — `erp.item.sku.list.get`**

| 项目 | 当前代码 | 官方 V2 要求 |
|------|---------|-------------|
| method | `kuaimai.item.sku.get` ❌ | `erp.item.sku.list.get` ✅ |
| 参数 | `sku_outer_id` ❌ | `outerId`（商家编码）或 `sysItemId`（系统主商品ID）✅ |
| 响应字段 | `result.get("skus", [])` ❌ | `result.get("itemSkus", [])` ✅ |
| 说明 | sku 信息包含在 itemSkus 数组中 | 需修改解析逻辑 |

**2. 查询商品关联供应商信息V1.0 — `item.supplier.list.get`**

| 项目 | 当前代码 | 官方要求 |
|------|---------|----------|
| method | `supplier.list.query` ❌ | `item.supplier.list.get` ✅ |
| 说明 | 该接口在 V2 环境下也能使用（文档显示支持 gw.superboss.cc） | 方法名不对应，需修改 |
| 参数 | 无（仅 method） | `sysItemIds` 或 `sysSkuIds` 必填 |

> ⚠️ 注意：供应商查询接口在官方文档中标记为 V1.0，但说明可以使用 V2 环境。方法名是 `item.supplier.list.get`（无 `erp.` 前缀），且需要 `sysItemIds` 参数。

---

## 修复清单

### 修复1（P0）: 后端 SKU 查询改用 V2 接口

**文件**: `backend/app/services/kuaimai_api.py`

修改 `get_sku_by_outer_id()` 函数，将 method 改为 `erp.item.sku.list.get`，参数改为 `outerId`，响应字段改为 `itemSkus`：

```python
async def get_sku_by_outer_id(sku_outer_id: str) -> Optional[Dict[str, Any]]:
    """根据外部编码获取SKU信息（V2）"""
    try:
        result = await _call_api(
            "erp.item.sku.list.get",
            {"outerId": sku_outer_id}
        )
        sku_list = result.get("itemSkus", [])
        if sku_list:
            return sku_list[0]
        return None
    except Exception as e:
        logger.error(f"查询SKU失败 sku_outer_id={sku_outer_id}: {e}")
        return None
```

### 修复2（P2）: 删除死代码

**文件**: `backend/app/services/kuaimai_api.py`

删除 `get_item_detail()` 函数（第121-131行），该函数从未被调用。

### 修复3（P1）: Android 端供应商查询接口修正

**文件**:
1. `app/src/main/java/com/kuaimai/pda/data/api/KuaimaiApiService.kt` — 注释修正
2. `app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt` — method 值修改 + 补充 `sysItemIds` 参数

将 `"supplier.list.query"` 改为 `"item.supplier.list.get"`，并补充 `sysItemIds` 参数。

---

## 总结

| 问题 | 等级 | 涉及文件 | 修改内容 |
|------|------|---------|---------|
| `kuaimai.item.sku.get` 非标准前缀 | **🔴 P0** | `kuaimai_api.py:105-118` | method→`erp.item.sku.list.get`, 参数→`outerId`, 响应→`itemSkus` |
| `supplier.list.query` 方法名不对 | **🟡 P1** | `ProductViewModel.kt:268`, `KuaimaiApiService.kt:14` | method→`item.supplier.list.get`, 补充`sysItemIds` |
| `kuaimai.item.detail.get` 死代码 | **🟢 P2** | `kuaimai_api.py:121-131` | 删除该函数 |
| `erp.item.general.addorupdate` | ✅ 正确 | — | 无需修改 |
| `open.token.refresh` | ✅ 正确 | — | 无需修改 |
