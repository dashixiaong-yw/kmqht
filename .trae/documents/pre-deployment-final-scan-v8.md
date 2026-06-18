# v1.31 终极全量扫描 — 剩余Bug与修复计划

> 四路并行扫描：安全/数据流/并发 + 后端/部署 + Compose UI + Android核心层
> 基准版本：v1.31 (3a06c70, 58 commits)
> 扫描时间：2026-06-18

---

## ✅ v1.31已验证修复

| 修复项 | 状态 |
|:-------|:----:|
| config.py import asyncio模块级 | ✅ |
| main.py ALL scheduler→_scheduler | ✅ |
| ImageCompressor recycle 在 Log 之后 | ✅ |
| CameraScanScreen DisposableEffect 释放 + 无 double-close | ✅ |
| HomeScreen 移除未使用 Color import | ✅ |
| PickListScreen CompletedOrdersList innerPadding | ✅ |
| PickItemRow clickable 在 clip 之前 | ✅ |
| PickOrderCard maxLines+overflow | ✅ |
| FilterChip maxLines+overflow | ✅ |
| ProductScreen TextOverflow | ✅ |
| GuideScreen qrScanError 成功解析重置 | ✅ |
| PickDetailScreen LazyColumn 移除 fillMaxSize | ✅ |
| ImageUploadSection 死代码删除 | ✅ |

---

## 🔴 CRASH — 必须立即修复（4项）

| # | 文件 | 行 | 问题 | 扫描来源 |
|:-:|:-----|:--:|:------|:--------:|
| **1** | `backend/main.py` | L109 | **缺少 `from typing import Optional`** → 容器重启时 `_scheduler: Optional[BackgroundScheduler]` NameError，模块加载立即崩溃 | 后端扫描 |
| **2** | `backend/main.py` | L72-L104 | **无 `@app.on_event("shutdown")`** → `_stop_scheduler()` 和 `stop_config_watcher()` 已定义但从未被调用，容器SIGTERM时调度器线程/监控任务泄漏 | 后端扫描 |
| **3** | `app/.../OrderSyncWorker.kt` | L221 | **`uploadImage()`返回值被忽略** → remoteId始终为0，后续delete_image用本地Room ID替代，误删错误资源；上传成功也不更新本地 ProductImageEntity | Android核心 |
| **4** | `app/.../ProductViewModel.kt` | L164-L171 | **serverUrl为空时生成相对路径URL** → `"$serverUrl${it.imageUrl}"` → `/images/xxx.jpg`，Coil加载失败，图片区空白（对比PickDetailViewModel有 isNotEmpty 检查） | Android核心 |

---

## 🟠 HIGH — 功能/安全严重缺陷（4项）

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **5** | `backend/.../system.py` | L165-L168 | **kuaimai_creds 写入无 `_config_lock`** → Web管理后台更新凭证时，与 API请求并发，`_build_common_params()` 读到新旧混合凭证导致MD5签名无效 | 后端+并发 |
| **6** | `backend/.../orders.py` | L498 | **`_cleanup_sku_images` 无路径穿越防护** → 无 `normpath` + `startswith` 检查（images.py delete有），数据库被篡改时可越界删文件 | 后端扫描 |
| **7** | `app/.../ImageRepository.kt` | L76-L97 | **`replaceImagesForSku` 全量替换可能误删本地图片** → 后端列表不完整时原子 DELETE+INSERT 会删除本地已上传但后端尚未返回的图片 | Android核心 |
| **8** | `app/.../KuaimaiInterceptor.kt` | L41-L44 | **凭证未配置时静默生成无效签名** → appKey/appSecret/session 为空时仍签名，快麦API返回模糊错误，无"凭证未配置"提示 | Android核心 |

---

## 🟡 MEDIUM — 功能性缺陷（7项）

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| 9 | `backend/.../kuaimai_api.py` | L59, L72 | `_call_api()` 读取凭证未持锁（与#5同根源） | 并发 |
| 10 | `backend/.../admin.py` | L59-L64 | APK上传无文件大小限制 → 可填满磁盘导致服务不可用 | 后端 |
| 11 | `backend/.../admin.py` | L508/L534/L603 | Pydantic camelCase vs JS snake_case → 用户状态始终显示禁用、创建时间均为`-` | 后端 |
| 12 | `app/.../App.kt + OrderSyncWorker.kt` | L40-L47 | `OrderSyncWorkerDeps lateinit` 在 WorkManager 提前初始化时可能未初始化崩溃 | Android核心 |
| 13 | `app/.../ProductScreen.kt` | L666-L683 | `finally` 中重复 close InputStream → 异常覆盖成功返回值导致 uriToFile 返回 null | Android核心 |
| 14 | `app/.../ScannerManager.kt` | L82-L84 | SoundPool 在快速 register/unregister 中引用覆盖泄漏 native 资源 | Android核心 |
| 15 | `backend/.../admin.py` | L507 | 权限回退渲染无 escapeHtml（低概率XSS） | 安全扫描 |

---

## 🔵 LOW — 轻微问题（3项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:--:|:------|
| 16 | `app/.../CameraScanScreen.kt` | L122 | BarcodeScanner native资源未在DisposableEffect释放（v1.31中已加过，确认下此处是否还残留旧实例） |
| 17 | `app/.../OrderSyncWorker.kt` | L14 | 未使用的 import PickItemDao |
| 18 | `app/.../PickDetailScreen.kt` | L321 | fillMaxSize + weight(1f) 同时使用（v1.31已修复过，确认无残留） |

---

## 修复优先级

### 🚨 P0 — 4项（部署阻断）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 1 | `from typing import Optional` | `main.py` L6 | 1行 |
| 2 | 添加 `@app.on_event("shutdown")` 调用 _stop_scheduler + stop_config_watcher | `main.py` L72-L104后 | 5行 |
| 3 | OrderSyncWorker 解析 uploadImage 返回值 → 更新 ProductImageEntity | `OrderSyncWorker.kt` L221 | 5行 |
| 4 | ProductViewModel serverUrl 空值检查 | `ProductViewModel.kt` L164-L171 | 2行 |

### ⚠️ P1 — 4项（功能严重）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 5 | system.py kuaimai_creds 写入加 _config_lock | `system.py` L165-L168 | 2行 |
| 6 | orders.py _cleanup_sku_images 加 normpath+startswith | `orders.py` L498 | 4行 |
| 7 | ImageRepository syncImagesFromBackend 改为增量合并 | `ImageRepository.kt` L76-L97 | 8行 |
| 8 | KuaimaiInterceptor 凭证空值检查+日志 | `KuaimaiInterceptor.kt` L41-L44 | 5行 |

### 📝 P2 — 7项（功能辅助+安全加固）

| # | 修改内容 | 文件 |
|:-:|----------|:-----|
| 9 | kuaimai_api.py _call_api() 凭证快照或加锁 | `kuaimai_api.py` |
| 10 | admin.py APK 上传加 100MB 大小限制 | `admin.py` |
| 11 | admin.js is_active→isActive / created_at→createdAt (3处) | `admin.py` |
| 12 | OrderSyncWorkerDeps 改为 @Volatile nullable | `App.kt` |
| 13 | ProductScreen 移除 finally 重复 close | `ProductScreen.kt` |
| 14 | ScannerManager register() 前先 soundPool?.release() | `ScannerManager.kt` |
| 15 | admin.js 权限回退 escapeHtml | `admin.py` |

---

## 验证步骤

1. `cd backend && python -c "from main import app; print('OK')"`
2. `.\gradlew :app:compileReleaseKotlin`（Step 3: lint）
3. `.\gradlew assembleRelease`（Step 4: 构建）
4. `.\scripts\sync-to-docker-deploy.ps1 -Force`（Step 7: 同步）
5. `git add -A && git commit -m "v1.32: ..." && git push`（Step 8: 提交）
