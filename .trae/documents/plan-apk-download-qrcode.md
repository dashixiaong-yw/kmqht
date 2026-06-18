# APK 下载二维码 - 实施计划

## 摘要

为管理后台的 APK 管理标签页新增 **APK 下载二维码**功能：后台上传并分发 APK 后，管理页面显示一个二维码，PDA 扫码后浏览器打开 APK 下载 URL 自动触发下载。

## 当前状态分析

### 已有能力
| 能力 | 文件 | 状态 |
|:-----|:-----|:-----|
| APK 上传 API | `admin.py` POST `/api/app-version/upload` | 已有 |
| APK 分发 API | `admin.py` POST `/api/app-version/publish` | 已有 |
| 版本查询 API | `system.py` GET `/api/app-version`（返回 `downloadUrl`） | 已有 |
| APK 静态目录挂载 | `main.py` `/apk` → FastAPI StaticFiles | 已有 |
| 二维码生成工具 | `qr_utils.py` `generate_qr_base64()` | 已有 |
| 管理后台 APK 标签页 | `admin.py` 内嵌 HTML+JS，`loadApk()` 动态渲染 | 已有 |

### 缺失环节
| 缺失 | 说明 |
|:-----|:------|
| `/apk/` 路径未跳过 API Key 认证 | 浏览器打开下载 URL 会返回 401 |
| 无 APK 下载二维码 | 管理后台 APK 标签页不显示下载二维码 |
| 无二维码生成接口 | 管理后台 JS 无法动态获取 APK 下载二维码 |

### 关键设计决策（从知识图谱确认）
- `GET /api/app-version` 无需 API Key 认证（PDA 启动检查更新）
- `/apk/` 路径目前**不在** SKIP_AUTH_PREFIXES 中，浏览器直连被拦截
- 管理后台已有扫码配置二维码生成模式（`generate_qr_base64` + `data:image/png;base64` 内嵌）
- 现有 Setup QR 协议：`kuaimai://setup?server=...&apikey=...`
- APK downloadUrl 格式：`{base_url}/apk/快麦取货通-{版本号}.apk`

## 方案

### 数据流

```
管理后台上传 APK → POST /api/app-version/upload
管理后台分发 APK → POST /api/app-version/publish
管理后台 JS     → GET /api/app-version/qrcode → 返回 QR code PNG
                → <img> 标签显示二维码
PDA 扫码二维码   → 浏览器打开 downloadUrl (纯HTTP URL)
                → /apk/ 已跳过 API Key 认证
                → 浏览器自动触发 APK 文件下载
```

### 变更文件

#### 1. [auth.py:18](file:///d:/trea项目/快麦取货通/backend/app/auth.py#L18) — 允许 APK 直链下载

**改动**：在 `SKIP_AUTH_PREFIXES` 元组中添加 `"/apk/"` 前缀。

```python
# 修改前
SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", "/redoc", "/openapi.json", "/admin", "/setup", "/api/app-version")

# 修改后
SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", "/redoc", "/openapi.json", "/admin", "/setup", "/api/app-version", "/apk/")
```

**原因**：`/apk/` 下的 APK 文件是公开下载资源，与 `/images/` 的访问控制策略一致。浏览器从 QR 码打开 URL 时不携带 API Key 请求头，必须跳过认证。

---

#### 2. [system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py) — 新增 APK 下载二维码接口

在现有 `get_app_version` 路由后新增 `GET /api/app-version/qrcode` 接口。

**路由**：`GET /api/app-version/qrcode`

**认证**：需携带 API Key（与 `/api/app-version/upload`、`/api/app-version/publish` 一致）

**实现逻辑**：
1. 读取版本信息 JSON
2. 若无已分发版本（`publishedAt` 为空或无 `currentVersion`），返回 404
3. 构造下载 URL（与 `get_app_version` 同样的方式：优先 `SERVER_URL`，否则从 `request.base_url` 获取）
4. 调用 `generate_qr_base64(download_url)` 生成二维码 base64
5. 返回 base64 编码的 PNG 图片数据（JSON 格式包裹，方便前端使用）

**返回格式**：
```json
{
  "success": true,
  "qrcode": "base64编码的PNG图片数据"
}
```

**不需要 API Key**：因为 admin 页 JS 已经使用 `api()` 函数自动携带 API Key header 调用所有 `/api/...` 路径，所以不需要额外加到 SKIP_AUTH_PREFIXES。auth 中间件对 `/api/app-version/qrcode` 的处理：
- 匹配 `/api/app-version` 前缀 → 进入 `startswith` 判断
- `prefix == "/api/app-version"` 为 true
- `request.url.path != "/api/app-version"` 也为 true（路径是 `/api/app-version/qrcode`）
- 执行 `break` → 不跳过认证 → 走正常 API Key 检查流程
- 因此 `qrcode` 端点需要 API Key（与 upload/publish 一致）

---

#### 3. [admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) — 管理后台 APK 页面显示下载二维码

**改动**：修改 `loadApk()` JavaScript 函数，在 APK 已分发时显示下载二维码。

在渲染 `publishedInfo` 和 `publishBtn` 的区域后，新增二维码区域：

```javascript
// 已分发时显示下载二维码
if (r.publishedAt && r.downloadUrl) {
    try {
        const qrResp = await api('/api/app-version/qrcode');
        if (qrResp.qrcode) {
            const qrImg = document.createElement('img');
            qrImg.src = 'data:image/png;base64,' + qrResp.qrcode;
            qrImg.style.cssText = 'width:160px;height:160px;margin-top:12px';
            container.querySelector('.card').appendChild(qrImg);
            
            // 下载链接文字提示
            const linkHint = document.createElement('p');
            linkHint.style.cssText = 'font-size:12px;color:#666;margin-top:4px';
            linkHint.textContent = 'PDA 扫码下载 APK';
            container.querySelector('.card').appendChild(linkHint);
        }
    } catch(e) {
        console.warn('加载下载二维码失败', e);
    }
}
```

**关键考虑**：由于 `loadApk()` 是 `async` 函数，内部可以 `await api('/api/app-version/qrcode')`。`api()` 是管理后台封装的通用 AJAX 调用函数，自动携带 API Key。

## 验证步骤

1. 后端语法检查：`python -c "from backend.app.routers.system import router"`
2. 前端效果验证：部署后登陆管理员后台 → APK 管理标签页 → 上传并分发 APK → 确认二维码显示
3. 浏览器测试：扫码或直接访问 `${downloadUrl}` → 确认触发 APK 下载
