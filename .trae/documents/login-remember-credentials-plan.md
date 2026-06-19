# 登录页记住密码 + 登录历史方案

## 一、需求概述

登录页增加两个功能（**全部本地加密存储，不上传服务器**）：

1. **记住密码**：用户可选择复选框，勾选后保存账号+密码到 PDA 本地加密存储，下次打开登录页自动填充
   - **关键规则**：一旦用户修改了用户名（键盘输入或从历史中选择不同账号），之前记住的密码立即失效清空
   - 只有用户**从未修改过用户名**的情况下，密码才保持自动填入
2. **登录历史**：即使不勾选记住密码，用户名输入框也要记录历史登录过的账号，下拉可选择（最近 10 条，按使用时间倒序）

## 二、当前状态分析

| 文件 | 现状 |
|------|------|
| [LoginScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt) | 简单用户名+密码输入框，无持久化，无历史记录 |
| [UserRepository.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt) | 登录成功时保存 token/userId/username/permissions 到 EncryptedSharedPreferences |
| [PrefsKeys.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/util/PrefsKeys.kt) | 定义了加密/普通 SharedPreferences 的所有 key |

**回归检查结论（涉及外部模块）：**

| 检查项 | 结果 |
|--------|------|
| `UserRepository` 其他实现类 | 只有 `UserRepositoryImpl`，接口新增方法无冲突 |
| `UserRepositoryImpl` 线程安全 | SharedPreferences 读写自带线程安全，新方法无需 suspend |
| `logout()` 是否误清"记住密码" | 不会，`clearLocalUser()` 只清 token/用户信息，独立于记住密码 |
| 导航回退状态重置 | LaunchedEffect(Unit) 在 Composable 重建时自动重载 |
| 其他模块（导航/首页/设置/扫码/WorkManager） | 未修改，零影响 |
| 并发修改 | 单用户 PDA，无并发场景 |

## 三、设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 数据流向 | **纯本地加密存储，不上传服务器** | 用户明确要求 |
| 存储位置 | EncryptedSharedPreferences（加密） | 密码为敏感数据 |
| 凭证管理归属 | UserRepository 接口新增方法 | 已有加密 prefs 引用，LoginScreen 无需额外参数 |
| 历史记录上限 | 最近 10 条 | 控制存储量 |
| 历史存储结构 | Gson 序列化 `List<String>` 为 JSON 字符串 | **关键修复**：`Set<String>` 不保留顺序，改用 JSON 保证有序 |
| 历史展示方式 | ExposedDropdownMenuBox（聚焦时弹出） | Material3 原生组件 |
| 记住密码默认值 | 关闭（false） | 共享 PDA 设备，默认不保存更安全 |
| 密码失效规则 | 用户名一旦被修改 → 密码立即清空 | 用户明确要求"更换用户名后记住密码失效" |

## 四、修改清单（仅 3 个文件）

### 4.1 PrefsKeys.kt — 新增 4 个 key 常量

```kotlin
// 登录页面相关（EncryptedSharedPreferences）
const val KEY_SAVE_PASSWORD = "save_password"       // 是否记住密码（boolean）
const val KEY_SAVED_USERNAME = "saved_username"     // 保存的用户名
const val KEY_SAVED_PASSWORD = "saved_password"     // 保存的密码
const val KEY_LOGIN_HISTORY = "login_history"       // 登录历史JSON字符串（Gson序列化 List<String>）
```

### 4.2 UserRepository.kt — 接口 + 实现新增 8 个方法

接口新增：

```kotlin
// 记住密码相关
fun isSavePasswordEnabled(): Boolean
fun setSavePasswordEnabled(enabled: Boolean)
fun getSavedUsername(): String
fun getSavedPassword(): String
fun saveCredentials(username: String, password: String)
fun clearSavedCredentials()

// 登录历史相关
fun getLoginHistory(): List<String>
fun saveToLoginHistory(username: String)
```

实现要点（全部操作 `@Named("encrypted") prefs`，纯本地加密存储）：

```kotlin
// saveToLoginHistory 实现
val json = prefs.getString(KEY_LOGIN_HISTORY, null)
val list = if (json != null) {
    gson.fromJson(json, Array<String>::class.java).toMutableList()
} else {
    mutableListOf()
}
list.remove(username)        // 去重
list.add(0, username)        // 新用户名列首
if (list.size > 10) list.removeAt(list.size - 1)  // 限10条
prefs.edit().putString(KEY_LOGIN_HISTORY, gson.toJson(list)).apply()
```

### 4.3 LoginScreen.kt — UI 修改

**参数不变**，通过 `userRepository` 调用新增方法。

**新增状态变量：**
```kotlin
var savePasswordChecked by remember { mutableStateOf(false) }
val loginHistory = remember { mutableStateListOf<String>() }
val savedUsername = remember { mutableStateOf("") }
```

**初始化（LaunchedEffect）：**
1. `savePasswordChecked = userRepository.isSavePasswordEnabled()`
2. `savedUsername = userRepository.getSavedUsername()`
3. 若记住密码开启 → `username = savedUsername`、`password = userRepository.getSavedPassword()`
4. `loginHistory.addAll(userRepository.getLoginHistory())`

**用户名 onValueChange（密码失效逻辑）：**
```kotlin
onValueChange = { newValue ->
    username = newValue
    errorMessage = ""
    if (newValue != savedUsername.value && password.isNotEmpty()) {
        password = ""
    }
}
```

**UI 布局（从上到下）：**
1. 用户名 ExposedDropdownMenuBox（历史下拉选择 / 手动输入）
2. 密码 OutlinedTextField（不变）
3. "记住密码" Row { Checkbox + Text }
4. 错误提示（不变）
5. 登录按钮（不变）

**登录成功后的保存逻辑：**
```kotlin
if (savePasswordChecked && password.isNotEmpty()) {
    userRepository.saveCredentials(username, password)
} else {
    userRepository.clearSavedCredentials()
}
userRepository.setSavePasswordEnabled(savePasswordChecked)
userRepository.saveToLoginHistory(username)
```

## 五、数据流

```
登录页打开 → LaunchedEffect 加载本地存储
  ├─ 记住密码开启 → username/savedUsername/password 全部预填
  └─ loginHistory 始终加载

用户编辑用户名
  └─ 发现 newValue != savedUsername → password = ""（不可恢复）

用户从历史选择
  ├─ 选的 == savedUsername → 保留密码
  └─ 选的 != savedUsername → 密码清空

登录成功
  ├─ 记住密码 && 密码非空 → saveCredentials(u, p)
  ├─ 否则 → clearSavedCredentials()
  ├─ setSavePasswordEnabled(savePasswordChecked)
  ├─ saveToLoginHistory(username)
  └─ onLoginSuccess()
```

## 六、边界情况

| 场景 | 处理 |
|------|------|
| 记住密码开启，不改用户名 | 密码保留，正常登录 |
| 记住密码开启，修改了用户名 | 密码立即清空，不再恢复 |
| 记住密码开启，从历史选不同账号 | 密码清空 |
| 记住密码开启，从历史选相同账号 | 密码保留 |
| 记住密码关闭 | 密码永远为空，历史正常记录 |
| 历史为空 | 用户名正常输入，不下拉 |
| 同一用户重复登录 | 历史去重，移到列首 |
| 应用数据清除 | 所有凭据和历史丢失 |

## 七、验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. 首次打开 → 无填充，记住密码未勾选
4. 不勾选登录 → 重开 → 可下拉历史，密码为空
5. 勾选登录 → 重开 → 自动填充账号密码
6. **修改用户名 → 密码立即清空**
7. 取消勾选 → 登录 → 重开 → 历史还在，密码为空
8. 连续不同账号登录 → 历史保留 10 条
