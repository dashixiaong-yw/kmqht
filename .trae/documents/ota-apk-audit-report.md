# OTA APK 自动更新 - 审计报告

## 审计范围

检查 OTA 自动更新功能的代码质量、Bug、回归风险，涉及文件共 14 个（后端 5 个 + Android 9 个）。

---

## 已通过的验证

- `./gradlew lint` ✅ 通过
- `./gradlew assembleRelease` ✅ 构建成功

---

## 发现的 Bug

### Bug 1 🔴 `forceUpdate` 字段未在前端使用

**严重程度**: 高
**文件**: [SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt) 第123-143行

**问题**: 后端 `AppVersionResponse.forceUpdate` 字段已正确返回，但 Android 端设置页的更新弹窗**始终显示"稍后再说"按钮**，未根据 `forceUpdate` 判断是否应隐藏。

**影响**: 管理员标记为"强制更新"时，用户仍然可以取消弹窗，无法强制用户更新。

**修复方案**: 在 `HasUpdate` 弹窗中判断 `state.info.forceUpdate`，为 true 时隐藏 dismissButton：

```kotlin
is UpdateCheckUiState.HasUpdate -> {
    AlertDialog(
        onDismissRequest = {
            if (!state.info.forceUpdate) viewModel.dismissUpdateCheck()
        },
        title = { Text("发现新版本") },
        text = { ... },
        confirmButton = {
            TextButton(onClick = {
                viewModel.startDownload(state.info)
                viewModel.dismissUpdateCheck()
            }) {
                Text("立即更新")
            }
        },
        dismissButton = {
            if (!state.info.forceUpdate) {
                TextButton(onClick = { viewModel.dismissUpdateCheck() }) {
                    Text("稍后再说")
                }
            }
        }
    )
}
```

---

### Bug 2 🟡 并发下载无防护

**严重程度**: 中
**文件**: [AppUpdateManager.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt) 第74-121行

**问题**: `downloadApk()` 每次调用都启动一个新线程。当 `MainActivity.onCreate` 自动检查触发下载后，若 Activity 重建（极少场景），会再次调用 `downloadApk()` 启动第二个线程同时写入同一文件，可能导致文件损坏。

**影响**: 极低概率（PDA 无旋转场景，Activity 很少重建），但存在隐患。

**修复方案**: 在 `downloadApk()` 开头检查当前状态，若已有下载进行中则跳过：

```kotlin
fun downloadApk(info: AppVersionResponse) {
    if (_downloadState.value is DownloadState.Downloading) return
    // ... rest of logic
}
```

---

### Bug 3 🟡 `SystemApiService.getAppVersion()` 返回值使用全限定名

**严重程度**: 中
**文件**: [SystemApiService.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/SystemApiService.kt) 第36行

**问题**: `com.kuaimai.pda.data.api.dto.AppVersionResponse` 使用全限定名，而同一文件中其他返回类型均使用 import 语句导入。这虽能编译，但反常规，容易被后续修改遗漏。

**影响**: 代码可维护性降低，不属于功能 Bug。

**修复方案**: 添加 import 并改为短名：

```kotlin
import com.kuaimai.pda.data.api.dto.AppVersionResponse

// ...
suspend fun getAppVersion(): AppVersionResponse
```

---

## 回归风险检查

### ✅ 后端 `get_app_version` API 变更

| 维度 | 旧行为 | 新行为 | 回归风险 |
|------|--------|--------|----------|
| 数据源 | 环境变量 `LATEST_VERSION` + `APK_DOWNLOAD_URL` | JSON 文件 `APK_VERSION_FILE` | **低** — 原有环境变量在现有部署中均为空，无实际调用者 |
| 认证 | 无需认证 | 无需认证（一致） | **无** |
| 响应格式 | `{latestVersion, downloadUrl}` | `{latestVersion, downloadUrl, updateNotes, forceUpdate, apkSize, publishedAt}` | **无** — 向前兼容，新增字段不会破坏旧客户端 |

### ✅ 后端 MainActivity 变更

| 变更 | 回归风险 |
|------|----------|
| `lifecycleScope.launch` 异步检查更新 | **无** — 非阻塞，失败时 catch 静默跳过，不影响原有 UI 和同步 Worker |
| 新增 `AppUpdateManager` 注入 | **无** — 通过 Hilt 注入，已有大量类似模式 |

### ✅ 后端 SettingsPage 变更

| 变更 | 回归风险 |
|------|----------|
| `updateCheckState` 收集 + 弹窗 | **无** — 独立的 `when` 分支，不影响已有 UI |
| 版本号添加 `.clickable` | **无** — 只新增了点击行为，不影响原有显示 |
| `Divider()` 已废弃警告 | **无** — 该警告已存在，非本次引入 |

### ✅ 后端 Router / Config 变更

| 文件 | 变更 | 回归风险 |
|------|------|----------|
| `config.py` | 新增 `APK_DIR`、`APK_VERSION_FILE` 常量 | **无** — 不影响已有配置项 |
| `models.py` | `AppVersionResponse` 新增 4 个字段 | **无** — 兼容扩展 |
| `main.py` | 新增 `/apk` 静态目录挂载 | **无** — 独立于 `/images` 挂载 |
| `admin.py` | 新增 APK 标签页 + upload/publish 接口 | **无** — 不影响现有 6 个标签页 |

### ⚠️ 已知但可接受的风险

| 风险 | 说明 | 处理策略 |
|------|------|----------|
| `checkForUpdate()` 依赖后端 base URL | 若用户清除 App 数据后首次启动（未扫码配置服务器），Retrofit 实例无法构造 | `catch` 中返回 `CheckError`，静默跳过，不影响引导页流程 |
| `SettingsViewModel` 中的 `startDownload` 使用 `viewModelScope` | ViewModel 被清理后，下载状态收集中断 | 下载线程继续运行，但安装需等待下次启动自动检测 |
| Apollo CSS HTML 中 `forceUpdate` 表单字段静态字符串 | Python f-string 模板字符串转义后 `${{r.forceUpdate}}` | 在原有 `${{}}` 转义约定下能正确执行 `r.forceUpdate`（值已由 JSON 序列化） |

---

## 修复优先级

| Bug | 优先级 | 说明 |
|-----|--------|------|
| Bug 1 - `forceUpdate` 未使用 | **P0 必须修复** | 直接影响管理员预期的强制更新功能 |
| Bug 2 - 并发下载无防护 | **P1 建议修复** | 极低概率，但修复简单 |
| Bug 3 - 全限定名 | **P2 可选修复** | 代码风格问题 |

---

## 结论

**未发现回归 Bug**。原有功能（登录、扫码、取货单管理、设置、离线同步等）均未受影响。

新增的 OTA 更新功能本身存在 **1 个功能缺陷（forceUpdate 未使用）** 和 **1 个并发隐患**，修复成本很低。
