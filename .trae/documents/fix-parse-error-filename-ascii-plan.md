# APK 解析错误修复方案 v2

## 根因

手动安装 APK 正常，说明 APK 文件本身有效。OTA 升级时报"解析错误"是因为：

```
APK 下载到 /data/data/<pkg>/cache/update/  →  文件权限 rw-------
→ content:// URI 不被 PDA 识别（ActivityNotFoundException）
→ file:// URI 降级 + setReadable(true, false)
→ 安装器仍无法读取（URI 编码问题 / SELinux 阻断 / 两者叠加）
→ 解析错误
```

两个可能原因需同时修复：

| 原因 | 说明 | 修复方式 |
|:-----|:------|:---------|
| 中文文件名 `Uri.fromFile()` 编码 | `file:///.../快麦取货通-2.22.apk` 中文未编码 | 文件名改为 ASCII |
| SELinux 阻断 `/data/data/` 文件读取 | 即使 DAC 权限允许，SELinux 可能阻止跨进程访问 | 复制到外部存储再安装 |

---

## 脑暴审查 — 16 项关联点

| # | 检查项 | 结论 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | **缓存命中检测** | ✅ 无影响 | 旧文件名不会被查找，触发重下载。一次性，可接受 |
| 2 | **FileProvider XML 映射** | ✅ 无影响 | 按目录（`update/`）映射，不关心文件名 |
| 3 | **ApkInstallReceiver 通知栏安装** | ✅ 无影响 | 始终使用 FileProvider content://，不走 file:// 降级 |
| 4 | **后端 Content-Disposition 一致性** | ✅ 变好 | 后端已改 `kuaimai-{v}.apk`，与本改动一致 |
| 5 | **用户可见性** | ✅ 无影响 | 文件名在 cacheDir 内部，用户看不到 |
| 6 | **后端存储文件名** | ✅ 无冲突 | 后端磁盘文件 `快麦取货通-{v}.apk`，与本改动独立 |
| 7 | **旧版本缓存残留** | ✅ 无害 | 随系统清理自动删除 |
| 8 | **后端 QR 码下载 URL** | ✅ 无影响 | 不涉及文件名 |
| 9 | **管理后台上传** | ✅ 无影响 | 上传文件名与缓存文件名独立 |
| 10 | **通知显示** | ✅ 无影响 | 使用 `version` 参数，不读文件名 |
| 11 | **DownloadState 传递** | ✅ 无影响 | `DownloadState.Completed(file)` 存储 File 对象，调用者不需改 |
| 12 | **外部存储是否可用** | ⚠️ 需确认 | 部分 PDA 可能无外部存储或路径为空，已用 `?.` 保护 + 有兜底 |
| 13 | **外部缓存目录权限** | ⚠️ 需测试 | `/sdcard/Android/data/<pkg>/cache/` 系统安装器能否读取需实测 |
| 14 | **文件复制耗时长** | ✅ 无问题 | 复制与 APK 文件大小正相关（通常 10~50MB），在 try-catch 内 |
| 15 | **外部存储空间不足** | ⚠️ 已处理 | `copyTo` 抛异常 → catch → 通知 + 浏览器下载兜底 |
| 16 | **setReadable 是否仍需保留** | ✅ 保留 | 对外部存储的副本也设置，双层保险 |

---

## 修复

### 文件：[AppUpdateManager.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt)

#### 改动 1：下载文件名改为 ASCII（L115）

```kotlin
// 改前
val apkFile = File(dir, "快麦取货通-${info.latestVersion}.apk")

// 改后
val apkFile = File(dir, "kuaimai-${info.latestVersion}.apk")
```

#### 改动 2：file:// 降级改为复制到外部存储再安装（L195-L209）

```kotlin
} catch (e: ActivityNotFoundException) {
    Log.w(TAG, "FileProvider URI 不被识别，尝试复制到外部存储后安装", e)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        try {
            apkFile.setReadable(true, false)
            val extCache = context.getExternalCacheDir()
            if (extCache != null) {
                val destDir = File(extCache, "update")
                destDir.mkdirs()
                val destFile = File(destDir, apkFile.name)
                destFile.delete()
                apkFile.copyTo(destFile, overwrite = true)
                destFile.setReadable(true, false)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(destFile), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                return true
            }
        } catch (e2: Exception) {
            Log.e(TAG, "外部存储复制安装也失败", e2)
        }
    }
    // 最后兜底：显示错误 + 通知
    showNotificationFailed("未找到安装器，请到系统文件管理器手动安装")
    return false
}
```

**原理**：
- 将 APK 从 `/data/data/<pkg>/cache/update/`（SELinux context: `app_data_file`）复制到 `/sdcard/Android/data/<pkg>/cache/update/`（SELinux context: `media_file`）
- 系统安装器（system UID）可访问外部存储路径
- **`getExternalCacheDir()` 不需要任何运行时权限**
- 复制失败时 catch → 通知 + 「在浏览器中下载」兜底

---

## 改动清单

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| AppUpdateManager.kt | 下载 APK 文件名改为 `kuaimai-{version}.apk` | 1 行 |
| AppUpdateManager.kt | file:// 降级改为复制到外部存储再安装 | ~18 行 |

---

## 版本号

2.22 → 2.23

---

## 完整 7 步流程

| Step | 内容 |
|:----:|------|
| 1 | 查阅知识图谱 |
| 2 | 修改 AppUpdateManager.kt（文件名 ASCII + 复制到外部存储） |
| 3 | 版本号 2.22→2.23 |
| 4 | 构建 APK |
| 5 | 更新知识图谱 |
| 6 | 同步 docker-deploy |
| 7 | Git 提交 `v2.23: 修复APK解析错误（ASCII文件名+外部存储复制安装）` |

---

## 验证

| 场景 | 预期 |
|:-----|:------|
| Android 6.0 PDA 正常安装 | 下载 → content:// 失败 → 复制到外部存储 → file:// → 安装成功 ✅ |
| 外部存储不可用（null） | 不触发复制，直接通知错误 + 弹窗显示 + 浏览器下载兜底 |
| 复制过程失败（I/O 异常） | catch → 通知错误 + 弹窗显示 + 浏览器下载兜底 |
| 高版本 Android（7.0+） | FileProvider content:// URI 正常工作，不走降级路径 |
