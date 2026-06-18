# 上线前最终审计修复 v1.37

## 审计发现汇总

两个子代理审计发现共 **约62个问题**。经实测验证当前代码，关键问题集中在以下几项：

---

## 🔴 P0 — 必须修复（功能异常/数据丢失风险）

| # | 问题 | 文件 | 行号 | 说明 |
|:-:|------|------|:----:|------|
| **1** | v1.36 **不完整修复**：`_call_api()` 仍用 `async with httpx.AsyncClient(...)` 每次创建新连接 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | L85 | `_get_client()` 定义了但没用到 |
| **2** | v1.36 **不完整修复**：`hasSupplier == 1` 未加 `str()` 兼容 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | L150 | 快麦API可能返回字符串 `"1"` |
| **3** | v1.36 **不完整修复**：`get_supplier_list()` 未加 `wrapper_key` 解包 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | L186 | 响应在 `supplier_list_query_response` 中 |
| **4** | v1.36 **不完整修复**：`_call_api()` 错误信息 `ValueError` 未包含完整 `code/zh_desc` | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | L94 | 丢code |
| **5** | **6个sync方法忽略API响应**：调用后端API后直接 `return true`，失败时操作被误删 | [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | L150-L196 | 数据丢失风险 |
| **6** | **`Map<String, Any>` 违反规范** | [KuaimaiApiService.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/KuaimaiApiService.kt) | L17, L23 | 禁止使用 `Any` |

---

## 🟡 P1 — 重要修复

| # | 问题 | 文件 | 行号 | 说明 |
|:-:|------|------|:----:|------|
| **7** | `apiService: KuaimaiApiService` 注入但未使用（死依赖） | [ProductViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) | L10, L77 | 应移除 |
| **8** | 图片URL拼接未处理斜杠：`$serverUrl$imageUrl` 可能产生双斜杠 | [ProductViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) | L167-L177 | 应加 `.trimEnd('/')` |
| **9** | `syncRemarkUpdate` 捕获 `Exception` 未区分网络异常vs业务异常 | [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | L135-L146 | 无法重试 |
| **10** | Token刷新期间并发请求直接返回null | [NetworkModule.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt) | L284-L285 | 其他请求直接401 |
| **11** | `runBlocking` 阻塞OkHttp分发线程 | [NetworkModule.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt) | L294 | 阻塞影响网络处理 |
| **12** | `_call_api()` 返回 None 时调用方 `result.get()` 触发 AttributeError | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | L98-L104 | None.get() |
| **13** | `_build_common_params()` 返回可变字典被外部修改 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | L44-L55 | 应重建 |

---

## 🟢 P2 — 建议修复

| # | 问题 | 文件 | 行号 |
|:-:|------|------|:----:|
| **14** | `OrderSyncWorker` 静态依赖获取破坏DI模式 | [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | L51-L71 |
| **15** | payload字段缺失时无具体日志 | [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | L199-L241 |
| **16** | 文件名 `SupplierListResponse.kt` 内容不匹配 | [SupplierListResponse.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/SupplierListResponse.kt) | 全文 |
| **17** | `refreshSession` POST无body | [SystemApiService.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/SystemApiService.kt) | L24-L27 |
| **18** | cache.py仅重试1次，无指数退避 | [cache.py](file:///d:/trea项目/快麦取货通/backend/app/services/cache.py) | L38-L41 |
| **19** | `get_supplier_list()` 分页硬编码200条 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | L172-L173 |

---

## 修复步骤

### Step 1: 修复后端 P0#1-4（v1.36不完整修复）
- [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py):
  - L85: `async with httpx.AsyncClient(...)` → `_get_client()` + `await client.post(...)`
  - L150: `== 1` → `== 1 or str(...) == "1"`
  - L186: `result.get("list", [])` → `result.get("supplier_list_query_response", {}).get("list", result.get("list", []))`
  - L94: `ValueError(f"快麦API错误: {error.get('msg')}")` → 包含 `code/zh_desc`
  - L98-104: 修复 None.get() 风险

### Step 2: 修复 Android P0#5-6
- [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) L150-196: 所有sync方法添加响应检查
- [KuaimaiApiService.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/KuaimaiApiService.kt) L17, L23: `Map<String, Any>` 改用具体类型

### Step 3: 修复后端 P1#12-13
### Step 4: 修复 Android P1#7-11
### Step 5: 构建验证
```bash
.\gradlew assembleRelease
```

### Step 6: 收尾
- 版本号 1.36 → 1.37
- 更新知识图谱
- sync-to-docker-deploy
- Git push
