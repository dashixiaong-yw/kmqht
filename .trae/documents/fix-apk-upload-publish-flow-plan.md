# APK 上传+分发链路修复计划

## 根因

`upload_app_version()` 写入 `/data/apk_version.json` 后，JS 调用 `loadApk()` 通过 `get_app_version()` 重新读取该文件。在 NAS/Docker 环境下，`os.replace()` 的写操作可能不会立即对后续读取可见（文件系统延迟），导致：

1. **第1次上传**：文件已写但回读为空 → JS 显示"暂无版本信息"→ 无分发按钮
2. **第2次上传**：文件已存在（第1次写入的），回读成功 → 显示分发按钮 → 分发成功 → 但 `get_app_version()` 返回版本信息时**未检查 APK 文件是否存在** → 下载 404
3. **第3次上传**：APK 文件已就绪 → 下载正常

## 修复方案

### 改动 2 个文件

#### 1. [admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) — upload_app_version Python 端

在 `upload_app_version()` 返回的响应中增加版本信息字段，前端不再需要重新调用 `loadApk()` 来获取：

```python
return {
    "success": True,
    "message": "上传成功，点击分发后所有PDA将收到更新",
    "latestVersion": latestVersion,
    "apkFileName": apk_filename,
    "updateNotes": updateNotes,
    "forceUpdate": forceUpdate,
    "apkSize": len(content),
    "publishedAt": "",
}
```

#### 2. [admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) — uploadApk JS 端

`uploadApk()` 中上传成功后，使用响应中的版本信息直接渲染展示卡片，不再调用 `loadApk()` 重新向后端查询：

```javascript
async function uploadApk() {
  // ...现有代码...
  try {
    const r = await api('/api/app-version/upload', { method: 'POST', body: formData, headers: {} });
    alert(r.message || '上传成功');
    // 直接用上传响应中的版本信息渲染，不再依赖重新查询
    if (r.latestVersion) {
      renderApkCard(r);
    } else {
      loadApk(); // 兜底：没有版本信息时走原有逻辑
    }
  }
  // ...catch/finally...
}
```

新增 `renderApkCard(r)` 函数，复用 `loadApk()` 中的渲染逻辑：

```javascript
function renderApkCard(r) {
  const container = document.getElementById('apkStatus');
  const uploadSection = document.getElementById('apkUploadSection');
  const forceLabel = r.forceUpdate ? '<span class="badge badge-red">强制更新</span>' : '<span class="badge badge-green">可选更新</span>';
  const sizeStr = r.apkSize ? (r.apkSize / 1024 / 1024).toFixed(1) + ' MB' : '未知';
  const publishedInfo = '<p style="font-size:13px;color:#dc2626;margin-top:4px">尚未分发</p>';
  const publishBtn = '<button class="btn btn-success" onclick="publishApk()" style="margin-top:8px">立即分发</button>';
  container.innerHTML = `
    <div class="card">
      <h3>当前版本信息</h3>
      <p>版本号: <strong>${escapeHtml(r.latestVersion)}</strong> ${forceLabel}</p>
      <p>APK 大小: ${sizeStr}</p>
      <p>更新说明:</p>
      <pre style="background:#f8fafc;padding:8px;border-radius:4px;font-size:13px;white-space:pre-wrap">${escapeHtml(r.updateNotes || '无')}</pre>
      ${publishedInfo}
      ${publishBtn}
      <div id="apkQrCode"></div>
    </div>
  `;
  uploadSection.style.display = 'block';
}
```

这样上传成功后不依赖文件系统的二次读取，直接使用后端返回的数据渲染。

### 3. [system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py) — get_app_version 增强

`get_app_version()` 中在返回版本信息前增加 APK 文件存在性检查，避免 JSON 有记录但文件被误删后仍返回版本信息：

```python
apk_path = os.path.join(APK_DIR, info.get("apkFileName", ""))
if not os.path.exists(apk_path):
    return AppVersionResponse(latestVersion="", downloadUrl="")
```

## 不修改的文件

| 文件 | 原因 |
|:-----|:------|
| publish_app_version | 逻辑正确，不需要改 |
| publishApk JS | 逻辑正确（已有自定义 confirmModal 但非本次 root cause） |
| auth.py | 与本次问题无关 |

## 验证

1. `python -c "from app.routers.admin import router"` 导入检查
2. 首次上传后立即看到分发按钮（无需二次操作）
3. 分发后下载正常（APK 文件存在检查）
