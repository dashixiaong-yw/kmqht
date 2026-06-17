# 计划：SERVER_URL 环境变量改进（支持 DEFAULT_DOMAIN 模式 + Docker 自动适配）

## 摘要

用户之前项目的模式是 `DEFAULT_DOMAIN=http://localhost:3000`，询问当前项目 `SERVER_URL=http://localhost:8090` 是否能在 Docker 部署后自动适配 IP 和端口。核心问题是：`localhost` 在 Docker 容器内指向容器自身而非宿主机，PDA 无法通过 `localhost` 访问服务器。

## 当前状态分析

### 现有机制

| 环境变量 | 当前默认值 | 使用位置 |
|----------|-----------|---------|
| `SERVER_URL` | `""`（空字符串） | `/setup` 生成二维码、`/admin` 生成二维码 |
| `SERVER_PORT` | `8000` | uvicorn 监听端口 |
| Docker端口映射 | 宿主机 `8900` → 容器 `8000` | docker-compose.yml |

### 现有 fallback 机制（仅 `/setup` 有）

```python
# system.py /setup 路由
server_url = SERVER_URL
if not server_url:
    host = request.headers.get("host", "")
    if host:
        scheme = "https" if request.url.scheme == "https" else "http"
        server_url = f"{scheme}://{host}"
```

### 问题

1. **`localhost` 在 Docker 内不可用**：容器内 `localhost` 指向自身，PDA 设备通过 `localhost` 访问的是 PDA 自己
2. **`/admin` 没有 fallback**：`SERVER_URL` 为空直接报红，没有走 Host 头推断
3. **端口不匹配**：Docker 内部 8000 端口映射到宿主机 8900 端口，`localhost:8090` 哪个都不对
4. **变量名不一致**：用户习惯用 `DEFAULT_DOMAIN`，当前用的是 `SERVER_URL`

## 改进方案

### 方案：新增 DEFAULT_DOMAIN 环境变量 + 增强 fallback

保持向后兼容，同时新增用户熟悉的 `DEFAULT_DOMAIN` 名称。

#### 1. config.py - 新增 DEFAULT_DOMAIN 配置

```python
DEFAULT_DOMAIN: str = os.getenv("DEFAULT_DOMAIN", "")  # 用户熟悉的命名
SERVER_URL: str = os.getenv("SERVER_URL", "")           # 保持向后兼容

# 实际使用的服务器地址：SERVER_URL > DEFAULT_DOMAIN > Host头推断
```

#### 2. system.py - 统一获取服务器地址的函数

提取一个公共函数 `get_server_url(request)`，供 `/setup` 和 `/admin` 共用：

```python
def get_server_url(request: Request = None) -> str:
    """获取服务器对外地址：SERVER_URL > DEFAULT_DOMAIN > Host头推断"""
    url = SERVER_URL or DEFAULT_DOMAIN
    if not url and request:
        host = request.headers.get("host", "")
        if host:
            scheme = "https" if request.url.scheme == "https" else "http"
            url = f"{scheme}://{host}"
    return url
```

#### 3. admin.py - 复用 get_server_url，增加 fallback

#### 4. .env.docker.example - 更新

```ini
# 服务器对外访问地址（PDA通过此地址访问后端）
# Docker部署：设置为 http://NAS_IP:8900（宿主机IP+Docker映射端口）
# 本地开发：可留空，系统自动从请求Host推断
DEFAULT_DOMAIN=http://YOUR_NAS_IP:8900
SERVER_URL=http://YOUR_NAS_IP:8900
```

### 为什么不支持 `http://localhost:8090` 自动适配 Docker IP？

| 假设场景 | 结果 | 原因 |
|---------|------|------|
| Docker容器内 `localhost:8090` | ❌ | `localhost` 指向容器自身，PDA无法访问；8090端口未映射 |
| 宿主机 `localhost:8900` | ⚠️ 仅宿主机本机能用 | PDA无法访问宿主机的 `localhost` |
| 宿主机真实IP `192.168.1.100:8900` | ✅ 正确 | 需要用户手动设置，或在浏览器访问 `/setup` 由系统自动推断 |

**最佳实践**：在宿主机的浏览器打开 `http://192.168.1.100:8900/setup`，系统会自动使用 Host 头（`192.168.1.100:8900`）生成二维码，无需手动配置 `SERVER_URL`。

## 涉及文件

| 文件 | 修改内容 |
|------|---------|
| `backend/app/config.py` | 新增 `DEFAULT_DOMAIN` 变量 |
| `backend/app/utils/__init__.py` 或 `backend/app/utils/server_url.py` (新增) | 公共 `get_server_url()` 函数 |
| `backend/app/routers/system.py` | `/setup` 改用 `get_server_url()` |
| `backend/app/routers/admin.py` | `/admin` 改用 `get_server_url()`，增加 fallback |
| `docker-deploy/.env.docker.example` | 新增 `DEFAULT_DOMAIN`，更新注释 |
| `backend/.env.docker.example` | 同步更新 |

## 不涉及的文件

- Android App 代码：PDA 端通过扫码或手动设置获取服务器地址，不受此影响
- Dockerfile：仅配置变更，不影响构建
- docker-compose.yml：仅端口映射，不变

## 验证步骤

1. `DEFAULT_DOMAIN` 为空时，浏览器访问 `/setup` 应使用 Host 头推断地址
2. `DEFAULT_DOMAIN=http://192.168.1.100:8900` 时，二维码内容应使用此值
3. `SERVER_URL` 优先级高于 `DEFAULT_DOMAIN`（两者同时设置时取 `SERVER_URL`）
4. `/admin` 页面二维码在 `SERVER_URL` 和 `DEFAULT_DOMAIN` 都为空时，增加显式提示"请配置环境变量或在浏览器访问 /setup"
5. 不影响现有 `SERVER_URL` 的行为（向后兼容）
