# 管理后台增加缩略图URL查看 + 诊断

## 当前问题

用户反馈：取货单详情（PickItemRow）中的库区图与箱规图的缩略图没有正常显示。需要先在管理后台查看缩略图的 URL 是否正确，定位根因。

## 分析：Android 端缩略图 URL 构造链路

```
后端图片上传 → 生成原始图 + _thumb.jpg
       ↓
DB product_images 表：
  image_url = "images/20260625/B08-24_area_abc123.jpg"
  file_path = "20260625/B08-24_area_abc123.jpg"
       ↓
Android PickDetailViewModel.getImageUrls():
  serverUrl = "https://frp-off.com:64623"  (来自 SharedPreferences)
  areaUrl = "https://frp-off.com:64623/images/20260625/B08-24_area_abc123.jpg"
  areaThumbUrl = buildThumbUrl(areaUrl)
              = "https://frp-off.com:64623/images/20260625/B08-24_area_abc123_thumb.jpg"
       ↓
PickItemRow UI:
  if (areaImageUrl != null) → 显示 areaThumbUrl 的 AsyncImage
```

## 可能的根因

| 可能性 | 说明 | 验证方式（通过后台） |
|:-------|:------|:-------------------|
| **缩略图未生成** | `_generate_thumbnail` 异常时仅 log，不报错 | 访问 `{serverURL}/images/{filePath}_thumb.jpg` 看是否 404 |
| **serverUrl 不匹配** | 手机配置的服务器地址与实际不一致 | 对比后台 `API_BASE` 与 `areaUrl` 的 base |
| **旧图片有缩略图但新上传的没有** | 替换旧图时只删了原图，没删旧缩略图，但新缩略图可能没生成 | 查看日志或直接访问 |
| **DB 中无对应记录** | SKU 没有上传过 area/box 图片 | searchImages 返回为空 |

## 改动

### 文件：[admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py)

**修改 `searchImages()` 函数**，每张图片同时显示：
- 原始图（现有逻辑）
- 缩略图（`filePath` 替换扩展名为 `_thumb.jpg`）
- 原始图和缩略图的完整 URL（用于诊断）

```javascript
// ========== 图片查看 ==========
async function searchImages() {{
  const sku = document.getElementById('imageSkuInput').value.trim();
  if (!sku) {{ alert('请输入SKU编码'); return; }}
  try {{
    const r = await api('/api/images/' + encodeURIComponent(sku));
    const images = r.data || [];
    const container = document.getElementById('imageResults');
    if (images.length === 0) {{
      container.innerHTML = '<div class="empty">未找到图片</div>';
      return;
    }}
    container.innerHTML = images.map(function(img) {{
      const fullUrl = API_BASE + '/images/' + img.filePath;
      const dot = img.filePath.lastIndexOf('.');
      const thumbUrl = dot > 0
        ? API_BASE + '/images/' + img.filePath.substring(0, dot) + '_thumb.jpg'
        : API_BASE + '/images/' + img.filePath + '_thumb.jpg';
      return '<div style="display:inline-block;margin:8px;text-align:center;vertical-align:top">' +
        '<div style="display:flex;gap:8px;margin-bottom:4px">' +
          '<div><img src="' + fullUrl + '" style="max-width:200px;max-height:200px;border-radius:8px;border:1px solid #e5e7eb" onerror="this.src=\'\';this.alt=\'加载失败\'" />' +
            '<div style="font-size:11px;color:#666;margin-top:2px">原图</div></div>' +
          '<div><img src="' + thumbUrl + '" style="max-width:100px;max-height:100px;border-radius:8px;border:1px solid #e5e7eb" onerror="this.src=\'\';this.alt=\'加载失败\'" />' +
            '<div style="font-size:11px;color:#666;margin-top:2px">缩略图</div></div>' +
        '</div>' +
        '<div style="font-size:12px;color:#666;margin-top:4px">' + (img.imageType === 'area' ? '库区图' : '箱规图') + '</div>' +
        '<div style="font-size:10px;color:#999;word-break:break-all;max-width:310px;text-align:left">原图: ' + fullUrl + '</div>' +
        '<div style="font-size:10px;color:#999;word-break:break-all;max-width:310px;text-align:left">缩略图: ' + thumbUrl + '</div>' +
      '</div>';
    }}).join('');
  }} catch(e) {{
    document.getElementById('imageResults').innerHTML = '<div class="empty">查询失败: ' + e.message + '</div>';
  }}
}}
```

**改动说明**：
- 每张图显示左右两张：原图（200px）+ 缩略图（100px）
- 下方显示图片类型（库区图/箱规图）
- 下方显示原图和缩略图的完整 URL，可复制验证
- 图片加载失败显示"加载失败"占位，不干扰其他内容

---

## 验证方法

1. 在管理后台「图片查看」Tab 中搜索已有库区图/箱规图的 SKU
2. 检查缩略图是否正常加载
3. 如果缩略图加载失败，URL 会显示为红色占位文字，可复制 URL 到浏览器验证
4. 根据显示结果判断根因：
   - 缩略图也正常 → 可能手机端 serverUrl 配置不对
   - 缩略图失败 → 后端缩略图生成有问题
   - 原图失败 → 图片文件可能丢失

## 版本号

2.24 → 2.25（不构建 APK，仅改后端）
