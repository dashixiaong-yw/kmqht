# 登录页历史下拉修复

## 一、用户澄清

**从未成功登录过**（网络未就绪），因此：
- 记住密码数据为空 → 正确行为（没有数据可加载）
- 登录历史为空 → 正确行为（没有登录记录）
- 下拉点不开 → **交互问题**：用户看到 trailingIcon（下拉箭头），点击无效

## 二、根因分析

### 下拉点不开的真正原因

当前代码的问题不在数据层，而在 UI 层交互设计：

**问题 1：矛盾的用户体验**

```kotlin
trailingIcon = {
    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
},
```

用户名输入框有一个下拉箭头图标（`TrailingIcon`），但：

```kotlin
onExpandedChange = { if (loginHistory.isNotEmpty()) dropdownExpanded = it }
```

当 `loginHistory` 为空时，`onExpandedChange` 被忽略 → `dropdownExpanded` 始终 `false` → `expanded` 始终 `false` → 下拉永不弹出。

用户看到箭头图标，点了没反应 → **判定为 bug**。

**问题 2：onValueChange 干扰 ExposedDropdownMenuBox**

```kotlin
dropdownExpanded = newValue.isEmpty() && loginHistory.isNotEmpty()
```

每次输入都重设 `dropdownExpanded`，与 `ExposedDropdownMenuBox` 自带的 `onExpandedChange` 竞争状态。

### 记住密码数据消失 — 已确认不是 bug

`clearLocalUser()` 不清除凭据 key，存储逻辑正确。纯因无成功登录导致无数据。

## 三、修复方案（只改 LoginScreen.kt，3 处改动）

### 3.1 数据加载改为 `remember` 同步初始化

从 `LaunchedEffect` 异步加载改为 `remember` 同步加载，消除时序风险：

```kotlin
// 旧：异步
var savePasswordChecked by remember { mutableStateOf(false) }
var savedUsername by remember { mutableStateOf("") }
val loginHistory = remember { mutableStateListOf<String>() }
LaunchedEffect(Unit) {
    savePasswordChecked = userRepository.isSavePasswordEnabled()
    savedUsername = userRepository.getSavedUsername()
    if (savePasswordChecked && savedUsername.isNotEmpty()) {
        username = savedUsername
        password = userRepository.getSavedPassword()
    }
    loginHistory.addAll(userRepository.getLoginHistory())
}

// 新：同步
val loadedHistory = remember { userRepository.getLoginHistory() }
val loginHistory = remember { mutableStateListOf<String>().apply { addAll(loadedHistory) } }
val savePasswordChecked by remember { mutableStateOf(userRepository.isSavePasswordEnabled()) }
val savedUser = remember { userRepository.getSavedUsername() }
val savedUsername by remember { mutableStateOf(savedUser) }

var username by remember {
    mutableStateOf(if (savePasswordChecked && savedUser.isNotEmpty()) savedUser else "")
}
var password by remember {
    mutableStateOf(
        if (savePasswordChecked && savedUser.isNotEmpty()) userRepository.getSavedPassword() else ""
    )
}
```

### 3.2 移除 onValueChange 中干扰下拉的代码

删除：
```kotlin
dropdownExpanded = newValue.isEmpty() && loginHistory.isNotEmpty()
```

让 `ExposedDropdownMenuBox` 通过 `onExpandedChange` 完全控制。

### 3.3 无历史数据时不显示 trailingIcon

当 `loginHistory` 为空时，箭头图标不给用户"可以展开"的暗示：

```kotlin
trailingIcon = {
    if (loginHistory.isNotEmpty()) {
        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
    }
},
```

**这解决了"用户看到箭头点不动"的核心体验问题。**

## 四、修改后的完整 LoginScreen 关键代码

```kotlin
@Composable
fun LoginScreen(
    userRepository: UserRepository,
    onLoginSuccess: () -> Unit
) {
    // ======== 同步加载所有数据 ========
    val loadedHistory = remember { userRepository.getLoginHistory() }
    val loginHistory = remember { mutableStateListOf<String>().apply { addAll(loadedHistory) } }
    val savePasswordChecked by remember { mutableStateOf(userRepository.isSavePasswordEnabled()) }
    val savedUser = remember { userRepository.getSavedUsername() }
    val savedUsername by remember { mutableStateOf(savedUser) }

    var username by remember {
        mutableStateOf(if (savePasswordChecked && savedUser.isNotEmpty()) savedUser else "")
    }
    var password by remember {
        mutableStateOf(
            if (savePasswordChecked && savedUser.isNotEmpty()) userRepository.getSavedPassword() else ""
        )
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // ... 其余状态变量保持不变

    // ======== 用户名输入 ========
    ExposedDropdownMenuBox(
        expanded = dropdownExpanded && loginHistory.isNotEmpty(),
        onExpandedChange = { if (loginHistory.isNotEmpty()) dropdownExpanded = it }
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { newValue ->
                username = newValue
                errorMessage = ""
                if (newValue != savedUsername && password.isNotEmpty()) {
                    password = ""
                }
            },
            label = { Text("用户名") },
            leadingIcon = { Icon(Icons.Default.Person, ...) },
            trailingIcon = {
                if (loginHistory.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                }
            },
            ...
        )
        ExposedDropdownMenu(
            expanded = dropdownExpanded && loginHistory.isNotEmpty(),
            onDismissRequest = { dropdownExpanded = false }
        ) {
            loginHistory.forEach { ... }
        }
    }

    // ======== 登录成功 ========
    ...
}
```

## 五、验证步骤

1. **首次打开（无历史数据）** → 用户名/密码为空，无下拉箭头 → **用户不会困惑点了没用**
2. **勾选记住密码后登录** → 数据保存
3. **退出再进入** → 用户名和密码自动填充，下拉箭头显示，可展开历史
4. **不勾选记住密码后登录** → 历史记录存在，下拉可展开
5. **修改用户名 → 密码立即清空**
6. `./gradlew lint` 通过
7. `./gradlew assembleRelease` 构建成功
