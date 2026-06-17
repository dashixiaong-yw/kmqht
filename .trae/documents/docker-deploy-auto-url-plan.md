# Docker 部署配置计划 (自动获取 IP)

## 目标

1. 后端自动从请求 Host 推断服务器地址，不再强制要求配置 `SERVER_URL`
2. 创建 `.env` 和 `kuaimai.json` 配置文件，用户拿去直接部署

## 修改方案

### 1. system.py - APK 下载地址改为自动获取

**文件**: `backend/app/routers/system.py`

`get_app_version()` 增加 `Request` 参数，从请求的 base URL 拼接 APK 下载地址：

```python
from fastapi import Request

@router.get("/api/app-version", response_model=AppVersionResponse)
def get_app_version(request: Request) -> AppVersionResponse:
    info = _load_version_info()
    if not info or not info.get("currentVersion"):
        return AppVersionResponse(latestVersion="", downloadUrl="")
    apk_path = os.path.join(APK_DIR, info.get("apkFileName", ""))
    apk_size = os.path.getsize(apk_path) if os.path.exists(apk_path) else 0
    base_url = str(request.base_url).rstrip("/")
    # 如果配置了SERVER_URL则优先使用（兼容反向代理场景）
    if SERVER_URL:
        base_url = SERVER_URL.rstrip("/")
    return AppVersionResponse(
        latestVersion=...,
        downloadUrl=f"{base_url}/apk/{info.get('apkFileName', '')}",
        ...
    )
```

### 2. admin.py - 管理后台二维码改为自动获取

**文件**: `backend/app/routers/admin.py`

`admin_page()` 传入 `Request`，`_build_admin_html()` 改为接收 `request_base_url` 参数：

```python
@router.get("/admin", response_class=HTMLResponse)
def admin_page(request: Request) -> HTMLResponse:
    base_url = str(request.base_url).rstrip("/")
    return HTMLResponse(content=_build_admin_html(base_url))
```

`_build_admin_html(base_url)` 内部：优先用 `SERVER_URL`（如果配置了），否则用 `base_url`。

### 3. 创建 docker-deploy/.env

```env
API_KEY=zxf199333

# 服务器端口
SERVER_PORT=8900

# 以下可选配置
# SERVER_URL=  # 不填写会自动从请求Host获取
CORS_ORIGINS=*
SESSION_WARNING_DAYS=5
```

### 4. 创建 docker-deploy/data/kuaimai.json

```json
{
  "app_key": "1981991413",
  "app_secret": "54279f7a085e405ebeb6af0d5e2cd68e",
  "session": "f9eae7b99b14478ea13e640a1be05fab",
  "refresh_token": "9a9d5d6da2f24636b206636fa34489f3",
  "updated_at": ""
}
```

### 5. 更新 .env.docker.example

移除 SERVER_URL 的强制标注，改为可选项说明

## 涉及文件

| 文件 | 操作 |
|------|------|
| `backend/app/routers/system.py` | 修改 - 传入 Request 自动获取 base URL |
| `backend/app/routers/admin.py` | 修改 - 传入 Request 自动获取 base URL |
| `backend/.env.docker.example` | 修改 - 说明 SERVER_URL 可选 |
| `docker-deploy/.env` | **新建** - 部署用 .env |
| `docker-deploy/data/kuaimai.json` | **新建** - 快麦凭证 |

## 验证

1. 更新同步到 docker-deploy
2. 确认文件结构完整可部署
