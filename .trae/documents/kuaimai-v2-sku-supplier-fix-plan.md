# 快麦 V2 API 最终修复方案

## 核心问题

V2 `erp.item.single.sku.get` / `erp.item.sku.list.get` 的响应字段**不含供应商信息**（只有 `hasSupplier: 0/1` 标记）。

`cache.py` 需要 `supplier_name` + `supplier_code` 才能写入缓存和 `pick_items` 表。

**当前代码使用 `erp.item.sku.list.get` → 拿不到 supplier_name/supplier_code → PDA 显示空供应商**

## 修复方案：两步查询

### Step 1：查 SKU 基本信息

**method**: `erp.item.single.sku.get`（V2 单 SKU 查询）
**参数**: `skuOuterId`
**响应**: `itemSkus` 数组，取第一个元素
**提取字段**: `sysItemId`, `sysSkuId`, `propertiesName`, `skuPicPath`, `skuRemark`, `hasSupplier`

### Step 2：如有供应商，查供应商信息

**method**: `item.supplier.list.get`（V2 环境支持 V1 method）
**参数**: `sysSkuIds` = skuId
**响应**: `suppliers` 数组
**提取字段**: `supplierName`, `supplierCode`

### 合并结果

在 `get_sku_by_outer_id` 中合并两步结果返回。

## 涉及文件

| 文件 | 修改 |
|------|------|
| `backend/app/services/kuaimai_api.py` | 重写 `get_sku_by_outer_id()` 两步查询 |
| `backend/app/services/cache.py` | 无需修改，api_data_to_dict 字段名不变 |

## 新增函数

```python
async def get_sku_by_outer_id(sku_outer_id: str) -> Optional[Dict[str, Any]]:
    """根据外部编码获取SKU信息（V2: erp.item.single.sku.get + item.supplier.list.get）"""
    try:
        # Step1: 查SKU基本信息
        sku_result = await _call_api(
            "erp.item.single.sku.get",
            {"skuOuterId": sku_outer_id}
        )
        sku_list = sku_result.get("itemSkus", [])
        if not sku_list:
            return None
        sku_data = sku_list[0]

        # 提取基本字段
        result = {
            "properties_name": sku_data.get("propertiesName", ""),
            "pic_path": sku_data.get("skuPicPath", ""),
            "remark": sku_data.get("skuRemark", ""),
            "sys_item_id": sku_data.get("sysItemId", 0),
            "sys_sku_id": sku_data.get("sysSkuId", 0),
            "supplier_name": "",
            "supplier_code": "",
        }

        # Step2: 如有供应商，查供应商信息
        if sku_data.get("hasSupplier") == 1:
            supplier_result = await _call_api(
                "item.supplier.list.get",
                {"sysSkuIds": str(result["sys_sku_id"])}
            )
            suppliers = supplier_result.get("suppliers", [])
            if suppliers:
                result["supplier_name"] = suppliers[0].get("supplierName", "")
                result["supplier_code"] = suppliers[0].get("supplierCode", "")

        return result
    except Exception as e:
        logger.error(f"查询SKU失败 sku_outer_id={sku_outer_id}: {e}")
        return None
```
