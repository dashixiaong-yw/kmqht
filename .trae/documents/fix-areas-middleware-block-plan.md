# v1.63: ApiKeyMiddleware 不拦截已登录用户

## 根因

```
PDA 已登录用户的每个请求都带 X-User-Token（有效token）
                 ↓
        ApiKeyMiddleware 先用空 X-API-Key 拦截 → 401
                 ↓
    路由层 Depends(get_current_user) 从未执行
```

## 修复

[auth.py](file:///d:/trea项目/快麦取货通/backend/app/auth.py) 2 处改动：

### 1. ApiKeyMiddleware — 有 X-User-Token 就放行

```python
# L41 之前插入：
user_token = request.headers.get("X-User-Token", "")
if user_token:
    return await call_next(request)  # 交给路由层 Depends(get_current_user) 验证
```

### 2. get_current_user — 先验证 User Token

```python
# 把 L66-L73 的 API Key 短路移到 L76 的 Token 验证之后
```

登录后的请求链路变为：

```
PDA 请求 → X-User-Token 有效 → 中间件放行
         → Depends(get_current_user) → 查 user_tokens 表 → 通过
         → 业务路由执行
```

## 步骤

| Step | 操作 |
|:----:|------|
| 1 | auth.py 2处改动 |
| 2 | 版本号 1.62→1.63 + sync + Git |

纯后端改动，不构建 APK。
