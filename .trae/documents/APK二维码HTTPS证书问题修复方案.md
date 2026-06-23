# APK 二维码下载 HTTPS 证书问题修复方案

## 一、问题分析

### 症状

管理后台点击"分发"后，PDA 扫描生成的二维码，在 Android 6.0 PDA 上提示穿透过来的证书有问题，无法下载。

### 根因

**完整数据流**：

```
管理后台分发 → apk_version.json 写入 publishedAt
    → 管理后台 JS 调用 /api/app-version/qrcode
    → 后端生成二维码：内容为 {SERVER_URL}/api/app-version/download
    → PDA 扫码 → 系统浏览器打开 https://frp-off.com:64623/api/app-version/download
    → Android 6.0 浏览器 SSL 证书校验失败 ❌
```

**关键事实**：

| 事实 | 详情 |
|:-----|:------|
| 后端运行方式 | `uvicorn main:app --host 0.0.0.0 --port 8900` — **纯 HTTP，无 TLS** |
| SERVER_URL | `https://frp-off.com:64623` |
| 证书终止位置 | FRP 隧道（SakuraFRP）在服务器端终止 TLS，将 HTTPS 转发为 HTTP 到后端 |
| 二维码内容 | `https://frp-off.com:64623/api/app-version/download` |
| AppUpdateManager 下载 | 使用 `@Named("trustAll")` OkHttpClient，信任所有证书 → **正常工作** |
| System browser 下载 | **Android 6.0 浏览器** → 使用系统 SSL 校验 → FRP 自签证书不受信任 → **下载失败** |

**不存在问题的地方**：
- AppUpdateManager.checkForUpdate() / downloadApk() — 使用 trustAll OkHttpClient 绕过证书校验 ✅
- `network_security_config.xml` — 已配置 `cleartextTrafficPermitted="true"` + 信任用户证书 ✅

**根本原因**：QR 码由 PDA 系统浏览器扫描打开，浏览器使用系统 SSL 证书链校验。FRP 隧道的自签证书在 Android 6.0 上不被信任。

### 前置条件

| 条件 | 状态 |
|:-----|:----:|
| 后端监听 HTTP:8900 | ✅ Dockerfile CMD 无 SSL 参数 |
| `network_security_config.xml` 允许 HTTP | ✅ `cleartextTrafficPermitted="true"` |
| AppUpdateManager 下载 OK | ✅ 使用 trustAll client |
| SERVER_URL 含 protocol | ✅ `https://frp-off.com:64623` |
| QR 码调用方（管理后台 JS） | `api('/api/app-version/qrcode')` 返回 base64 图片 |

## 二、修改内容

**方案**：新增 `APK_DOWNLOAD_BASE_URL` 环境变量，专门控制 APK 下载 URL。当设置此变量时，QR 码和 `get_app_version` 中的下载链接使用此 URL 替代 `SERVER_URL`。这样管理员可以设置为内部 HTTP 地址，绕过 FRP 的证书问题。

### 文件 1：`backend/app/config.py`

**改动**：新增配置项

```python
# APK 下载专用 URL（用于二维码和 OTA 下载链接）
# 当部署在 FRP/反向代理后且证书不被旧设备信任时，可设置为内部 HTTP 地址
APK_DOWNLOAD_BASE_URL: str = os.getenv("APK_DOWNLOAD_BASE_URL", "")
```

### 文件 2：`backend/app/routers/system.py`

**改动 A**：导入新配置

```python
from app.config import APK_DIR, APK_VERSION_FILE, SERVER_URL, APK_DOWNLOAD_BASE_URL, kuaimai_creds
```

**改动 B**：`get_app_version()` L103 使用 `APK_DOWNLOAD_BASE_URL`

```python
# 优先用 APK_DOWNLOAD_BASE_URL（专门控制下载链接），再降级到 SERVER_URL
download_base = (APK_DOWNLOAD_BASE_URL.rstrip("/")
                 if APK_DOWNLOAD_BASE_URL
                 else (SERVER_URL.rstrip("/") if SERVER_URL else str(request.base_url).rstrip("/")))

return AppVersionResponse(
    latestVersion=...,
    downloadUrl=f"{download_base}/api/app-version/download",
    ...
)
```

**改动 C**：`get_app_version_qrcode()` L158 使用 `APK_DOWNLOAD_BASE_URL`

```python
download_base = (APK_DOWNLOAD_BASE_URL.rstrip("/")
                 if APK_DOWNLOAD_BASE_URL
                 else (SERVER_URL.rstrip("/") if SERVER_URL else str(request.base_url).rstrip("/")))
download_url = f"{download_base}/api/app-version/download"
```

### 文件 3：`backend/.env.docker.example`

**改动**：文档化新变量

```ini
# APK 下载专用 URL（用于二维码和 OTA 下载链接）
# 当 FRP/反向代理证书不被旧设备信任时，设置为此能绕过 SSL 问题
# 示例：http://192.168.1.100:8900
APK_DOWNLOAD_BASE_URL=http://192.168.1.100:8900
```

## 三、原理

```
管理后台分发 → QR 码内容 = APK_DOWNLOAD_BASE_URL/api/app-version/download
                                     ↓
                         设置 APK_DOWNLOAD_BASE_URL=http://192.168.1.100:8900
                                     ↓
                         PDA 扫码 → 系统浏览器打开 HTTP URL
                                     ↓
                         Android 6.0 HTTP 下载成功 ✅（network_security_config 允许明文）
```

- API 调用（如 `get_app_version`/`checkForUpdate`）仍走 HTTPS（`SERVER_URL`），AppUpdateManager 用 trustAll 无问题
- 仅下载/二维码用 HTTP（`APK_DOWNLOAD_BASE_URL`），浏览器无证书校验
- 不设置 `APK_DOWNLOAD_BASE_URL` 时，行为与之前完全一致（回退到 `SERVER_URL`）

## 四、回归风险分析

| 风险 | 分析 | 等级 |
|:-----|:-----|:----:|
| 未设置 `APK_DOWNLOAD_BASE_URL` | 回退到 `SERVER_URL`，行为完全不变 | 无影响 |
| 设置错误的 URL | 管理员责任，可回滚配置 | 低 |
| AppUpdateManager 使用 HTTP 下载 | trustAll client 支持 HTTP，无问题 | 低 |
| 管理后台不受影响 | 仅后端 QR 码 URL 变更，管理后台 UI 不变 | 无影响 |
| 未分发时 | `_load_version_info()` 返回空，不进入 URL 逻辑 | 无影响 |

## 五、验证步骤

1. 后端部署：NAS 执行 `docker-compose up -d --build`
2. 管理后台 → 上传 APK → 分发
3. 查看 QR 码 → 确认二维码内容为 `http://...`（如已设置 `APK_DOWNLOAD_BASE_URL`）
4. Android 6.0 PDA 扫描 QR 码 → 可正常下载
5. Android 新版 PDA 扫描 QR 码 → 可正常下载
6. 设置 `APK_DOWNLOAD_BASE_URL` 为空 → QR 码退回 HTTPS（`SERVER_URL`）
