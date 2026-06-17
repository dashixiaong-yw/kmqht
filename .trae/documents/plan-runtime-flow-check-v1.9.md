# v1.9 全面运行流程模拟检查计划

## 摘要

基于对前端（Kotlin/Compose）和后端（Python FastAPI）代码的全面审查，通过模拟6个核心运行流程发现 **3个P0关键Bug + 4个P1中等问题 + 3个P2轻微问题**。

## 当前状态分析

- 当前版本：v1.8（3处版本号一致）
- 上次检查：v1.8运行流程模拟检查，修复了3个问题
- 本次重点：模拟完整运行流程，检查bug、逻辑缺陷、运行流畅性

## 模拟流程与发现的问题

### 流程1: 登录流程 ✅ 基本正常
- AppNavigation → LoginScreen → login() → HomeScreen
- 强制改密流程正常
- 网络错误友好提示正常
- **无关键问题**

### 流程2: PDA扫码取货流程 ⚠️ 发现2个问题
- PickDetailScreen → 硬件扫码 → onBarcodeScanned → API/本地更新
- **BUG-01 (P0)**: `completeAllItems()` 和 `deleteItem()` 未设置 `isLoading` 状态
  - 文件: `PickDetailViewModel.kt` L219-243, L325-339
  - 影响: 用户可连续点击"全部完成"按钮，触发多次并发API调用
  - 对比: `completeItem()`/`restoreItem()` 正确设置了 `isLoading`
  - 修复: 添加 `_isLoading.value = true` 在try前，`finally { _isLoading.value = false }`

- **BUG-02 (P1)**: `scanFailureEvent` 使用 `collect` 而非 `collectLatest`
  - 文件: `PickDetailScreen.kt` L157-161
  - 影响: 快速连续扫码失败时可能丢失事件
  - 修复: `collect` → `collectLatest`

### 流程3: 商品详情流程 ⚠️ 发现2个问题
- ProductScreen → 扫码/输入SKU → 加载信息 → 编辑备注/供应商/图片
- **BUG-03 (P0)**: `ProductViewModel.loadImages()` Flow收集泄漏
  - 文件: `ProductViewModel.kt` L143-158
  - 影响: 每次调用 `loadSkuInfo()` 都会启动一个新的 `collectLatest` 协程，该协程永不结束（Flow是无限流）。多次扫码切换SKU后，多个收集器同时活跃，可能导致UI状态在旧SKU和新SKU之间闪烁
  - 修复: 使用 `Job` 跟踪收集协程，在新调用前取消旧的；或改用 `stateIn` 将Flow转为StateFlow

- **BUG-04 (P1)**: `AuthRepository` 重复定义 `KEY_USER_TOKEN` 常量
  - 文件: `AuthRepository.kt` L43
  - 影响: 与 `PrefsKeys.KEY_USER_TOKEN` 重复定义，虽然当前值相同（"user_token"），但违反v1.7统一常量的设计决策
  - 修复: 删除 `AuthRepository` 中的 `KEY_USER_TOKEN`，改用 `PrefsKeys.KEY_USER_TOKEN`

### 流程4: 离线/在线切换流程 ⚠️ 发现1个问题
- 在线操作失败 → 入队 → 网络恢复 → OrderSyncWorker同步
- **BUG-05 (P0)**: 图片上传无离线支持
  - 文件: `ProductViewModel.kt` L311-343
  - 影响: 离线时图片上传直接失败，不会入队等待网络恢复后重试。`OrderSyncWorker` 虽然有 `syncImageUpload` 方法，但前端从未调用入队操作，属于死代码路径
  - 修复: 在 `uploadImage()` 的catch块中，将图片文件保存到本地并创建 `upload_image` 类型的 PendingOperation

### 流程5: 会话过期流程 ⚠️ 发现1个问题
- HomeScreen → 检查session过期时间 → 显示警告 → Token刷新失败弹窗
- **BUG-06 (P1)**: `tokenRefreshFailed` 使用 `collect` 而非 `collectLatest`
  - 文件: `HomeScreen.kt` L111-117
  - 影响: 快速连续的token刷新失败事件可能丢失
  - 修复: `collect` → `collectLatest`

### 流程6: 取货单删除流程 ⚠️ 发现1个问题
- PickListScreen → 长按删除 → 确认弹窗 → API删除
- **BUG-07 (P1)**: 后端 `delete_order` 不检查订单状态
  - 文件: `backend/app/routers/orders.py` L339-375
  - 影响: 根据项目约束"允许删除任何未完成的取货单"，后端应限制只能删除未完成（status=0）的订单，但当前代码允许删除任何状态的订单
  - 修复: 添加 `if order_row["status"] == 1: raise HTTPException(status_code=400, detail="已完成的取货单不能删除")`

### 其他P2问题

- **CODE-01 (P2)**: `ProductScreen` 不监听PDA硬件扫码
  - 文件: `ProductScreen.kt`
  - 影响: 用户在商品详情页按PDA扫码键无响应，需手动输入
  - 修复: 添加 `LaunchedEffect` 监听 `scannerManager.scanResult`（需注入ScannerManager到ProductViewModel）

- **CODE-02 (P2)**: `PickDetailScreen` 相机扫码按钮为TODO空实现
  - 文件: `PickDetailScreen.kt` L253-264
  - 影响: 点击相机图标无任何响应
  - 修复: 暂时隐藏按钮或添加提示"暂未开放"

- **CODE-03 (P2)**: `completeAllItems()` catch块未调用 `loadOrder()`
  - 文件: `PickDetailViewModel.kt` L232-241
  - 影响: API失败后乐观更新了本地数据，但未刷新订单信息（如completedCount），可能导致进度显示不准确
  - 修复: 在catch块末尾添加 `loadOrder()`

## 修复方案

### BUG-01: completeAllItems/deleteItem 未设置isLoading

**文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt`

```kotlin
// completeAllItems() - L219
fun completeAllItems() {
    viewModelScope.launch {
        _isLoading.value = true  // 新增
        try {
            // ... 现有代码 ...
        } catch (e: Exception) {
            // ... 现有代码 ...
            loadOrder()  // CODE-03修复
        } finally {
            _isLoading.value = false  // 新增
        }
    }
}

// deleteItem() - L325
fun deleteItem(itemId: Long) {
    viewModelScope.launch {
        _isLoading.value = true  // 新增
        try {
            // ... 现有代码 ...
        } catch (e: Exception) {
            // ... 现有代码 ...
        } finally {
            _isLoading.value = false  // 新增
        }
    }
}
```

### BUG-02: scanFailureEvent改用collectLatest

**文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt` L158

```kotlin
// 修改前
viewModel.scanFailureEvent.collect { message ->
// 修改后
viewModel.scanFailureEvent.collectLatest { message ->
```

### BUG-03: ProductViewModel.loadImages() Flow收集泄漏

**文件**: `app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt`

```kotlin
// 新增: 跟踪图片加载协程
private var imagesJob: kotlinx.coroutines.Job? = null

// 修改 loadImages()
private suspend fun loadImages(skuOuterId: String) {
    // 取消之前的收集协程
    imagesJob?.cancel()
    imagesJob = viewModelScope.launch {
        try {
            val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
            productImageDao.getBySkuOuterId(skuOuterId).collectLatest { images ->
                val areaImage = images.find { it.imageType == "area" }
                val boxImage = images.find { it.imageType == "box" }
                _uiState.value = _uiState.value.copy(
                    areaImageUrl = areaImage?.let { "$serverUrl${it.imageUrl}" },
                    boxImageUrl = boxImage?.let { "$serverUrl${it.imageUrl}" }
                )
            }
        } catch (e: Exception) {
            Log.w("ProductViewModel", "加载SKU图片失败: ${e.message}")
        }
    }
}
```

注意: `loadImages` 当前是 `private suspend`，需要改为 `private` (非suspend)，因为现在它只是启动一个协程而不等待结果。同时 `loadSkuInfo` 中调用 `loadImages(skuOuterId)` 不再需要是suspend调用。

### BUG-04: AuthRepository重复常量

**文件**: `app/src/main/java/com/kuaimai/pda/data/repository/AuthRepository.kt`

```kotlin
// 删除 companion object 中的 KEY_USER_TOKEN
// 修改 refreshSession() 中的引用
val userToken = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""
```

### BUG-05: 图片上传离线支持

**文件**: `app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt`

在 `uploadImage()` 的catch块中添加离线入队逻辑：

```kotlin
fun uploadImage(imageFile: File, imageType: String) {
    val skuOuterId = _uiState.value.skuOuterId
    if (skuOuterId.isBlank()) return

    _uiState.value = _uiState.value.copy(isUploading = true, uploadProgress = 0)
    viewModelScope.launch {
        try {
            val imageUrl = imageRepository.uploadImage(imageFile, imageType, skuOuterId)
            // ... 现有成功逻辑 ...
        } catch (e: Exception) {
            // 离线支持：将图片复制到持久目录并入队
            try {
                val pendingDir = File(context.filesDir, "pending_images")
                pendingDir.mkdirs()
                val pendingFile = File(pendingDir, "${skuOuterId}_${imageType}_${System.currentTimeMillis()}.jpg")
                imageFile.copyTo(pendingFile, overwrite = true)

                val payload = """{"sku_outer_id":"${skuOuterId}","image_type":"${imageType}","file_path":"${pendingFile.absolutePath}"}"""
                pickOrderRepository.enqueueUploadImage(skuOuterId, payload)

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "图片将在网络恢复后自动上传"
                )
            } catch (queueError: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "上传图片失败: ${e.message}"
                )
            }
        }
    }
}
```

需要在 `PickOrderRepository` 接口和实现中添加 `enqueueUploadImage` 方法：

```kotlin
// PickOrderRepository 接口
suspend fun enqueueUploadImage(skuOuterId: String, payload: String)

// PickOrderRepositoryImpl 实现
override suspend fun enqueueUploadImage(skuOuterId: String, payload: String) {
    enqueueOperation(
        operationType = "upload_image",
        orderId = 0L,  // 图片上传不关联特定订单
        targetId = 0L,
        payload = payload
    )
}
```

注意: ProductViewModel 需要注入 Context（Application context）来访问 filesDir。可通过 `@ApplicationContext` 注入。

### BUG-06: tokenRefreshFailed改用collectLatest

**文件**: `app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt` L113

```kotlin
// 修改前
authRepository.tokenRefreshFailed.collect {
// 修改后
authRepository.tokenRefreshFailed.collectLatest {
```

### BUG-07: 后端delete_order添加状态检查

**文件**: `backend/app/routers/orders.py` L339-375

```python
@router.delete("/{order_id}", response_model=BaseResponse)
def delete_order(order_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """删除取货单（仅允许删除未完成的取货单）"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT id, status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if not order_row:
        raise HTTPException(status_code=404, detail="取货单不存在")

    # 新增: 不允许删除已完成的取货单
    if order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="已完成的取货单不能删除")

    # ... 后续删除逻辑不变 ...
```

### CODE-01: ProductScreen监听PDA硬件扫码

**文件**: `app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt`

需要在ProductViewModel中注入ScannerManager，并在ProductScreen中添加LaunchedEffect监听。由于这涉及修改ViewModel的构造函数（Hilt依赖注入），改动较大，标记为P2延后处理。

### CODE-02: 相机扫码按钮TODO

**文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt`

暂时保留TODO，添加视觉提示（灰色或禁用状态），避免用户误点无响应。

### CODE-03: completeAllItems catch块添加loadOrder()

已合并到BUG-01的修复方案中。

## 假设与决策

1. **BUG-05图片离线支持**：需要注入Application Context到ProductViewModel，使用 `@ApplicationContext` 注解
2. **BUG-03 Flow收集泄漏**：选择使用Job跟踪+取消方案，而非stateIn方案，因为改动更小
3. **CODE-01硬件扫码**：标记P2延后，因为需要修改ViewModel构造函数
4. **CODE-02相机扫码**：标记P2延后，功能未实现仅UI占位

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 3处版本号一致（build.gradle.kts + CHANGELOG.md + gradle.properties）
4. 知识图谱已更新
5. docker-deploy已同步
6. Git提交推送完成
