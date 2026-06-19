# 冷启动卡死 + 退出登录无反应 & 全功能修复

## 一、用户确认的前提

1. **网络正常**（第一次可以登录成功）
2. **冷启动卡死首屏** — 清除后台重开 → "正在验证登录状态…"一直不消失
3. **退出登录无反应** — 点击"确定"后弹窗消失，什么都没发生
4. **预期行为** — 退出后解除登录状态，退回到登录页面

## 二、共同根因分析

两个问题的**共同根因**都是同一个设计缺陷：

**App 核心操作（启动鉴权 / 退出登出）强制依赖服务端网络请求完成，没有本地降级路径。**

### 冷启动卡死：`validateToken()` 必须在 LaunchedEffect 内完成

```kotlin
// AppNavigation.kt L86-L99
LaunchedEffect(Unit) {
    if (userRepository.isLoggedIn()) {
        val valid = userRepository.validateToken()  // ← 网络调用，阻塞 LaunchedEffect
        ...
    }
    isCheckingAuth = false  // ← 这一行永远等不到
}
```

`validateToken()` → `apiService.getCurrentUser(token)` → Retrofit 网络请求。即使网络"正常"，这个调用也可能：
- 服务端正忙于处理登录后的 `syncKuaimaiCredentials` → 响应延迟
- 服务端返回非预期响应 → Gson 反序列化异常（虽然被 catch，但仍需等服务端返回）
- OkHttp 连接池耗尽、SSL 握手慢等

**核心问题**：启动鉴权不应依赖网络。登录时已保存 `KEY_SESSION_EXPIRE = 当前时间 + 7天`，冷启动只需读本地值即可判断 token 是否过期。

### 退出登录无反应：`logout()` 在协程中调用网络API

```kotlin
// SettingsScreen.kt
scope.launch {
    userRepository.logout()  // → apiService.logout(token) → 网络请求
    onLogout()               // ← 等网络请求完成才能到这一行
}
```

弹窗已关，网络请求在后台静默运行，用户看不到任何反馈 → "没有反应"。

## 三、修复方案

### 3.1 冷启动卡死：网络验证 → 本地时间戳验证

**UserRepository.kt — 新增 1 个方法**（仅读本地加密存储，0 网络依赖）：

```kotlin
/** 检查本地token是否在有效期内（不调网络） */
fun isTokenLocallyValid(): Boolean {
    if (getToken().isEmpty() || _currentUser.value == null) return false
    val expireTime = prefs.getLong(PrefsKeys.KEY_SESSION_EXPIRE, 0L)
    return expireTime > System.currentTimeMillis()
}
```

**AppNavigation.kt — 启动逻辑改写**（删掉 `validateToken()` 调用）：

```kotlin
// 旧
LaunchedEffect(Unit) {
    if (userRepository.isLoggedIn()) {
        val valid = userRepository.validateToken()  // 网络调用！
        ...
    }
    ...
}

// 新
LaunchedEffect(Unit) {
    if (userRepository.isLoggedIn() && userRepository.isTokenLocallyValid()) {
        val guideShown = prefs.getBoolean(KEY_GUIDE_SHOWN, false)
        startDestination = if (guideShown) Routes.HOME else Routes.GUIDE
    } else {
        startDestination = Routes.LOGIN
    }
    isCheckingAuth = false
}
```

**安全兜底**：如果 token 被服务端撤销但本地未过期 → 用户会短暂看到 HOME → 首次 API 调用返回 401 → `handleAuthError` 触发 → `loginRequired` 事件 → 自动跳 LOGIN。这是标准乐观鉴权。

### 3.2 退出登录：弹窗不关 + 显示 loading

```kotlin
var isLoggingOut by remember { mutableStateOf(false) }

AlertDialog(
    onDismissRequest = { if (!isLoggingOut) showLogoutDialog = false },
    title = { Text("退出登录") },
    text = { Text(if (isLoggingOut) "正在退出..." else "确定要退出当前账号吗？") },
    confirmButton = {
        TextButton(
            onClick = {
                if (isLoggingOut) return@TextButton
                isLoggingOut = true
                scope.launch {
                    userRepository.logout()
                    isLoggingOut = false
                    showLogoutDialog = false
                    onLogout()
                }
            },
            enabled = !isLoggingOut
        ) {
            Text(if (isLoggingOut) "退出中..." else "确定", ...)
        }
    },
    dismissButton = {
        if (!isLoggingOut) {
            TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
        }
    }
)
```

### 3.3 popUpTo(0) 修正（AppNavigation.kt ×2处）

```kotlin
// 旧：popUpTo(0) — 0 不是有效 backstack entry ID
navController.navigate(Routes.LOGIN) {
    popUpTo(0) { inclusive = true }
}

// 新：使用 NavGraph 的 startDestinationId
navController.navigate(Routes.LOGIN) {
    popUpTo(navController.graph.startDestinationId) { inclusive = true }
}
```

L106（loginRequired 监听）和 L213（退出登录）都需要改。

### 3.4 LoginScreen 强制改密弹窗拦截返回键

```kotlin
import androidx.activity.compose.BackHandler

if (showChangePasswordDialog) {
    BackHandler(enabled = true) { /* 吞掉返回键 */ }
    AlertDialog(...)
}
```

### 3.5 HomeScreen 会话预警动态刷新

```kotlin
LaunchedEffect(Unit) {
    while (true) {
        authRepository?.let {
            val expireTime = it.getSessionExpireTime()
            if (expireTime > 0L) {
                val now = System.currentTimeMillis()
                // ... 计算 showSessionWarning / sessionWarningText
            }
        }
        delay(60 * 60 * 1000L)
    }
}
```

## 四、修改文件清单

| # | 文件 | 改动 | 理由 |
|:--:|------|------|------|
| 1 | [UserRepository.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt) | 新增 `isTokenLocallyValid()` | 启动鉴权不依赖网络 |
| 2 | [AppNavigation.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt) | validateToken→isTokenLocallyValid；popUpTo(0)→startDestinationId | 冷启动秒进 |
| 3 | [SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt) | isLoggingOut + 弹窗不关 | 退出反馈可见 |
| 4 | [LoginScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt) | BackHandler | 改密不能跳过 |
| 5 | [HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) | while+delay 动态刷新 | 预警实时性 |

## 五、验证步骤

1. 登录成功 → 清除后台 → 冷启动 → **秒进 HOME**（无等待）
2. 清除后台 → 冷启动（网络不通时）→ 同样秒进 HOME
3. 设置页点"退出登录" → 弹窗显示"正在退出…" → 确认 → 跳转到登录页
4. 登录页强制改密弹窗 → 按系统返回键 → 不消失
5. 登录页有历史 → 下拉可选择
6. `./gradlew lint` 通过
7. `./gradlew assembleRelease` 构建成功
