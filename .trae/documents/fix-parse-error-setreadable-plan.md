# 解析错误修复方案

## 根因

```
APK 下载到 cacheDir/update/ → 文件权限 rw------- (仅本应用UID可读)
→ file:// URI 降级启动系统安装器
→ 安装器进程(不同UID) 读取 APK 文件内容 → 无权限 → "解析错误"
```

`FileProvider content://` URI 通过 `FLAG_GRANT_READ_URI_PERMISSION` 临时授权给安装器读取，所以没问题。但 `file://` URI 没有授权机制，完全依赖文件系统权限。

---

## 脑暴审查 — 10 项关联点

| # | 检查项 | 结论 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | `setReadable` 在 Android 6.0 上是否有效 | ✅ | API Level 1 就存在，所有版本可用 |
| 2 | `ownerOnly=false` 是否必要 | ✅ | `true` 只让本应用可读，安装器仍读不到 |
| 3 | `setReadable` 返回 false 如何处理 | ⚠️ 很罕见 | 文件系统不支持权限变更才会返回 false（如某些 ROM 的 tmpfs），此时 try-catch 兜住，原有安装失败逻辑继续 |
| 4 | 通知栏点击安装（ApkInstallReceiver）是否受影响 | ✅ 不受影响 | ApkInstallReceiver 始终使用 FileProvider content:// URI，不经过 file:// 降级 |
| 5 | 设置全局可读后的安全风险 | ✅ 无风险 | 文件在 cacheDir 中，其他应用正常情况下无法枚举 cacheDir 路径。只有知道完整路径才能读取。安装完成后用户通常会关闭安装界面，文件后续被缓存清理 |
| 6 | `在浏览器中下载` 兜底是否仍需要 | ✅ 需要保留 | 如果 `setReadable` 失败 + 安装器解析错误，浏览器下载到 `/sdcard/Download/` 是最终兜底 |
| 7 | 文件大小校验是否确保 APK 完整 | ✅ | `apkFile.length() != info.apkSize` 已校验，下载完整性没问题 |
| 8 | `setReadable` 调用时机 | ✅ | 放在 `file://` Intent 之前、try 块之内，与现有异常处理一致 |
| 9 | 是否需要额外 import | ✅ 不需要 | `File.setReadable()` 是 java.io.File 的原生方法 |
| 10 | 用户是否能看到文件变成全局可读 | ✅ 不需要 | 这是内部操作，用户无感知 |

---

## 修复

### 文件：[AppUpdateManager.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/update/AppUpdateManager.kt#L199)

在 `file://` 降级 Intent 启动前，将 APK 文件设为全局可读：

```kotlin
try {
    apkFile.setReadable(true, false)
    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(fallbackIntent)
    return true
} catch (e2: Exception) {
    Log.e(TAG, "file:// URI 降级也失败", e2)
}
```

`setReadable(true, false)` 的含义：
- `readable = true`：授予读取权限
- `ownerOnly = false`：不限于文件所有者（即所有用户可读）

---

## 改动清单

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| AppUpdateManager.kt | file:// 降级前加 1 行 `apkFile.setReadable(true, false)` | 1 行 |

---

## 版本号

2.20 → 2.21

---

## 完整 7 步流程

| Step | 内容 |
|:----:|------|
| 1 | 查阅知识图谱 |
| 2 | 修改 AppUpdateManager.kt（加 1 行 `setReadable`） |
| 3 | 版本号 2.20→2.21 |
| 4 | 构建 APK |
| 5 | 更新知识图谱 |
| 6 | 同步 docker-deploy |
| 7 | Git 提交 `v2.21: 修复 file:// 降级时系统安装器无权限读取APK导致解析错误` |

---

## 验证

| 场景 | 预期 |
|:-----|:------|
| Android 6.0 PDA 正常安装 | 下载 → content:// 失败 → file:// + setReadable → 安装界面弹出 → 安装成功 ✅ |
| setReadable 返回 false（极罕见） | 安装器无权限 → 解析错误 → 弹窗显示错误 → 用户点「在浏览器中下载」兜底 |
| 通知栏点安装 | FileProvider content:// URI，正常工作，不受影响 |
| 高版本 Android（7.0+） | FileProvider content:// URI，正常工作，不走 file:// 降级 |
