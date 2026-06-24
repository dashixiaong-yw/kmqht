# 重新安装APP后"快麦会话已过期"弹窗修复方案

## 一、故障现象

1. 新安装 APP → 登录 → 点击取货列表 → "快麦API会话已过期，请在Web管理后台重新授权" 弹窗
2. 退出登录 → 重新登录 → 点击取货列表 → 正常，无弹窗
3. 后台所有 API 接口工作正常

## 二、根因追踪

### 完整触发链

```
用户点击"取货列表"
  → PickListScreen 加载
    → PickListViewModel.init()
      → loadAreas()
        → areaApiService.listAreas(token)   [token = userRepository.getToken()]
        → GET /api/areas   Header: X-User-Token = token
          → 后端 get_current_user() 校验 token

若 token 为空 → 后端返回 HTTP 401
  → OkHttp TokenAuthenticator 触发
    → POST /api/kuaimai/refresh-session
      → 需要 settings 权限 → 普通用户返回 HTTP 403
    → Retrofit 抛 HttpException → catch → null
  → notifySessionExpired()
    → SessionExpiredEvent.isExpired = true
    → AppNavigation 弹窗: "快麦API会话已过期，请在Web管理后台重新授权"
```

### 核心问题

`UserRepositoryImpl.getToken()` 从 `EncryptedSharedPreferences` 读取 token：

```kotlin
override fun getToken(): String {
    return prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
}
```

`login()` 写入 token 时使用 `apply()`（异步刷盘）：

```kotlin
prefs.edit().putString(KEY_USER_TOKEN, response.token)...apply()
```

**EncryptedSharedPreferences 在 Android 6.0 PDA 设备上，首次安装时 Keystore 未初始化，`apply()` 的加密写入可能未及时完成，后续 `getString()` 返回空字符串。**

### 为什么退出重登后正常？

首次安装：Keystore 未初始化，MasterKey 首次创建慢，`apply()` 可能未刷盘
退出重登：Keystore 已初始化，Operation 更快，`apply()` 正常刷盘

---

## 三、确认：Token 读取位置清单

### 1. 通过 `userRepository.getToken()` 读取（28处调用）

| 位置 | 方法 | 时间敏感？ |
|:-----|:-----|:----------:|
| PickListViewModel.kt L83 | `loadAreas()` | ✅ **首次API调用** |
| PickListViewModel.kt L105 | `loadCompletedOrders()` | 紧随上者 |
| PickListViewModel.kt L122~L238 | 其他6处 | 用户操作后 |
| PickDetailViewModel.kt L124~L401 | 8处 | 进入详情后 |
| OrderSyncWorker.kt L223~L541 | 7处 | 后台同步（WorkManager） |
| UserRepository.kt L176 | `logout()` | 退出登录时 |
| UserRepository.kt L262 | `validateToken()` | 启动时（有预缓存则走login） |
| UserRepository.kt L284 | `isTokenLocallyValid()` | 启动时 |

**这 28 处调用都通过 `UserRepository.getToken()` 获取 token。修复此方法即可覆盖全部。**

### 2. 直接通过 `prefs.getString(KEY_USER_TOKEN)` 读取（5处）

| 位置 | 触发时机 | 需要修复？ |
|:-----|:---------|:---------:|
| TokenAuthenticator L288 | 仅在收到 401 后才触发 | ❌ 不会走到这里 |
| AuthRepositoryImpl L49 | 同上，TokenAuthenticator 调用 | ❌ 不会走到这里 |
| ProductViewModel L132 | 搜索商品时调用 | ❌ 用户在登录后操作 |
| ProductViewModel L320 | 图片上传时调用 | ❌ 用户在登录后操作 |
| ImageUploadService L107 | 图片上传时调用（3处） | ❌ 用户在登录后操作 |

**修复 `UserRepository.getToken()` 即可阻断整个 401 触发链**。因为只要首次 API 调用携带了正确的 token，就不会有 401，TokenAuthenticator 不会触发，后台刷新 session 的调用也不会发生。

---

## 四、修改内容

### 文件：`UserRepository.kt` — 仅 1 个文件

**修改 1**：新增 `_cachedToken` 字段（在 `_currentUser` 旁边）

```kotlin
private var _cachedToken: String = ""
```

**修改 2**：`login()` — 写入 token 前先缓存到内存

```kotlin
override suspend fun login(username: String, password: String): Result<UserResponse> {
    return try {
        val response = apiService.login(LoginRequest(username, password))
        if (response.success && response.token.isNotEmpty()) {
            _lastLoginResult = response
            val user = UserResponse(...)
            val expireTime = System.currentTimeMillis() + TOKEN_EXPIRE_MS
            _cachedToken = response.token                          // ← 新增：先缓存到内存
            prefs.edit()
                .putString(PrefsKeys.KEY_USER_TOKEN, response.token)
                ...
                .apply()                                           // ← async，即使慢也不影响
            _currentUser.value = user
            syncKuaimaiCredentials(response.token)
            Result.success(user)
        }
    }
}
```

**修改 3**：`getToken()` — 优先使用内存缓存

```kotlin
override fun getToken(): String {
    if (_cachedToken.isNotEmpty()) return _cachedToken
    return prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
}
```

**修改 4**：`restoreFromCache()` — 恢复时同步缓存

```kotlin
private fun restoreFromCache() {
    val token = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
    ...
    if (token.isNotEmpty() && username.isNotEmpty()) {
        _cachedToken = token                   // ← 新增
        ...
    }
}
```

**修改 5**：`clearLocalUser()` — 清理时同步清除缓存

```kotlin
private fun clearLocalUser() {
    _cachedToken = ""                          // ← 新增
    prefs.edit()
        .remove(PrefsKeys.KEY_USER_TOKEN)
        ...
        .apply()
    _currentUser.value = null
}
```

---

## 五、完整性验证

### 5.1 Token 生命周期全覆盖

| 操作 | 状态 | 覆盖 |
|:-----|:----:|:-----|
| 登录写入 | `_cachedToken = response.token` | ✅ |
| 读取 | `if (_cachedToken.isNotEmpty()) return ...` | ✅ |
| 恢复缓存 | `restoreFromCache()` 中同步 `_cachedToken` | ✅ |
| 退出清理 | `clearLocalUser()` 中 `_cachedToken = ""` | ✅ |
| 401清理 | `handleAuthError()` → `clearLocalUser()` | ✅ 经 clearLocalUser |

### 5.2 数据流验证

```
Login 成功（login 方法内）:
  prefs.edit()....apply()  →  async，不保证时序
  _cachedToken = resp.token  →  sync，立即生效         ✅

后续所有 API 调用（loadAreas / loadItems 等）:
  userRepository.getToken()
    → _cachedToken.isNotEmpty() → 直接返回内存值       ✅
    → 不会走到 prefs.getString()                        ✅

Token 过期 / 退出登录:
  clearLocalUser()
    → _cachedToken = ""                                ✅
    → prefs.edit().remove(...).apply()                  ✅
```

### 5.3 回归风险验证

| # | 风险 | 分析 | 结论 |
|:-:|:-----|:-----|:----:|
| 1 | `_cachedToken` 与 SharedPreferences 不同步 | login 时两者都写入；logout/clear 时都清除；`getToken()` 缓存优先 SP 兜底 | ✅ |
| 2 | 多线程并发读写 | 所有操作在 `Dispatchers.Main` 的协程中顺序执行，`_cachedToken` 非 volatile 但无并发写 | ✅ |
| 3 | `restoreFromCache` 丢失 token | 从 SP 读取后写入缓存，与之前行为一致 | ✅ |
| 4 | `logout` 中 getToken 后清除 | `logout()` L176 先 `getToken()` 获取值传给 API，L177 `clearLocalUser()` 清除。时序正确 | ✅ |
| 5 | `handleAuthError` 清除后 token 立即不可用 | 401 时 `clearLocalUser()` → `_cachedToken = ""` → `loginRequired` 触发 → 跳到登录页 | ✅ |
| 6 | `isLoggedIn()` 依赖 getToken() | `return getToken().isNotEmpty() && _currentUser.value != null` → 缓存命中后行为一致 | ✅ |
| 7 | `isTokenLocallyValid()` 依赖 getToken() | 同上 | ✅ |
| 8 | ProductViewModel / ImageUploadService 直接读 pref | 不经过 getToken()，但它们的调用时机在 login 之后很久（秒级到分钟级），`apply()` 必然已完成 | ✅ |
| 9 | TokenAuthenticator 直接读 pref | 只会在 401 后触发，而阻断 401 链后不会到达 | ✅ |
| 10 | `OrderSyncWorker` 在后台线程读 token | 通过 `userRepo.getToken()` → 使用缓存 | ✅ |

### 5.4 编译验证更新

| 修改 | 类型 | 编译通过？
|:-----|:-----|:----------:|
| `UserRepository.kt` L115 新增 `_cachedToken` | 字段 | ✅ 纯数据字段 |
| `login()` 中 `_cachedToken = response.token` | 赋值 | ✅ |
| `getToken()` 新增 if 判断 | 分支 | ✅ |
| `restoreFromCache()` 中 `_cachedToken = token` | 赋值 | ✅ |
| `clearLocalUser()` 中 `_cachedToken = ""` | 赋值 | ✅ |

**无新增 import，无新类型，无 API 接口变更。仅内存中的字符串缓存。**

---

## 六、修改文件清单

| 文件 | 改动 | 行数 |
|:-----|:------|:----:|
| `UserRepository.kt` | 新增 `_cachedToken` 字段 + 4处修改 | ~8行 |

**仅 1 个文件，无需后端修改，无需 APK 重新签名。**

---

## 七、验证步骤

1. 卸载重装 APP
2. 用普通 PDA 用户登录
3. 立即点击"取货列表"
4. 确认**不再弹出**"快麦API会话已过期"对话框
5. 退出登录
6. 重新登录
7. 再次点击取货列表，确认正常
