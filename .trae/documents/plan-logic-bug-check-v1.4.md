# 全面检查计划 - v1.4 逻辑流程与Bug修复

## 摘要

对比设计文档（kuaimai-pda-app-plan.md F1-F35）与实际代码实现，发现4个P0关键Bug、6个P1重要问题、4个P2次要问题。主要集中在：引导页配置丢失、下拉刷新不刷新明细、图片上传未实现、路由参数缺失、连续扫码模式不完整、设置页缺少配置UI等。

## 当前状态分析

### 已验证的功能（F1-F35）

| 功能 | 状态 | 说明 |
|------|------|------|
| F1 取货单管理 | ✅ | 创建/列表/详情完整 |
| F2 扫码待办 | ✅ | 扫码添加+完成/恢复 |
| F3 自动完成 | ✅ | 后端定时任务+前端检测 |
| F4 商品详情 | ✅ | SKU信息+备注编辑+二次确认 |
| F5 图片上传 | ❌ P0 | onClick为空，未触发图片选择器 |
| F6 PDA扫码适配 | ⚠️ | 硬件扫码OK，相机降级TODO |
| F7 离线操作队列 | ✅ | 9种操作类型全部实现 |
| F8 扫码反馈 | ✅ | 三种反馈类型 |
| F9 重复扫码检测 | ⚠️ P1 | 检测OK但未高亮滚动到该行 |
| F10 图片压缩 | ✅ | ImageCompressor 1024px/80% |
| F11 API Key认证 | ✅ | 中间件+加密存储 |
| F12 网络状态指示 | ✅ | NetworkStatusIndicator |
| F13 屏幕常亮 | ✅ | FLAG_KEEP_SCREEN_ON |
| F14 全部完成按钮 | ✅ | completeAllItems |
| F15 下拉刷新 | ❌ P0 | 只刷新订单信息，不刷新明细 |
| F16 会话过期预警 | ✅ | 黄色警告条（天数硬编码P2） |
| F17 PDA触摸优化 | ✅ | 56dp最小触摸热区 |
| F18 供应商关联修改 | ✅ | 选择+二次确认+快麦API |
| F19 取货单删除 | ✅ | 确认弹窗+二次确认 |
| F20 待办行删除 | ✅ | 长按+确认弹窗 |
| F21 已完成查看 | ✅ | 7天内已完成列表 |
| F22 图片删除/替换 | ⚠️ | 删除OK，替换未实现（同F5） |
| F23 首次使用引导 | ❌ P0 | 引导页不保存配置到SharedPreferences |
| F24 取货单排序 | ⚠️ P1 | 未按拣货区分组相邻显示 |
| F25 长按操作 | ✅ | 长按删除+查看详情 |
| F27 连续扫码模式 | ⚠️ P1 | 开关OK但PDA扫码不清空输入框 |
| F28 API Key加密 | ✅ | EncryptedSharedPreferences |
| F31 条码格式兼容 | ✅ | clean_barcode+validate_barcode |
| F32 冷启动优化 | ✅ | Room缓存秒加载 |
| F35 Token刷新失败 | ✅ | 弹窗+跳转设置 |

## 发现的问题清单

### P0 - 关键Bug（必须修复）

#### BUG-01: GuideScreen引导页不保存配置
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/guide/GuideScreen.kt`
- **问题**: serverUrl和scanMethod只存在Compose本地变量中，onFinish回调不保存到SharedPreferences。引导完成后配置丢失，App无法连接服务器。
- **影响**: F23功能完全失效
- **修复**: GuideScreen接收SharedPreferences参数，在StepServerConfig的"下一步"中保存serverUrl，在StepScanMethod的"下一步"中保存scanMethod，在StepComplete的"开始使用"中保存guide_shown标记

#### BUG-02: PickDetailScreen下拉刷新不刷新明细数据
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt` L232-256
- **问题**: refresh()只调用getOrderDetail获取取货单信息，不获取明细列表。多PDA场景下，其他PDA添加的明细看不到。
- **影响**: F15功能不完整
- **修复**: refresh()中额外调用orderApiService.getOrderDetail()获取items，逐条upsert到本地数据库

#### BUG-03: ProductScreen图片上传按钮onClick为空
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt` L174-175
- **问题**: `onUploadArea = { /* 由外部图片选择器触发 */ }` 和 `onUploadBox = { /* 由外部图片选择器触发 */ }` 是空lambda，没有触发图片选择器
- **影响**: F5功能完全失效，无法上传图片
- **修复**: 使用ActivityResultLauncher启动系统图片选择器（PickVisualMedia），选择后调用viewModel.uploadImage()

#### BUG-04: AppNavigation Product路由缺少orderId参数
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt` L37,147
- **问题**: `Routes.PRODUCT = "product/{skuOuterId}"` 没有orderId参数。ProductViewModel中currentOrderId始终为0，导致无法精确查询当前订单下的SKU。
- **影响**: 从取货详情页进入商品详情时，可能查到错误订单的同SKU商品
- **修复**: 修改路由为 `product/{skuOuterId}?orderId={orderId}`，PickDetailScreen导航时传入orderId

### P1 - 重要问题

#### BUG-05: PickDetailScreen连续扫码模式未自动清空输入框
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt` L121-161
- **问题**: PDA硬件扫码走viewModel.onBarcodeScanned()，但不会清空Screen的scanInput变量。连续扫码模式下，扫码后输入框不清空，光标不回位。
- **影响**: F27功能不完整
- **修复**: 监听scanSuccessEvent，成功后清空scanInput并重新requestFocus

#### BUG-06: PickDetailScreen重复扫码未高亮滚动到该行
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt` L125-131
- **问题**: F9要求"高亮已存在的行"，当前只显示Snackbar提示，没有滚动到重复行
- **影响**: 用户体验不完整
- **修复**: 使用LazyListState.animateScrollToItem()滚动到重复行，并添加临时高亮效果

#### BUG-07: SettingsScreen缺少服务器地址/API Key/扫码配置UI
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt`
- **问题**: SettingsViewModel有完整的方法（saveServerUrl/saveApiKey/setScanMethod/toggleSound/toggleVibration），但SettingsScreen只有用户管理和退出登录，缺少服务器配置、API Key、扫码方式、反馈开关的UI
- **影响**: 用户无法在设置页修改服务器地址、API Key等配置
- **修复**: 在SettingsScreen中添加配置区域：服务器地址输入+保存、API Key输入+保存、扫码方式选择、声音/振动开关

#### BUG-08: PickListScreen取货单排序未按F24要求
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/picklist/PickListScreen.kt` L133-143
- **问题**: F24要求"同一拣货区的单据相邻显示"，当前仅按创建时间倒序
- **影响**: 不符合设计要求
- **修复**: 对activeOrders按拣货区（从orderNo中提取）分组排序：先按拣货区分组，组内按创建时间倒序

#### BUG-09: OrderSyncWorker 4xx错误不应重试
- **文件**: `app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt` L103-127
- **问题**: 所有API错误统一重试，但404（明细不存在）、409（SKU已存在）等4xx错误不应重试，应直接标记为冲突
- **影响**: 无效重试浪费资源
- **修复**: 在syncOperation中catch HttpException，4xx错误直接标记冲突（retryCount=-1），5xx才重试

#### BUG-10: PickDetailViewModel API失败时本地状态不回滚
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt` L167-191
- **问题**: completeItem/restoreItem先调用API再调用pickOrderRepository.updateItemStatus()。但updateItemStatus内部是乐观更新（先更新本地再入队），如果API失败，本地状态已更新但不会回滚。
- **影响**: 在线模式下API失败，UI显示已完成但实际未完成
- **修复**: 调整顺序：先乐观更新本地+入队，API成功则无需额外操作（离线队列会跳过已同步的操作）；或改为API失败时回滚本地状态

### P2 - 次要问题

#### BUG-11: PickDetailScreen相机扫码按钮TODO未实现
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt` L228-229
- **问题**: 相机扫码按钮onClick只有 `// TODO: 启动CameraScanScreen`
- **影响**: F6摄像头降级方案未实现
- **修复**: 暂保留TODO，后续版本实现ML Kit扫码

#### BUG-12: LoginScreen网络错误提示用字符串匹配
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt` L191-201
- **问题**: friendlyErrorMessage通过`message.contains("ConnectException")`等字符串匹配判断异常类型，Retrofit/OkHttp的异常类名可能被混淆
- **影响**: 混淆后错误提示可能不准确
- **修复**: 改用`when (throwable)` + `is SocketTimeoutException`等类型检查

#### BUG-13: backend _check_order_timeout第二条SQL可能误匹配
- **文件**: `backend/main.py` L180-188
- **问题**: `WHERE completion_type = 1`可能匹配到之前手动完成（completion_type=0）的订单下的未完成明细
- **影响**: 可能将手动完成订单中的未完成明细也标记为完成
- **修复**: 改为 `WHERE order_id IN (SELECT id FROM pick_orders WHERE status = 0 AND completion_type = 1)` 加上 `AND status = 0`

#### BUG-14: HomeScreen会话过期预警天数硬编码
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt` L88
- **问题**: `daysLeft in 0..4` 是硬编码的5天阈值
- **影响**: 阈值调整需要修改代码
- **修复**: 提取为AppConstants中的常量 `SESSION_WARNING_DAYS = 5`

## 修复方案

### BUG-01: GuideScreen保存配置

```kotlin
// GuideScreen.kt
@Composable
fun GuideScreen(
    prefs: SharedPreferences,  // 新增参数
    onFinish: () -> Unit
) {
    // StepServerConfig的"下一步"中:
    prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply()

    // StepScanMethod的"下一步"中:
    prefs.edit().putInt(KEY_SCAN_METHOD, selectedScanMethod).apply()

    // StepComplete的"开始使用"中:
    prefs.edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
    onFinish()
}
```

### BUG-02: 下拉刷新明细数据

```kotlin
// PickDetailViewModel.kt refresh()
fun refresh() {
    viewModelScope.launch {
        _isRefreshing.value = true
        try {
            val detail = orderApiService.getOrderDetail(orderId)
            // 更新取货单信息
            pickOrderRepository.updateOrder(orderEntity)
            // 更新明细数据（逐条upsert）
            detail.items.forEach { itemResponse ->
                val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, itemResponse.skuOuterId)
                if (existing == null) {
                    // 新明细，插入
                    pickOrderRepository.insertItem(itemEntity)
                }
                // 已存在的明细由Room Flow自动更新
            }
            loadSuppliers()
        } catch (e: Exception) { ... }
        finally { _isRefreshing.value = false }
    }
}
```

### BUG-03: 图片上传实现

```kotlin
// ProductScreen.kt
// 使用ActivityResultLauncher
val pickImageLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri ->
    uri?.let {
        val file = uriToFile(it, context)
        if (file != null) {
            val imageType = pendingImageType ?: return@let
            viewModel.uploadImage(file, imageType)
            pendingImageType = null
        }
    }
}

// ImageUploadGrid的onClick改为:
onUploadArea = {
    pendingImageType = "area"
    pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
}
```

### BUG-04: Product路由添加orderId

```kotlin
// AppNavigation.kt
object Routes {
    const val PRODUCT = "product/{skuOuterId}?orderId={orderId}"
    fun productRoute(skuOuterId: String, orderId: Long = 0L): String =
        "product/$skuOuterId?orderId=$orderId"
}

// PickDetailScreen导航时:
navController.navigate(Routes.productRoute(skuOuterId, orderId = orderId))
```

### BUG-05: 连续扫码清空输入框

```kotlin
// PickDetailScreen.kt
// 监听扫码成功事件
LaunchedEffect(Unit) {
    viewModel.scanSuccessEvent.collect {
        viewModel.provideFeedback(context, ScanFeedbackType.SUCCESS)
        // 连续扫码模式下清空输入框并重新聚焦
        if (continuousScanMode) {
            scanInput = ""
            focusRequester.requestFocus()
        }
    }
}
```

### BUG-06: 重复扫码高亮滚动

```kotlin
// PickDetailScreen.kt
val listState = rememberLazyListState()

// 重复扫码时滚动到该行
LaunchedEffect(duplicateScan) {
    if (duplicateScan) {
        viewModel.provideFeedback(context, ScanFeedbackType.DUPLICATE)
        snackbarHostState.showSnackbar("重复扫码！该SKU已在当前取货单中")
        // 滚动到重复行
        val duplicateIndex = filteredItems.indexOfFirst { it.skuOuterId == lastScannedSku }
        if (duplicateIndex >= 0) {
            listState.animateScrollToItem(duplicateIndex)
        }
        viewModel.clearDuplicateScan()
    }
}
```

### BUG-07: SettingsScreen添加配置UI

在SettingsScreen中添加以下区域（在用户管理Card之前）：
- 服务器地址：OutlinedTextField + 保存按钮
- API Key：OutlinedTextField（PasswordVisualTransformation）+ 保存按钮
- 扫码方式：3个RadioButton（PDA硬件/相机/手动）
- 声音开关：Switch
- 振动开关：Switch

### BUG-08: 取货单按拣货区分组排序

```kotlin
// PickListScreen.kt
val sortedOrders = activeOrders.sortedWith(
    compareBy<PickOrderEntity> { extractAreaName(it.orderNo) }
        .thenByDescending { it.createdAt }
)
```

### BUG-09: OrderSyncWorker区分4xx/5xx

```kotlin
// OrderSyncWorker.kt syncOperation()
private suspend fun syncOperation(op: PendingOperationEntity): Boolean {
    return try {
        when (op.operationType) { ... }
    } catch (e: retrofit2.HttpException) {
        if (e.code() in 400..499) {
            // 客户端错误，标记冲突不再重试
            Log.e(TAG, "客户端错误${e.code()}，标记冲突: ${op.operationType}")
            pendingOperationDao.updateRetryCount(op.id, -1)
            true  // 从队列中移除
        } else {
            false  // 服务端错误，重试
        }
    } catch (e: Exception) {
        Log.e(TAG, "同步操作失败: ${op.operationType}, error=${e.message}")
        false
    }
}
```

### BUG-10: API失败回滚本地状态

```kotlin
// PickDetailViewModel.kt completeItem()
fun completeItem(itemId: Long) {
    viewModelScope.launch {
        try {
            orderApiService.completeItem(orderId, itemId)
            pickOrderRepository.updateItemStatus(itemId, 1, TimeUtils.now())
        } catch (e: Exception) {
            // API失败，回滚本地状态
            _errorMessage.value = "完成明细失败: ${e.message}"
        }
    }
}
```

注意：当前updateItemStatus内部是乐观更新（先更新本地再入队），在线模式下应改为先调API成功后再更新本地。但离线模式下需要乐观更新。解决方案：检查网络状态，在线时先API后本地，离线时乐观更新。

## 假设与决策

1. **BUG-03图片上传**：使用系统图片选择器（PickVisualMedia），不实现相机拍照（后续版本考虑）
2. **BUG-04路由参数**：orderId为可选参数，默认0，兼容从首页直接进入商品详情的场景
3. **BUG-06重复扫码高亮**：使用LazyListState滚动+临时背景色高亮，高亮2秒后恢复
4. **BUG-10 API失败回滚**：简化方案——在线模式先调API再更新本地（不使用乐观更新），离线模式使用乐观更新
5. **BUG-11相机扫码**：本次不实现，保留TODO标记

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 功能验证：
   - 引导页配置保存后，设置页能正确读取
   - 下拉刷新能看到其他PDA添加的明细
   - 商品详情页能选择图片并上传
   - 从取货详情进入商品详情能正确关联orderId
   - 连续扫码模式下输入框自动清空
   - 重复扫码时自动滚动到该行
   - 设置页能修改服务器地址/API Key/扫码方式
   - 取货列表按拣货区分组显示
