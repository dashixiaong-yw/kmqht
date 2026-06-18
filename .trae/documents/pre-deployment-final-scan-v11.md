# 快麦API上线前最终审查与修复计划（v2）

## 概述
双代理审计了后端(7个文件)和Android端(8个文件)的快麦API交互代码，汇总上线前需要修复的全部问题。

---

## 第一部分：Android端问题（共9个）

### 🔴 P0 — 必须修复

| # | 问题 | 文件 | 修复方案 |
|:-:|------|------|---------|
| A1 | `querySupplierList` + `SupplierListResponse` + `SupplierDto` 死代码 | [KuaimaiApiService.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/KuaimaiApiService.kt) L16-L18, [SupplierListResponse.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/SupplierListResponse.kt) 全文 | 删除 `querySupplierList` 方法 + 删除 `SupplierListResponse.kt` |
| A2 | `SupplierDto` 无 `@SerializedName` | [SupplierListResponse.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/SupplierListResponse.kt) | 补充 `@SerializedName("supplierName")` 等 |
| A3 | `ItemUpdateRequest.suppliers` 顶层字段死代码 + 缺 `@SerializedName` | [ItemUpdateRequest.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateRequest.kt) L15 | 删除该字段 |

### 🟡 P1 — 建议修复

| # | 问题 | 文件 | 修复方案 |
|:-:|------|------|---------|
| A4 | `syncSupplierUpdate` 用 `skuRemark = "."` 可能覆盖现有备注 | [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) L234 | 改为不传 `skuRemark` |
| A5 | 离线队列始终入队不尝试在线调用 | [PickOrderRepository.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/PickOrderRepository.kt) L149-L177 | 检查网络状态，在线时直接调用API |

### 🟢 P2 — 后续优化

| # | 问题 | 文件 |
|:-:|------|------|
| A6 | 空供应商列表无限"加载中" | [SupplierSelectDialog.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/SupplierSelectDialog.kt) L89-L100 |
| A7 | payload字段缺失时无具体日志 | [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) L199-L241 |
| A8 | `skuPropertiesName` 序列化名需核对 | [ItemUpdateRequest.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateRequest.kt) L23 |
| A9 | `supplierName` 序列化为 `itemTitle` | [ItemUpdateRequest.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateRequest.kt) L28-L31 |

---

## 第二部分：后端问题（共12个）

### 🔴 P0 — 必须修复

| # | 问题 | 文件 | 修复方案 |
|:-:|------|------|---------|
| B1 | `get_supplier_list()` 不走通用 `_call_api`，响应解析缺少 wrapper_key 处理 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L160-L182 | 复用 `_call_api.get("list", [])` |
| B2 | `refresh_session()` 不走通用 `_call_api` | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L187-L252 | 复用 `_call_api` |
| B3 | `get_supplier_list()` 异常被静默吞掉 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L176-L182 | 修复响应解析 |

### 🟡 P1 — 建议修复

| # | 问题 | 文件 | 修复方案 |
|:-:|------|------|---------|
| B4 | `_call_api` vs `get_supplier_list`/`refresh_session` 使用不同 Content-Type | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | 已确认V2 API用data=params，supplier需files=multipart，是快麦要求 |
| B5 | cache.py 重试无延迟退避 | [cache.py](file:///d:/trea项目/快麦取货通/backend/app/services/cache.py) L38-L40 | 加 `asyncio.sleep(1)` |
| B6 | config.py 与 kuaimai_api.py 循环依赖 | [config.py](file:///d:/trea项目/快麦取货通/backend/app/config.py) L115 | 将 `_config_lock` 移到 config.py |
| B7 | `_refresh_kuaimai_session` 注释与实际不一致 | [main.py](file:///d:/trea项目/快麦取货通/backend/main.py) L350 | 更新注释 |
| B8 | sku_cache.cached_at 无索引 | [database.py](file:///d:/trea项目/快麦取货通/backend/app/database.py) L118-L131 | 加索引 |

---

## 第三部分：修复步骤

### Step 1: 全量API回归测试（验证当前状态）
```bash
cd d:\trea项目\快麦取货通
python test_kuaimai_full.py
```

### Step 2: 修复后端关键问题（B1/B2/B6/B7/B8）
### Step 3: 修复Android关键问题（A1/A2/A3/A4）
### Step 4: 修复后端中等问题（B5）
### Step 5: Android构建验证
```bash
./gradlew assembleRelease
```
### Step 6: sync + Git
