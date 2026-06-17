# 快麦API集成最终缺陷检查计划

## 逐文件审查结果

### 已确认无缺陷的文件
- `backend/app/config.py` ✅ — KuaimaiCredentials、load/save、API URL、session过期检查均正确
- `backend/app/routers/system.py` ✅ — session-status和refresh-session接口逻辑正确
- `backend/app/models.py` ✅ — DTO定义正确
- `backend/main.py` ✅ — 定时任务配置正确（7天刷新+24小时警告）
- `backend/kuaimai.example.json` ✅ — 包含refresh_token字段
- `app/src/main/java/com/kuaimai/pda/util/SignUtils.kt` ✅ — MD5签名算法与后端一致
- `app/src/main/java/com/kuaimai/pda/util/AppConstants.kt` ✅ — baseUrl为`https://gw.superboss.cc/`
- `app/src/main/java/com/kuaimai/pda/util/PrefsKeys.kt` ✅ — 常量定义完整
- `app/src/main/java/com/kuaimai/pda/data/api/SystemApiService.kt` ✅ — 后端session接口定义正确
- `app/src/main/java/com/kuaimai/pda/data/api/dto/KuaimaiSessionDto.kt` ✅ — DTO与后端一致
- `app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt` ✅ — DI配置正确，TokenAuthenticator通过后端中转
- `app/src/main/java/com/kuaimai/pda/data/repository/AuthRepository.kt` ✅ — 使用PrefsKeys常量，通过后端中转刷新
- `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsViewModel.kt` ✅ — session状态逻辑正确
- `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt` ✅ — 快麦连接状态Card UI正确

### 发现的缺陷

#### BUG-1 [P0]: KuaimaiInterceptor无法处理嵌套JSON值
- **文件**：`app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt:56`
- **代码**：`params[key] = json.getString(key)`
- **问题**：`ItemUpdateRequest`包含`List<SkuUpdateDto>`和`List<SupplierUpdateDto>`，Gson序列化后`skus`和`suppliers`是JSON数组。`JSONObject.getString()`对非String值抛`JSONException`，catch捕获后跳过，导致嵌套参数丢失
- **影响**：商品备注更新(`updateItemRemark`)和供应商更新(`updateItemSupplier`)功能静默失败
- **修复**：改为`params[key] = json.get(key).toString()`，嵌套值会转为JSON字符串

#### BUG-2 [P1]: 后端kuaimai_api.py中List import未使用
- **文件**：`backend/app/services/kuaimai_api.py:5`
- **代码**：`from typing import Any, Dict, List, Optional`
- **问题**：`List`未被使用
- **修复**：移除`List`

#### BUG-3 [P1]: 后端_call_api对非String参数值缺少类型安全处理
- **文件**：`backend/app/services/kuaimai_api.py:57-58`
- **代码**：`if extra_params: params.update(extra_params)`
- **问题**：extra_params中的dict/list值传给httpx的`data=`时，httpx会调用`str()`，但对dict/list会输出Python repr格式而非JSON字符串。快麦API期望嵌套参数为JSON字符串
- **影响**：当前后端只有2个简单API（参数都是str/int），暂无问题。但未来扩展时可能出错
- **修复**：在_call_api中将extra_params的值统一转为str（dict/list先json.dumps）

#### BUG-4 [P2]: KuaimaiInterceptor中`import okhttp3.Request`未使用
- **文件**：`app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt:9`
- **代码**：`import okhttp3.Request`
- **问题**：`Request`类未在代码中使用
- **修复**：移除该import

---

## 修复计划

### 1. [P0] KuaimaiInterceptor正确处理嵌套JSON值
- **文件**：`app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt`
- L56: `params[key] = json.getString(key)` → `params[key] = json.get(key).toString()`
- 移除未使用的`import okhttp3.Request`

### 2. [P1] 后端kuaimai_api.py移除未使用的List import
- **文件**：`backend/app/services/kuaimai_api.py`
- L5: `from typing import Any, Dict, List, Optional` → `from typing import Any, Dict, Optional`

### 3. [P1] 后端_call_api增加参数值类型安全处理
- **文件**：`backend/app/services/kuaimai_api.py`
- 在_call_api中，extra_params合并前将非str值转为str（dict/list先json.dumps）

## 验证步骤
1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 确认Interceptor能正确处理嵌套JSON值（skus/suppliers参数不丢失）
