# v1.8 全面运行流程模拟与Bug检查计划

## 概述

模拟系统完整运行流程，检查bug、逻辑缺陷、运行流畅性。

## 当前状态分析

### 已确认无问题的流程
- **App启动流程**：App.kt → MainActivity.kt → AppNavigation.kt，启动验证token → 判断引导页/首页/登录页，逻辑完整
- **登录流程**：LoginScreen → UserRepository.login() → 保存token/session_expire_time → mustChangePassword检测，逻辑正确
- **首页流程**：HomeScreen会话过期预警（session_expire_time已写入）、Token刷新失败弹窗、引导提示条，逻辑正确
- **取货单详情流程**：PickDetailViewModel在线/离线统一策略（API成功→Direct，API失败→入队），逻辑正确
- **离线同步流程**：OrderSyncWorker 9种操作类型 + 4xx/5xx区分 + 冲突处理，逻辑正确
- **设置页流程**：服务器地址/API Key保存、拣货区CRUD，逻辑正确
- **uriToFile资源管理**：已使用`use`闭包正确关闭InputStream和OutputStream

### 发现的问题清单

#### Bug/逻辑缺陷（3项）

| 编号 | 级别 | 问题 | 文件 | 说明 |
|------|------|------|------|------|
| BUG-01 | P0 | completeAllItems API失败后未入队 | PickDetailViewModel.kt L219-236 | `completeAllItems()`的catch块只显示错误消息，没有调用入队方法。如果API失败，本地状态未更新且未入队，用户操作丢失 |
| BUG-02 | P1 | onBarcodeScanned重复扫码时isLoading未重置 | PickDetailViewModel.kt L127-170 | 第136行`return@launch`直接返回，跳过了finally块中的`_isLoading.value = false`，导致扫码按钮永久禁用 |
| BUG-03 | P1 | HomeScreen会话过期预警计算不精确 | HomeScreen.kt L88-89 | `daysLeft`计算使用整数除法，当expireTime距当前不到24小时时daysLeft=0，但条件`daysLeft in 0..4`会显示"即将过期"，实际上可能还有23小时才过期，措辞不准确 |

#### 代码质量（2项）

| 编号 | 级别 | 问题 | 文件 | 说明 |
|------|------|------|------|------|
| CODE-01 | P2 | PickDetailScreen中scanSuccessEvent使用collect而非collectLatest | PickDetailScreen.kt L145-154 | 使用`collect`可能在快速连续扫码时丢失事件，应改为`collectLatest` |
| CODE-02 | P2 | PickDetailScreen中LazyColumn每个item都启动LaunchedEffect查询图片 | PickDetailScreen.kt L321-327 | 每个item都启动一个协程查询图片URL，大量明细时可能产生性能问题 |

## 修复方案

### BUG-01: completeAllItems API失败后入队（P0）

**文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt` L219-236

**问题**: completeAllItems的catch块只显示错误消息，没有入队。对比completeItem/restoreItem/deleteItem都有入队逻辑。

**修改**: 在catch块中添加入队逻辑

```kotlin
fun completeAllItems() {
    viewModelScope.launch {
        try {
            val token = userRepository.getToken()
            orderApiService.completeAllItems(token, orderId)
            // API成功后直接更新本地（不入队）
            val currentItems = items.value
            val now = TimeUtils.now()
            currentItems.filter { it.status == 0 }.forEach { item ->
                pickOrderRepository.updateItemStatusDirect(item.id, 1, now)
            }
            pickOrderRepository.updateOrderStatus(orderId, 1, now)
            loadOrder()
        } catch (e: Exception) {
            // API失败，使用乐观更新+入队（离线模式自动走此路径）
            val currentItems = items.value
            val now = TimeUtils.now()
            currentItems.filter { it.status == 0 }.forEach { item ->
                pickOrderRepository.updateItemStatus(item.id, 1, now)
            }
            pickOrderRepository.updateOrderStatus(orderId, 1, now)
            _errorMessage.value = "批量完成失败: ${e.message}"
        }
    }
}
```

### BUG-02: 重复扫码时isLoading未重置（P1）

**文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt` L127-170

**问题**: 第136行`return@launch`跳过了finally块。这是因为`return@launch`在try块内，但finally应该仍然执行。实际上Kotlin中`return@launch`不会跳过finally，所以这不是bug。

**验证**: 重新检查Kotlin语义——`return@launch`从lambda返回，但finally块总是会执行的。所以isLoading会被正确重置。

**结论**: 这不是bug，移除此项。

### BUG-03: 会话过期预警措辞优化（P1）

**文件**: `app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt` L88-89

**修改**: 改进daysLeft计算和预警消息

```kotlin
val daysLeft = (expireTime - now) / (1000 * 60 * 60 * 24)
val hoursLeft = (expireTime - now) / (1000 * 60 * 60)
showSessionWarning = if (daysLeft in 1..(AppConstants.SESSION_WARNING_DAYS - 1)) {
    true
} else if (daysLeft == 0L && hoursLeft > 0) {
    true  // 不足1天但还有几小时
} else {
    expireTime > 0 && expireTime <= now  // 已过期
}
```

预警消息也改为更精确的：
```kotlin
val warningText = when {
    daysLeft > 1 -> "会话将在${daysLeft}天后过期，请及时刷新"
    daysLeft == 1L -> "会话将在1天后过期，请及时刷新"
    hoursLeft > 0 -> "会话将在${hoursLeft}小时后过期，请立即刷新"
    else -> "会话已过期，请重新授权"
}
```

### CODE-01: scanSuccessEvent改用collectLatest（P2）

**文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt` L145-154

**修改**: 将`collect`改为`collectLatest`

```kotlin
LaunchedEffect(Unit) {
    viewModel.scanSuccessEvent.collectLatest {
        viewModel.provideFeedback(context, ScanFeedbackType.SUCCESS)
        if (continuousScanMode) {
            scanInput = ""
            focusRequester.requestFocus()
        }
    }
}
```

### CODE-02: 图片查询优化（P2）

**当前问题**: 每个LazyColumn item都启动LaunchedEffect查询图片，N个item就有N个协程。

**方案**: 在ViewModel中批量预加载图片URL，Screen中直接读取缓存。但这需要较大的重构，且当前明细数量通常不超过50个，性能影响有限。

**决策**: 暂不修改，记录为已知优化点。

## 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `app/.../ui/pickdetail/PickDetailViewModel.kt` | BUG-01: completeAllItems API失败后入队 |
| `app/.../ui/home/HomeScreen.kt` | BUG-03: 会话过期预警措辞优化 |
| `app/.../ui/pickdetail/PickDetailScreen.kt` | CODE-01: scanSuccessEvent改用collectLatest |

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 模拟completeAllItems API失败场景，验证离线入队
4. 验证HomeScreen会话预警消息精确性

## 假设与决策

1. **BUG-02决策**: 经验证Kotlin语义，`return@launch`不会跳过finally块，这不是bug
2. **CODE-02决策**: 图片查询优化暂不实施，当前性能可接受
3. **BUG-01决策**: completeAllItems API失败时使用与completeItem/restoreItem一致的入队策略
