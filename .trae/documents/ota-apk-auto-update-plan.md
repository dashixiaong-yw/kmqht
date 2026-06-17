# OTA APK 自动更新功能计划

## 一、目标

实现快麦取货通 APK 的远程 OTA 自动更新功能：管理员通过管理后台上传新版本 APK → 点击"分发"一键发布 → 已安装该 APK 的所有 PDA 设备在下次启动时自动检测 → 自动下载 → 自动拉起安装。

## 二、成功标准

1. 后端支持分两阶段操作：**上传** APK 文件（暂存）+ **分发**（正式发布）
2. 后端提供版本查询 API（无需登录认证，PDA 启动时匿名访问）
3. PDA 端在 **每次 App 启动时** 自动检查版本更新
4. 发现新版本时 **自动下载** APK（后台下载，不阻塞界面）
5. 下载完成后 **自动拉起系统安装界面**
6. 设置页保留手动检查入口（点击版本号也可触发）
7. `./gradlew lint` 通过，`./gradlew assembleRelease` 构建成功
8. 后端更新同步到 docker-deploy

## 三、当前状态分析

| 维度 | 现状 | 备注 |
|------|------|------|
| 后端 `/api/app-version` | 已有，返回 `latestVersion` + `downloadUrl` | 缺少 changelog、forceUpdate、apkSize 字段 |
| 后端环境变量 | `APK_DOWNLOAD_URL`、`LATEST_VERSION` | 改为从 JSON 文件读取，便于管理后台修改 |
| 后端 APK 文件服务 | 无 APK 存储目录和静态文件挂载 | 新增 `/apk` 路由挂载 `/data/apk` 目录 |
| 后端管理后台 | 已有 6 个标签页 | 新增「APK 管理」标签页，支持上传+分发 |
| Android API 层 | `SystemApiService` 有 session/credentials 接口 | 新增 `getAppVersion()` 方法 |
| Android 启动流程 | `MainActivity.onCreate` → setContent → enqueueSyncWorker | 插入版本检查逻辑 |
| Android 下载/安装 | 无 | 全新实现：OkHttp 下载 + FileProvider + Intent 安装 |
| Android 权限 | `REQUEST_INSTALL_PACKAGES` 已声明 | 无需新增权限 |
| Docker 部署 | `docker-compose.yml` 映射 `/data` 卷 | 新增 `/data/apk` 子目录 |
| 同步脚本 | `sync-to-docker-deploy.ps1` | backend 目录同步即可，Dockerfile 无需修改 |

## 四、详细方案

### 4.1 后端：新增 APK 版本配置文件

**文件**: `backend/app/config.py`

```python
# APK 版本配置（用于 OTA 更新）
APK_DIR: str = os.getenv("APK_DIR", "/data/apk")
APK_VERSION_FILE: str = os.getenv("APK_VERSION_FILE", "/data/apk_version.json")
```

**版本信息 JSON 结构**（管理后台操作自动维护）：

```json
{
  "currentVersion": "1.18",
  "apkFileName": "快麦取货通-1.18.apk",
  "updateNotes": "",
  "forceUpdate": false,
  "publishedAt": "2026-06-15 10:00:00"
}
```

**单文件存储原则**：
- APK 目录 `/data/apk/` 始终只保留 **1 个** APK 文件
- 新版本上传并分发后，旧版 APK 文件 **立即删除**
- JSON 文件中只记录当前版本信息，无历史版本
- 简化管理，降低存储占用

### 4.2 后端：AppVersionResponse 模型增强

**文件**: `backend/app/models.py`

```python
class AppVersionResponse(BaseModel):
    """应用版本响应（供 PDA 端查询）"""
    success: bool = True
    message: str = "操作成功"
    latestVersion: str = ""        # 已分发的最新版本号
    downloadUrl: str = ""          # APK 下载地址
    updateNotes: str = ""          # 更新说明
    forceUpdate: bool = False      # 是否强制更新
    apkSize: int = 0               # APK 文件大小（字节）
    publishedAt: str = ""          # 分发时间
```

### 4.3 后端：版本查询 API（无需登录认证）

**文件**: `backend/app/routers/system.py`

- 从 JSON 读取 `currentVersion`、`apkFileName` 等字段返回
- 自动计算 `downloadUrl` = `{SERVER_URL}/apk/{apkFileName}`
- 自动获取 APK 文件大小
- **不要求登录认证**（PDA 启动时匿名调用）

```python
@router.get("/api/app-version", response_model=AppVersionResponse)
def get_app_version() -> AppVersionResponse:
    """获取当前分发的应用版本（无需认证，供PDA启动时自动检查）"""
    info = _load_version_info()
    if not info or not info.get("currentVersion"):
        return AppVersionResponse(latestVersion="", downloadUrl="")
    apk_path = os.path.join(APK_DIR, info.get("apkFileName", ""))
    apk_size = os.path.getsize(apk_path) if os.path.exists(apk_path) else 0
    server_url = SERVER_URL.rstrip("/") if SERVER_URL else ""
    return AppVersionResponse(
        latestVersion=info.get("currentVersion", ""),
        downloadUrl=f"{server_url}/apk/{info.get('apkFileName', '')}",
        updateNotes=info.get("updateNotes", ""),
        forceUpdate=info.get("forceUpdate", False),
        apkSize=apk_size,
        publishedAt=info.get("publishedAt", ""),
    )

def _load_version_info() -> dict:
    """从 JSON 文件读取版本信息"""
    try:
        with open(APK_VERSION_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}
```

### 4.4 后端：主入口挂载 APK 静态文件目录

**文件**: `backend/main.py`

```python
from app.config import APK_DIR

# startup_event 中
os.makedirs(APK_DIR, exist_ok=True)
if os.path.exists(APK_DIR):
    app.mount("/apk", StaticFiles(directory=APK_DIR), name="apk")
```

### 4.5 后端：Admin 页面新增 APK 管理标签页

**文件**: `backend/app/routers/admin.py`

#### 新增两个 API 接口

**上传接口**（暂存 APK 文件，不发布）：

```python
@router.post("/api/app-version/upload")
def upload_app_version(
    file: UploadFile = File(...),
    latestVersion: str = Form(...),
    updateNotes: str = Form(""),
    forceUpdate: bool = Form(False),
    user: dict = Depends(check_permission("settings")),
) -> BaseResponse:
```

逻辑：
1. 校验版本号格式
2. **删除 `/data/apk/` 目录下所有旧 APK 文件**（确保单文件原则）
3. 保存新 APK 到 `/data/apk/快麦取货通-{versionName}.apk`
4. 更新 JSON 文件中的 `currentVersion`、`apkFileName`、`updateNotes`、`forceUpdate` 字段（标记为「未分发」状态）
5. 返回 `{success: true, message: "上传成功，点击分发后所有PDA将收到更新"}`

**分发接口**（正式发布，旧 APK 已被覆盖无需再删）：

```python
@router.post("/api/app-version/publish")
def publish_app_version(
    user: dict = Depends(check_permission("settings")),
) -> BaseResponse:
```

逻辑：
1. 检查 JSON 中 `currentVersion` 是否有值
2. 记录 `publishedAt` 为当前时间
3. 写入 JSON 文件
4. 返回 `{success: true, message: "分发成功，所有PDA下次启动将自动更新"}`

> 说明：上传时已删除旧 APK 并保存新 APK，分发只是"激活"标记。

#### Admin HTML 新增「APK 管理」标签页

功能：
| 区域 | 内容 |
|------|------|
| **当前版本** | 显示 `currentVersion`、`publishedAt`（已分发时显示）、APK 大小、更新说明、强制更新状态 |
| **操作** | 如果已上传但未分发，显示「立即分发」按钮；如果已分发，显示当前版本信息 |
| **上传新版本** | 表单：APK 文件选择 + 版本号输入 + 更新说明文本域 + 强制更新勾选框 + "上传"按钮 |

### 4.6 Android：新增 AppUpdateDto

**文件**: `app/src/main/java/com/kuaimai/pda/data/api/dto/AppUpdateDto.kt`

```kotlin
package com.kuaimai.pda.data.api.dto

data class AppVersionResponse(
    val success: Boolean = true,
    val message: String = "操作成功",
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val updateNotes: String = "",
    val forceUpdate: Boolean = false,
    val apkSize: Long = 0,
    val publishedAt: String = ""
)
```

### 4.7 Android：SystemApiService 新增版本检查接口

**文件**: `app/src/main/java/com/kuaimai/pda/data/api/SystemApiService.kt`

```kotlin
/** 获取已分发的最新应用版本（无需 token，匿名访问） */
@GET("api/app-version")
suspend fun getAppVersion(): AppVersionResponse
```

### 4.8 Android：新增 AppUpdateManager

**文件**: `app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt`

核心功能类，通过 Hilt `@Inject` 注入单例。

```kotlin
@Singleton
class AppUpdateManager @Inject constructor(
    @Named("backend") private val retrofit: Retrofit,
    @ApplicationContext private val context: Context,
) {
    private val systemApi = retrofit.create(SystemApiService::class.java)

    /** 检查是否有新版本 */
    suspend fun checkForUpdate(): CheckResult

    /** 开始下载 APK，进度通过 StateFlow 通知 */
    val downloadState: StateFlow<DownloadState>

    /** 安装已下载的 APK */
    fun installApk(apkFile: File)
}

sealed class CheckResult {
    data class HasUpdate(val info: AppVersionResponse) : CheckResult()
    data object NoUpdate : CheckResult()
    data class CheckError(val message: String) : CheckResult()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}
```

技术要点：
- `checkForUpdate()` 比较 `latestVersion` 与 `BuildConfig.VERSION_NAME`（使用 `compareTo`）
- 下载使用 OkHttp + 流式写入，避免 OOM
- 下载目标：`context.cacheDir/update/快麦取货通-{version}.apk`
- 下载完成后校验文件大小是否匹配 `apkSize`
- `installApk()` 使用 FileProvider 生成 content URI，通过 `Intent.ACTION_VIEW` 启动安装
- `downloadState` 用 `StateFlow` 暴露给 UI 层订阅

### 4.9 Android：FileProvider 配置

**文件**: `app/src/main/AndroidManifest.xml`

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**新增文件**: `app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="apk_downloads" path="update/" />
</paths>
```

### 4.10 Android：启动时自动检查更新

**文件**: `app/src/main/java/com/kuaimai/pda/MainActivity.kt`

在 `onCreate()` 中新增自动检查逻辑：

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... 现有逻辑 ...

        // 启动时自动检查更新（非阻塞，失败不影响正常启动）
        lifecycleScope.launch {
            when (val result = appUpdateManager.checkForUpdate()) {
                is CheckResult.HasUpdate -> {
                    // 有更新 → 自动开始下载
                    appUpdateManager.downloadApk(result.info)
                    // 监听下载状态
                    lifecycleScope.launch {
                        appUpdateManager.downloadState.collect { state ->
                            when (state) {
                                is DownloadState.Completed -> {
                                    appUpdateManager.installApk(state.file)
                                }
                                // 失败静默处理，下次启动重试
                                else -> {}
                            }
                        }
                    }
                }
                // 无更新或检查失败 → 静默跳过
                else -> {}
            }
        }
    }
}
```

**下载通知**：下载期间显示一个系统通知（Notification），告知用户"正在下载更新…"，完成后通知变为"更新已就绪，点击安装"。

### 4.11 Android：设置页保留手动检查入口

**文件**: `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt`

版本号改为可点击，点击后手动触发检查更新流程（与启动时逻辑一致，仅触发方式不同）：

```kotlin
Text(
    text = "v${BuildConfig.VERSION_NAME}",
    modifier = Modifier
        .fillMaxWidth()
        .clickable {
            scope.launch {
                manualCheckUpdate()
            }
        },
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodySmall,
)
```

手动检查时显示对话框：
- 有新版本 → 版本信息弹窗（版本号 + 更新说明 + 大小 + "立即更新" / "稍后"）
- 已是最新 → Toast 提示
- 检查失败 → Toast 提示

### 4.12 后端：Android SystemApiService 获取后端 base URL 的问题

目前的 `@Named("backend")` Retrofit 实例的 base URL 是从 `EncryptedSharedPreferences` 读取用户扫码配置的服务器地址（如 `http://192.168.1.100:8900`）。

**问题**：`/api/app-version` 接口无需登录，PDA 在未登录状态下也能访问。但 Retrofit base URL 由 `NetworkModule` 提供，依赖 `PrefsKeys.SERVER_URL`。

**方案**：无需修改。因为 PDA 首次使用必须扫码配置服务器地址（或登录），此时 `SERVER_URL` 已写入偏好设置。启动时 `NetworkModule` 能正常构造 Retrofit 实例。

边界情况：如果用户清除 App 数据后首次启动，`SERVER_URL` 为空。此时 `checkForUpdate()` 返回 `CheckError`，静默跳过，不影响正常导航到引导页扫码。

### 4.13 项目规则更新

**文件**: `.trae/rules/README.md`

- 开发命令表：新增 `./gradlew assembleRelease` 的说明（已有）
- 项目结构：新增 `update/` 目录说明
- 工作流程中 Step 4 构建APK 后增加 OTA 上传分发步骤说明

### 4.14 部署说明

`docker-compose.yml` 无需修改，`./data:/data` 卷映射已包含子目录。

首次部署时：
```bash
mkdir -p ./data/apk
```

后续流程：
1. 本地构建 Release APK：`./gradlew assembleRelease`
2. 浏览器打开管理后台 → APK 管理 → 上传 APK + 填写版本信息
3. 确认后点击「立即分发」
4. 所有 PDA 下次启动时自动更新

## 五、涉及文件清单

### 后端（5 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/app/config.py` | **修改** | 新增 APK_DIR、APK_VERSION_FILE 常量 |
| `backend/app/models.py` | **修改** | AppVersionResponse 新增字段 |
| `backend/app/routers/system.py` | **修改** | get_app_version 从 JSON 读取 + 去掉登录依赖 |
| `backend/app/routers/admin.py` | **修改** | 新增 APK 管理标签页 + upload/publish 接口 |
| `backend/main.py` | **修改** | startup_event 挂载 /apk 静态目录 |

### Android（8 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/src/main/java/com/kuaimai/pda/data/api/dto/AppUpdateDto.kt` | **新建** | AppVersionResponse DTO |
| `app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt` | **新建** | 检查+下载+安装管理器 |
| `app/src/main/java/com/kuaimai/pda/data/api/SystemApiService.kt` | **修改** | 新增 getAppVersion() |
| `app/src/main/java/com/kuaimai/pda/MainActivity.kt` | **修改** | 启动时自动检查更新 |
| `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt` | **修改** | 版本号可点击 + 手动检查弹窗 |
| `app/src/main/AndroidManifest.xml` | **修改** | 添加 FileProvider |
| `app/src/main/res/xml/file_paths.xml` | **新建** | FileProvider 路径配置 |
| `app/src/main/java/com/kuaimai/pda/update/UpdateNotificationHelper.kt` | **新建** | 下载进度通知管理 |

### 规则文件（1 个）

| 文件 | 操作 | 说明 |
|------|------|------|
| `.trae/rules/README.md` | **修改** | 新增 OTA 更新流程说明 |

## 六、假设与决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 后端版本 API 是否需登录 | **不需要** | PDA 启动时未登录也能检查更新 |
| APK 存储策略 | **单文件模式**：始终只保留 1 个 APK，上传时自动删除旧文件 | 节省存储，管理简单 |
| 版本信息存储 | JSON 文件 | 轻量，管理后台可直接读写 |
| 上传/分发流程 | 上传(暂存+覆盖旧文件) → 分发(激活标记) | 防止误操作，管理员确认后再推送到所有设备 |
| Android 下载方式 | OkHttp 流式下载 | 可控进度，避免 OOM |
| Android 安装方式 | FileProvider + Intent.ACTION_VIEW | Android 7+ 标准方式 |
| 启动时检查失败处理 | **静默跳过**，不影响正常启动 | 非核心功能不应阻塞 App 使用 |
| 下载中 App 被杀 | 下次启动重新检查下载 | 幂等设计，无需断点续传 |
| 下载通知 | 使用 Notification | 让用户感知后台下载进度 |

## 七、边界情况与失败模式

| 场景 | 处理方式 |
|------|----------|
| 后端版本 API 不可达 | 静默跳过，不影响启动 |
| 下载中断/网络断连 | 捕获异常，静默失败，下次启动重试 |
| 存储空间不足 | 捕获 IOException，通知提示"存储空间不足，无法下载更新" |
| 已是最新版本 | 静默跳过 |
| 版本号对比 | 使用 `VersionNameComparator`（`1.18` vs `1.19`），确保比较正确 |
| 下载中用户退出 App | 后台线程继续下载，完成后发通知引导安装 |
| 多个 PDA 同时请求 | 后端无状态，每个设备独立下载 |
| APK 文件被误删 | `/api/app-version` 返回空，PDA 端静默跳过 |
| 强制更新弹窗被关闭 | 不可关闭（`setCancelable(false)`），仅"立即更新"按钮 |

## 八、验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. 后端启动后 `curl /api/app-version` 返回正确的已分发版本信息
4. 管理后台 → APK 管理 → 上传 APK → 版本信息展示正常
5. 点击「立即分发」→ JSON 文件中 `publishedAt` 字段更新
6. 构建旧版本 APK → 安装到 PDA → app 启动自动检测到新版本 → 自动下载
7. 下载完成 → 拉起系统安装界面
8. 安装成功后启动新版本 → 不再提示更新
