# v1.45 - v1.54 版本审计报告

## 审计概况

5 路并行代理审计了 10 个版本，深入阅读了 30+ 个源文件。

| 审计轮次 | 版本 | 缺陷数 | 风险点数 |
|:--|:--|:--:|:--:|
| 组1 | v1.45 + v1.46 | 0 | 2（低风险） |
| 组2 | v1.47 + v1.48 | 0 | 2（低风险） |
| 组3 | v1.49 + v1.50 | 1 | 3 |
| 组4 | v1.51 + v1.52 | 2 | 2 |
| 组5 | v1.53 + v1.54 | 1 | 3 |
| **合计** | **10个版本** | **4** | **12** |

---

## 缺陷清单

### 🔴 P0（1 项）

| # | 版本 | 描述 | 位置 |
|---|:--:|------|------|
| **P0-1** | v1.75引入 | **`orders.py` L82 语法错误**：三个 `HTTPException(...)` 调用挤在同一行无分号分隔，Python 无法解析，后端启动即崩溃。这是 v1.75 修复 `add_item` IntegrityError 时 search/replace 误匹配 `create_order` 中 `raise` 行导致的 | [orders.py:L82](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L82) |

### 🟠 P1（1 项）

| # | 版本 | 描述 | 位置 |
|---|:--:|------|------|
| **P1-1** | v1.50 | **AppUpdateManager APK 下载缺少 SSL 绕过**：`downloadApk()` 中的 OkHttpClient 没配 `hostnameVerifier + sslSocketFactory`，FRP HTTPS 场景下下载 APK 抛 `SSLHandshakeException`。与 v1.71 修复过的 ImageUploadService 同根同源 | [AppUpdateManager.kt:L47-L50](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt#L47-L50) |

### 🟡 P2（2 项）

| # | 版本 | 描述 | 位置 |
|---|:--:|------|------|
| **P2-1** | v1.52→v1.73 | **PickItemRow 规格图触摸热区回归**：v1.52 修复为 56dp，v1.73 重构时退回 52dp（当前代码 `Modifier.size(52.dp)` 而非 56dp）。与用户确认：按原型还原即可，不需要强制 56dp，但本次审计确认此回归存在 | [PickItemRow.kt:L86-L88](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt#L86-L88) |
| **P2-2** | v1.54 | **`apkFileName` 为空时文件存在检查被绕过**：`os.path.join(APK_DIR, "")` 返回目录路径，`os.path.exists()` 对目录返回 `True`，空文件名绕过 APK 文件缺失保护 | [system.py:L95-L98](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L95-L98) |

### 🟢 P3（3 项）

| # | 版本 | 描述 |
|---|:--:|------|
| P3-1 | v1.57 | system.py 注释过时：`get_app_version_qrcode` 注释说"需 API Key 认证"，实际 v1.65 后已公开 |
| P3-2 | v1.47 | `/apk` 静态文件挂载冗余：`main.py` 中 `app.mount("/apk", ...)` 与 `download_apk()` 功能重复，且无需认证 |
| P3-3 | v1.52 | `publish_app_version` 未验证 APK 文件是否存在于磁盘，可能产生"分发成功"但 PDA 无更新可下的矛盾 |

### ⚪ 低优先级（7项）

| # | 描述 |
|---|------|
| R1 | restore_item TOCTOU 并发窗口（SELECT+MUTATE 无事务保护） |
| R2 | system.py L98 冗余 `os.path.exists()` 检查（L97 已确保） |
| R3 | admin.py `loadApk()` 与 `renderApkCard()` 版本卡片模板重复 |
| R4 | config.py SERVER_URL 注释写死 `http://`（应为 `http(s)://`） |
| R5 | docker-compose.yml env_file 引用 `.env.docker.example` 泄露 API Key |
| R6 | v1.50 CHANGELOG 声称"引导页校验规则已兼容 https"但实际无校验规则 |
| R7 | NetworkModule SSL 绕过逻辑应提取为可复用公共类 |

---

## 修复方案

### P0-1：orders.py L82 语法修复

**现状**：
```python
raise HTTPException(status_code=409, detail="该SKU已存在于取货单中") HTTPException(status_code=409, detail="该SKU已存在于取货单中") HTTPException(status_code=500, detail="创建取货单失败，请稍后重试")
```

**修复**：
```python
raise HTTPException(status_code=500, detail="创建取货单失败，请稍后重试")
```

这是 `create_order` 的重试失败分支（单号冲突重试 3 次用尽后），正确的错误是 500 "创建取货单失败"。

---

### P1-1：AppUpdateManager SSL 绕过

**文件**：[AppUpdateManager.kt:L47-L50](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt#L47-L50)

```kotlin
// 修改前
private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

// 修改后：复用 NetworkModule 的 unsafeSslSocketFactory 和 unsafeTrustManager
// 方案：注入 SSLSocketFactory + X509TrustManager 到 AppUpdateManager 构造函数
// 或直接在 Builder 中内联绕过逻辑（与 NetworkModule 保持一致）
```

---

### P2-2：system.py apkFileName 空值防御

**文件**：[system.py:L95-L98](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L95-L98)

```python
# 修改前
apk_path = os.path.join(APK_DIR, info.get("apkFileName", ""))
if not os.path.exists(apk_path):

# 修改后
apk_filename = info.get("apkFileName", "")
if not apk_filename:
    return AppVersionResponse(latestVersion="", downloadUrl="")
apk_path = os.path.join(APK_DIR, apk_filename)
if not os.path.exists(apk_path):
```

---

## 修改清单

| # | 优先级 | 文件 | 改动 |
|---|:--:|------|------|
| 1 | 🔴P0 | `backend/app/routers/orders.py` L82 | 删除多余的 `HTTPException(409)`，保留一行 `HTTPException(500)` |
| 2 | 🟠P1 | `app/.../AppUpdateManager.kt` L47-L50 | OkHttpClient 添加 SSL 绕过 |
| 3 | 🟡P2 | `backend/app/routers/system.py` L95-L98 | apkFileName 空值检查 |
| 4 | 🟢P3 | `backend/app/routers/system.py` L148 | 注释更新（移除"需 API Key 认证"） |
| 5 | 🟢P3 | `backend/main.py` L91-L93 | 移除或条件化 `/apk` 静态挂载 |
| 6 | 🟢P3 | `backend/app/routers/admin.py` L102 | publish_app_version 增加 APK 文件存在性验证 |

---

## 验证步骤

1. **P0 验证**：后端启动成功（Python 语法通过），`POST /api/orders` 正常创建取货单
2. **P1 验证**：HTTPS 环境下 PDA 检查更新 → APK 下载成功（不抛 SSLHandshakeException）
3. **P2 验证**：手动清空 `apk_version.json` 的 `apkFileName` → `/api/app-version` 返回空版本号
4. `./gradlew lint` 通过
5. `./gradlew assembleRelease` 构建成功
