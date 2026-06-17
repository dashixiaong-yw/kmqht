# 快麦API全链路最终测试计划

## 测试目标
验证所有快麦API接口的数据获取、回传操作、修改操作均能正常进行，确保系统功能完整可用。

## 测试范围

| 编号 | 接口 | 方向 | 调用方 | 系统影响 |
|------|------|------|--------|---------|
| T1 | `erp.item.single.sku.get` | 查询 | 后端 kuaimai_api.py | PDA扫码时获取SKU信息 |
| T2 | `item.supplier.list.get` | 查询 | 后端 kuaimai_api.py + Android ProductViewModel | SKU详情页加载供应商 |
| T3 | `erp.item.general.addorupdate(备注)` | 修改 | Android OrderSyncWorker | 离线同步备注修改到快麦 |
| T4 | `erp.item.general.addorupdate(供应商)` | 修改 | Android OrderSyncWorker | 离线同步供应商修改到快麦 |
| T5 | `open.token.refresh` | 刷新 | 后端 kuaimai_api.py | 定时/手动刷新session |

## 测试方案

### T1: SKU查询数据完整性

用真实SKU `B08-12` 调用后端 `get_sku_by_outer_id()` 的完整逻辑（两步查询）：
1. 调用 `erp.item.single.sku.get` 获取基本信息
2. 根据 `hasSupplier` 决定是否查 `item.supplier.list.get`
3. 验证映射后的8个字段全部有值

**验证字段**: properties_name, pic_path, remark, sys_item_id, sys_sku_id, item_outer_id, supplier_name, supplier_code

### T2: 供应商列表加载

用 `item.supplier.list.get` 测试：
1. `sysItemIds` 参数获取供应商列表
2. 验证返回的 `supplierName` 字段正确
3. 验证 `supplierCode` 字段（可能为空）

### T3: 备注修改完整链路

顺序执行：
1. 调用 `erp.item.general.addorupdate` 写入备注（同步当前OrderSyncWorker的完整参数）
2. 调用 `erp.item.single.sku.get` 验证备注已写入
3. 验证新旧备注变化可检测

### T4: 供应商修改完整链路

顺序执行：
1. 调用 `erp.item.general.addorupdate` 写入供应商（同步当前OrderSyncWorker的完整参数）
2. 调用 `item.supplier.list.get` 验证供应商变更
3. 后续恢复原供应商

### T5: 会话刷新

1. 调用 `open.token.refresh`
2. 验证返回的 session 结构完整

## 测试脚本执行命令

```bash
cd d:\trea项目\快麦取货通
python test_kuaimai_full.py
```

## 预期结果

| 测试 | 预期 | 失败影响 |
|------|------|---------|
| T1 | 8个字段全部正确映射 | PDA扫码无法获取SKU信息 |
| T2 | 返回供应商列表(至少1个) | 商品详情页无法切换供应商 |
| T3 | 备注写入成功+可验证 | 离线备注修改失败 |
| T4 | 供应商写入成功 | 离线供应商修改失败 |
| T5 | session刷新成功 | session过期后无法自动续期 |

## 修复（如测试失败）

基于测试结果确定需要修复的代码。
