# 性能优化方案（最终版）

## 审查发现

### `_isLoading` 不影响 UI

`PickDetailViewModel._isLoading` 在 Screen 中**从未被 collect**。去掉它对性能无任何影响，不作改动。

### 真正瓶颈

| 场景 | 真实瓶颈 | 影响 |
|:-----|:---------|:-----|
| 扫码添加 | **等待 API 返回后才出现** → 用户以为卡 | 等待 ~1-2s 看不到商品 |
| 列表显示 | 每次 Room 发射 → 全列表重排序/过滤 → 每个可见 item 做 2 次 Room 查询 | 列表项多时帧率下降 |
| 商品详情 | `isLoading=true` → spinner → API 返回后才显示 | 白屏 ~1-2s |

---

## 优化方案（3项）

### 优化1：PickDetailViewModel — 扫码后立即显示"添加中"占位行

**核心思路**：在 ViewModel 维护一个独立的 `_pendingItems: StateFlow<List<String>>`，扫码后立即添加编码到该列表，Screen 侧立即显示占位行。API 返回后将其移除。

**不用 temp ID**：不创建临时 PickItemEntity，不操作 Room，不涉及 delete/insert 双发射。

**改动**：[PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt)

```kotlin
// 新增
private val _pendingItems = MutableStateFlow<List<String>>(emptyList())
val pendingItems: StateFlow<List<String>> = _pendingItems.asStateFlow()

fun onBarcodeScanned(barcode: String) {
    viewModelScope.launch {
        lastScannedSku = barcode
        try {
            // 查重
            val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, barcode)
            if (existing != null) {
                _duplicateScan.value = true
                return@launch
            }

            // 立即加入待处理列表（UI 瞬间显示占位行）
            _pendingItems.value = _pendingItems.value + barcode
            _scanSuccessEvent.emit(Unit)

            try {
                val token = userRepository.getToken()
                val response = orderApiService.addItem(token, orderId, AddOrderItemRequest(barcode))
                // Room 插入真实数据
                pickOrderRepository.insertItem(PickItemEntity(
                    id = response.id, orderId = orderId,
                    skuOuterId = response.skuOuterId,
                    sysItemId = response.sysItemId, sysSkuId = response.sysSkuId,
                    propertiesName = response.propertiesName, picPath = response.picPath,
                    status = response.status,
                    supplierName = response.supplierName, supplierCode = response.supplierCode,
                    remark = response.remark, itemOuterId = response.itemOuterId,
                    createdAt = TimeUtils.parseBeijingTime(response.createdAt)
                        .let { if (it > 0) it else TimeUtils.now() }
                ))
                val newSupplier = response.supplierName
                if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
                    _suppliers.value = _suppliers.value + newSupplier
                }
                _order.value = _order.value?.copy(totalCount = (_order.value?.totalCount ?: 0) + 1)
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 409) {
                    _errorMessage.value = null
                    syncItemsFromBackend()
                    _duplicateScan.value = true
                } else {
                    _errorMessage.value = "添加明细失败: ${e.message}"
                    _scanFailureEvent.emit("添加明细失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "添加明细失败: ${e.message}"
            _scanFailureEvent.emit("添加明细失败: ${e.message}")
        } finally {
            // 无论成功失败，从待处理列表中移除
            _pendingItems.value = _pendingItems.value - barcode
        }
    }
}
```

**注意**：查重在前，占位在后 → 重复 SKU 不会出现占位行然后被移除。

**改动**：[PickDetailScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt)

LazyColumn 中在 `filteredItems` 之前插入待处理行：

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize().weight(1f),
    state = listState
) {
    // GAP-XX: 扫码后立即显示的占位行（添加中）
    val pendingItems by viewModel.pendingItems.collectAsState()
    items(pendingItems) { barcode ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 20.dp, end = 12.dp).alpha(0.7f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧占位规格图（加载中动画）
                Box(
                    modifier = Modifier.size(90.dp, 90.dp)
                        .clip(RoundedCornerShape(8.dp)).background(SurfaceGray),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(12.dp))
                // 右侧显示编码 + "添加中..."
                Column(Modifier.weight(1f)) {
                    Text(barcode, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("添加中...", fontSize = 13.sp, color = TextMuted)
                }
            }
        }
    }

    // 原有真实数据列表
    items(items = filteredItems, key = { "${it.status}_${it.id}" }) { item ->
        PickItemRow(...)
    }
}
```

**效果**：扫码后占位行立即出现在顶部，API 到齐后占位行消失、真实行出现。用户连续扫码时每个编码都立即显示，非常流畅。

### 优化2：PickDetailViewModel — 图片URL批量加载（减少 LaunchedEffect 开销）

将每个 item 独立 LaunchedEffect × 2 次 Room 查询，改为 ViewModel 层 1 次批量加载。

**改动**：[PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt)

```kotlin
// 新增
private val _imageUrlsMap = MutableStateFlow<Map<String, ImageUrls>>(emptyMap())
val imageUrlsMap: StateFlow<Map<String, ImageUrls>> = _imageUrlsMap.asStateFlow()

// init 中新增
viewModelScope.launch {
    items.collectLatest { itemList ->
        val skus = itemList.map { it.skuOuterId }.distinct()
        val current = _imageUrlsMap.value
        val newSkus = skus.filter { it !in current }
        if (newSkus.isEmpty()) return@collectLatest
        // 仅对新 SKU 做批量加载
        val map = newSkus.associateWith { sku -> getImageUrls(sku) }
        _imageUrlsMap.value = current + map
    }
}
```

**改动**：[PickDetailScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt)

移除 LaunchedEffect 中的 `getImageUrls()` 和 4 个 `remember`：

```
原有（第297-309行）：
  val areaImageUrl by remember(item.skuOuterId) { mutableStateOf(...) }
  LaunchedEffect(item.skuOuterId) { val urls = viewModel.getImageUrls(sku); areaImageUrl = urls.areaUrl; ... }

改为：
  val imageUrlsMap by viewModel.imageUrlsMap.collectAsState()
  // item 内直接取
  val urls = imageUrlsMap[item.skuOuterId]
  val areaImageUrl = urls?.areaUrl
  val boxImageUrl = urls?.boxUrl
  val areaThumbUrl = urls?.areaThumbUrl
  val boxThumbUrl = urls?.boxThumbUrl
```

### 优化3：ProductViewModel — 立即显示条码，不设 isLoading

**改动**：[ProductViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L125)

```
改前：
  _uiState.value = _uiState.value.copy(isLoading = true, error = null)
  currentSkuDetail = null
  currentItem = null

改后：
  _uiState.value = _uiState.value.copy(
      isLoading = false,
      error = null,
      skuOuterId = skuOuterId     // ← 立即显示条码
  )
  currentSkuDetail = null
  currentItem = null
```

ProductScreen UI 逻辑不变：`isLoading=false` + `skuOuterId` 非空 → 直接显示内容区域。内容区域 `propertiesName.ifEmpty { skuOuterId }` 先显示编码，API 到齐后更新为名称。

出错分支也不用设 `isLoading = false`（初始就是 `false`）。

---

## 改动清单

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| PickDetailViewModel.kt | `onBarcodeScanned()` 改为 pending 优先 + 新增 `pendingItems` + 新增 `imageUrlsMap` 批量加载 | ~25 行新增，~10 行删除 |
| PickDetailScreen.kt | LazyColumn 新增 pending 占位行 + 移除 LaunchedEffect 图片逻辑 | ~30 行新增，~15 行删除 |
| ProductViewModel.kt | `loadSkuInfo()` 开头去掉 `isLoading=true`，补 `skuOuterId` | ~3 行修改 |

## 前置条件审查

| # | 检查项 | 结论 |
|:-:|:-------|:-----|
| 1 | `pendingItems` 并发安全？ | StateFlow 是线程安全的，append/remove 操作在同一个 coroutine 内顺序执行 |
| 2 | 重复扫码时 pending 状态？ | 查重在前（Room 查），重复则直接返回，pending 不含重复编码 |
| 3 | pending 行是否会干扰 `animateScrollToItem(0)`？ | `scrollToItem(0)` 跳到 LazyColumn 第 0 项 = pending 区域第一个。扫码后看到的是 pending 占位行，符合预期 |
| 4 | pending 行是否会干扰供应商过滤？ | pendingItems 独立于 `filteredItems`，始终显示在顶部，不受过滤影响。这样未完成的待处理行不会被过滤掉 |
| 5 | `_imageUrlsMap` 中 sku 删除后 map 是否清理？ | `collectLatest` 每次发射新列表，只在已有 map 中追加新 sku。删除的 sku 不会从 map 中移除，但没关系（仅有轻微内存残留） |
| 6 | `_imageUrlsMap` 并发竞态？ | `items` Flow 在同一个 coroutine 内收集，同一时间只有一个 `collectLatest` 在执行，无并发问题 |
| 7 | pending 行中扫码重复？ | 若 pending 行对应的 API 还没返回，用户又扫相同编码 → Room 查重，placeholder 已在 Room 中（不对，pending 没有插入 Room）。等一下——pending 只是 ViewModel 的列表，Room 里还没有。所以第二次扫描查重时 Room 里没有记录，会再次添加同一个 SKU。**这是个问题！** |
| 8 | 问题7怎么解决？ | 查重时除 Room 外，同时检查 `pendingItems` 中是否已有该编码。若有则标记为重复 |
| 9 | ProductScreen 无 isLoading 时，扫码进入商品详情会不会闪？ | 不会。`onScanBarcode()` 调用 `loadSkuInfo()` 立即设 `skuOuterId` → UI 显示条码 → 数据加载完成后替换为名称。无闪烁 |
| 10 | 不修改后端/数据库/PickItemRow | ✅ |

**问题7-8 修复补充**：`onBarcodeScanned()` 查重逻辑改为：

```kotlin
val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, barcode)
val pending = _pendingItems.value.contains(barcode)
if (existing != null || pending) {
    _duplicateScan.value = true
    return@launch
}
```

---

## 验证

1. 快速连续扫码 → 每个编码瞬间显示"添加中..."占位行，API 返回后自动替换为完整数据
2. 扫码重复 SKU → pending+Room 双查重，正常触发重复提示
3. 扫码 API 失败 → 占位行移除，显示错误提示
4. 扫30个商品后滚动 → 图片不卡顿
5. 商品详情页 → 立即显示条码，数据异步更新
6. lint 通过
