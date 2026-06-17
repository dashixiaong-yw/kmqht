# v1.12 回归检查与全面缺陷修复计划

## 摘要

基于对近期5次更新（v1.7～v1.11）的全面审查，以及对6个核心运行流程的模拟检查，发现 **1个P0回归Bug + 2个P1逻辑缺陷 + 1个P2结构问题**。

## 近期5次更新清单

| 版本 | 核心变更 | 概述 |
|:----:|:---------|:-----|
| v1.11 | KuaimaiInterceptor JSON解析修复 + `_call_api`参数类型安全 + `refresh_session` multipart/form-data | 快麦API全面缺陷修复 |
| v1.10 | Web管理后台 + App设置页精简 + App与Web权限分离 | 管理功能从App迁移到Web |
| v1.9 | isLoading修复 + Flow泄漏修复 + 图片离线支持 + scanFailureEvent collectLatest + 后端delete_order状态检查 | 运行流程模拟检查修复 |
| v1.8 | Token自动刷新机制 + refreshToken存储 + 后端中转刷新 | 快麦Session自动刷新 |
| v1.7 | 13项修复（CORS/加密存储/常量时间比较/强制改密/脱敏/PrefsKeys等） | 全面代码质量与安全检查 |

## 运行流程模拟检查发现

### 流程1: 首次使用引导流程 ⚠️ 发现1个P0回归bug

```
App启动 → GuideScreen (3步引导) → 保存配置 → HomeScreen → SettingsScreen
```

**BUG-01 (P0 - v1.10回归)**: GuideScreen将API Key写入普通SharedPreferences而非EncryptedSharedPreferences

- **文件**: [GuideScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/guide/GuideScreen.kt) L92-94
- **v1.7设计决策**: `SEC-03`规定API Key/App Key/App Secret等敏感凭证必须存储在 `@Named("encrypted")` 的EncryptedSharedPreferences中（AES256_GCM加密）
- **v1.10变更**: 设置了精简后，原本通过SettingsViewModel保存API Key的逻辑被移除，而GuideScreen直接用普通 `prefs` 保存API Key
- **影响**: API Key以明文存储在普通SharedPreferences中，违反安全规范
- **对比**: 引导前两步 `serverUrl`（L91）存普通prefs合理，但 `apiKey`（L93-L94）需要改存加密prefs
- **修复**: GuideScreen需要获取加密的SharedPreferences来保存API Key

### 流程2: 取货单在线删除流程 ⚠️ 发现1个P1逻辑缺陷

```
PickListScreen → 长按删除 → confirmDelete() → API deleteOrder → 失败 → 无离线入队
```

**BUG-02 (P1)**: PickListViewModel.confirmDelete() API失败后未入队离线队列

- **文件**: [PickListViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListViewModel.kt) L176-189
- **对比**: PickDetailViewModel中completeItem()/restoreItem()/deleteItem()都遵循"API成功→Direct方法，API失败→入队方法"的统一策略，但confirmDelete()失败后只显示错误消息
- **影响**: 离线时删除取货单操作会直接丢失，网络恢复后不会自动同步
- **v1.8设计决策**: `在线/离线统一策略`（知识图谱中记录）要求所有写操作都必须遵循此模式
- **修复**: catch块中添加 `pickOrderRepository.deleteItemWithQueue()` 或新增 `deleteOrderWithQueue()` 方法

### 流程3: 快麦Session过期自动刷新流程 ⚠️ 发现1个P1逻辑缺陷

```
OkHttp 401 → TokenAuthenticator.authenticate() → 刷新失败 → SessionExpiredEvent.notifyExpired() → 无人监听
```

**BUG-03 (P1)**: SessionExpiredEvent无UI监听者，TokenAuthenticator刷新失败不通知用户

- **文件**: [SessionExpiredEvent.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/util/SessionExpiredEvent.kt) L12-29 + [AppNavigation.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt) L75-82
- **现状**: TokenAuthenticator中刷新Session失败后通过`SessionExpiredEvent.notifyExpired()`通知，但没有任何UI组件监听此事件。AppNavigation只监听了`userRepository.loginRequired`（用户Token过期），不监听快麦Session过期
- **影响**: 快麦Session过期后（30天有效期），AutoRefresh失败时用户看不到任何提示，仅正常业务请求失败时显示错误消息，用户可能不理解原因
- **修复**: 在AppNavigation或MainActivity中监听SessionExpiredEvent，弹出对话框引导用户刷新快麦Session

### 流程4: 引导页API Key保存流程 ⚠️ 发现1个P2结构问题

**BUG-04 (P2)**: GuideScreen保存API Key的注释与实际代码不一致

- **文件**: [GuideScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/guide/GuideScreen.kt) L93-94
- **注释**: `// API Key保存到加密SharedPreferences（由SettingsViewModel处理）`
- **实际**: 使用的是普通 `prefs` 对象（非 `@Named("encrypted")`）
- **修复**: 与BUG-01一并修复，注入加密prefs

### 流程5+6: 其他流程 ✅ 基本正常

- v1.11的KuaimaiInterceptor JSON解析修复正确（`json.get(key)?.toString()` 替代 `json.getString(key)`）
- v1.9的isLoading/Flow泄漏/collectLatest/delete_order状态检查均已生效
- v1.8的Token自动刷新机制正常（`multipart/form-data`格式已对齐官方文档）
- 后端 `_call_api` 参数类型安全处理正确（非字符串值自动JSON序列化）
- 后端 `refresh_session` 使用的httpx `files=` 参数正确

## 修复方案

### BUG-01: GuideScreen API Key写入加密存储

**文件**: `app/src/main/java/com/kuaimai/pda/ui/guide/GuideScreen.kt`

```kotlin
// 修改函数签名，增加encryptedPrefs参数
@Composable
fun GuideScreen(
    prefs: SharedPreferences,
    encryptedPrefs: SharedPreferences,  // 新增
    onFinish: () -> Unit
) {
    // ...
    
    // StepServerConfig的onNext回调 - L89-97
    onNext = {
        prefs.edit().putString(PrefsKeys.KEY_SERVER_URL, serverUrl.trim()).apply()
        if (apiKey.isNotBlank()) {
            // API Key保存到加密SharedPreferences
            encryptedPrefs.edit().putString(PrefsKeys.KEY_API_KEY, apiKey.trim()).apply()
        }
        currentStep = 1
    }
```

**文件**: `app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt`

```kotlin
// 注入@Named("encrypted") encryptedPrefs参数
fun AppNavigation(
    userRepository: UserRepository,
    prefs: SharedPreferences,
    @Named("encrypted") encryptedPrefs: SharedPreferences,  // 新增
    authRepository: AuthRepository
) {
    // ...
    composable(Routes.GUIDE) {
        GuideScreen(
            prefs = prefs,
            encryptedPrefs = encryptedPrefs,  // 传入
            onFinish = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.GUIDE) { inclusive = true }
                }
            }
        )
    }
```

**文件**: `MainActivity.kt`（需要找到调用AppNavigation的地方，传入encryptedPrefs）

### BUG-02: PickListViewModel.confirmDelete() API失败入队

**文件**: `app/src/main/java/com/kuaimai/pda/ui/picklist/PickListViewModel.kt`

```kotlin
fun confirmDelete() {
    val order = _deleteTarget.value ?: return
    viewModelScope.launch {
        try {
            val token = userRepository.getToken()
            orderApiService.deleteOrder(token, order.id)
            // API成功：直接删除本地
            pickOrderRepository.deleteOrder(order)
        } catch (e: Exception) {
            // API失败：乐观删除+入队
            pickOrderRepository.deleteOrder(order)
            _errorMessage.value = "删除取货单失败，将在网络恢复后重试: ${e.message}"
        } finally {
            _deleteTarget.value = null
        }
    }
}
```

注意：需要在 `PickOrderRepository` 中添加 `deleteOrderWithQueue` 方法（创建 `delete_order` 类型的PendingOperation），或在本地删除后手动创建PendingOperation。

首先需要在 `PickOrderRepository` 添加接口方法：
```kotlin
/** 删除取货单（乐观删除本地+写入离线队列） */
suspend fun deleteOrderWithQueue(order: PickOrderEntity)
```

实现：
```kotlin
override suspend fun deleteOrderWithQueue(order: PickOrderEntity) {
    pickOrderDao.delete(order)
    enqueueOperation(
        operationType = "delete_order",
        orderId = order.id,
        targetId = 0L
    )
}
```

并在 `PickListViewModel` 中使用 `deleteOrderWithQueue` 替换原来的 `deleteOrder`。

### BUG-03: SessionExpiredEvent UI监听

**文件**: `app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt`

在AppNavigation中添加对SessionExpiredEvent的监听：

```kotlin
// 监听快麦Session过期事件，引导用户重新配置
LaunchedEffect(Unit) {
    SessionExpiredEvent.isExpired.collect { isExpired ->
        if (isExpired) {
            // 跳转到设置页（或弹出对话框）让用户知道需要重新授权
            // 由于设置页已精简，可以弹出对话框提示用户访问Web后台
            // 或导航到一个专门的说明页
        }
    }
}
```

由于v1.10已将快麦配置管理迁移到Web后台，App端仅需显示提示对话框：

```kotlin
var showSessionExpiredDialog by remember { mutableStateOf(false) }

LaunchedEffect(Unit) {
    SessionExpiredEvent.isExpired.collectLatest {
        showSessionExpiredDialog = true
    }
}

// 在Composable中添加AlertDialog
if (showSessionExpiredDialog) {
    AlertDialog(
        onDismissRequest = { showSessionExpiredDialog = false },
        title = { Text("快麦会话已过期") },
        text = { Text("快麦API会话已过期，请通过Web管理后台重新授权（浏览器访问 http://服务器地址:8900/admin）") },
        confirmButton = {
            TextButton(onClick = { showSessionExpiredDialog = false }) {
                Text("知道了")
            }
        }
    )
}
```

### BUG-04: 引导页API Key保存注释修正

与BUG-01一并修复，注释保持一致。

## 假设与决策

1. **BUG-01的修复范围**：仅修复GuideScreen中的API Key保存路径，不影响SettingsScreen（已精简无服务器配置）
2. **BUG-02的修复**：需要新增 `deleteOrderWithQueue` 方法到PickOrderRepository接口和实现
3. **BUG-03的修复**：在AppNavigation中添加对话框，不导航到设置页（因为设置页已无快麦配置）
4. **不涉及v1.11的回归**：KuaimaiInterceptor的JSON解析修复和_call_api类型安全都已正确实现

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 3处版本号一致（build.gradle.kts + CHANGELOG.md + gradle.properties）→ 递增至 v1.12
4. 知识图谱已更新
5. docker-deploy已同步
6. Git提交推送完成
