# 管理后台增加SKU缩略图查看

## 问题

Android 端取货单明细行已有 SKU 缩略图显示（`PickItemRow.kt` 使用 `item.picPath`），但**管理后台图片搜索只显示库区图和箱规图**，无法查看 SKU 缩略图（picPath）的 URL 是否正确。

用户需要先在后台验证缩略图 URL，再判断 Android 端不显示的原因。

## 改动

### 文件：[admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py)

**修改 `searchImages()` JavaScript 函数**，在查询库区/箱规图的同时，调用 `GET /api/sku/{sku_outer_id}` 获取 SKU 详情的 `picPath`，显示为一张独立图片，并展示 URL 原文。

```javascript
// ========== 图片查看 ==========
async function searchImages() {
  const sku = document.getElementById('imageSkuInput').value.trim();
  if (!sku) { alert('请输入SKU编码'); return; }
  try {
    const r = await api('/api/images/' + encodeURIComponent(sku));
    const images = r.data || [];

    // 获取SKU缩略图（picPath）
    let thumbnailHtml = '';
    try {
      const skuDetail = await api('/api/sku/' + encodeURIComponent(sku));
      if (skuDetail && skuDetail.picPath) {
        thumbnailHtml = '<div style="display:inline-block;margin:8px;text-align:center;vertical-align:top">' +
          '<img src="' + skuDetail.picPath + '"' +
               ' style="max-width:200px;max-height:200px;border-radius:8px;border:1px solid #e5e7eb"' +
               ' onerror="this.src=\'\';this.alt=\'图片加载失败\'" />' +
          '<p style="font-size:12px;color:#666;margin-top:4px">SKU缩略图</p>' +
          '<p style="font-size:10px;color:#999;word-break:break-all;max-width:200px">' + skuDetail.picPath + '</p>' +
        '</div>';
      }
    } catch(e) {
      // SKU缩略图查询失败，不阻塞库区/箱规图显示
    }

    const container = document.getElementById('imageResults');
    if (images.length === 0 && !thumbnailHtml) {
      container.innerHTML = '<div class="empty">未找到图片</div>';
      return;
    }
    container.innerHTML = (thumbnailHtml || '') + images.map(function(img) {
      return '<div style="display:inline-block;margin:8px;text-align:center;vertical-align:top">' +
        '<img src="' + API_BASE + '/images/' + img.filePath + '"' +
             ' style="max-width:200px;max-height:200px;border-radius:8px;border:1px solid #e5e7eb"' +
             ' onerror="this.src=\'\';this.alt=\'图片加载失败\'" />' +
        '<p style="font-size:12px;color:#666;margin-top:4px">' + (img.imageType === 'area' ? '库区图' : '箱规图') + '</p>' +
      '</div>';
    }).join('');
  } catch(e) {
    document.getElementById('imageResults').innerHTML = '<div class="empty">查询失败: ' + e.message + '</div>';
  }
}
```

**改动说明**：
- 调用 `api('/api/sku/' + sku)` 获取 SKU 详情中的 `picPath`（阿里 CDN URL）
- 如果 `picPath` 非空，显示为一张"SKU缩略图"，**同时显示 URL 原文**（用于验证地址是否正确）
- 图片加载失败显示占位文字，不影响其他图片
- `vertical-align:top` 确保三列图片顶部对齐

---

## 验证

在管理后台「图片查看」Tab 中：
1. 输入一个已有 SKU 编码 → 搜索
2. 期望看到三张图（SKU缩略图、库区图、箱规图）
3. SKU缩略图下方显示完整的 URL 地址
4. 如果 URL 错误或无法访问，图片显示"图片加载失败"

## 版本号

2.24 → 2.25
