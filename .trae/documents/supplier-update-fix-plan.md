# 供应商更换功能修复方案（最终版）

## 问题分析

### API测试结论（已全部验证）

| 方案 | 结果 | 原因 |
|------|------|------|
| `addorupdate` + `suppliers[].code=""` | ❌ API成功，供应商不变 | addorupdate的suppliers仅用于新建商品时 |
| `addorupdate` + `suppliers[].code=supplierId` | ❌ 同上，等待30秒仍未变化 | 同上 |
| `item.supplier.update` + `suppliers[].id=supplierId` | ❌ "供应商可能不存在" | supplierId不是供应商编码 |
| `supplier.list.query` 采购模块 | ❌ 401权限不足 | APP Key没有采购模块权限 |
| `erp.supplier.list.get` | ❌ 401权限不足 | APP Key没有此接口权限 |

### 结论：无法通过快麦API更换供应商

由于 `supplierCode`(供应商编码) 在所有可用API中均无法获取，且所有修改接口都要求 `code` 必填，**目前无法通过快麦API修改供应商关系**。

## 方案：本地化供应商管理

### 设计思路

1. PDA扫码时从 `item.supplier.list.get` 获取供应商名称和supplierId
2. 在 **PDA本地数据库** 中维护 `supplier_info` 表，存储 supplierId → supplierName 映射
3. 用户切换供应商时：**仅更新本地**（PDA数据库 + 后端数据库），不调用快麦API
4. `OrderSyncWorker.syncSupplierUpdate` → **改为仅本地同步**（删除快麦API调用）

### 优点
- PDA上供应商切换立即生效
- 不依赖快麦API权限
- 供应商名称与快麦ERP一致（从API获取）

### 改动内容

### 1. SupplierDto 新增 supplierId 字段

**文件**: `app/src/main/java/com/kuaimai/pda/data/api/dto/SupplierListResponse.kt`

```kotlin
data class SupplierDto(
    val supplierName: String = "",
    val supplierCode: String = "",
    val supplierId: Long = 0  // 新增
)
```

### 2. PickOrderRepository 供应商修改改为仅本地同步

**文件**: `app/src/main/java/com/kuaimai/pda/data/repository/PickOrderRepository.kt`

`updateSupplierWithQueue` payload 补充 `supplier_id` 字段，operationType 可以沿用。

### 3. OrderSyncWorker syncSupplierUpdate 改为仅本地同步

**文件**: `app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt`

`syncSupplierUpdate` 不再调用快麦API，仅更新数据库后返回成功。
（或者改为同步到后端API而非快麦API）

## 涉及文件清单

| 文件 | 操作 |
|------|------|
| `SupplierListResponse.kt` | SupplierDto 新增 `supplierId: Long` |
| `PickOrderRepository.kt` | payload 补充 `supplier_id` |
| `OrderSyncWorker.kt` | `syncSupplierUpdate` 改为仅本地同步 |
