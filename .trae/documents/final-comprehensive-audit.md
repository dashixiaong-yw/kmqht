# 最终全面审查报告

## 审查范围

全量审查 Android App（`app/`）+ 后端服务（`backend/`），覆盖：代码质量、逻辑正确性、性能、流程完整性、安全性。

---

## 审计结果

### P0 🔴 必须修复

#### 0.1 `/api/app-version` 被 API Key 中间件拦截

**文件**: [auth.py](file:///d:/trea项目/快麦取货通/backend/app/auth.py#L18)

```python
SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", "/redoc", "/openapi.json", "/admin", "/setup")
```

`/api/app-version` **不在跳过列表中**。当 `API_KEY` 配置后，PDA 请求此接口会被返回 401，导致更新检查永远失败。

**后端行为**：
- `API_KEY` 有值 → `ApiKeyMiddleware` 安装 → `/api/app-version` 被拦截 → PDA 收到 401 → `CheckResult.CheckError` → 静默跳过 → 用户永远检测不到更新
- `API_KEY` 为空 → 中间件不安装 → 正常

**修复**: 在 `SKIP_AUTH_PREFIXES` 中添加 `/api/app-version`：

```python
SKIP_AUTH_PREFIXES = ("/images/", "/health", "/docs", "/redoc", "/openapi.json", "/admin", "/setup", "/api/app-version")
```

---

### P1 🟡 建议修复

#### 1.1 `SettingsViewModel.startDownload` 多次调用导致重复 collect

**文件**: [SettingsViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsViewModel.kt#L105-L117)

每次调用 `startDownload()` 都启动一个 `viewModelScope.launch { downloadState.collect { ... } }`。如果用户在短时间内多次点击"立即更新"按钮，会创建多个 collect 协程，每个协程都会在 DownloadState.Completed 时触发 `installApk`，导致多次拉起系统安装界面。

**修复**: 在 `startDownload()` 中添加防重复标记：

```kotlin
private var isDownloadingUpdate = false

fun startDownload(info: AppVersionResponse) {
    if (isDownloadingUpdate) return
    isDownloadingUpdate = true
    appUpdateManager.downloadApk(info)
    viewModelScope.launch {
        appUpdateManager.downloadState.collect { state ->
            when (state) {
                is DownloadState.Completed -> {
                    appUpdateManager.installApk(state.file)
                    isDownloadingUpdate = false
                }
                is DownloadState.Failed -> {
                    isDownloadingUpdate = false
                }
                else -> {}
            }
        }
    }
}
```

#### 1.2 启动时自动检查与设置页手动检查同时触发

**文件**: [MainActivity.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/MainActivity.kt#L71-L85) + [SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt#L347)

App 启动时 `MainActivity.onCreate` 自动调用 `checkForUpdate()`。如果用户立即进入设置页点击版本号，会再触发一次 `checkForUpdate()`。两次检查可能并发执行，虽然 `downloadApk` 已有并发防护，但 HTTP 请求本身会重复浪费带宽。

**修复**: 添加应用级去重标记（在 `AppUpdateManager` 中）：

```kotlin
private val isCheckingUpdate = AtomicBoolean(false)

suspend fun checkForUpdate(): CheckResult {
    if (!isCheckingUpdate.compareAndSet(false, true)) {
        return CheckResult.CheckError("正在检查中")
    }
    return withContext(Dispatchers.IO) {
        try {
            // ... 原有逻辑 ...
        } finally {
            isCheckingUpdate.set(false)
        }
    }
}
```

#### 1.3 `downloadApk` 文件名与构建输出不一致

**文件**: [AppUpdateManager.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt#L81)

```kotlin
val apkFile = File(dir, "kuaimai-pickup-${info.latestVersion}.apk")
```

实际构建输出为 `快麦取货通-{versionName}.apk`。缓存文件命名不一致无功能性影响，但容易造成混淆。建议统一为 `快麦取货通-{version}.apk`。

---

### P2 🟢 建议项

#### 2.1 后端 APK 上传无文件类型校验

**文件**: [admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L29-L65)

`upload_app_version` 未校验上传文件的 MIME 类型或内容是否为合法 APK/ZIP 格式。有 `settings` 权限的管理员可上传任意文件。

**修复**: 检查文件名后缀和 MIME 类型：

```python
if not file.filename.endswith(".apk"):
    raise HTTPException(status_code=400, detail="仅支持 .apk 文件")
mime = file.content_type or ""
if mime and "octet-stream" not in mime and "java-archive" not in mime:
    raise HTTPException(status_code=400, detail="文件类型不合法")
```

#### 2.2 后端版本号路径穿越风险

**文件**: [admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L47)

```python
apk_filename = f"快麦取货通-{latestVersion}.apk"
```

若 `latestVersion` 包含 `../` 或 `/`，可能造成路径穿越。应校验版本号格式：

```python
import re
if not re.match(r'^\d+\.\d+$', latestVersion.strip()):
    raise HTTPException(status_code=400, detail="版本号格式错误，仅支持主版本.次版本（如 1.22）")
```

#### 2.3 `SettingsScreen.Divider` 废弃警告

**文件**: [SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt#L268)

`Divider()` 已废弃，应改为 `HorizontalDivider()`。该警告非本次引入，但建议一并修复。

```kotlin
// 将:
import androidx.compose.material3.Divider
Divider()
// 改为:
import androidx.compose.material3.HorizontalDivider
HorizontalDivider()
```

#### 2.4 后端 JSON 文件并发写无锁

**文件**: [admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L82-L94) + [system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L29-L35)

`_load_version_info()` / `_save_version_info()` 使用简单文件读写，无文件锁。两个管理请求并发上传/分发时可能覆盖写入。FastAPI 单线程同步处理无此问题，但在异步上下文中（如 Gunicorn 多 worker）可能存在。

**修复（当前单 worker 场景可忽略，留作备注）**:

```python
import fcntl

def _save_version_info(info: dict) -> None:
    with open(APK_VERSION_FILE, "w", encoding="utf-8") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        json.dump(info, f, ensure_ascii=False, indent=2)
        fcntl.flock(f, fcntl.LOCK_UN)
```

> 注：Docker 单 worker 部署下此问题不存在，仅多 worker 时需要。**当前暂不修复**。

---

## 回归风险确认

| 维度 | 结论 |
|------|------|
| 原有 Android 功能（登录、扫码、取货单、设置） | ✅ 无影响 |
| 原有后端 API（取货单、图片、用户、快麦） | ✅ 无影响 |
| 原有管理后台 6 个标签页 | ✅ 无影响 |
| 定时任务（超时检查、清理） | ✅ 无影响 |
| Docker 部署 | ✅ 无影响（sync 脚本已同步） |
| 启动后下载线程泄漏 | ✅ 无泄漏（lifecycleScope 自动取消） |

---

## 总结

| 优先级 | 问题 | 影响 |
|--------|------|------|
| **P0** | `/api/app-version` 被 API Key 中间件拦截 | PDA 无法检查更新（API_KEY 配置时） |
| **P1** | `startDownload` 重复 collect | 多次拉起系统安装界面 |
| **P1** | 启动 + 手动检查同时触发 | 重复 HTTP 请求 |
| **P1** | 下载文件名不一致 | 混淆，不影响功能 |
| **P2** | APK 上传无类型校验 | 可上传任意文件 |
| **P2** | 版本号路径穿越 | 安全防护缺失 |
| **P2** | Divider 废弃警告 | 编译警告 |

P0 问题必须修复，P1 建议修复，P2 可选修复。
