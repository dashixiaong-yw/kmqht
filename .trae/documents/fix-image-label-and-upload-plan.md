# Bug1 标签截断 + Bug2 图片上传 + Bug3 点击跳转 + Bug4 原型对齐 修复方案

## 一、Bug1: 取货单详情「规格图」「库区」「箱图」文字截断

### 根因

[PickItemRow.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt) 中三个图片底部标签 Box 高度不足以容纳 10sp 文字的自然行高，导致**垂直方向裁切**：

| 标签 | Box 高度 | 10sp 文字自然行高 | 差值 | 截断程度 |
|:-----|:--------|:-----------------|:----|:--------|
| "规格图" | **14dp** | ~15-16dp | -1~2dp | 上下轻微裁切 |
| "库区" | **12dp** | ~15-16dp | **-3~4dp** | 明显裁切 |
| "箱图" | **12dp** | ~15-16dp | **-3~4dp** | 明显裁切 |

**不是宽度问题**——三个 Box 都是 56dp 宽，中文"规格图"3字约需 30dp，"库区/箱图"2字约需 20dp，宽度充足。

### 修复

**文件：**[PickItemRow.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt)

需同时修复三个问题：**标签截断**、**布局对齐原型**、**按钮尺寸对齐原型**。

#### 改动1：标签高度 + 防御性约束

三处标签 Box 的 `height` 改为 **18dp**，并添加 `maxLines = 1` + `overflow = TextOverflow.Ellipsis`。

详见下方完整布局改动中标注 `[标签]` 的部分。

#### 改动2：右侧布局对齐原型（Column: 图片在上，按钮在下）

**原型结构**：
```
item-actions（垂直 Column）
├── item-img-boxes (Row: 库区图 40×40 + 箱图 40×40)
└── .btn-complete (小按钮: padding 4px 14px, 圆角, 非正方形)
```

**当前代码问题**：
- 库区图/箱图/完成按钮 平铺在同一行 Row 中，行高80dp
- 图片 56×56 偏大（原型 40×40）
- 完成/恢复按钮 56×56 巨大（原型小按钮）

**修改方案**：右侧改为 Column，上方 Row(库区+箱图)，下方 Button(完成/恢复)。图片缩小为 44dp，按钮改为小尺寸（padding 6px 12dp, fontSize 13sp）。

#### 完整新代码（整个函数重写）

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickItemRow(
    item: PickItemEntity,
    onComplete: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
    onImageClick: () -> Unit = {},
    areaImageUrl: String? = null,
    boxImageUrl: String? = null,
    onAreaImageClick: () -> Unit = {},
    onBoxImageClick: () -> Unit = {}
) {
    val isCompleted = item.status == 1
    val contentAlpha = if (isCompleted) 0.55f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .combinedClickable(
                onClick = { },
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧：规格图（带底部标注）
            Box(
                modifier = Modifier
                    .clickable { onImageClick() }
                    .size(width = 52.dp, height = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceGray),
                contentAlignment = Alignment.Center
            ) {
                if (item.picPath.isNotEmpty()) {
                    AsyncImage(
                        model = item.picPath,
                        contentDescription = "规格图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = "规格图", fontSize = 9.sp, color = TextMuted)
                }
                // [标签] 规格图
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .background(SurfaceGray.copy(alpha = 0.8f))
                        .align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "规格图",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 中间：规格名 + 供应商名
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.propertiesName.ifEmpty { item.skuOuterId },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.supplierName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SupplierRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 右侧：图片 + 按钮（垂直排列，对齐原型 item-actions）
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 第一行：库区图 + 箱图（item-img-boxes）
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 库区图
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceGray)
                            .clickable { onAreaImageClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!areaImageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = areaImageUrl,
                                contentDescription = "库区图",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(text = "库区", fontSize = 9.sp, color = TextMuted)
                        }
                        // [标签] 库区
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(SurfaceGray.copy(alpha = 0.8f))
                                .align(Alignment.BottomCenter),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "库区",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 箱图
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceGray)
                            .clickable { onBoxImageClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!boxImageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = boxImageUrl,
                                contentDescription = "装箱图",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(text = "箱图", fontSize = 9.sp, color = TextMuted)
                        }
                        // [标签] 箱图
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(SurfaceGray.copy(alpha = 0.8f))
                                .align(Alignment.BottomCenter),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "箱图",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 第二行：完成/恢复按钮（对齐原型 .btn-complete）
                if (isCompleted) {
                    TextButton(
                        onClick = onRestore,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 56.dp)
                    ) {
                        Text("恢复", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    TextButton(
                        onClick = onComplete,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = SuccessBg,
                            contentColor = SuccessText
                        ),
                        modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 56.dp)
                    ) {
                        Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
```

> **新增 import**（上述代码需的缺失 import）：
> ```kotlin
> import androidx.compose.foundation.layout.Arrangement
> import androidx.compose.material3.ButtonDefaults
> import androidx.compose.material3.TextButton
> ```

---

## 二、Bug2: 商品详情页库区图/箱图上传一直失败

### 根因

**两根因：**

**① 异常被完全吞掉（主要）**：`ProductViewModel.uploadImage()` 的 catch 块中，**无论什么异常都走离线队列入队逻辑**，用户永远看到 "图片将在网络恢复后自动上传"。而且没有任何 `Log.e` 日志，真实错误完全不可见。

**② ImageUploadService 的 OkHttpClient 缺少 SSL 绕过（核心）**：`ImageUploadService` 注入的 OkHttpClient 没有 `.sslSocketFactory(unsafeSslSocketFactory, unsafeTrustManager)` 和 `.hostnameVerifier { _, _ -> true }`。服务器使用 FRP 隧道，证书很可能是自签的，而 Retrofit 后端 API 用的是另一个带 SSL 绕过的客户端（`trustAllClient`），唯独图片上传缺了 SSL 绕过配置。

### 完整上传链路（问题节点标注）

```
ProductScreen 选择图片 → uriToFile()
  → viewModel.uploadImage(file, type)
    → imageRepository.uploadImage(file, type, skuOuterId)
      → ImageCompressor.compress(file)
      → uploadService.uploadImage(file, type, skuOuterId)
        → OkHttp POST $serverUrl/api/upload
           ⚡ SSLHandshakeException (自签证书)
        → repeat(3) 重试 → 全部失败
    → catch (e: Exception)
      → ⚡ 无条件入离线队列, Log.e 都没有
      → infoMessage = "图片将在网络恢复后自动上传"
```

**为什么取货单 API 正常但图片上传不行？**

`NetworkModule.kt` 中有两个 OkHttpClient：
- Retrofit 用的 `trustAllClient` = 基础 client + **unsafeSSL** + trustAllHostname → ✅ 取货单/用户API正常
- `ImageUploadService` 注入的是**基础 client**（无 unsafeSSL）→ ❌ 自签证书 → SSLHandshakeException

### 修复

**文件1：**[ImageUploadService.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/ImageUploadService.kt) + [NetworkModule.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt)

`ImageUploadService` 使用 `@Inject constructor` 注入 `OkHttpClient`，该 OkHttpClient 没有 SSL 绕过。需要让 ImageUploadService 使用带 SSL 绕过的客户端。

**方案**：在 NetworkModule 中新增 `@Named("trustAll")` 的 OkHttpClient provider，ImageUploadService 的构造参数添加 `@Named("trustAll")` 限定：

NetworkModule.kt 新增 provider：

```kotlin
@Provides
@Singleton
@Named("trustAll")
fun provideTrustAllOkHttpClient(
    kuaimaiInterceptor: KuaimaiInterceptor,
    loggingInterceptor: HttpLoggingInterceptor,
    tokenAuthenticator: TokenAuthenticator
): OkHttpClient {
    return OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(kuaimaiInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .hostnameVerifier { _, _ -> true }
        .sslSocketFactory(unsafeSslSocketFactory, unsafeTrustManager)
        .build()
}
```

ImageUploadService.kt 构造参数加 `@Named("trustAll")`：

```kotlin
// 改前
class ImageUploadService @Inject constructor(
    private val client: OkHttpClient,
    @Named("encrypted") private val encryptedPrefs: SharedPreferences
)

// 改后
class ImageUploadService @Inject constructor(
    @Named("trustAll") private val client: OkHttpClient,
    @Named("encrypted") private val encryptedPrefs: SharedPreferences
)
```

> 同时需要在 ImageUploadService.kt 顶部新增 `import javax.inject.Named`（已存在则跳过）

**文件2：**[ProductViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt)

修复异常吞没问题——区分网络异常和业务异常，添加 `Log.e` 日志：

```kotlin
// 改前 (L367-388)
} catch (e: Exception) {
    // 离线支持：将图片复制到持久目录并入队
    try {
        val pendingDir = File(appContext.filesDir, "pending_images")
        ...
        val payload = """..."""
        pickOrderRepository.enqueueUploadImage(skuOuterId, payload)
        _uiState.value = _uiState.value.copy(
            isUploading = false,
            infoMessage = "图片将在网络恢复后自动上传"
        )
    } catch (queueError: Exception) {
        _uiState.value = _uiState.value.copy(
            isUploading = false,
            error = "上传图片失败: ${e.message}"
        )
    }
}

// 改后
} catch (e: IOException) {
    // 网络类异常 → 入离线队列，等待网络恢复后重试
    Log.e(TAG, "图片上传网络异常，入离线队列: ${e.message}", e)
    try {
        val pendingDir = File(appContext.filesDir, "pending_images")
        ...
        val payload = """..."""
        pickOrderRepository.enqueueUploadImage(skuOuterId, payload)
        _uiState.value = _uiState.value.copy(
            isUploading = false,
            infoMessage = "图片将在网络恢复后自动上传"
        )
    } catch (queueError: Exception) {
        _uiState.value = _uiState.value.copy(
            isUploading = false,
            error = "上传图片失败: ${e.message}"
        )
    }
} catch (e: Exception) {
    // 非网络异常（如 403/400/500）→ 直接展示错误，不入队
    Log.e(TAG, "图片上传失败: ${e.message}", e)
    _uiState.value = _uiState.value.copy(
        isUploading = false,
        error = "上传图片失败: ${e.message ?: "未知错误"}"
    )
}
```

> 需要在 ProductViewModel.kt 顶部添加 `import android.util.Log` 和 `import java.io.IOException`

**文件3：**[ProductScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt)

上传失败后 `infoMessage` 显示为红色错误卡片而非蓝色信息卡片，不用改动代码——现有 UI 已正确处理 `error` 为黄色警告卡片：

```kotlin
// ProductScreen.kt L260-277 — infoMessage 蓝色卡片
Row { Icon + Text(uiState.infoMessage) ... }

// ProductScreen.kt L279-298 — error 黄色警告卡片
Row { Icon + Text(uiState.error) ... }
```

---

## 四、Bug3: 取货单详情中库区图/箱图点击后跳转到商品详情页（非预期行为）

### 根因

[PickDetailScreen.kt:L340-L344](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt#L340-L344) 三个图片点击事件全部设为导航到商品详情页：

```kotlin
onImageClick = { onNavigateToProduct(item.skuOuterId) },
onAreaImageClick = { onNavigateToProduct(item.skuOuterId) },  // ← 应显示大图
onBoxImageClick = { onNavigateToProduct(item.skuOuterId) }    // ← 应显示大图
```

规格图导航是合理的，但**库区图和箱图**点击后应显示原图大图预览（如原型中的 `showModal` 行为）。

### 修复

**文件：**[PickDetailScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt)

新增图片大图预览 Dialog + 修改 click handler：

```kotlin
// 新增：图片预览弹窗状态（添加到 var showDeleteConfirm 附近）
var previewImageUrl by remember { mutableStateOf<String?>(null) }
var previewImageLabel by remember { mutableStateOf("") }

// 新增：大图预览弹窗（添加到 AlertDialog 删除确认弹窗附近）
previewImageUrl?.let { url ->
    AlertDialog(
        onDismissRequest = { previewImageUrl = null },
        title = { Text(previewImageLabel) },
        text = {
            AsyncImage(
                model = url,
                contentDescription = previewImageLabel,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        },
        confirmButton = {
            TextButton(onClick = { previewImageUrl = null }) {
                Text("关闭")
            }
        }
    )
}

// 修改 PickItemRow 调用的 click handler (L340-L344)
PickItemRow(
    ...
    onImageClick = { onNavigateToProduct(item.skuOuterId) },  // 规格图保持导航
    ...
    onAreaImageClick = {
        if (areaImageUrl != null) {
            previewImageUrl = areaImageUrl
            previewImageLabel = "库区图 - ${item.skuOuterId}"
        }
    },
    onBoxImageClick = {
        if (boxImageUrl != null) {
            previewImageUrl = boxImageUrl
            previewImageLabel = "箱图 - ${item.skuOuterId}"
        }
    }
)
```

> **新增 import**：
> ```kotlin
> import coil.compose.AsyncImage
> import androidx.compose.ui.layout.ContentScale
> ```
> （如已有则跳过）

---

## 五、Bug4: 原型首页对比遗漏项修复

并行搜索对比原型与 Android 端实现，发现以下关键差异：

### 差异1: Logo 图标框缺失

**文件：**[HomeScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt)

原型首页顶部有 56×56 蓝色圆角方块图标框，当前仅纯文字。需在标题前添加图标框：

```kotlin
Box(
    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(PrimaryLightBg),
    contentAlignment = Alignment.Center
) { Text("📦", fontSize = 28.sp) }
```

### 差异2: 模块卡片图标背景色不一致 + 缺少可见边框

**文件：**[HomeScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) - ModuleCard 函数

原型中三个模块卡片有 **白色底色 + 1px 灰色边框 + box-shadow 阴影**，图标框颜色分别为蓝/红/灰。

**当前问题**：
- 所有卡片图标框统一蓝色
- 卡片缺少可见边框（白色卡片在白色背景上无边界感）

**修复**：
1. ModuleCard 添加 `iconBgColor: Color` 参数，调用侧传不同色值
2. Card 添加 `border(1.dp, BorderGray, RoundedCornerShape(12.dp))` 和 `elevation`

```kotlin
@Composable
private fun ModuleCard(
    title: String,
    description: String,
    iconBgColor: Color,          // ← 新增
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),  // ← 新增边框
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),  // ← 新增阴影
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(...) {
            Column(
                modifier = Modifier.background(iconBgColor)  // ← iconBgColor 替代 PrimaryLightBg
                ...
            ) { icon() }
            ...
        }
    }
}

// 调用处
ModuleCard("取货列表", "查看和管理取货单", PrimaryLightBg, { List图标 }) { ... }
ModuleCard("商品详情", "扫码查看规格信息", DangerBg, { Search图标 }) { ... }
ModuleCard("设置", "扫码方式、反馈开关", SurfaceGray, { Settings图标 }) { ... }
```

> 需要新增 import：`import androidx.compose.foundation.border`、`import com.kuaimai.pda.ui.theme.DangerBg`、`import com.kuaimai.pda.ui.theme.BorderGray`

### 差异3: 模块描述文案

| 模块 | 当前 | 修正为 |
|------|------|--------|
| 商品详情 | "查看商品信息和图片" | "扫码查看规格信息" |
| 设置 | "扫码方式、反馈开关" | **不修改** |

---

## 六、修改清单

| # | 文件 | 改动 | Bug |
|:--:|:-----|:------|:---:|
| 1 | [PickItemRow.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt) | 完整重写布局：标签18dp + 右侧Column(图片Row+按钮Button)对齐原型 + 图片44dp + 按钮TextButton | 1 |
| 2 | [NetworkModule.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt) | 新增 `provideTrustAllOkHttpClient` provider（含 SSL 绕过） | 2 |
| 3 | [ImageUploadService.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/ImageUploadService.kt) | 构造参数 `client` → `@Named("trustAll") client` | 2 |
| 4 | [ProductViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) | 异常分级（IOException→入队, 其他→error）+ Log.e日志 | 2 |
| 5 | [PickDetailScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt) | 新增图片大图预览Dialog + 库区图/箱图click改为显示大图 | 3 |
| 6 | [HomeScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) | 新增Logo图标框 + 模块卡片图标背景色(红/灰) + 文案对齐原型 | 4 |

## 七、验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. Bug1: 取货单详情中"规格图""库区""箱图"标签文字完整显示，完成/恢复按钮在第二行
4. Bug2: 商品详情页上传库区图/箱图，确认上传成功
5. Bug3: 取货单详情点击库区图/箱图，弹出大图预览弹窗
6. Bug4: 首页显示Logo图标框 + 模块卡片图标颜色正确(蓝/红/灰) + 描述文案正确
