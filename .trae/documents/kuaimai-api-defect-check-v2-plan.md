# 快麦API集成二次缺陷检查计划

## 检查结果

经过对前后端所有快麦API相关代码的逐行审查，对比快麦开放平台官方文档，发现以下缺陷：

---

### P0 - 严重缺陷

#### BUG-1: 前端KuaimaiInterceptor发送JSON body，但后端发送form-urlencoded，且快麦API可能只接受form-urlencoded
- **官方文档**：公共参数+业务参数一起提交，刷新接口要求 `Content-Type: multipart/form-data`
- **后端** `kuaimai_api.py:65`：`client.post(KUAIMAI_API_BASE, data=params)` — httpx的`data=`发送form-urlencoded
- **前端** `KuaimaiInterceptor.kt:77-78`：`newBodyJson.toRequestBody("application/json; charset=utf-8")` — 发送JSON body
- **风险**：前后端请求格式不一致，且快麦API可能不接受JSON body。后端用form-urlencoded是正确的，前端应保持一致
- **修复**：前端KuaimaiInterceptor改为发送form-urlencoded格式

#### BUG-2: 前端KuaimaiInterceptor注释中仍写`app_key`等旧参数名
- **KuaimaiInterceptor.kt:19**：注释 `1. 添加公共参数（app_key、timestamp、session、method等）`
- **实际代码**：已改为`appKey`等正确参数名
- **风险**：注释与代码不一致，误导维护者
- **修复**：更新注释为`appKey、timestamp、session、method等`

---

### P1 - 中等缺陷

#### BUG-3: 后端_call_api响应解析逻辑有bug
- **kuaimai_api.py:70-76**：
  ```python
  error_response = result.get(f"{method.replace('.', '_')}_response", {})
  if "error_response" in result:
      ...
  return error_response
  ```
- **问题**：L70先取了`{method}_response`赋值给`error_response`变量，然后L71检查`result`中是否有`error_response` key。如果API成功返回，`result`中只有`{method}_response`，没有`error_response`，L71不会命中，L76返回`{method}_response`的内容。逻辑正确但变量名`error_response`极度误导。
- **风险**：变量名误导，但逻辑正确。不过如果`{method}_response`不在result中（某些API可能返回不同的key结构），会返回空dict
- **修复**：重命名变量为`api_response`，并增加空响应检查

#### BUG-4: 后端KuaimaiApiService只有2个API方法，但注释说7个
- **kuaimai_api.py:88**：注释 `# ==================== 7个API方法 ====================`
- **实际**：只有 `get_sku_by_outer_id` 和 `get_item_detail` 2个方法
- **修复**：注释改为2个

#### BUG-5: 前端OkHttpClient同时添加了ApiKeyInterceptor和KuaimaiInterceptor
- **NetworkModule.kt:132-133**：
  ```kotlin
  .addInterceptor(apiKeyInterceptor)
  .addInterceptor(kuaimaiInterceptor)
  ```
- **问题**：ApiKeyInterceptor给所有请求添加`X-API-Key`头，KuaimaiInterceptor给快麦请求添加签名。但快麦API不需要`X-API-Key`头，后端API不需要签名参数。两个拦截器都作用于同一个OkHttpClient，导致：
  - 快麦API请求也带了`X-API-Key`头（无害但多余）
  - 后端API请求也经过KuaimaiInterceptor（但被host匹配过滤掉了，所以无害）
- **风险**：功能上无害，但架构不清晰。如果未来ApiKeyInterceptor或KuaimaiInterceptor有副作用可能出问题
- **修复**：当前可接受，不做修改

#### BUG-6: TokenAuthenticator中KEY_USER_TOKEN硬编码
- **NetworkModule.kt:257**：`private const val KEY_USER_TOKEN = "user_token"`
- **PrefsKeys.kt:9**：`const val KEY_USER_TOKEN = "user_token"`
- **问题**：TokenAuthenticator硬编码了key名，没有使用PrefsKeys常量
- **修复**：TokenAuthenticator应引用PrefsKeys.KEY_USER_TOKEN

#### BUG-7: AuthRepositoryImpl中KEY_USER_TOKEN也硬编码
- **AuthRepository.kt:43**：`private const val KEY_USER_TOKEN = "user_token"`
- **问题**：同BUG-6，应使用PrefsKeys.KEY_USER_TOKEN
- **修复**：引用PrefsKeys.KEY_USER_TOKEN

---

### P2 - 轻微缺陷

#### BUG-8: 后端refresh_session中refreshToken参数名可能需要驼峰
- **kuaimai_api.py:135**：`params["refreshToken"] = kuaimai_creds.refresh_token`
- **官方文档**：请求参数名为`refreshToken`（驼峰）
- **当前代码**：已使用驼峰，正确
- **结论**：无需修改

#### BUG-9: 前端KuaimaiApiService的@POST路径是"router"而非完整URL
- **KuaimaiApiService.kt:18**：`@POST("router")`
- **问题**：Retrofit的@POST路径会拼接在baseUrl后面，baseUrl是`https://gw.superboss.cc/router`，所以最终URL是`https://gw.superboss.cc/routerrouter`，这是错误的！
- **验证**：Retrofit的@POST路径是相对于baseUrl的path，如果baseUrl以`/router`结尾，@POST("router")会变成`/routerrouter`
- **修复**：@POST路径应改为`""`或`./`，或者baseUrl不以`/router`结尾而@POST写`router`

**等一下，让我再仔细验证这个**：Retrofit的baseUrl必须以`/`结尾。如果baseUrl是`https://gw.superboss.cc/`，@POST("router")就是`https://gw.superboss.cc/router`。但当前baseUrl是`https://gw.superboss.cc/router`，不以`/`结尾，Retrofit会报错。

让我重新检查NetworkModule中kuaimai Retrofit的baseUrl配置...

- **NetworkModule.kt:148**：`val baseUrl = prefs.getString(PrefsKeys.KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL`
- **DEFAULT_BASE_URL** = `AppConstants.KUAIMAI_API_URL` = `"https://gw.superboss.cc/router"`
- **Retrofit要求**：baseUrl必须以`/`结尾

**这是一个P0 BUG！** Retrofit的baseUrl必须以`/`结尾，否则会抛出IllegalArgumentException。当前`https://gw.superboss.cc/router`不以`/`结尾。

但如果之前能正常构建和运行，说明可能baseUrl实际存储的值不同，或者Retrofit有容错处理。需要确认。

实际上，Retrofit在创建时如果baseUrl不以`/`结尾，会自动在末尾加`/`。但这样`https://gw.superboss.cc/router/` + `router` = `https://gw.superboss.cc/router/router`，这是错误的！

**正确做法**：
- baseUrl = `https://gw.superboss.cc/`
- @POST("router")

或者：
- baseUrl = `https://gw.superboss.cc/router/`（以`/`结尾）
- @POST("") 或 @POST(".") 

但这需要确认当前代码是否真的有这个问题。如果之前能正常运行，可能baseUrl存储的值不同。

---

## 修复计划

### 1. [P0] 前端KuaimaiInterceptor改为发送form-urlencoded格式
- **文件**：`app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt`
- 将JSON body改为form-urlencoded格式，与后端保持一致

### 2. [P0] 前端KuaimaiInterceptor注释更新
- **文件**：`app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt`
- 更新注释中的参数名

### 3. [P0] 前端Retrofit baseUrl与@POST路径修正
- **文件**：`app/src/main/java/com/kuaimai/pda/util/AppConstants.kt`
- `KUAIMAI_API_URL` 改为 `"https://gw.superboss.cc/"`（不以router结尾）
- **文件**：`app/src/main/java/com/kuaimai/pda/data/api/KuaimaiApiService.kt`
- 所有 `@POST("router")` 保持不变（因为baseUrl现在是`https://gw.superboss.cc/`，拼接后就是`https://gw.superboss.cc/router`）

### 4. [P1] 后端_call_api变量名修正
- **文件**：`backend/app/services/kuaimai_api.py`
- `error_response` 重命名为 `api_response`

### 5. [P1] 后端API方法数量注释修正
- **文件**：`backend/app/services/kuaimai_api.py`
- 注释从7个改为2个

### 6. [P1] TokenAuthenticator和AuthRepositoryImpl使用PrefsKeys常量
- **文件**：`app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt`
- **文件**：`app/src/main/java/com/kuaimai/pda/data/repository/AuthRepository.kt`
- `KEY_USER_TOKEN` 硬编码改为 `PrefsKeys.KEY_USER_TOKEN`

## 不修改的内容
- ApiKeyInterceptor和KuaimaiInterceptor共用OkHttpClient（功能无害）
- 签名算法MD5保持不变
- SESSION_WARNING_DAYS前端定义保留

## 验证步骤
1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 确认前后端参数名、请求格式、URL一致
