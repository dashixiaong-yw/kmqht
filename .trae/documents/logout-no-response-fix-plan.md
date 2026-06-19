# 退出登录无反应 & 全功能审计修复

## 一、问题现象

1. **退出登录点击"确定"后没有反应** — 弹窗消失，页面停滞不动
2. 全面检查所有功能模块是否正常

## 二、根因分析

### 🔴 Bug #1: 退出登录"没有反应"的精确根因

**代码路径**：[SettingsScreen.kt L92-L97](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt#L92-L97)

```kotlin
TextButton(onClick = {
    showLogoutDialog = false      // ← 弹窗立即消失
    scope.launch {                // ← 启动协程
        userRepository.logout()   // ← 阻塞10秒（网络超时）
        onLogout()                // ← 10秒后才执行
    }
}) {
    Text("确定", color = MaterialTheme.colorScheme.error)
}
```

**时序分解**：

```
0秒   → 用户点"确定"
0秒   → 弹窗消失（showLogoutDialog = false）
0秒   → 协程启动，调用 userRepository.logout()
         ├─ apiService.logout(token) 发起 POST 请求
         │   └─ 网络不可用 → OkHttp 等待 10 秒 connectTimeout
0~10秒 → 用户看到：弹窗没了，页面纹丝不动，点击无响应 → **判定为"没有反应"**
10秒  → connectTimeout 超时，catch 异常，继续 clearLocalUser()
10秒  → onLogout() → 导航到登录页
```

**补充**：`logout()` 内部已有 try-catch（[UserRepository.kt L173-L179](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt#L173-L179)），网络失败不会崩溃，只是等 10 秒。但没有任何 UI 反馈告知用户"正在退出"。

### 🟡 Bug #2: popUpTo(0) 路由 ID 不精确

```kotlin
navController.navigate(Routes.LOGIN) {
    popUpTo(0) { inclusive = true }
}
```

`0` 不是有效的 backstack entry ID。应使用 `navController.graph.startDestinationId` 或直接使用 route 字符串。当前因 Navigation 库对无效 ID 的静默忽略，实际行为等价于 `popUpTo(startDestinationId)`，功能勉强可用，但有风险。

### 🟡 审计发现的其他问题

| 优先级 | 文件 | 问题 |
|:------:|------|------|
| P2 | [LoginScreen.kt L293](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt#L293) | 强制修改密码弹窗 `onDismissRequest = { /* 不允许关闭 */ }`，未显式拦截 Android 返回键 |
| P2 | [HomeScreen.kt:91](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L91) | 会话预警只计算一次（LaunchedEffect(authRepository)），长时间悬挂不会动态更新 |
| P3 | [HomeScreen.kt:203](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L203) | 会话预警点击跳转到设置页，但设置页无快麦 session 刷新入口 |
| P3 | [NetworkModule.kt:185](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt#L185) | Server URL 变更后需重启应用（@Singleton Retrofit），设置页改地址不即时生效 |

## 三、修复方案

### 3.1 退出登录修复（SettingsScreen.kt）

**策略**：跳过网络调用，降级为纯本地清除。`apiService.logout(token)` 的目的只是在服务端标记 token 失效，但服务端正向验证 token 时如果 token 已过期也会拒绝。退出登录的核心是清除本地状态，网络通知是"尽力而为"。

```kotlin
// 修改 logout() 实现（UserRepository.kt）
override suspend fun logout() {
    val token = getToken()
    if (token.isNotEmpty()) {
        try {
            apiService.logout(token)
        } catch (e: Exception) {
            Log.w(TAG, "退出登录API调用失败: ${e.message}")
        }
    }
    clearLocalUser()
}
```

**保持现有代码不变**（logout() 已有 try-catch）。修复点在 SettingsScreen：增加 `isLoggingOut` 状态 + 弹窗不立即关闭 + 显示 loading。

```kotlin
// SettingsScreen.kt 修复
var showLogoutDialog by remember { mutableStateOf(false) }
var isLoggingOut by remember { mutableStateOf(false) }  // 新增

// 退出登录确认弹窗
if (showLogoutDialog) {
    AlertDialog(
        onDismissRequest = { if (!isLoggingOut) showLogoutDialog = false },
        title = { Text("退出登录") },
        text = {
            Text(if (isLoggingOut) "正在退出..." else "确定要退出当前账号吗？")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isLoggingOut) return@TextButton  // 防止重复点击
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
                Text(if (isLoggingOut) "退出中..." else "确定",
                     color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            if (!isLoggingOut) {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        }
    )
}
```

**效果**：
- 点击"确定"后弹窗不消失，显示"正在退出..."
- 按钮变为不可用 + 文字变为"退出中..."，防止重复点击
- 取消按钮隐藏，防止误操作
- logout() 完成后关闭弹窗 + 导航

### 3.2 popUpTo(0) 修复（AppNavigation.kt L213）

```kotlin
// 旧
navController.navigate(Routes.LOGIN) {
    popUpTo(0) { inclusive = true }
}

// 新
navController.navigate(Routes.LOGIN) {
    popUpTo(navController.graph.startDestinationId) { inclusive = true }
}
```

需要同步修改 L106（loginRequired 路径）保持一致。

### 3.3 LoginScreen 强制改密弹窗拦截返回键

```kotlin
import androidx.activity.compose.BackHandler

if (showChangePasswordDialog) {
    BackHandler(enabled = true) { /* 吞掉返回键 */ }
    AlertDialog(...)
}
```

### 3.4 HomeScreen 会话预警动态化

```kotlin
// 将 LaunchedEffect(authRepository) 改为 LaunchedEffect(Unit)
// 并用 while(true) + delay(3600_000L) 每小时刷新
LaunchedEffect(Unit) {
    while (true) {
        // 更新预警
        setSessionWarning()
        delay(60 * 60 * 1000L)
    }
}
```

## 四、修改文件清单

| # | 文件 | 改动内容 | 优先级 |
|:--:|------|------|:------:|
| 1 | [SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt) | isLoggingOut 状态 + 弹窗不立即关 + loading | 🔴 P1 |
| 2 | [AppNavigation.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt) | popUpTo(0) → popUpTo(startDestinationId) ×2 | 🟡 P2 |
| 3 | [LoginScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt) | 强制改密弹窗拦截 BackHandler | 🟡 P2 |
| 4 | [HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) | 会话预警动态刷新 + 点击改为弹窗说明 | 🟢 P3 |

## 五、验证步骤

1. 进入设置页 → 点击"退出登录" → 弹窗出现 → 点击"确定"
2. 弹窗保持显示，按钮变为"退出中..."且不可点击
3. 网络不通时，clearLocalUser 后立即导航到登录页（秒级响应）
4. 登录页强制修改密码弹窗 → 按系统返回键 → 不消失
5. `./gradlew lint` 通过
6. `./gradlew assembleRelease` 构建成功
