# v1.86 完整性审计 — 最终版

> 前置条件逐项检查

---

## 一、各项 Bug 修复状态

| # | 修复项 | 状态 | 检查结论 |
|:-:|:-------|:----:|:---------|
| 1 | **Bug 3: httpx 连接池** | ✅ | `kuaimai_api.py` 已加 `keepalive_expiry=30`+TransportError重试 |
| 2 | **Bug 4: 供应商筛选** | ✅ | `loadSuppliersFromLocal()` 已从 `onBarcodeScanned()` 中删除 |
| 3 | **日志导出 → 弹窗** | ✅ | 按钮改为弹窗显示内容+复制 |
| 4 | **Bug 1/2: Worker 同步** | ❌ | **日志确认 `fetchLatestSkuData` 全部失败，需改走后端中转** |

---

## 二、逐项前置条件验证

### 条件1：`SystemApiService` 在 App.kt 中注入

- Hilt 已提供 `SystemApiService`（NetworkModule L230 Singleton）✅
- 但 `App.kt` 目前**没有**声明 `@Inject lateinit var systemApiService: SystemApiService` ❌
- `OrderSyncWorkerDeps` 中也没有 `systemApiService` 字段 ❌
- **必须添加**：App.kt +2行，并补充 import

### 条件2：Worker 可导入 SystemApiService

- `SystemApiService` 在 `com.kuaimai.pda.data.api` 包下 ✅
- Worker 文件已有同包 `ImageUploadService`、`KuaimaiApiService` 等，import 路径一致 ✅
- `SkuDetailResponse` 返回值不需要显式 import（类型推导）✅

### 条件3：后端 `itemTitle` 保证不为空

- system.py L271：先取 `sku_info.get("properties_name", "")` 作为 fallback ✅
- 然后尝试获取真实 title，失败时回退 ✅
- **结论：后端永远返回非空 itemTitle** ✅

### 条件4：`loadSuppliersFromLocal()` 是否还有调用方

- 全局搜索仅定义处 1 处匹配 ✅
- 无其他调用方，是死代码，不产生任何影响 ✅

### 条件5：`loadSuppliersFromLocal()` 去掉 `viewModelScope.launch` 是否安全

- 函数体内仅操作 StateFlow `items.value` 和 `_suppliers.value`（非阻塞）✅
- 无 suspend 函数调用 ✅
- `map`/`filter`/`distinct`/`sorted` 均为同步操作 ✅
- **结论：安全，不会阻塞 UI 线程** ✅

### 条件6：导出日志弹窗是否需额外安全检查

- `readText()` 读取最大 500 行 ≈ 50KB，Composable 中无性能问题 ✅
- `catch (_: Exception)` 兜底 ✅
- **结论：安全** ✅

### 条件7：`kuaimai_api.py` TransportError 异常传播是否正确

- TransportError 继承自 RequestError ✅
- 内层 `raise` 后的异常被外层 `except httpx.RequestError` 捕获 ✅
- `if last_exception: raise` 路径虽为冗余（不会执行到），但不影响逻辑 ✅
- **结论：正确** ✅

---

## 三、最终结论

**已修复且正确（3项）**：
- httpx 连接池 ✅ 
- 供应商筛选 ✅ 
- 日志弹窗 ✅

**需要继续修复（1项）**：
- Bug 1/2 Worker 直连快麦 API 失败 → 改走后端中转

修复方案预计改动：

| 文件 | 改动内容 | 行数 |
|:-----|:---------|:----:|
| [App.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/App.kt) | + `@Inject lateinit var systemApiService` + `OrderSyncWorkerDeps` 赋值 | +2 |
| [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | + import + `systemApiService` lazy + `fetchLatestSkuDataViaBackend` 方法 + 替换两处调用 | ~25 |
