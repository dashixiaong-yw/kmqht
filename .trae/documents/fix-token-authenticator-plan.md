# 修复：TokenAuthenticator 误刷新快麦 Session 导致弹窗

## 问题

每次用户系统 token 到期（7 天）后，TokenAuthenticator 捕获到 HTTP 401，错误地去调用 `POST /api/kuaimai/refresh-session`（刷新快麦 session），而非直接跳转登录页。刷新失败后弹出"快麦API会话已过期"的错误弹窗。

## 根因

**TokenAuthenticator 刷新了错误的对象：**

```
HTTP 401（用户 token 到期）
→ TokenAuthenticator 调 refreshSession()   ← 错！这是刷新快麦 session
→ 需要 settings 权限 + refresh_token → 失败
→ notifySessionExpired() → 弹窗"会话过期"    ← 用户看到的错误弹窗
```

**正确行为应该是：**
```
HTTP 401（用户 token 到期）
→ 直接 notifySessionExpired() → 跳转登录页 → 重新登录
                                  ↑ 用户只需要重新输入密码
```

## 后端状态确认

后端 `main.py` 已配置自动刷新机制：
- `_refresh_kuaimai_session` 每 24 小时执行一次
- `open.token.refresh` 成功后 session 值不变、仅延长 30 天有效期
- **不受本次改动影响，保持不变**

## 改动

### 文件：NetworkModule.kt（第 266-316 行）

`TokenAuthenticator.authenticate()` 简化：遇到 401 直接通知过期，不再尝试刷新 Kuaimai session。

```kotlin
// 改前（约50行）
override fun authenticate(route, response): Request? {
    if (response.code != 401) return null
    if (retryCount >= 2) { notifySessionExpired(); return null }
    if (!isRefreshing.compareAndSet(false, true)) return null
    try {
        val result = runBlocking { systemApiService.refreshSession(userToken) }
        if (result?.success == true) return retryRequest
        else { notifySessionExpired(); return null }
    } finally { isRefreshing.set(false) }
}

// 改后（约10行）
override fun authenticate(route, response): Request? {
    if (response.code != 401) return null
    val retryCount = response.request.header("X-Retry-Count")?.toIntOrNull() ?: 0
    if (retryCount >= 2) return null
    notifySessionExpired()
    return null
}
```

**关键变化**：
- 移除 `systemApiService.refreshSession()` 调用
- 移除 `isRefreshing` AtomicBoolean（不再需要）
- 移除 `systemApiServiceProvider` 的注入需求
- 移除 `runBlocking`（不再有协程调用）
- 401 后直接通知过期 → AppNavigation 跳转登录页

### 其他文件：不变

| 文件 | 状态 | 说明 |
|:-----|:----:|:-----|
| 后端 main.py | ✅ 不变 | 快麦 session 仍每 24h 自动刷新 |
| HomeScreen | ✅ 不变 | session 预警仍正常显示 |
| SettingsScreen | ✅ 不变 | 不变 |
| AuthRepository | ✅ 不变 | `/api/kuaimai/refresh-session` 接口保留（管理后台手动刷新用） |
| UserRepository | ✅ 不变 | 不变 |

## 影响

- 用户 token 到期后（每 7 天）→ 直接跳到登录页 → 重新输入密码
- 后端 24h 自动刷新快麦 session，不受影响
- 管理后台手动"刷新 session"功能不受影响

## 验证

1. 后端停用后重启 → App 请求 401 → 跳转登录页（无错误弹窗）
2. 登录后正常工作
3. lint 通过
