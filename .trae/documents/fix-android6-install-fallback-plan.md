# Android 6.0 OTA 安装失败修复方案

## 问题定位

测试结果明确显示：

```
APK 下载成功 → installApk 调用 FileProvider content:// URI
→ PDA ROM 安装器无法识别 content:// URI → ActivityNotFoundException
→ 通知栏提示 "未找到安装器，请到系统文件管理器手动"
→ 对话框回到初始状态，用户看不到任何错误信息
```

**两个问题**：

| 问题 | 位置 | 影响 |
|:-----|:-----|:------|
| **installApk `content://` URI 不被 Android 6.0 PDA 识别** | AppUpdateManager.kt L183-L193 | 安装直接失败 |
| **`installApk` 返回 `false` 时对话框未设置错误信息** | MainActivity.kt L164-L171 | 用户只看得到通知栏错误，弹窗无反馈 |

## 完整流程（7 步）

| Step | 阶段 | 内容 |
|:----:|:----:|------|
| 1 | 开发 | 查阅知识图谱 |
| 2 | 开发 | 修改 AppUpdateManager.kt + MainActivity.kt |
| 3 | 版本号 | 2.18 → **2.19** |
| 4 | 构建 | `./gradlew assembleRelease` |
| 5 | 发布 | 更新知识图谱 |
| 6 | 发布 | 同步 docker-deploy |
| 7 | 发布 | Git 提交推送 `v2.19: Android 6.0 安装失败降级修复` |

## 改动

### 改动 1：AppUpdateManager — installApk 增加 `file://` URI 降级

**文件**：[AppUpdateManager.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt#L195-L198)

```kotlin
} catch (e: ActivityNotFoundException) {
    Log.w(TAG, "FileProvider URI 不被识别，尝试 file:// URI 降级", e)
    // Android 6.0 (API < 24) 支持 file:// URI，FileUriExposedException 从 Android 7.0 开始
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            return true
        } catch (e2: Exception) {
            Log.e(TAG, "file:// URI 降级也失败", e2)
        }
    }
    showNotificationFailed("未找到安装器，请到系统文件管理器手动安装")
    return false
}
```

**原理**：`FileUriExposedException` 只在 Android 7.0+ (API 24+) 上禁止 `file://` URI。Android 6.0 及以下完全支持。如果 PDA 的安装器不认识 `content://` URI，退回到 `file://` 就能正常打开安装界面。

### 改动 2：MainActivity — installApk 失败时设置错误信息

**文件**：[MainActivity.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/MainActivity.kt#L164-L171)

```kotlin
is DownloadState.Completed -> {
    val installed = appUpdateManager.installApk(state.file)
    if (installed) {
        showUpdateDialog = false
        updateInfo = null
    } else {
        isDownloading = false
        downloadErrorMsg = "下载成功，但安装失败：系统未找到安装器，请点击「在浏览器中下载」重新下载后手动安装"
    }
}
```

## 效果

### 改前流程

```
点击立即更新 → 下载成功 → FileProvider URI → ActivityNotFoundException
→ 通知栏错误(用户可能没看到) → 对话框回到初始状态
→ 用户看到对话框以为下载失败 → 再次点击 → 循环
```

### 改后流程

```
点击立即更新 → 下载成功 → FileProvider URI → ActivityNotFoundException
→ file:// URI 降级 → 安装界面弹出 ✅
                              （Android 6.0 大部分 PDA 可工作）

如果降级也失败 → 对话框显示红色错误文字：
  "下载成功，但安装失败：系统未找到安装器，请点击「在浏览器中下载」重新下载后手动安装"
                  + 「在浏览器中下载」按钮（已存在）
```

## 改动清单

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| AppUpdateManager.kt | installApk ActivityNotFoundException 增加 `file://` URI 降级 | ~15 行 |
| MainActivity.kt | installApk 返回 false 时设置 downloadErrorMsg | ~3 行 |

**涉及的**：
- 更新版本号：2.18 → 2.19
- 改 AppUpdateManager.kt
- 改 MainActivity.kt

**不涉及的**：
- 不改后端
- 不改 AndroidManifest
- 不引入新依赖
