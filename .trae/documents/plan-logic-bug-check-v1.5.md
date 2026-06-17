# 全面检查计划 - v1.5 逻辑流程与Bug修复

## 摘要

对比设计文档（F1-F35）与v1.4实际代码实现，发现1个P0关键Bug、2个P1重要问题、3个P2次要问题。主要集中在：在线模式completeItem/restoreItem重复入队、PDA硬件扫码未监听、uriToFile资源泄漏等。

## 当前状态分析

### 已验证的v1.4修复（14个Bug全部正确实现）

| v1.4修复项 | 验证结果 |
|------------|----------|
| BUG-01 GuideScreen保存配置 | ✅ prefs参数传入，每步保存正确 |
| BUG-02 下拉刷新明细数据 | ✅ refresh()中upsert明细到本地数据库 |
| BUG-03 图片上传实现 | ✅ PickVisualMedia + uriToFile |
| BUG-04 Product路由orderId | ✅ 路由定义+参数解析+导航传参 |
| BUG-05 连续扫码清空输入框 | ✅ scanSuccessEvent监听+清空+聚焦 |
| BUG-06 重复扫码高亮滚动 | ✅ items.indexOfFirst+animateScrollToItem |
| BUG-07 SettingsScreen配置UI | ✅ 服务器/API Key/扫码/反馈开关 |
| BUG-08 取货单分组排序 | ✅ sortedWith按拣货区+时间 |
| BUG-09 4xx/5xx错误区分 | ✅ HttpException catch分支 |
| BUG-10 API失败回滚 | ✅ 先API后本地更新 |
| BUG-12 LoginScreen类型检查 | ✅ is SocketTimeoutException等 |
| BUG-13 超时SQL修正 | ✅ WHERE status = 1 AND completion_type = 1 |
| BUG-14 预警天数常量 | ✅ SESSION_WARNING_DAYS |
| StateFlow组合调用 | ✅ collectAsState() |

### 功能完整性验证（F1-F35）

| 功能 | 状态 | 说明 |
|------|------|------|
| F1 取货单管理 | ✅ | 创建/列表/详情完整 |
| F2 扫码待办 | ✅ | 扫码添加+完成/恢复 |
| F3 自动完成 | ✅ | 后端定时任务+前端检测 |
| F4 商品详情 | ✅ | SKU信息+备注编辑+二次确认 |
| F5 图片上传 | ✅ | PickVisualMedia选择器+uriToFile |
| F6 PDA扫码适配 | ⚠️ | 硬件扫码OK，相机降级TODO（保留） |
| F7 离线操作队列 | ✅ | 9种操作类型全部实现 |
| F8 扫码反馈 | ✅ | 三种反馈类型 |
| F9 重复扫码检测 | ✅ | 检测+滚动+Snackbar |
| F10 图片压缩 | ✅ | ImageCompressor 1024px/80% |
| F11 API Key认证 | ✅ | 中间件+加密存储 |
| F12 网络状态指示 | ✅ | NetworkStatusIndicator |
| F13 屏幕常亮 | ✅ | FLAG_KEEP_SCREEN_ON |
| F14 全部完成按钮 | ✅ | completeAllItems |
| F15 下拉刷新 | ✅ | PullToRefreshBox+明细同步 |
| F16 会话过期预警 | ✅ | 黄色警告条+常量 |
| F17 PDA触摸优化 | ✅ | 56dp最小触摸热区 |
| F18 供应商关联修改 | ✅ | 选择+二次确认+快麦API |
| F19 取货单删除 | ✅ | 确认弹窗+二次确认 |
| F20 待办行删除 | ✅ | 长按+确认弹窗 |
| F21 已完成查看 | ✅ | 7天内已完成列表 |
| F22 图片删除/替换 | ✅ | 删除OK，上传新图自动覆盖旧图 |
| F23 首次使用引导 | ✅ | 引导页+配置保存 |
| F24 取货单排序 | ✅ | 按拣货区分组排序 |
| F25 长按操作 | ✅ | 长按删除+查看详情 |
| F27 连续扫码模式 | ✅ | 开关+清空输入框 |
| F28 API Key加密 | ✅ | EncryptedSharedPreferences |
| F31 条码格式兼容 | ✅ | clean_barcode+validate_barcode |
| F32 冷启动优化 | ✅ | Room缓存秒加载 |
| F35 Token刷新失败 | ✅ | 弹窗+跳转设置 |

## 发现的问题清单

### P0 - 关键Bug

#### BUG-01: 在线模式completeItem/restoreItem重复入队
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt` L176-210
- **问题**: v1.4修复BUG-10时改为"先API后本地"，但在线模式API成功后调用`pickOrderRepository.updateItemStatus()`，而该方法内部会乐观更新+入队（PickOrderRepository.kt L105-114）。这意味着在线模式下API已成功，但操作仍被入队到离线队列，导致OrderSyncWorker重复执行已完成的操作。
- **影响**: 每次在线完成/恢复明细，OrderSyncWorker都会重复调用一次API（虽然后端幂等不会出错，但浪费资源且可能产生误导性日志）
- **修复方案**: 在PickOrderRepository中新增`updateItemStatusDirect()`方法（只更新本地数据库不入队），在线模式API成功后调用此方法；离线模式仍使用`updateItemStatus()`（乐观更新+入队）

### P1 - 重要问题

#### BUG-02: PickDetailScreen未监听PDA硬件扫码
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt`
- **问题**: ScannerManager通过`_scanResult` StateFlow发布PDA硬件扫码结果（ScannerManager.kt L94），但PickDetailScreen没有collect这个Flow。当前只有手动输入框的onDone回调（L229-232）会触发viewModel.onBarcodeScanned()。PDA硬件扫码事件不会被自动处理。
- **影响**: F2扫码待办功能在PDA硬件扫码模式下完全失效
- **修复方案**: 在PickDetailScreen中collect scannerManager.scanResult，将硬件扫码结果传递给viewModel.onBarcodeScanned()。使用LaunchedEffect+collectLatest监听。

#### BUG-03: deleteItem在线模式逻辑不一致
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt` L314-323
- **问题**: deleteItem只调用`pickOrderRepository.deleteItemWithQueue()`（乐观更新+入队），但在线模式下应先调API成功后再更新本地，与completeItem/restoreItem的逻辑不一致。
- **影响**: 在线模式下deleteItem不调API，仅入队等OrderSyncWorker执行，响应延迟
- **修复方案**: 与completeItem/restoreItem保持一致，在线模式先API后本地（updateItemStatusDirect），离线模式乐观更新+入队

### P2 - 次要问题

#### BUG-04: uriToFile未安全关闭inputStream
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt` L643-655
- **问题**: `inputStream.copyTo(output)` 后调用 `inputStream.close()`，但如果copyTo抛出异常，inputStream不会被关闭（资源泄漏）
- **修复**: 使用 `inputStream.use { it.copyTo(output) }` 替代手动close

#### BUG-05: SettingsScreen配置remember位置不当
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt`
- **问题**: `var serverUrl by remember { mutableStateOf(viewModel.getServerUrl()) }` 和 `var apiKey by remember { mutableStateOf(viewModel.getApiKey()) }` 在Card的Column内部使用remember，可能导致重组时状态不一致
- **修复**: 将serverUrl和apiKey的remember移到SettingsScreen函数顶部（与其他状态变量一起）

#### BUG-06: completeAllItems也使用updateItemStatus导致重复入队
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt` L215-230
- **问题**: completeAllItems在线模式API成功后，遍历items调用updateItemStatus，同样会重复入队
- **修复**: 使用updateItemStatusDirect替代

## 修复方案

### BUG-01: 新增updateItemStatusDirect + 在线/离线区分

**PickOrderRepository.kt** 新增方法：
```kotlin
/**
 * 直接更新明细状态（不入队，用于在线模式API成功后）
 */
override suspend fun updateItemStatusDirect(id: Long, status: Int, completedAt: Long?) {
    pickItemDao.updateStatus(id, status, completedAt)
}
```

**PickOrderRepositoryImpl.kt** 实现：
```kotlin
override suspend fun updateItemStatusDirect(id: Long, status: Int, completedAt: Long?) {
    pickItemDao.updateStatus(id, status, completedAt)
}
```

**PickDetailViewModel.kt** 修改completeItem/restoreItem：
```kotlin
fun completeItem(itemId: Long) {
    viewModelScope.launch {
        _isLoading.value = true
        try {
            // 在线模式：先API，成功后直接更新本地（不入队）
            val token = userRepository.getToken()
            orderApiService.completeItem(token, orderId, itemId)
            pickOrderRepository.updateItemStatusDirect(itemId, 1, TimeUtils.now())
        } catch (e: Exception) {
            // API失败，使用乐观更新+入队（离线模式）
            pickOrderRepository.updateItemStatus(itemId, 1, TimeUtils.now())
            _errorMessage.value = "完成明细失败: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}
```

注意：不需要注入NetworkMonitor。简化方案：API成功→updateItemStatusDirect（不入队），API失败→updateItemStatus（入队，等网络恢复后同步）。

### BUG-02: 监听PDA硬件扫码

**PickDetailScreen.kt** 添加：
```kotlin
// 监听PDA硬件扫码结果
LaunchedEffect(Unit) {
    viewModel.scannerManager.scanResult
        .collectLatest { barcode ->
            if (barcode.isNotEmpty()) {
                viewModel.onBarcodeScanned(barcode)
                viewModel.scannerManager.clearResult()
            }
        }
}
```

注意：ScannerManager已通过Hilt注入到PickDetailViewModel中，需要确保ScannerManager在Activity层面已register()。需检查MainActivity中是否已注册。

### BUG-03: deleteItem在线模式先API后本地

```kotlin
fun deleteItem(itemId: Long) {
    viewModelScope.launch {
        _isLoading.value = true
        try {
            val token = userRepository.getToken()
            orderApiService.deleteItem(token, orderId, itemId)
            pickOrderRepository.deleteItemDirect(itemId)
        } catch (e: Exception) {
            pickOrderRepository.deleteItemWithQueue(itemId)
            _errorMessage.value = "删除明细失败: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}
```

需要在PickOrderRepository中新增`deleteItemDirect()`方法。

### BUG-04: uriToFile资源安全关闭

```kotlin
private fun uriToFile(uri: Uri, context: Context): java.io.File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = java.io.File.createTempFile("upload_", ".jpg", context.cacheDir)
        tempFile.outputStream().use { output ->
            inputStream.use { it.copyTo(output) }
        }
        tempFile
    } catch (e: Exception) {
        null
    }
}
```

### BUG-05: remember位置调整

将SettingsScreen中serverUrl和apiKey的remember移到函数顶部。

### BUG-06: completeAllItems使用updateItemStatusDirect

```kotlin
fun completeAllItems() {
    viewModelScope.launch {
        try {
            val token = userRepository.getToken()
            orderApiService.completeAllItems(token, orderId)
            // API成功后直接更新本地（不入队）
            items.filter { it.status == 0 }.forEach { item ->
                pickOrderRepository.updateItemStatusDirect(item.id, 1, TimeUtils.now())
            }
        } catch (e: Exception) {
            _errorMessage.value = "批量完成失败: ${e.message}"
        }
    }
}
```

## 假设与决策

1. **BUG-01简化方案**：不注入NetworkMonitor，API成功→updateItemStatusDirect，API失败→updateItemStatus（入队）。这样离线模式下API必然失败，自动走乐观更新+入队路径
2. **BUG-02 PDA扫码**：ScannerManager已注入ViewModel，但需确认register()在Activity层面已调用。如果未注册，需要在MainActivity中注册
3. **BUG-03 deleteItem**：需要新增deleteItemDirect方法和orderApiService.deleteItem方法
4. **F6相机扫码**：保留TODO，后续版本实现

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 功能验证：
   - 在线模式completeItem/restoreItem/deleteItem/completeAllItems不重复入队
   - PDA硬件扫码能触发onBarcodeScanned
   - uriToFile资源正确关闭
   - SettingsScreen配置状态在重组时保持一致
