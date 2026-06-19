# 商品详情输入框自动聚焦 + 连续扫码修复方案

## 一、问题

1. 进入商品详情页，输入框没有自动获得焦点
2. 不支持 PDA 硬件扫码
3. 不支持连续扫码（扫完一个后自动清空 + 聚焦，可直接扫下一个）

## 二、根因

**ProductScreen.kt** 的 [ScanInputSection](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt#L313-L332) 中存在多个缺失：

| 功能 | ProductScreen | PickDetailScreen（参考实现） |
|:-----|:-------------|:---------------------------|
| FocusRequester 自动聚焦 | ❌ 无 | ✅ `LaunchedEffect(Unit) { focusRequester.requestFocus() }` |
| PDA 硬件扫码监听 | ❌ 无 | ✅ `scannerManager.scanResult.collectLatest { ... }` |
| 扫码后清空输入框 + 重新聚焦 | ❌ 无 | ✅ `scanInput = ""` + `focusRequester.requestFocus()` |
| ScannerManager 注入 ViewModel | ❌ 无 | ✅ `val scannerManager: ScannerManager` |

## 三、修复方案

### 改动1：ProductViewModel 注入 ScannerManager

**文件：[ProductViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt)**

```kotlin
// 改前
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pickItemDao: PickItemDao,
    private val productImageDao: ProductImageDao,
    private val pickOrderRepository: PickOrderRepository,
    private val imageRepository: ImageRepository,
    private val systemApiService: SystemApiService,
    @Named("encrypted") private val prefs: SharedPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

// 改后
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pickItemDao: PickItemDao,
    private val productImageDao: ProductImageDao,
    private val pickOrderRepository: PickOrderRepository,
    private val imageRepository: ImageRepository,
    private val systemApiService: SystemApiService,
    private val scannerManager: ScannerManager,
    @Named("encrypted") private val prefs: SharedPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
```

### 改动2：ProductScreen 添加 PDA 硬件扫码监听 + 连续扫码

**文件：[ProductScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt)**

在 Scaffold 之前添加 FocusRequester 和 LaunchedEffect：

```kotlin
// 添加 import
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

// 在 ProductScreen 函数体内（约 L100-L130 之间）
val uiState by viewModel.uiState.collectAsState()
val context = LocalContext.current

// 自动聚焦和连续扫码
val scanFocusRequester = remember { FocusRequester() }

// 自动聚焦输入框
LaunchedEffect(Unit) {
    scanFocusRequester.requestFocus()
}

// 监听PDA硬件扫码结果
LaunchedEffect(Unit) {
    viewModel.scannerManager.scanResult.collectLatest { barcode ->
        if (barcode.isNotEmpty()) {
            viewModel.onScanBarcode(barcode)
            viewModel.scannerManager.clearResult()
        }
    }
}
```

ScanInputSection 调用处传递 focusRequester：

```kotlin
// 改前
ScanInputSection(
    scanInput = uiState.scanInput,
    onScanInputChange = viewModel::updateScanInput,
    onConfirmScan = viewModel::confirmScanInput
)

// 改后
ScanInputSection(
    scanInput = uiState.scanInput,
    onScanInputChange = viewModel::updateScanInput,
    onConfirmScan = viewModel::confirmScanInput,
    focusRequester = scanFocusRequester
)
```

### 改动3：ScanInputSection 签名添加 FocusRequester 参数

```kotlin
// 改前
@Composable
private fun ScanInputSection(
    scanInput: String,
    onScanInputChange: (String) -> Unit,
    onConfirmScan: () -> Unit
) {
    OutlinedTextField(
        value = scanInput,
        onValueChange = onScanInputChange,
        label = { Text("扫码/输入SKU编码") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(20.dp))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onConfirmScan() })
    )
}

// 改后
@Composable
private fun ScanInputSection(
    scanInput: String,
    onScanInputChange: (String) -> Unit,
    onConfirmScan: () -> Unit,
    focusRequester: FocusRequester
) {
    OutlinedTextField(
        value = scanInput,
        onValueChange = onScanInputChange,
        label = { Text("扫码/输入SKU编码") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(20.dp))
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onConfirmScan() })
    )
}
```

### 改动4：ProductViewModel 中 onScanBarcode 完成后清空输入框（连续扫码支持）

```kotlin
// 改前
fun onScanBarcode(barcode: String) {
    if (barcode.isBlank()) return
    _uiState.value = _uiState.value.copy(scanInput = barcode)
    loadSkuInfo(barcode)
}

// 改后
fun onScanBarcode(barcode: String) {
    if (barcode.isBlank()) return
    _uiState.value = _uiState.value.copy(scanInput = barcode)
    loadSkuInfo(barcode)
    _uiState.value = _uiState.value.copy(scanInput = "")
}
```

### 改动5：确认扫码后也清空输入框（连续扫码支持）

```kotlin
// 改前
fun confirmScanInput() {
    val input = _uiState.value.scanInput.trim()
    if (input.isNotEmpty()) {
        loadSkuInfo(input)
    }
}

// 改后
fun confirmScanInput() {
    val input = _uiState.value.scanInput.trim()
    if (input.isNotEmpty()) {
        _uiState.value = _uiState.value.copy(scanInput = "")
        loadSkuInfo(input)
    }
}
```

> **注意**：`onScanBarcode` 和 `confirmScanInput` 中清空输入框后，UI 层的 `LaunchedEffect` 通过 `collectLatest` 会在下一次 PDA 扫码时自动重聚焦。如果是手动输入模式，用户点击输入框即可再次输入。

## 四、修改清单

| # | 文件 | 改动 |
|:--:|:------|:------|
| 1 | [ProductViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) | constructor 注入 `scannerManager: ScannerManager` |
| 2 | [ProductViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) | `onScanBarcode()` 清空 `scanInput` 支持连续扫码 |
| 3 | [ProductViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) | `confirmScanInput()` 清空 `scanInput` 支持连续手动输入 |
| 4 | [ProductScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt) | 新增 import: `LaunchedEffect`, `FocusRequester`, `focusRequester` |
| 5 | [ProductScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt) | 添加 `scanFocusRequester` + `LaunchedEffect(Unit)` 自动聚焦 + PDA扫码监听 |
| 6 | [ProductScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt) | ScanInputSection 调用处传递 `focusRequester` |
| 7 | [ProductScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt) | ScanInputSection 添加 `focusRequester` 参数，OutlinedTextField 添加 `.focusRequester()` |

## 五、验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. 安装到 PDA，进入商品详情页面
4. 确认输入框自动获得焦点（光标闪烁）
5. PDA 硬件扫码，确认自动输入并查询
6. 查询完成后输入框自动清空，可直接扫下一个条码
