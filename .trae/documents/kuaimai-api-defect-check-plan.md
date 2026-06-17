# 快麦API集成缺陷检查计划

## 检查范围
对比快麦ERP开放平台官方文档与项目代码，检查所有快麦API相关配置、参数、签名、接口调用的一致性和正确性。

---

## 缺陷清单（按严重程度排序）

### P0 - 严重缺陷（会导致API调用失败）

#### BUG-1: 后端API地址与官方文档不一致
- **官方文档**：`https://gw.superboss.cc/router`（2022年4月1日后申请的APP Key统一使用V2正式环境）
- **后端代码** `config.py:33`：`https://openapi.kuaimai.com/router`
- **前端代码** `AppConstants.kt:17`：`https://openapi.kuaimai.com/router`
- **风险**：如果快麦已切换到新域名，旧域名可能随时停用；当前如果API能正常调用则暂不修改，但应提供环境变量切换能力
- **修复**：将后端默认值改为官方文档地址 `https://gw.superboss.cc/router`，前端同理

#### BUG-2: 后端公共参数 `v` 应为 `version`，值应为 `1.0` 而非 `2.0`
- **官方文档**：参数名 `version`，可选值 `1.0`
- **后端代码** `kuaimai_api.py:41`：`"v": "2.0"`
- **前端代码** `KuaimaiInterceptor.kt:70`：`params["v"] = "2.0"`
- **风险**：参数名和值都与官方文档不一致，如果快麦服务端严格校验会导致API调用失败
- **修复**：后端改为 `"version": "1.0"`，前端改为 `params["version"] = "1.0"`

#### BUG-3: 后端公共参数 `app_key` 应为 `appKey`（驼峰命名）
- **官方文档**：参数名 `appKey`（驼峰）
- **后端代码** `kuaimai_api.py:36`：`"app_key": kuaimai_creds.app_key`（下划线）
- **前端代码** `KuaimaiInterceptor.kt:66`：`params["app_key"] = appKey`（下划线）
- **风险**：参数名与官方不一致，签名计算会不同，导致签名校验失败
- **修复**：后端改为 `"appKey": kuaimai_creds.app_key`，前端改为 `params["appKey"] = appKey`

#### BUG-4: 后端使用 `data=params`（form-urlencoded）但官方要求 `multipart/form-data` 或 JSON
- **官方文档刷新接口**：`Content-Type: multipart/form-data`
- **官方文档通用**：公共参数+业务参数一起提交
- **后端代码** `kuaimai_api.py:65`：`response = await client.post(KUAIMAI_API_BASE, data=params)` — httpx的`data=`发送form-urlencoded
- **前端代码** `KuaimaiInterceptor.kt:79`：发送JSON body
- **风险**：后端和前端发送格式不一致，且可能都不符合快麦要求
- **修复**：统一使用form-urlencoded格式（与httpx `data=` 一致），前端KuaimaiInterceptor也应改为form-urlencoded

### P1 - 中等缺陷（功能不完整或有隐患）

#### BUG-5: 前端KuaimaiInterceptor读取access_token的key不一致
- **KuaimaiInterceptor.kt:31**：`private const val KEY_ACCESS_TOKEN = "access_token"`
- **KuaimaiInterceptor.kt:45**：`val accessToken = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""`
- **PrefsKeys.kt:19**：`const val KEY_SESSION = "session"`
- **问题**：Interceptor用`access_token`作为key读取，但PrefsKeys定义的是`session`，两者不一致。如果其他地方用`PrefsKeys.KEY_SESSION`存储，Interceptor就读不到
- **修复**：KuaimaiInterceptor应使用`PrefsKeys.KEY_SESSION`而非硬编码的`access_token`

#### BUG-6: 前端TokenAuthenticator的refreshSession逻辑有问题
- **NetworkModule.kt:287**：`apiService.refreshSession(emptyMap())` — 传空Map
- **问题**：KuaimaiApiService.refreshSession需要传refreshToken参数，但这里传了emptyMap。且这个refreshSession是直接调快麦API，但App端没有存储refreshToken
- **修复**：App端不直接调快麦API刷新，应通过后端中转（SystemApiService.refreshSession）。TokenAuthenticator应改为调后端接口

#### BUG-7: 前端没有存储refreshToken的PrefsKey
- **PrefsKeys.kt**：没有 `KEY_REFRESH_TOKEN` 常量
- **问题**：App端虽然通过后端中转刷新session，但如果App端直接调快麦API（如KuaimaiApiService），则缺少refreshToken
- **修复**：既然App端通过后端中转，TokenAuthenticator应改为调后端接口，不需要在App端存储refreshToken

#### BUG-8: 后端refresh_session响应解析可能错误
- **kuaimai_api.py:70**：`error_response = result.get(f"{method.replace('.', '_')}_response", {})`
- **问题**：对于`open.token.refresh`方法，响应key应该是`open_token_refresh_response`，但代码先取了这个key，然后又检查`error_response`。如果API返回成功，`result`的key就是`open_token_refresh_response`，但代码在L70把它赋给了`error_response`变量名（虽然只是变量名不影响逻辑）
- **实际风险**：如果刷新接口返回的JSON结构是 `{"open_token_refresh_response": {"session": {...}}}`，则`result.get("open_token_refresh_response")`能正确获取。但如果是 `{"session": {...}}`，则获取不到
- **修复**：refresh_session应直接解析result，不通过_call_api的通用解析逻辑

#### BUG-9: 后端_call_api发送请求格式问题
- **kuaimai_api.py:65**：`response = await client.post(KUAIMAI_API_BASE, data=params)`
- **问题**：httpx的`data=`会将参数编码为form-urlencoded，但params中包含`method`、`appKey`等公共参数和业务参数混在一起。快麦API的公共参数和业务参数是否应该分开？
- **根据官方文档**：所有参数（公共+业务）一起提交，form-urlencoded格式
- **结论**：当前实现基本正确，但需确认参数名是否正确（见BUG-2/3）

### P2 - 轻微缺陷（代码质量/一致性）

#### BUG-10: 前端KuaimaiInterceptor缺少`method`参数
- **后端** `_build_common_params`：包含`"method": method`
- **前端** `KuaimaiInterceptor.kt`：没有添加`method`参数
- **问题**：前端拦截器只添加了`app_key`、`timestamp`、`session`、`format`、`v`、`sign_method`，缺少`method`
- **影响**：前端KuaimaiApiService的每个接口已经通过`@POST("router")`定义，但method参数应该在请求体中而非URL路径中。当前KuaimaiApiService的调用方需要自行在params中传入method，拦截器不负责添加
- **结论**：这不是bug，因为KuaimaiApiService的调用方会在params中传入method。但与后端不一致（后端在_build_common_params中统一添加）

#### BUG-11: 前后端SESSION_WARNING_DAYS定义重复
- **后端** `config.py:39`：`SESSION_WARNING_DAYS: int = int(os.getenv("SESSION_WARNING_DAYS", "5"))`
- **前端** `AppConstants.kt:20`：`const val SESSION_WARNING_DAYS = 5`
- **问题**：前端定义了但未使用（session状态由后端API返回），属于冗余代码
- **修复**：可保留作为参考，不影响功能

#### BUG-12: 后端save_kuaimai_config未保存session字段
- **config.py:127-129**：只保存`updated_at`和`refresh_token`
- **问题**：refresh_session成功后如果返回了新的accessToken（防御性处理L151），新的session值没有写回文件
- **修复**：save_kuaimai_config应同时保存session字段

---

## 修复计划

### 1. 后端kuaimai_api.py - 修正公共参数名称和版本号
- `"app_key"` → `"appKey"`
- `"v": "2.0"` → `"version": "1.0"`

### 2. 后端config.py - 修正默认API地址
- `KUAIMAI_API_BASE` 默认值改为 `"https://gw.superboss.cc/router"`

### 3. 后端config.py - save_kuaimai_config增加session字段保存
- 增加保存 `session` 字段

### 4. 后端kuaimai_api.py - refresh_session不通过_call_api通用逻辑
- 直接调用快麦API并解析响应，避免_call_api的响应key解析问题

### 5. 前端KuaimaiInterceptor.kt - 修正参数名和版本号
- `params["app_key"]` → `params["appKey"]`
- `params["v"] = "2.0"` → `params["version"] = "1.0"`
- `KEY_ACCESS_TOKEN = "access_token"` → 使用 `PrefsKeys.KEY_SESSION`

### 6. 前端AppConstants.kt - 修正API地址
- `KUAIMAI_API_URL` 改为 `"https://gw.superboss.cc/router"`

### 7. 前端NetworkModule.kt - TokenAuthenticator改为调后端接口
- 不再直接调快麦API刷新session
- 改为通过SystemApiService调后端 `/api/kuaimai/refresh-session`

### 8. 前端KuaimaiApiService.kt - 移除refreshSession接口
- App端不应直接调快麦API刷新session，统一通过后端中转

## 不修改的内容
- 前端KuaimaiApiService的其他6个API接口保留（App端可能仍需直接调快麦API查询商品等）
- 签名算法MD5保持不变（与sign_method=md5一致）
- SESSION_WARNING_DAYS前端定义保留

## 验证步骤
1. 后端启动后确认API地址和参数名正确
2. 前端构建通过（lint + assembleDebug）
3. 检查前后端参数名一致性
