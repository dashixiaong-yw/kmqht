# 快麦凭证同步修复计划

## 问题

PDA端 `KuaimaiInterceptor` 从 `SharedPreferences` 读取 `KEY_APP_KEY`、`KEY_APP_SECRET`、`KEY_SESSION` 用于对直接发往 `gw.superboss.cc/router` 的快麦API请求做签名。但这些值从未写入PDA本地存储，始终为空字符串。`AuthRepository` 中的 `setAppKey()` / `setAppSecret()` 方法无调用方。

影响：OrderSyncWorker 中的 `syncRemarkUpdate()` 和 `syncSupplierUpdate()` 直接调用快麦API时使用空凭证签名，请求失败。

## 修复

### 后端
1. `backend/app/models.py` - 新增 `KuaimaiCredentialsResponse` 模型
2. `backend/app/routers/system.py` - 新增 `GET /api/kuaimai/credentials` 端点，返回当前快麦凭证

### PDA端
3. `SystemApiService.kt` - 新增 `getKuaimaiCredentials()` 方法
4. 新增 `KuaimaiCredentialsResponse.kt` DTO
5. `UserRepositoryImpl.login()` - 登录成功后调用后端API获取凭证并写入encryptedPrefs
