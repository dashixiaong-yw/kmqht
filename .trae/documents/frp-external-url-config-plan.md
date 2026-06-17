# FRP 外部访问地址配置计划

## 一、现状分析

### 当前架构

```
FRP 外部（frp-off.com:64623）
        │
        ▼ (TCP 隧道转发)
    NAS 宿主机 :8900
        │
        ▼ (Docker端口映射)
    kuaimai-server 容器 :8000
```

- FRP 隧道 `frp-off.com:64623` 已经成功映射到 NAS 的 Docker 端口 `8900`
- PDA 当前可能配置的是**内网 LAN 地址**（如 `http://192.168.1.xxx:8900`）
- 后端管理后台的二维码使用 `SERVER_URL` 环境变量生成，未配置时从请求 Host 自动获取
- backend/app/routers/admin.py:L27-L28 已有自动获取逻辑

### 关键发现

| 项目 | 说明 |
|:-----|:------|
| **PDA 连接方式** | Retrofit `baseUrl` = `EncryptedSharedPreferences` 中的 `server_url` |
| **图片URL拼接** | 所有 `imageUrl` 存储相对路径（如 `/images/20260618/xxx.jpg`），显示时拼接 `serverUrl + imageUrl` |
| **二维码生成后端逻辑** | `base_url = SERVER_URL if SERVER_URL else str(request.base_url)` — **`SERVER_URL` 优先，只有未设置时才自动获取** |
| **admin 管理页** | 浏览器直接访问，与 PDA 配置无关 |

### ⚠️ 关于"之前修改的自动获取服务器 IP"

整个项目中 **有且只有2处** 使用 `SERVER_URL` + 自动获取 Host 的逻辑，都在后端：

#### 位置 1：admin.py:L28（二维码生成）

[admin.py:L27-L28](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L27-L28)
```python
# 优先用环境变量 SERVER_URL（兼容反向代理），否则从请求 Host 自动获取
base_url = SERVER_URL if SERVER_URL else str(request.base_url).rstrip("/")
```

#### 位置 2：system.py:L88（OTA APK 下载地址）

[system.py:L87-L92](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L87-L92)
```python
# 优先用环境变量 SERVER_URL（兼容反向代理），否则从请求 Host 自动获取
base_url = SERVER_URL.rstrip("/") if SERVER_URL else str(request.base_url).rstrip("/")

return AppVersionResponse(
    ...
    downloadUrl=f"{base_url}/apk/{info.get('apkFileName', '')}",
```

两者逻辑完全一致（`SERVER_URL` 优先 → 自动获取作为 fallback）。

| 场景 | `SERVER_URL` 值 | 影响 QR 码 | 影响 APK 下载 |
|:-----|:----------------|:-----------|:--------------|
| 不设置（当前） | `""`（未配置） | 自动获取内网 IP | 自动获取内网 IP |
| **设置 FRP（我们要做的）** | `http://frp-off.com:64623` | **FRP 外部地址** | **FRP 外部地址** |

**结论：完全兼容，无冲突。** 两处代码都使用同样的模式——`SERVER_URL` 有值时直接使用，自动获取只作为保底。一个 `.env` 配置变更同时解决两处。

## 二、改动方案

**结论：只需要一个配置变更，无需修改任何代码。**

### 改动点

#### 1. 后端 .env 文件添加 `SERVER_URL`

**文件**：`backend/.env`（或 `docker-deploy/.env`）

**操作**：追加一行：

```ini
SERVER_URL=http://frp-off.com:64623
```

#### 2. 重启 Docker 容器

```bash
cd backend   # 或 docker-deploy/
docker-compose down
docker-compose up -d --build
```

### 不需要改的

- ❌ **Android 代码**（NetworkModule.kt、GuideScreen.kt 等）—— 无需修改
- ❌ **后端代码**（admin.py、qr_utils.py 等）—— 无需修改
- ❌ **Docker Compose 配置**—— 无需修改，.env 文件本来就通过 `env_file` 加载
- ❌ **FRP 客户端配置**—— 隧道已正常工作

### 影响范围

| 方面 | 影响 |
|:-----|:-----|
| **PDA API 请求** | 所有请求通过 `http://frp-off.com:64623` → 走公网 FRP 隧道 → NAS 8900 |
| **图片加载** | `serverUrl + imageUrl` 拼接后通过 FRP 公网访问，延迟略增但可接受 |
| **admin 管理后台** | 不受影响，仍通过浏览器直接访问 NAS 内网IP |
| **PDA 扫码配置** | 管理后台二维码自动生成 `kuaimai://setup?server=http://frp-off.com:64623&apikey=xxx` |

**延迟说明**：同一局域网内 PDA 使用 FRP 外网地址，流量会走 NAS → FRP 服务器 → PDA，比直连 LAN 多一跳。但图片和 JSON 流量极小，对使用体验影响可忽略。

## 三、操作步骤

### Step 1：更新 .env 配置

编辑 `backend/.env`（或 `docker-deploy/.env`），添加或修改：

```ini
# 已有配置保持不变...
SERVER_URL=http://frp-off.com:64623
```

### Step 2：重启容器

```bash
cd backend
docker-compose down
docker-compose up -d
```

### Step 3：验证

- 浏览器访问 `http://NAS内部IP:8900/admin`，查看二维码内容是否包含 `server=http://frp-off.com:64623`
- 外网尝试：`curl http://frp-off.com:64623/health`

### Step 4：更新 PDA 配置

**方式 A：重新扫码（推荐）**
- 浏览器访问 admin 页面，用 PDA 扫描新二维码

**方式 B：手动修改**
- PDA 设置页 → 手动将服务器地址改为 `http://frp-off.com:64623`

### Step 5：验证 PDA 连接

- PDA 能正常加载取货单数据
- PDA 能正常加载库区图和箱图（图片通过 FRP 公网访问）

## 四、回滚方案

如果 FRP 公网访问出现问题，PDA 只需将服务器地址改回内网 IP 即可，无需任何服务端变更。
