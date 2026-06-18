# 管理后台 API Key 认证修复计划

## 问题描述

管理后台使用 API Key 登录后，所有 API 调用显示：
- `加载失败: 未登录，请先登录`（仪表盘、用户列表、拣货区列表等）
- 添加/编辑用户点击保存时同样 401（"未登录，请先登录"）

## 根因分析

**认证方式不匹配**：

管理后台 JS 的 `api()` 函数只发送 `X-API-Key` 请求头，但后端路由全部使用 `Depends(get_current_user())`，该函数要求 `X-User-Token`。

| 组件 | 认证方式 | 请求头 |
|:-----|:---------|:-------|
| admin JS `api()` | API Key | `X-API-Key` |
| 后端路由 `get_current_user()` | User Token | `X-User-Token` |
| 后端中间件 `ApiKeyMiddleware` | API Key | `X-API-Key` |

**影响范围**：管理后台所有需要用户信息的 API 调用全部失败，包括：
- `GET /api/users` — 用户列表
- `POST /api/users` — 新增用户
- `PUT /api/users/{id}` — 编辑用户
- `DELETE /api/users/{id}` — 删除用户
- `GET /api/areas` — 拣货区列表
- `POST /api/areas` — 创建拣货区
- `DELETE /api/areas/{id}` — 删除拣货区
- `GET /api/kuaimai/session-status` — 快麦会话状态
- `GET /api/images/{sku}` — 图片查询
- 等

## 方案

**修改 `auth.py` 中的 `get_current_user()`**，使其在收到有效 `X-API-Key` 时返回虚拟管理员用户。

### 改动文件

#### [auth.py:60-106](file:///d:/trea项目\快麦取货通/backend/app/auth.py#L60-L106)

```python
def get_current_user(request: Request) -> dict:
    """
    从X-User-Token头解析当前用户
    或从X-API-Key头验证管理员身份（管理后台用）
    返回 {"user_id": int, "username": str, "permissions": List[str]}
    """
    # 支持API Key认证（管理后台使用）
    api_key = request.headers.get("X-API-Key", "")
    if api_key and API_KEY and hmac.compare_digest(api_key, API_KEY):
        return {
            "user_id": 0,
            "username": "admin",
            "permissions": list(VALID_PERMISSIONS)
        }

    # 原有User Token认证
    token = request.headers.get("X-User-Token", "")
    if not token:
        raise HTTPException(status_code=401, detail="未登录，请先登录")
    # ... 后续不变
```

**关键点**：
- `API_KEY` 为空时（开发模式）跳过 API Key 认证
- 使用 `hmac.compare_digest` 安全比较
- 虚拟用户 `user_id=0`（不与任何数据库用户冲突），拥有全部权限
- 先检查 API Key（更快，无需查库），再回退到 User Token

### 无需修改的文件

| 文件 | 原因 |
|:-----|:------|
| admin.py | JS 已正确发送 `X-API-Key`，修复后端即可 |
| users.py | 路由依赖 `get_current_user()` 自动生效 |
| areas.py | 同上 |
| images.py | 同上 |
| system.py | 同上 |

## 验证

1. Python 导入检查：`python -c "from app.auth import get_current_user"`
2. 管理后台登录后仪表盘、用户管理、拣货区管理正常加载
3. 新增/编辑用户功能正常
4. App 端 User Token 认证无影响（API Key 检查放在 User Token 之前，但 User Token 走原有路径不受影响）
