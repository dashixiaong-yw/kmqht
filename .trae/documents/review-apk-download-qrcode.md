# APK 下载二维码功能 — 自查报告

## 审计范围

审查上次实现的 3 个文件修改，确认无逻辑缺陷、无回归风险。

---

## 自查结果：0 Bug，全部通过 ✅

### 1. auth.py:18 — `/apk/` 加入 SKIP_AUTH_PREFIXES

| 检查项 | 结论 |
|:-------|:-----|
| 路径前缀冲突 | ✅ `"/apk/"` 带尾部斜杠，不会误配 `/api/` 系路径 |
| 遍历顺序影响 | ✅ `startswith` 独⽴检查每个前缀，顺序不影响 |
| 仅暴露 APK 下载 | ✅ 浏览器直链下载无需 API Key，与 `/images/` 策略一致 |

### 2. system.py:109-120 — `GET /api/app-version/qrcode` 新接口

| 检查项 | 结论 |
|:-------|:-----|
| 路由注册顺序 | ✅ FastAPI 精确匹配路径，`/api/app-version` 不影响 `/api/app-version/qrcode` |
| 认证拦截 | ✅ auth 中间件对 `/api/app-version/qrcode` 执行 `break`，走 API Key 检查 |
| 未分发时返回值 | ✅ `{"success":false, "qrcode":""}` — status 200，不会 404 |
| path traversal | ✅ `apkFileName` 由上传 API 校验为 `快麦取货通-{version}.apk` 格式 |
| URL 拼接 | ✅ 与 `get_app_version` 使用同一逻辑（`SERVER_URL` → `request.base_url`） |
| import 正确 | ✅ `from app.utils.qr_utils import generate_qr_base64` |

### 3. admin.py:740-751 — 管理后台显示下载二维码

| 检查项 | 结论 |
|:-------|:-----|
| DOM 时序 | ✅ `innerHTML` 先同步渲染 `<div id="apkQrCode">`，async 回调后操作此元素 |
| 重复调用安全 | ✅ 每次 `loadApk()` 重建卡⽚和 `apkQrCode` div，各回调操作最新 DOM |
| XSS 防护 | ✅ `qrResp.qrcode` 为服务端生成的 base64（仅 `a-zA-Z0-9+/=`），非用户输入 |
| API Key 缺失处理 | ✅ `.catch()` 捕获 401 并 `console.warn`，不影响页面其他功能 |
| 上传后状态切换 | ✅ upload 清除 `publishedAt`，QR 代码块不加载 |
| 分发后状态切换 | ✅ publish 设置 `publishedAt`，`loadApk()` 重新渲染并加载 QR |
| 登录前/后一致性 | ✅ 未登录时 `api()` 失败 → catch 静默；登录切换回 APK 标签时重新加载 |

---

## 核心检查点验证

### 认证流程
```
浏览器请求 /apk/快麦取货通-1.41.apk
  → ApiKeyMiddleware.startswith("/apk/") → True
  → return await call_next(request)  ✅ 跳过认证
  → FastAPI StaticFiles 直接返回文件
```

### QR 码 API 认证流程
```
管理后台 JS → api('/api/app-version/qrcode') → 携带 X-API-Key header
  → ApiKeyMiddleware.startswith("/api/app-version") → True
  → prefix == "/api/app-version" and path != "/api/app-version" → break
  → X-API-Key 检查 → hmac.compare_digest → 通过
  → system.py 生成 QR 码返回  ✅ 需 API Key，与 upload/publish 一致
```

---

## 结论

**0 Bug，0 回归风险。** 3 处修改均正确，认证边界清晰，JS 异步逻辑无竞态，可部署。
