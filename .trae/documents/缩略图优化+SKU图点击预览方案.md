# 缩略图优化 + SKU图点击预览方案

## 一、UI 布局调整

### 当前 → 修改后

```
┌──────────┐  propertiesName    ┌────┐ ┌────┐     ┌──────────┐  propertiesName   ┌────┐ ┌────┐
│ 90×90    │  (→商品详情)       │ 44 │ │ 44 │  →  │ 90×90    │  (→商品详情)      │ 32 │ │ 32 │
│ SKU pic  │  supplierName      │库区│ │箱图│     │ SKU pic  │  supplierName     │库区│ │箱图│
│ (→商品)  │                     │    │ │    │     │ (→预览)  │                    │缩略│ │缩略│
└──────────┘                     └────┘ └────┘     └──────────┘                    └────┘ └────┘
```

### 点击行为变更

| 操作 | 当前 | 修改后 |
|:-----|:-----|:-------|
| 点击 SKU 图（左侧 90×90） | → 跳转商品详情页 | → **大图预览（阿里CDN原图）** |
| 点击 propertiesName 文字 | 无反应 | → **跳转商品详情页** |
| 点击库区图/箱图（右侧 44dp） | → 大图预览 | 不变 |

---

## 二、修改内容

### 修改 1（后端）：上传库区/箱图时自动生成缩略图

**文件**：`backend/app/routers/images.py`

**新增 import**（文件顶部）：

```python
from PIL import Image
```

**新增函数**（放在 upload_image 之前或文件末尾）：

```python
def _generate_thumbnail(file_path: str) -> None:
    """生成200px缩略图，失败不影响主流程"""
    try:
        img = Image.open(file_path)
        img.thumbnail((200, 200), Image.LANCZOS)
        base, _ = os.path.splitext(file_path)
        thumb_path = f"{base}_thumb.jpg"
        img.convert("RGB").save(thumb_path, "JPEG", quality=50)
        logger.info(f"缩略图生成成功: {os.path.basename(thumb_path)}")
    except Exception as e:
        logger.warning(f"生成缩略图失败: {e}")
```

**在 upload_image() 中调用**（写入文件后、DB 操作前）：

```python
    # 写入全尺寸文件...  （已有）
    with open(file_path, "wb") as f:
        f.write(content)
    # 生成缩略图（新增）
    _generate_thumbnail(file_path)
    # 文件写入成功，操作DB...  （已有）
```

缩略图命名示例：`B08-24_area_a1b2c3d4.jpg` → `B08-24_area_a1b2c3d4_thumb.jpg`（同目录）

---

### 修改 2（Android）：PickDetailViewModel 提供缩略图 URL

**文件**：`PickDetailViewModel.kt`

**新增 data class**（与其他 data class 一起放在文件顶部）：

```kotlin
data class ImageUrls(
    val areaUrl: String?,
    val boxUrl: String?,
    val areaThumbUrl: String?,
    val boxThumbUrl: String?
)
```

**新增辅助方法**（放在 getImageUrls 旁边）：

```kotlin
/** 从完整URL构造缩略图URL（在扩展名前插入 _thumb） */
private fun buildThumbUrl(fullUrl: String?): String? {
    if (fullUrl == null) return null
    val dot = fullUrl.lastIndexOf('.')
    return if (dot > 0) "${fullUrl.substring(0, dot)}_thumb${fullUrl.substring(dot)}" else fullUrl
}
```

**修改 getImageUrls() 返回类型和实现**（当前 L471-L483）：

```kotlin
suspend fun getImageUrls(skuOuterId: String): ImageUrls {
    return try {
        val areaImage = imageRepository.getImageBySkuAndType(skuOuterId, AppConstants.IMAGE_TYPE_AREA)
        val boxImage = imageRepository.getImageBySkuAndType(skuOuterId, AppConstants.IMAGE_TYPE_BOX)
        val serverUrl = prefs.getString(PrefsKeys.KEY_SERVER_URL, AppConstants.DEFAULT_SERVER_URL)?.trim() ?: AppConstants.DEFAULT_SERVER_URL
        val areaUrl = areaImage?.let { url -> if (serverUrl.isNotEmpty()) "${serverUrl.trimEnd('/')}/${url.imageUrl}" else url.imageUrl }
        val boxUrl = boxImage?.let { url -> if (serverUrl.isNotEmpty()) "${serverUrl.trimEnd('/')}/${url.imageUrl}" else url.imageUrl }
        ImageUrls(
            areaUrl = areaUrl,
            boxUrl = boxUrl,
            areaThumbUrl = buildThumbUrl(areaUrl),
            boxThumbUrl = buildThumbUrl(boxUrl)
        )
    } catch (e: Exception) {
        Log.w("PickDetailViewModel", "获取图片URL失败: ${e.message}")
        ImageUrls(null, null, null, null)
    }
}
```

---

### 修改 3（Android）：PickItemRow 新参数 + 点击分离 + 缩略图

**文件**：`PickItemRow.kt`

**函数签名修改**（当前 L52-L61）：

```kotlin
// 修改前
fun PickItemRow(
    item: PickItemEntity,
    onComplete: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
    onImageClick: () -> Unit = {},       // ← 删除
    areaImageUrl: String? = null,
    boxImageUrl: String? = null,
    onAreaImageClick: () -> Unit = {},
    onBoxImageClick: () -> Unit = {}
)

// 修改后
fun PickItemRow(
    item: PickItemEntity,
    onComplete: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
    onSkuImageClick: () -> Unit = {},    // ← 新增：SKU图→大图预览
    onSkuNameClick: () -> Unit = {},     // ← 新增：文字→跳转商品
    areaImageUrl: String? = null,
    boxImageUrl: String? = null,
    areaThumbUrl: String? = null,         // ← 新增
    boxThumbUrl: String? = null,          // ← 新增
    onAreaImageClick: () -> Unit = {},
    onBoxImageClick: () -> Unit = {}
)
```

**具体行修改**：

| 位置 | 当前代码 | 修改为 |
|:-----|:---------|:-------|
| L86 | `.clickable { onImageClick() }` | `.clickable { onSkuImageClick() }` |
| L94 | `model = item.picPath` | 不变（SKU图保持CDN直连） |
| L106 | `Column(modifier = Modifier.weight(1f))` | `Column(modifier = Modifier.weight(1f).clickable { onSkuNameClick() })` |
| L137 | `.size(44.dp)` | `.size(32.dp)` |
| L145 | `model = areaImageUrl` | `model = areaThumbUrl` |
| L155 | `.size(44.dp)` | `.size(32.dp)` |
| L163 | `model = boxImageUrl` | `model = boxThumbUrl` |

---

### 修改 4（Android）：PickDetailScreen 传递新参数 + SKU预览

**文件**：`PickDetailScreen.kt`

**新增状态变量**（在 L292-L298 附近）：

```kotlin
var areaThumbUrl by remember { mutableStateOf<String?>(null) }
var boxThumbUrl by remember { mutableStateOf<String?>(null) }
var previewPicImageUrl by remember { mutableStateOf<String?>(null) }
```

**修改 LaunchedEffect**（L294-L298）：

```kotlin
LaunchedEffect(item.skuOuterId) {
    val urls = viewModel.getImageUrls(item.skuOuterId)
    areaImageUrl = urls.areaUrl
    boxImageUrl = urls.boxUrl
    areaThumbUrl = urls.areaThumbUrl      // ← 新增
    boxThumbUrl = urls.boxThumbUrl        // ← 新增
}
```

**修改 PickItemRow 调用**（L300-L320 替换为）：

```kotlin
PickItemRow(
    item = item,
    onComplete = { viewModel.completeItem(item.id) },
    onRestore = { viewModel.restoreItem(item.id) },
    onLongPress = { showDeleteConfirm = item },
    onSkuNameClick = { onNavigateToProduct(item.skuOuterId) },   // ← 文字→跳转商品
    onSkuImageClick = {                                            // ← SKU图→大图预览
        if (item.picPath.isNotEmpty()) {
            previewPicImageUrl = item.picPath
        }
    },
    areaImageUrl = areaImageUrl,
    boxImageUrl = boxImageUrl,
    areaThumbUrl = areaThumbUrl,
    boxThumbUrl = boxThumbUrl,
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

**新增 SKU 图预览弹窗**（放在已有预览弹窗 L412-L432 之后）：

```kotlin
// SKU图大图预览
previewPicImageUrl?.let { url ->
    AlertDialog(
        onDismissRequest = { previewPicImageUrl = null },
        shape = RoundedCornerShape(16.dp),
        title = { Text("SKU图") },
        text = {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = "SKU图",
                modifier = Modifier.fillMaxWidth(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        },
        confirmButton = {
            TextButton(onClick = { previewPicImageUrl = null }) {
                Text("关闭")
            }
        }
    )
}
```

---

## 三、修改文件清单

| 文件 | 行数 | 修改类型 |
|:-----|:----:|:---------|
| `backend/app/routers/images.py` | ~20行 | 新增 `_generate_thumbnail` + import + 调用 |
| `PickDetailViewModel.kt` | ~25行 | 新增 `ImageUrls` data class + `buildThumbUrl` + 修改 `getImageUrls` |
| `PickItemRow.kt` | ~13行 | 签名2参替换+2新参+model替换2处+clickable 1处 |
| `PickDetailScreen.kt` | ~18行 | 新增3状态变量+修改LaunchedEffect+修改PickItemRow调用+新增SKU预览弹窗 |

---

## 四、方案完整性逐行验证

### 4.1 后端 images.py

| 检查 | 代码位置 | 结果 |
|:-----|:---------|:----:|
| `from PIL import Image` 新增 | 文件顶部 | ✅ 独立于其他 import |
| `_generate_thumbnail` 函数 | 新增 | ✅ 含 try-catch |
| `img.thumbnail((200,200), LANCZOS)` | 函数内 | ✅ Lanczos 高质量缩略 |
| `save(thumb_path, "JPEG", quality=50)` | 函数内 | ✅ JPEG 压缩 |
| logger.info 成功日志 | 函数内 | ✅ |
| logger.warning 失败日志 | catch 内 | ✅ |
| 调用时机在写文件后 DB 前 | upload_image() | ✅ |

### 4.2 PickDetailViewModel.kt

| 检查 | 代码位置 | 结果 |
|:-----|:---------|:----:|
| `ImageUrls` 4个字段 | data class | ✅ area/box + thumb |
| `buildThumbUrl(null)` 返回 null | 方法内 | ✅ 空安全 |
| `buildThumbUrl("a.jpg")` 返回 `"a_thumb.jpg"` | substring 逻辑 | ✅ |
| `getImageUrls` 返回 `ImageUrls` | 返回值类型 | ✅ |
| catch 返回 `ImageUrls(null,null,null,null)` | catch 块 | ✅ |

### 4.3 PickItemRow.kt

| 检查 | 行号 | 结果 |
|:-----|:----:|:-----|
| 参数: `onSkuImageClick` (替代原 `onImageClick`) | L57 位置 | ✅ 默认值 `{}` |
| 参数: `onSkuNameClick` (新增) | L57 附近 | ✅ 默认值 `{}` |
| 参数: `areaThumbUrl` (新增) | L59 附近 | ✅ 默认值 `null` |
| 参数: `boxThumbUrl` (新增) | L59 附近 | ✅ 默认值 `null` |
| L86 clickable: `onImageClick()` → `onSkuImageClick()` | L86 | ✅ 名称一致 |
| L106 Column: 增加 `.clickable { onSkuNameClick() }` | L106 | ✅ 函数名匹配 |
| L145: `areaImageUrl` → `areaThumbUrl` | L145 | ✅ 参数名匹配 |
| L163: `boxImageUrl` → `boxThumbUrl` | L163 | ✅ 参数名匹配 |
| `clickable` 无需额外 import | L5 | ✅ 已导入 |

### 4.4 PickDetailScreen.kt

| 检查 | 行号 | 结果 |
|:-----|:----:|:-----|
| `areaThumbUrl` 状态变量 | L292 附近 | ✅ |
| `boxThumbUrl` 状态变量 | L292 附近 | ✅ |
| `previewPicImageUrl` 状态变量 | L292 附近 | ✅ 与 `previewImageUrl` 独立 |
| LaunchedEffect: `.areaUrl`/`.boxUrl` 解构 | L295 | ✅ |
| LaunchedEffect: `areaThumbUrl` 赋值 | L295 | ✅ |
| LaunchedEffect: `boxThumbUrl` 赋值 | L295 | ✅ |
| `onSkuNameClick` = 跳转商品 | L305 | ✅ |
| `onSkuImageClick` = 大图预览 | L305 | ✅ |
| `areaThumbUrl` 传递给 PickItemRow | L300-320 | ✅ |
| `boxThumbUrl` 传递给 PickItemRow | L300-320 | ✅ |
| SKU预览弹窗 `previewPicImageUrl` | L412之后 | ✅ 独立弹窗 |
| SKU预览弹窗 `ContentScale.Fit` | 弹窗内 | ✅ |
| 库区图预览弹窗不受影响 | L412-L432 | ✅ `previewImageUrl` 路径不变 |

---

## 五、回归验证

| # | 风险 | 预案 | 等级 |
|:-:|:-----|:-----|:----:|
| 1 | `onImageClick` 被删除 → 编译失败 | PickDetailScreen 改为 `onSkuNameClick` | ✅ |
| 2 | `Pair` 被改为 `ImageUrls` → `.first`/`.second` 失效 | PickDetailScreen 解构改为 `.areaUrl`/`.boxUrl` | ✅ |
| 3 | 旧图片无 `_thumb` 文件 | Coil 404 静默灰底，下次上传自动生成 | 低 |
| 4 | 缩略图生成抛异常中断上传 | try-catch + warn 日志 | ✅ |
| 5 | propertiesName 点击与 Card 冲突 | Compose 支持嵌套 clickable | ✅ |
| 6 | 图片清理不删缩略图 | 后续在 `_cleanup_orphan_images` 补充 | 低 |
