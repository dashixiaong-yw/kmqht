# 库区图/箱图/SKU图缩略图优化方案

## 一、现状分析

### 所有图片加载链路

| 图片类型 | 来源 | 当前尺寸 | 当前加载 | 痛点 |
|:---------|:-----|:--------:|:---------|:-----|
| 库区图/箱图 | 后端 `/images/...` (FRP隧道) | 44×44dp | 全尺寸~200KB | FRP带宽有限 |
| SKU主图(picPath) | 阿里CDN `img.alicdn.com` | 90×90dp | CDN原图 | PDA通过FRP访问外网慢 |

### 共同问题

即使只显示巴掌大的缩略图，Coil 仍下载 **全尺寸原图**（~200KB）。一屏 5-8 条数据 = 1-1.6MB 无谓传输。

---

## 二、修改方案

### 修改 1（后端）：上传时自动生成缩略图（解决库区图/箱图）

**文件**：`backend/app/routers/images.py` — 约 +15 行

```python
from PIL import Image

def _generate_thumbnail(file_path: str) -> None:
    """生成200px宽度的缩略图，失败不影响主流程"""
    try:
        img = Image.open(file_path)
        img.thumbnail((200, 200), Image.LANCZOS)
        base, _ = os.path.splitext(file_path)
        thumb_path = f"{base}_thumb.jpg"
        img.convert("RGB").save(thumb_path, "JPEG", quality=50)
    except Exception as e:
        logger.warning(f"生成缩略图失败: {e}")
```

在 `upload_image()` 写文件后调用：

```python
# 写入全尺寸文件
with open(file_path, "wb") as f:
    f.write(content)

# 生成缩略图（失败不影响主流程）
_generate_thumbnail(file_path)
```

**效果**：全尺寸 ~200KB → 缩略图 ~5-15KB（200px, Q50）

### 修改 2（后端）：代理接口支持缩放（解决 SKU 阿里CDN 图片）

**文件**：`backend/app/routers/images.py` — proxy_image 加 `width` 参数

```python
@router.get("/images/proxy")
def proxy_image(
    url: str = Query(...),
    width: Optional[int] = Query(None, description="缩略宽度，为空则返回原图"),
    ...
):
    response = httpx.get(url, timeout=10.0)
    content = response.content
    
    if width and width > 0:
        from PIL import Image
        import io
        try:
            img = Image.open(io.BytesIO(content))
            img.thumbnail((width, width * 10), Image.LANCZOS)
            buf = io.BytesIO()
            img.convert("RGB").save(buf, "JPEG", quality=60)
            content = buf.getvalue()
        except Exception as e:
            logger.warning(f"代理图片缩放失败: {e}")
    
    return Response(content=content, media_type="image/jpeg")
```

### 修改 3（Android）：PickDetailViewModel 构造缩略图 URL

**文件**：`PickDetailViewModel.kt`

```kotlin
data class ImageUrls(
    val areaUrl: String?,        // 库区图完整URL
    val boxUrl: String?,         // 箱图完整URL
    val areaThumbUrl: String?,   // 库区图缩略图URL
    val boxThumbUrl: String?     // 箱图缩略图URL
)

/** 从完整URL构造缩略图URL（后端缩略图：_thumb 后缀） */
private fun buildThumbUrl(fullUrl: String?): String? {
    if (fullUrl == null) return null
    val dot = fullUrl.lastIndexOf('.')
    return if (dot > 0) "${fullUrl.substring(0, dot)}_thumb${fullUrl.substring(dot)}" else fullUrl
}

/** 从阿里CDN URL构造代理缩略图URL */
private fun buildKuaimaiThumbUrl(picPath: String?, serverUrl: String?): String? {
    if (picPath.isNullOrBlank() || serverUrl.isNullOrBlank()) return picPath
    return "$serverUrl/api/images/proxy?url=${java.net.URLEncoder.encode(picPath, "UTF-8")}&width=200"
}
```

修改 `getImageUrls()` 返回类型：

```kotlin
suspend fun getImageUrls(skuOuterId: String): ImageUrls {
    ...
    val areaUrl = ...  // 完整URL
    val boxUrl = ...   // 完整URL
    return ImageUrls(
        areaUrl = areaUrl,
        boxUrl = boxUrl,
        areaThumbUrl = buildThumbUrl(areaUrl),
        boxThumbUrl = buildThumbUrl(boxUrl)
    )
}
```

### 修改 4（Android）：PickItemRow 缩小缩略图 + 使用缩略图URL

**文件**：`PickItemRow.kt`

| 参数 | 改动 |
|:-----|:------|
| 新增参数 | `areaThumbUrl: String? = null` |
| 新增参数 | `boxThumbUrl: String? = null` |
| L137 库区图 Box | `44.dp` → **`32.dp`** |
| L155 箱图 Box | `44.dp` → **`32.dp`** |
| L145 AsyncImage(model) | `areaImageUrl` → **`areaThumbUrl`** |
| L163 AsyncImage(model) | `boxImageUrl` → **`boxThumbUrl`** |
| 点击回调 | **不变** — 仍使用 `areaImageUrl`/`boxImageUrl`（完整URL） |

### 修改 5（Android）：PickDetailScreen 传递缩略图URL

**文件**：`PickDetailScreen.kt`

```kotlin
// 获取图片URL
LaunchedEffect(item.skuOuterId) {
    val urls = viewModel.getImageUrls(item.skuOuterId)
    areaImageUrl = urls.areaUrl          // 完整URL → 点击预览用
    boxImageUrl = urls.boxUrl
    areaThumbUrl = urls.areaThumbUrl     // 缩略图URL → 列表显示用
    boxThumbUrl = urls.boxThumbUrl
}

PickItemRow(
    areaImageUrl = areaImageUrl,         // 完整URL → 点击回调
    boxImageUrl = boxImageUrl,
    areaThumbUrl = areaThumbUrl,         // 缩略图URL → AsyncImage
    boxThumbUrl = boxThumbUrl,
    onAreaImageClick = {                 // 不变 — 点击时用完整URL预览
        previewImageUrl = areaImageUrl
        ...
    },
    ...
)
```

---

## 三、数据流总览

```
┌─ 拍照上传 ────────────────────────────────────────┐
│  Camera → ImageCompressor(~200KB)                  │
│    → POST /api/upload                              │
│      → 后端保存全尺寸                               │
│      → PIL生成缩略图(200px, Q50, ~10KB)             │
│      → DB记录imageUrl(指向全尺寸)                   │
└────────────────────────────────────────────────────┘

┌─ 列表显示 ──────────────────────────────────────────┐
│  PickDetailViewModel.getImageUrls(skuOuterId)       │
│    → ServerUrl + imageUrl = 完整URL                  │
│    → buildThumbUrl() = 缩略图URL(后端直接映射)       │
│  PickItemRow:                                       │
│    AsyncImage(thumbUrl) ← 只下载~10KB ✅             │
│    点击 → onAreaImageClick(完整URL) → 大图预览       │
└────────────────────────────────────────────────────┘

┌─ SKU主图(picPath) ──────────────────────────────────┐
│  PickItemRow左侧 90×90:                              │
│    目前: AsyncImage(picPath) → CDN原图               │
│    之后: → 通过后端代理缩放                           │
└────────────────────────────────────────────────────┘
```

---

## 四、修改文件清单

| 文件 | 改动量 | 说明 |
|:-----|:------:|:------|
| `backend/app/routers/images.py` | +20行 | `_generate_thumbnail()` + proxy加width |
| `PickDetailViewModel.kt` | +20行 | `ImageUrls` data class + `buildThumbUrl()` + 修改`getImageUrls` |
| `PickItemRow.kt` | +2参数 + 4行 | 新增`areaThumbUrl`/`boxThumbUrl`参数，Box 44→32dp |
| `PickDetailScreen.kt` | +4行 | 接收thumbUrl并传递给PickItemRow |

---

## 五、回归风险

| 风险 | 分析 | 等级 |
|:-----|:-----|:----:|
| 旧图片无缩略图 | `buildThumbUrl`仅做字符串替换，`_thumb`文件不存在时返回404→Coil显示灰底 | 低（下次重传后生成） |
| 缩略图生成失败 | catch异常，不影响上传主流程，仅打warn日志 | 无影响 |
| proxy带width参数 | width为None时保持原行为，向下兼容 | 无影响 |
| 旧版APK不传thumbUrl | PickItemRow参数有默认值`null`，不传则走`areaImageUrl`(原行为) | 无影响 |
| PDA直连阿里CDN | 需等proxy缩放完成，增加一次后端中转，但FRP内比PDA直连外网快 | FRP内网更稳定 |

---

## 六、验证步骤

1. `cd backend && docker-compose up -d --build`
2. 重新安装 APK → 登录 PDA → 进入取货单详情
3. 确认库区图/箱图 32dp 缩略图正常显示
4. 确认 SKU 主图（90×90）正常显示
5. 点击缩略图 → 大图预览加载全尺寸
6. 查看后端日志：`生成缩略图成功: xxx_thumb.jpg`
7. 感受列表滚动流畅性提升
