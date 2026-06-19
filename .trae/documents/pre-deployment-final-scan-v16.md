# v1.56: 首次安装登录全链路修复

## 双代理审计总结果

### Android 端：6项核心审计全部通过 ✅

| # | 审计项 | 结果 |
|:-:|--------|:----:|
| 1 | `DEFAULT_SERVER_URL` = `"https://frp-off.com:64623"` | ✅ |
| 2 | 首次启动 Retrofit baseUrl 正确 | ✅ |
| 3 | `http://localhost:1/` 占位 URL 不被触发 | ✅ |
| 4 | 路由判断正确到达 LOGIN 页 | ✅ |
| 5 | 登录 URL 拼接正确 | ✅ |
| 6 | GuideScreen 保存逻辑正确 | ✅ |

### 后端：25条路由认证全矩阵 ✅+1个风险

| 类别 | 数量 | 详情 |
|------|:----:|------|
| 通过 | 24 | 认证逻辑符合设计 |
| **需修复** | **1** | `/api/users/login` 首次安装无 API Key 被阻 |

---

## 🔴 核心问题：鸡生蛋循环

```
┌──────────────────────────────────────────────────┐
│  首次安装 → 打开 App → 登录页                       │
│                                                     │
│  点击登录 → POST /api/users/login                 │
│  ApiKeyInterceptor: X-API-Key: "" (空)            │
│  ↓                                                  │
│  ApiKeyMiddleware: if not api_key: → return 401   │
│  ↓                                                  │
│  LoginScreen: 显示 "用户名或密码错误" ← 不准确       │
│                                                     │
│  用户不知道需要 API Key，也不知道去哪里配置           │
└──────────────────────────────────────────────────┘
```

API Key 配置入口（GuideScreen/设置页）都在登录之后。登录需要 API Key → 配置入口在登录后 → 死循环。

### 修复：登录接口免 API Key

**文件**: [auth.py](file:///d:/trea项目\快麦取货通/backend/app/auth.py) L19

```diff
-SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", ..., "/api/app-version", "/apk/")
+SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", ..., "/api/app-version", "/apk/", "/api/users/login")
```

**安全性不受影响**：
- 登录路由自带限流保护（`_MAX_LOGIN_FAILS=5` 失败锁定5分钟）
- 快麦凭证同步等其他用户 API 仍需 API Key
- 与其他无需 API Key 的路由（health/app-version/download）安全级别一致

---

## 实现步骤

| Step | 操作 | 文件 |
|:----:|------|------|
| 1 | 修改 SKIP_AUTH_PREFIXES | auth.py L19 |
| 2 | 无需构建 APK（仅后端改动） | - |
| 3 | 版本号 1.55→1.56 | 6处 |
| 4 | 知识图谱 + sync-docker + Git push | - |

## 验证

部署容器重启后：清除 PDA 应用数据 → 首次打开 → 登录页 → 输入密码 → 登录成功，不再返回 401
