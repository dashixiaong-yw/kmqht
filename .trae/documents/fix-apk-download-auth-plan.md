# APK下载扫码 401 修复 (v1.51)

## 问题现象

扫码 APK 下载二维码后，PDA 浏览器显示：
```json
{"success": false, "message": "缺少API Key"}
```

## 根因

[auth.py](file:///d:/trea项目\快麦取货通/backend/app/auth.py) L19 的 `SKIP_AUTH_PREFIXES` 包含 `/api/app-version`，但 L36-38 有精确匹配保护逻辑：

```python
if prefix == "/api/app-version" and request.url.path != "/api/app-version":
    break  # ← 只有精确 /api/app-version 才跳过认证
```

这导致：
| URL | 行为 | 正确？ |
|-----|:----:|:------:|
| `GET /api/app-version` | 跳过认证 ✅ | 正确 |
| `GET /api/app-version/download` | 被拦截 ❌ → 401 | **错误** |
| `GET /api/app-version/qrcode` | 被拦截 ❌ → 401 | **错误** |
| `POST /api/app-version/upload` | 被拦截 ✅ → 401 | 正确（需API Key） |
| `POST /api/app-version/publish` | 被拦截 ✅ → 401 | 正确（需API Key） |

**管理后台 JS** 调用 `/api/app-version/qrcode` 时有 API Key header 所以正常返回二维码。但二维码内容 URL `https://frp-off.com:64623/api/app-version/download` 被 PDA 浏览器打开时**没有 API Key**，被中间件拦截。

## 修复方案

### 方案：SKIP_AUTH_PREFIXES 中增加 download 和 qrcode

**文件**: [auth.py](file:///d:/trea项目\快麦取货通/backend/app/auth.py) L19

```diff
-SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", "/redoc", "/openapi.json", "/setup", "/api/app-version", "/apk/", "/apk-download")
+SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", "/redoc", "/openapi.json", "/setup", "/api/app-version", "/api/app-version/download", "/api/app-version/qrcode", "/apk/", "/apk-download")
```

L36-38 的精匹配保护逻辑保持不变，新增的 `/api/app-version/download` 和 `/api/app-version/qrcode` 作为独立前缀，会直接 `return await call_next(request)` 跳过认证。

## 验证

扫码 APK 二维码后，浏览器应直接下载 APK 文件，不再返回 401 JSON。
