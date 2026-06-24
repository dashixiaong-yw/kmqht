# 更新后"快麦会话已过期"弹窗修复方案（v2.3）

## 一、根因确认

### 正确的理解

| 事实 | 来源 |
|:-----|:------|
| 后端快麦 API session 有自动刷新机制（每24小时） | `main.py` 定时任务 |
| 后端从不因快麦session过期返回401 | `auth.py` 只在token验证失败时返回401 |
| 401的唯一来源是用户token无效/过期/为空 | `auth.py:get_current_user()` |
| "快麦会话过期"对话框的触发源是 `TokenAuthenticator` | 代码追踪确认 |

### 真实触发链

```
APP更新后:
  旧用户 token 因 Keystore 不稳定 / 加密数据不可读 → getString(KEY_USER_TOKEN) 返回 ""
  → restoreFromCache() → _cachedToken = ""（旧token不可用）
  → isLoggedIn() == false → startDestination = LOGIN

用户登录成功 → 正常使用

过了一段时间（或者 APP 进程被杀死重启）:
  再次 EncryptedSharedPreferences 读取 token → 可能又返回 ""
  → getToken() → _cachedToken 已空 → prefs.getString() → 返回 ""
  → API 调用 → 后端 401

TokenAuthenticator:
  → userToken = ""（空）
  → 仍然尝试 systemApiService.refreshSession("")
    → POST /api/kuaimai/refresh-session  Header: X-User-Token = ""
    → 后端 get_current_user 验证 → token为空 → HTTP 401
    → Retrofit 抛 HttpException
  → refreshResult == null → notifySessionExpired() → 弹窗 ❌
```

### 根本问题

**TokenAuthenticator 没有检测用户 token 为空的情况**。用户 token 为空时不应尝试刷新 session（因为 refreshSession 也依赖用户 token），也不应弹"会话过期"对话框——而应让 401 自然传播给 ViewModel 的 `handleAuthError()`，由它跳转到登录页。

---

## 二、修改方案

### 修改 1：`TokenAuthenticator` — 用户 token 为空时直接放行

**文件**：`NetworkModule.kt`

修改 `authenticate()` 方法中读取 token 后的逻辑：

```kotlin
val userToken = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""

// 用户 token 为空 → 直接放行 401，让 ViewModel 的 handleAuthError 处理跳转登录
if (userToken.isEmpty()) {
    Log.w(TAG, "用户 token 为空，放行401跳转登录（非快麦session过期）")
    return null
}
```

### 修改 2：`UserRepositoryImpl.clearLocalUser()` — 补充清理快麦凭证残留

**文件**：`UserRepository.kt`

即使快麦 session 不会过期，旧快麦凭证留在本地也可能与新的取货单数据不一致。补充清理：

```kotlin
private fun clearLocalUser() {
    _cachedToken = ""
    prefs.edit()
        .remove(PrefsKeys.KEY_USER_TOKEN)
        .remove(PrefsKeys.KEY_USER_ID)
        .remove(PrefsKeys.KEY_USER_NAME)
        .remove(PrefsKeys.KEY_USER_PERMISSIONS)
        .remove(PrefsKeys.KEY_SESSION_EXPIRE)
        .remove(PrefsKeys.KEY_APP_KEY)        // ← 新增
        .remove(PrefsKeys.KEY_APP_SECRET)      // ← 新增
        .remove(PrefsKeys.KEY_SESSION)         // ← 新增
        .apply()
    _currentUser.value = null
    Log.i(TAG, "本地用户数据已清除")
}
```

---

## 三、修改文件清单

| 文件 | 改动 | 行数 |
|:-----|:------|:----:|
| `NetworkModule.kt` (TokenAuthenticator) | 读取token后增加空值判断，为空时直接return null | ~5行 |
| `UserRepository.kt` | `clearLocalUser()` 补充清理3个快麦凭证key | ~3行 |

---

## 四、为什么这个方案有效

```
TokenAuthenticator 收到 401:
  → 读取 userToken
  → 如果 token 为空 → 直接返回 null
    → 401 不被拦截，正常传播给 OkHttp/Retrofit
    → Retrofit 抛 HttpException
    → ViewModel 的 catch 捕获 → handleAuthError(e)
      → clearLocalUser() → _cachedToken = ""
      → _loginRequired.emit(Unit)
    → AppNavigation 收到 loginRequired
    → 跳转登录页
  → 用户重新登录
    → _cachedToken = newToken（立即生效）
    → 所有 API 正常 ✅
    → TokenAuthenticator 不再触发 ✅
    → 无"快麦会话过期"弹窗 ✅
```

重新登录后用户 token 非空 → `getToken()` 返回有效值 → API 正常 → 无 401 → TokenAuthenticator 不触发 → **从根本上避免了这个弹窗的出现**。

---

## 五、回归风险

| # | 风险 | 说明 |
|:-:|:-----|:------|
| 1 | 空 token 时放行 401 是否会丢失原始请求 | 401 会到达 ViewModel，它做 `handleAuthError` → clearUser → 跳转登录，请求本身不需要重试 |
| 2 | `refreshSession()` 在 token 非空时仍正常工作 | `if (userToken.isEmpty())` 条件保护，非空时走原有逻辑 |
| 3 | 登录后还是出现 401 | 登录后 `_cachedToken` 有效 → 无 401 → 无影响 |
| 4 | 快麦凭证清理后登录是否会重新同步 | `login()` 中 `syncKuaimaiCredentials()` 重新获取 ✅ |
