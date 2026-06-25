# 更新弹窗增加"浏览器下载"按钮方案（修订版）

## 方案

更新弹窗保留现有的「立即更新」按钮（内部下载），**新增**一个「在浏览器中下载」按钮。两个按钮共存，浏览器下载作为 Android 6.0 的兜底方案。

---

## 脑暴审查 — 9 项关联点

| # | 检查项 | 结论 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | **SSL 证书** | ⚠️ **最大风险** | 后端 `downloadUrl` 的域名 `frp-off.com` 若使用自签名证书或 Let's Encrypt（Android 6.0 CA 列表可能过期），**系统浏览器会显示安全警告页面，用户不会看到下载**。而 app 内部下载使用 `trustAll OkHttpClient` 绕过验证，不会有此问题。 |
| 2 | **PDA 无浏览器** | ⚠️ 需防御 | 部分国产 PDA 预装的是简易浏览器或无浏览器。`startActivity(intent)` 会抛 `ActivityNotFoundException`，需 try-catch 并给用户反馈 |
| 3 | **Android 6.0 Chrome 下载行为** | ✅ 正常 | 下载到 `/sdcard/Download/`，通知栏显示进度和完成提示，用户点通知可直接安装 |
| 4 | **AOSP 默认浏览器** | ⚠️ 可能异常 | 部分 Android 6.0 ROM 的默认浏览器不支持 APK 下载（直接显示乱码），但概率较小 |
| 5 | **`forceUpdate=true` 场景** | ✅ 没问题 | 按钮放在 text 区域内，不受 `onDismissRequest` 和 `dismissButton` 的限制，强制更新时也能用 |
| 6 | **下载中同时点浏览器** | ✅ 无冲突 | 两个下载走不同通道，互不干扰 |
| 7 | **`Intent.FLAG_ACTIVITY_NEW_TASK`** | ✅ 必要 | 从非 Activity Context（弹窗内）启动 Activity 必须加此 flag |
| 8 | **已有 import 检查** | ✅ 需新增 | MainActivity.kt 当前没有 import `android.content.Intent` 和 `android.net.Uri`，需新增 |
| 9 | **后端 Content-Type** | ✅ 正确 | `api/app-version/download` 已设置 `media_type="application/vnd.android.package-archive"`，浏览器自动识别下载 |

---

## 改动

### 文件：[MainActivity.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/MainActivity.kt)

**1. 新增 import**（文件顶部）：

```kotlin
import android.content.Intent
import android.net.Uri
```

**2. 弹窗 text 区域底部新增「在浏览器中下载」按钮**（位于 `downloadErrorMsg` 显示之后、`}` 闭合之前）：

```kotlin
if (downloadErrorMsg != null) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = downloadErrorMsg!!,
        color = androidx.compose.ui.graphics.Color(0xFFDC2626),
        fontSize = 13.sp
    )
}
// 【新增】浏览器下载按钮（始终可用，不依赖 isDownloading 状态）
Spacer(modifier = Modifier.height(12.dp))
TextButton(
    onClick = {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
        } catch (e: Exception) {
            downloadErrorMsg = "打开浏览器失败: ${e.message}"
        }
    }
) {
    Text(
        "在浏览器中下载",
        color = androidx.compose.ui.graphics.Color(0xFF2563EB),
        fontSize = 14.sp
    )
}
```

（使用 `catch (e: Exception)` 统一处理，涵盖 `ActivityNotFoundException` 等所有异常，无需额外 import。）

---

## 最终改动清单

| 文件 | 变动 | 行数 |
|:-----|:-----|:----:|
| MainActivity.kt | 新增 `import android.content.Intent, android.net.Uri` | 2 行 |
| MainActivity.kt | 弹窗 text Column 底部新增浏览器下载按钮 | ~15 行 |
| （无其他文件） | | |

## 验证

| 场景 | Android 6.0 | Android 10+ |
|:-----|:-----------|:-----------|
| 服务器有合法证书 | 弹窗 → 点浏览器下载 → 浏览器正常下载 → 通知栏点击安装 | 同左 |
| 服务器自签名证书 | 弹窗 → 点浏览器下载 → 浏览器显示安全警告（用户需手动确认） → 下载 → 安装 | 同左 |
| PDA 无浏览器 | 弹窗不关，显示"未找到浏览器"红色提示 | 同左 |
| 内部下载失败后 | 点浏览器下载兜底，不依赖 app 内部下载链路 | 同左 |

## 不涉及的

- 不改后端
- 不改 AppUpdateManager
- 不改 AndroidManifest
- 不引入新依赖
- 不修改版本号（等下次正常迭代一起提交）
