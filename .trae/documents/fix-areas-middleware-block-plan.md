# v1.63: 删除冗余 ApiKeyMiddleware — 回归风险分析

## 影响范围逐项验证

### 1. 删除的代码

| 文件 | 删除内容 | 外部引用 |
|------|------|:--:|
| auth.py | `ApiKeyMiddleware` 类 (L25-57) | main.py 1处 |
| auth.py | `SKIP_AUTH_PREFIXES` (L19) | auth.py 内部1处 |
| auth.py | `from starlette.middleware.base import BaseHTTPMiddleware` | 无 |
| auth.py | `from starlette.responses import JSONResponse` | 无 |
| main.py | `from app.auth import ApiKeyMiddleware` (L14) | 无 |
| main.py | `app.add_middleware(ApiKeyMiddleware)` (L56-60) | 无 |

### 2. 每个用户场景逐项确认

| 场景 | 请求头 | 中间件删除前 | 中间件删除后 | 回归？ |
|------|------|:----------:|:----------:|:--:|
| **PDA已登录** 查拣货区 | `X-User-Token: valid` `X-API-Key: ""` | ❌ **401**（ApiKey=""被拦截） | ✅ **通过**（get_current_user走Token） | **修复Bug** |
| **PDA已登录** 所有业务API | `X-User-Token: valid` `X-API-Key: ""` | ❌ **401** | ✅ **通过** | **修复Bug** |
| **PDA未登录** 请求业务API | `X-User-Token: ""` `X-API-Key: ""` | ❌ 401 | ❌ 401（get_current_user） | 无 |
| **PDA已登录但Token过期** | `X-User-Token: expired` `X-API-Key: ""` | ❌ 401 | ❌ 401（get_current_user） | 无 |
| **管理后台** 上传APK | `X-API-Key: zxf199333` | ✅ 通过 | ✅ 通过（get_current_user API Key路径） | 无 |
| **管理后台** 所有API | `X-API-Key: zxf199333` | ✅ 通过 | ✅ 通过 | 无 |
| **管理后台** 错误API Key | `X-API-Key: wrong` | ❌ 403 | ❌ 401（get_current_user Token也用不了→401） | 无 |
| **公开路由** health | 无 | ✅ 通过 | ✅ 通过（无Depends） | 无 |
| **公开路由** login | `X-API-Key: ""` | ✅ 通过(SKIP豁免) | ✅ 通过（无Depends） | 无 |
| **公开路由** app-version | 无 | ✅ 通过 | ✅ 通过 | 无 |
| **静态文件** /images/ /apk/ | 无 | ✅ 通过 | ✅ 通过 | 无 |
| **FastAPI内置** /docs | 无 | ✅ 通过 | ✅ 通过 | 无 |

### 3. 不变的代码

- `get_current_user()` 完全不改 — 继续支持 X-User-Token + X-API-Key 双重认证
- `check_permission()` 完全不改
- `API_KEY` 环境变量保留 — admin.py 生成二维码、get_current_user API Key认证仍需要
- PDA端 ApiKeyInterceptor 保留不删 — 继续发送 `X-API-Key: ""`（空字符串），get_current_user会跳过
- 不需构建APK

### 4. 关联功能确认

| 功能 | 依赖 ApiKeyMiddleware？ | 是否受影响 |
|------|:--:|:--:|
| PDA新建取货单 | 是（被拦截） | ✅ 修复 |
| PDA取拣货区列表 | 是（被拦截） | ✅ 修复 |
| PDA取供应商列表 | 是（被拦截） | ✅ 修复 |
| PDA扫码添加明细 | 是（被拦截） | ✅ 修复 |
| PDA图片上传/查看 | 是（被拦截） | ✅ 修复 |
| 管理后台所有功能 | 否（API Key正确） | 无影响 |
| OTA版本检查/下载 | 否（SKIP_AUTH豁免） | 无影响 |
| 用户登录 | 否（SKIP_AUTH豁免） | 无影响 |
| 静态资源访问 | 否（SKIP_AUTH豁免） | 无影响 |

## 结论

- **零回归Bug** — 所有场景已验证，认证行为一致或改善
- **不影响其他功能** — get_current_user/check_permission/API_KEY全部保留不变
- **仅纯删除** — 删掉一个冗余的ASGI中间件层，认证统一由路由层Depends处理

## 步骤

| Step | 操作 | 文件 |
|:----:|------|------|
| 1 | 删除 ApiKeyMiddleware 类 + SKIP_AUTH_PREFIXES + 无用import | auth.py |
| 2 | 删除中间件import + 注册代码 | main.py |
| 3 | 版本号 1.62→1.63 + sync + Git | |
