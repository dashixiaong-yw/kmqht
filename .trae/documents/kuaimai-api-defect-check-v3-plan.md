# 快麦API集成三次缺陷检查计划

## 检查结果

经过对前后端所有快麦API相关代码的逐行审查，发现以下缺陷：

---

### P0 - 严重缺陷（会导致功能静默失败）

#### BUG-1: KuaimaiInterceptor无法正确处理嵌套JSON对象
- **文件**：`KuaimaiInterceptor.kt:50-61`
- **问题**：Interceptor用`json.getString(key)`解析请求体，但`ItemUpdateRequest`包含`List<SkuUpdateDto>`和`List<SupplierUpdateDto>`嵌套对象。`getString`对JSON数组/对象会抛出`JSONException`，catch块捕获后跳过，导致`skus`和`suppliers`参数丢失
- **影响**：商品备注更新和供应商更新功能会静默失败（参数丢失但API调用不会报错，只是快麦服务端收不到skus/suppliers数据）
- **修复方案**：Interceptor改为遍历JSON的所有key，对非String值用`json.get(key).toString()`而非`json.getString(key)`

#### BUG-2: KuaimaiApiService用@Body发送Map/对象，Retrofit序列化为JSON，但Interceptor又解析JSON转form-urlencoded
- **文件**：`KuaimaiApiService.kt`
- **问题**：流程冗余且脆弱。`@Body Map<String, String>` → Gson序列化为JSON → Interceptor解析JSON → 转form-urlencoded。对于嵌套对象，Gson序列化的JSON中数组/对象值在form-urlencoded中应作为JSON字符串传递
- **修复方案**：
  - 方案A：KuaimaiApiService改为`@FormUrlEncoded` + `@FieldMap`，Interceptor不再解析JSON
  - 方案B：保留@Body，但Interceptor正确处理嵌套值（用`get().toString()`而非`getString()`）
  - **选择方案B**，因为ItemUpdateRequest包含嵌套对象，无法简单转为`Map<String, String>`给`@FieldMap`。快麦API对嵌套参数（如skus）期望接收JSON字符串作为form字段值

### P1 - 中等缺陷

#### BUG-3: 后端kuaimai_api.py中List import未使用
- **文件**：`kuaimai_api.py:5`
- **问题**：`from typing import Any, Dict, List, Optional`，`List`未被使用
- **修复**：移除`List`

#### BUG-4: 后端_call_api中params包含非String值时，httpx data=会出错
- **文件**：`kuaimai_api.py:65`
- **问题**：`client.post(KUAIMAI_API_BASE, data=params)`，httpx的`data=`会将参数编码为form-urlencoded，但要求值都是str类型。如果params中包含int/float/dict/list，httpx会自动转str。但嵌套对象（如skus列表）需要先JSON序列化
- **影响**：当前后端只有2个简单API（get_sku_by_outer_id和get_item_detail），参数都是str/int，所以不会出错。但如果未来后端也调用带嵌套参数的API（如erp.item.general.addorupdate），就会有问题
- **修复**：在_call_api中增加参数值类型检查，非str值自动转str（dict/list先JSON序列化）

---

## 修复计划

### 1. [P0] KuaimaiInterceptor正确处理嵌套JSON值
- **文件**：`app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt`
- 将`json.getString(key)`改为`json.get(key).toString()`
- 对于嵌套对象/数组，`toString()`会输出JSON字符串，作为form字段值传递给快麦API

修改前：
```kotlin
val json = JSONObject(bodyString)
val keys = json.keys()
while (keys.hasNext()) {
    val key = keys.next()
    params[key] = json.getString(key)  // 对数组/对象抛JSONException
}
```

修改后：
```kotlin
val json = JSONObject(bodyString)
val keys = json.keys()
while (keys.hasNext()) {
    val key = keys.next()
    val value = json.get(key)
    // 嵌套对象/数组转为JSON字符串，简单值直接toString
    params[key] = value.toString()
}
```

### 2. [P1] 后端kuaimai_api.py移除未使用的List import
- **文件**：`backend/app/services/kuaimai_api.py`
- `from typing import Any, Dict, List, Optional` → `from typing import Any, Dict, Optional`

### 3. [P1] 后端_call_api增加参数值类型安全处理
- **文件**：`backend/app/services/kuaimai_api.py`
- 在_call_api中，将extra_params的值统一转为str类型（dict/list先json.dumps）

## 不修改的内容
- KuaimaiApiService的@Body方式保留（因为ItemUpdateRequest包含嵌套对象，不适合@FieldMap）
- 后端config.py、system.py、models.py、main.py — 已无缺陷
- SignUtils.kt — 签名算法正确
- AppConstants.kt — baseUrl已修正
- NetworkModule.kt — DI配置正确
- SettingsViewModel.kt / SettingsScreen.kt — session状态逻辑正确

## 验证步骤
1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 确认Interceptor能正确处理嵌套JSON值
4. 确认前后端参数名、请求格式一致
