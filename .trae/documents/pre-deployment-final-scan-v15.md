# 回归审计最终确认 v1.39

## 核查结论

**v1.34-v1.38 所有修复已确认全部生效。** 后端6项修复、Android 9项修复均通过。发现1个P1副作用问题。

---

## ✅ v1.34-v1.38 修复核查（15项全部PASS）

### 后端（6项）

| # | 修复项 | 文件 | 行号 | 状态 |
|:-:|--------|------|:----:|:----:|
| 1 | `_call_api()` 使用 `_get_client()` 连接池 | kuaimai_api.py | L89 | ✅ |
| 2 | `ValueError` 包含完整 code/zh_desc | kuaimai_api.py | L97-99 | ✅ |
| 3 | `hasSupplier` str() 兼容 | kuaimai_api.py | L155 | ✅ |
| 4 | `get_supplier_list()` wrapper_key 解包 | kuaimai_api.py | L191 | ✅ |
| 5 | `_call_api()` None 防御 | kuaimai_api.py | L112 | ✅ |
| 6 | `get_supplier_list/refresh_session` 用 `_get_client()` | kuaimai_api.py | L183/L220 | ✅ |

### Android（9项）

| # | 修复项 | 文件 | 行号 | 状态 |
|:-:|--------|------|:----:|:----:|
| 7 | KuaimaiApiService 返回 `ItemUpdateResponse` DTO | KuaimaiApiService.kt | 全部 | ✅ |
| 8 | 6个sync方法 try-catch 响应检查 | OrderSyncWorker.kt | L145-222 | ✅ |
| 9 | `syncSupplierUpdate` 调用 `updateItemSupplier` | OrderSyncWorker.kt | L268 | ✅ |
| 10 | syncRemarkUpdate/syncSupplierUpdate 检查 response.success | OrderSyncWorker.kt | L239-244/L268-273 | ✅ |
| 11 | `syncSupplierUpdate` 不传 skuRemark | OrderSyncWorker.kt | L261-265 | ✅ 条件通过(见P1) |
| 12 | `ItemUpdateRequest` suppliers死代码移除+@SerializedName补全 | ItemUpdateRequest.kt | 全部 | ✅ |
| 13 | `ItemUpdateResponse` 已创建 | ItemUpdateResponse.kt | 全部 | ✅ |
| 14 | ProductViewModel 移除KuaimaiApiService注入 | ProductViewModel.kt | L70-78 | ✅ |
| 15 | 图片URL 3处全部 trimEnd('/') | ProductViewModel.kt | L171/174/356 | ✅ |

---

## 🔴 新发现 P1 — 必须修复

### #16 `SkuUpdateDto.skuRemark` 默认空字符串被序列化，覆盖快麦已有备注

**文件**: [ItemUpdateRequest.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateRequest.kt) L22
**根因**: `val skuRemark: String = ""` — Gson序列化非null字段，`syncSupplierUpdate` 未传 `skuRemark` 时发送 `"remark":""`，快麦API可能将其解释为清空备注
**修复**: `String = ""` → `String? = null`，Gson默认跳过null字段

## 🟡 新发现 P2

| # | 问题 | 文件 | 行号 |
|:-:|------|------|:----:|
| 17 | `syncSupplierUpdate/syncRemarkUpdate`中 `apiService` null-check延后到请求构建后 | OrderSyncWorker.kt | L267/L225 |
| 18 | `PickDetailViewModel` 默认URL硬编码 `""` 而非引用常量 | PickDetailViewModel.kt | L375 |

---

## 修复步骤

### Step 1: 修复 P1#16
- [ItemUpdateRequest.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateRequest.kt) L22: `val skuRemark: String = ""` → `val skuRemark: String? = null`

### Step 2: 修复 P2#17
- [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt):
  - L225: `val kmApi = apiService ?: return false` 移至方法体最前面
  - L267: `val kmApi = apiService ?: return false` 移至方法体最前面 (L248)

### Step 3: 构建验证
```bash
.\gradlew assembleRelease
```

### Step 4: 收尾
- 版本号 1.38 → 1.39
- 更新知识图谱
- sync-to-docker-deploy
- Git push
