# 快麦API集成全面缺陷修复计划（v2 - 含官方文档验证）

> ⚠️ **历史文档声明**：本文档撰写于 v1.77 之前。关于响应格式、字段名、编码方式的**最终权威信息**，请参阅 [kuaimai-api-spec.md](../../.trae/rules/kuaimai-api-spec.md)（v1.81 规则文档）。

## 摘要

对快麦ERP开放平台API集成相关的17个文件进行了逐行审查，并对照快麦官方文档（open.kuaimai.com）验证了API地址、公共参数、签名算法、请求格式、响应格式。发现 **6个bug**（1个P0、2个P1、2个P2、1个P2新增），以及 **1个架构缺陷**。

## 官方文档验证结果

| 检查项 | 官方文档 | 代码实现 | 一致性 |
|--------|---------|---------|--------|
| API地址 | `https://gw.superboss.cc/router` | `https://gw.superboss.cc/` + `@POST("router")` | ✅ |
| appKey（驼峰） | `appKey` | `appKey` | ✅ |
| version | `1.0` | `1.0` | ✅ |
| sign_method | 默认hmac，可选md5/hmac/hmac-sha256 | `md5` | ✅（md5是合法选项） |
| timestamp格式 | GMT+8，`2020-09-21 16:58:00` | `yyyy-MM-dd HH:mm:ss` | ✅ |
| session | 即access_token | `session` | ✅ |
| 签名算法 | 参数按key排序→拼接key1value1→前后加appSecret→MD5转大写 | SignUtils.kt实现一致 | ✅ |
| 请求格式(通用) | `application/x-www-form-urlencoded` | form-urlencoded | ✅ |
| 请求格式(刷新会话) | `multipart/form-data` | `application/x-www-form-urlencoded` | ⚠️ 见BUG-6 |
| erp.item.general.addorupdate | skus/suppliers为array类型 | ItemUpdateRequest包含List字段 | ✅ |
| open.token.refresh响应 | `{"session": {"accessToken":..., "refreshToken":...}}` | 后端解析逻辑覆盖两种key | ⚠️ 见备注 |

> **备注 (v1.81更新)**：实际观察到的 `open.token.refresh` 响应为**扁平结构** `{"success": true, "code": 0}`（multipart 编码的请求响应一律扁平）。`refresh_frequently` 码表示 token 仍有效、短期内无需重复刷新，属正常行为。后端已做兼容处理。详见 [kuaimai-api-spec.md](../../.trae/rules/kuaimai-api-spec.md)。

## 已确认无缺陷的文件（12个）

- `backend/app/config.py` — 凭证管理、session刷新逻辑正确
- `backend/app/routers/system.py` — session状态查询和刷新接口正确
- `backend/app/routers/admin.py` — 管理后台快麦配置页面正确
- `backend/app/services/cache.py` — SKU缓存+快麦API调用正确
- `backend/app/models.py` — DTO定义与后端接口一致
- `backend/main.py` — 定时刷新session任务正确
- `app/.../util/SignUtils.kt` — MD5签名算法与快麦文档一致
- `app/.../util/TimeUtils.kt` — 时间格式化正确（yyyy-MM-dd HH:mm:ss）
- `app/.../util/AppConstants.kt` — baseUrl以/结尾、@POST("router")拼接正确
- `app/.../util/PrefsKeys.kt` — 常量定义完整
- `app/.../di/NetworkModule.kt` — Hilt依赖注入正确
- `app/.../data/api/SystemApiService.kt` — 后端中转接口定义正确

## 发现的缺陷

---

### BUG-1 [P0] KuaimaiInterceptor嵌套JSON解析崩溃

**文件**: `KuaimaiInterceptor.kt` L56

**问题**: `json.getString(key)` 对嵌套JSON数组/对象值抛出JSONException，导致参数丢失。

当 `ItemUpdateRequest`（包含 `skus: List<SkuUpdateDto>` 和 `suppliers: List<SupplierUpdateDto>`）被Gson序列化为JSON后，`skus` 和 `suppliers` 的值是JSON数组。`JSONObject.getString()` 无法处理非字符串值，会抛出异常。虽然外层有try-catch，但catch后整个参数解析中断，导致 `id` 和 `method` 等关键参数也可能丢失（取决于JSON key的迭代顺序）。

**影响**: 商品备注更新(`updateItemRemark`)和供应商更新(`updateItemSupplier`)功能完全失效。OrderSyncWorker中的 `syncRemarkUpdate` 和 `syncSupplierUpdate` 调用必然失败。

**官方文档依据**: `erp.item.general.addorupdate` 接口文档确认 `skus` 为 `array` 类型，`suppliers` 为 `array` 类型，在form-urlencoded中以JSON字符串格式传递。

**修复方案**:
```kotlin
// L56: 改为 json.get(key).toString()，安全处理所有JSON值类型
val key = keys.next()
val value = json.get(key)
params[key] = value?.toString() ?: ""
```

---

### BUG-2 [P1] KuaimaiInterceptor未使用的import

**文件**: `KuaimaiInterceptor.kt` L9

**问题**: `import okhttp3.Request` 未使用。

**修复方案**: 删除 L9 `import okhttp3.Request`

---

### BUG-3 [P1] 后端kuaimai_api.py未使用的List import

**文件**: `kuaimai_api.py` L5

**问题**: `from typing import Any, Dict, List, Optional` — `List` 未使用。

**修复方案**: 改为 `from typing import Any, Dict, Optional`

---

### BUG-4 [P1] 后端_call_api参数值类型安全缺失

**文件**: `kuaimai_api.py` L57-58

**问题**: `_call_api` 中 `extra_params` 的值可能是 `dict` 或 `list` 类型，直接通过 `params.update(extra_params)` 合并后，用 `httpx data=params` 发送时，httpx会将dict/list值转为Python字符串表示（如 `{'key': 'val'}`），而非JSON字符串。快麦API要求复杂参数值为JSON字符串格式。

当前后端只有2个API方法（`get_sku_by_outer_id` 和 `get_item_detail`），它们的 `extra_params` 都是简单字符串值，所以目前不会触发此bug。但作为防御性编程，应确保参数值类型安全。

**修复方案**: 在 `params.update(extra_params)` 之后、签名之前，遍历params将非字符串值序列化为JSON：
```python
# 确保所有参数值为字符串（快麦API要求复杂值为JSON字符串）
for key in list(params.keys()):
    if not isinstance(params[key], str):
        params[key] = json.dumps(params[key], ensure_ascii=False)
```

---

### BUG-5 [P2] KuaimaiApiService的@Body参数类型与KuaimaiInterceptor流程

**文件**: `KuaimaiApiService.kt` L43-51

**问题**: `updateItemRemark` 和 `updateItemSupplier` 使用 `@Body params: ItemUpdateRequest`，Retrofit将 `ItemUpdateRequest` 序列化为JSON body，然后KuaimaiInterceptor读取body后用JSONObject解析，再重建为FormBody。

修复BUG-1后，此流程可以正常工作：Gson序列化→Interceptor读取JSON字符串→`json.get(key).toString()`安全解析→重建FormBody。`method`字段不在Interceptor的覆盖范围内所以被保留，`id`字段（Long）会被正确转为字符串。

**结论**: 修复BUG-1后此问题自动解决，不需要额外修改。

---

### BUG-6 [P2] 后端refresh_session使用form-urlencoded而非multipart/form-data

**文件**: `kuaimai_api.py` L142

**问题**: 快麦官方文档明确要求 `open.token.refresh` 接口的Content-Type为 `multipart/form-data`，但后端代码使用 `httpx data=params` 发送的是 `application/x-www-form-urlencoded` 格式。

**影响**: 对于不含文件上传的请求，两种格式在参数传递上功能等价，快麦API服务端通常两种格式都接受。但严格来说不符合官方文档要求。

**修复方案**: 改用 `files=` 参数发送multipart/form-data：
```python
# 使用multipart/form-data格式（与快麦官方文档一致）
files = {key: (None, str(value)) for key, value in params.items()}
response = await client.post(KUAIMAI_API_BASE, files=files)
```

---

### ARCH-1 [架构] SettingsScreen缺少快麦session状态展示

**文件**: `SettingsScreen.kt`

**问题**: 之前的会话记录中提到SettingsScreen应该有"快麦连接状态"Card，但当前代码中没有。SettingsViewModel也没有注入SystemApiService和session相关方法。

**影响**: 用户无法在App端查看快麦session状态，也无法手动刷新session。只能通过Web管理后台操作。

**决策**: 此项为功能缺失而非bug，Web管理后台已覆盖此需求。暂不处理，记录在案。

---

## 修复计划

### Step 1: 修复BUG-1（P0 - KuaimaiInterceptor嵌套JSON解析）
- 文件: `KuaimaiInterceptor.kt` L56
- 改 `json.getString(key)` → `json.get(key)?.toString() ?: ""`

### Step 2: 修复BUG-2（P1 - 删除未使用import）
- 文件: `KuaimaiInterceptor.kt` L9
- 删除 `import okhttp3.Request`

### Step 3: 修复BUG-3（P1 - 删除未使用List import）
- 文件: `kuaimai_api.py` L5
- 改 `from typing import Any, Dict, List, Optional` → `from typing import Any, Dict, Optional`

### Step 4: 修复BUG-4（P1 - 参数值类型安全）
- 文件: `kuaimai_api.py` L58后
- 在 `params.update(extra_params)` 后添加非字符串值序列化逻辑

### Step 5: 修复BUG-6（P2 - refresh_session改用multipart/form-data）
- 文件: `kuaimai_api.py` L142
- 改 `data=params` → `files={key: (None, str(value)) for key, value in params.items()}`

### Step 6: 验证
- `./gradlew lint`
- `./gradlew assembleDebug`

### Step 7: 收尾
- 更新版本号（3处一致）
- 更新知识图谱
- 同步docker-deploy
- Git提交推送

## 假设与决策

1. **sign_method=md5**: 官方文档默认为hmac，但md5是合法选项。代码中显式指定了`sign_method=md5`，服务端会根据此参数选择对应的签名验证算法，所以不影响功能。
2. **BUG-1修复后嵌套JSON值的form-urlencoded传输**: 快麦API的 `erp.item.general.addorupdate` 接口接受 `skus` 和 `suppliers` 参数为JSON字符串。`json.get(key).toString()` 对JSON数组输出如 `[{"skuId":123,"skuRemark":"test"}]`，这正好是快麦API期望的格式。
3. **BUG-6的multipart/form-data**: 虽然官方文档要求multipart/form-data，但form-urlencoded在不含文件时功能等价。为严格遵循文档，仍改为multipart/form-data。
4. **ARCH-1暂不处理**: SettingsScreen的快麦session状态展示是功能增强而非bug，Web管理后台已覆盖此需求。
5. **KuaimaiApiService的@Body方式**: 虽然先用Gson序列化为JSON body再由Interceptor转为FormBody看起来绕了一圈，但这是Retrofit+Interceptor架构的合理模式，修复BUG-1后可正常工作。

## 官方文档来源

- 公共参数: https://open.kuaimai.com/docs/api/API文档/交易/上传发货/
- erp.item.general.addorupdate: https://open-doc.kuaimai.com/doc/92340482/f5Ql0OZC/9gMPwzLJ
- open.token.refresh: https://open.kuaimai.com/docs/api/API文档/基础/刷新会话必接/
- API地址: https://gw.superboss.cc/router（2022年4月1日后统一V2正式环境）
