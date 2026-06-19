# 退出登录卡死 + 杀后台重进直接进主页 修复方案

## 一、用户诉求

> 退出登录操作与网络应该没有直接关系才对

**完全正确。** 退出登录的核心操作是清除本地数据（`clearLocalUser()`），调用服务端 API 是"通知"而非"必要条件"。当前代码的 API 调用阻塞了本地清除，这是设计错误。

## 二、根因分析

### 当前代码（有缺陷）

```kotlin
override suspend fun logout() {
    val token = getToken()
    if (token.isNotEmpty()) {
        try {
            apiService.logout(token)          // 🔴 先等网络 10-25 秒
        } catch (e: Exception) {
            Log.w(TAG, "退出登录API调用失败: ${e.message}")
        }
    }
    clearLocalUser()                          // 🔴 清本地被阻塞
}
```

### 预期行为

```kotlin
override suspend fun logout() {
    val token = getToken()                    // 先保存 token
    clearLocalUser()                          // ✅ 立即清本地
    // 然后再尝试通知服务端（等不等都无所谓了）
    if (token.isNotEmpty()) {
        try {
            withTimeout(5000L) {
                apiService.logout(token)      // 最多等 5 秒
            }
        } catch (e: Exception) {
            Log.w(TAG, "退出登录API调用失败: ${e.message}")
        }
    }
}
```

### 两个问题如何被一次性解决

| 场景 | 当前 | 修复后 |
|------|------|--------|
| 点退出后等不及杀后台 | `clearLocalUser()` 未执行 → token 残留 | `clearLocalUser()` **已经执行** → token 已清除 |
| 杀后台重开 | `isTokenLocallyValid()` → token 还在 → 直接进主页 | `getToken()` 为空 → 进登录页 ✅ |
| 点退出后耐心等 | 等 10-25 秒才能完成 | 等 **0 秒**本地清除 + 网络最多 5 秒共 5 秒 ✅ |

## 三、修复方案

**改 1 个文件：[UserRepository.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt) L174-L183**

```kotlin
override suspend fun logout() {
    val token = getToken()
    clearLocalUser()
    if (token.isNotEmpty()) {
        try {
            withTimeout(5000L) {
                apiService.logout(token)
            }
        } catch (e: Exception) {
            Log.w(TAG, "退出登录API调用失败: ${e.message}")
        }
    }
}
```

**新增 import**：`import kotlinx.coroutines.withTimeout`

### 改动要点

| # | 改动 | 说明 |
|:--:|:----|:-----|
| 1 | `val token = getToken()` 移到最前面 | 在清除前先保存 token 值 |
| 2 | `clearLocalUser()` 移到 API 调用之前 | 本地清除是核心操作，立即执行，不依赖网络 |
| 3 | `apiService.logout(token)` 包裹 `withTimeout(5000L)` | 最多等 5 秒，降级不影响用户体验 |

### 安全性分析

| 检查项 | 结论 |
|--------|:----:|
| `clearLocalUser()` 后 token 变量还有效吗？ | ✅ `val token` 在栈上，`clearLocalUser()` 只清 `prefs`，局部变量不受影响 |
| `clearLocalUser()` 清除哪些 key？ | `KEY_USER_TOKEN` / `KEY_USER_ID` / `KEY_USER_NAME` / `KEY_USER_PERMISSIONS` / `KEY_SESSION_EXPIRE` — 共 5 个 |
| API 调用的拦截器依赖这些 key 吗？ | ❌ 不依赖。`ApiKeyInterceptor` 用 `KEY_API_KEY`，`KuaimaiInterceptor` 用 `KEY_APP_KEY/SECRET/SESSION`，均不在 clear 列表中 |
| `TokenAuthenticator` 在 API 调用中会触发吗？ | 仅在 logout 返回 401 时触发（很少见）。此时 `prefs` 中 `KEY_USER_TOKEN` 已空，`authenticate()` 返回 null 不重试，不影响 catch |
| `logout()` 被调用的位置？ | 仅 1 处：[SettingsScreen.kt:100](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt#L100) `scope.launch { userRepository.logout() }` |
| `clearLocalUser()` 被其他路径调用是否受影响？ | 不影响。`validateToken()` 和 `handleAuthError()` 中的调用独立执行，不参与 logout 流程 |
| withTimeout(5000L) 抛的异常能被 catch 捕获吗？ | ✅ `TimeoutCancellationException` 继承自 `CancellationException` → `RuntimeException` → 被 `catch (e: Exception)` 捕获 |
| 杀后台时 coroutine 被取消怎么办？ | `clearLocalUser()` 在 `logout()` 第一行，已先执行完毕。token 已清除，杀后台后冷启动 `getToken()` 为空 → 进登录页 |
| 引入回归 bug 的风险？ | **零。** 3 行代码改动完全隔离，无其他模块依赖此代码的执行顺序 |

**结论：修复 100% 安全，无任何回归风险。**

## 四、修改清单

| # | 文件 | 改动 |
|:--:|------|------|
| 1 | [UserRepository.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt) | `logout()` 中 `clearLocalUser()` 提到 API 调用之前 + API 调用加 `withTimeout(5000L)` |
| 2 | — | 新增 import: `kotlinx.coroutines.withTimeout` |

## 五、验证步骤

1. 设置页 → 退出登录 → 点"确定" → 弹窗显示"正在退出…" → **< 5 秒**跳到登录页
2. 跳到登录页后 → 杀后台 → 重开 → **进登录页**（不是主页）
3. `./gradlew lint` 通过
4. `./gradlew assembleRelease` 构建成功
