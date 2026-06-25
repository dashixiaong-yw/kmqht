# 移除浏览器下载入口 — OTA已稳定工作

## 背景

OTA 更新已稳定可用（AppUpdateManager downloadApk + installApk），不再需要浏览器下载作为兜底。

## 审计 — 完整性/前置条件/回归风险

### 1. MainActivity.kt — 移除"在浏览器中下载"按钮

| 检查项 | 状态 | 说明 |
|:-------|:----:|:------|
| `info.downloadUrl` 是否被 OTA 下载器使用 | ✅ 保留 | `AppUpdateManager.downloadApk()` L121 仍使用 `info.downloadUrl`，端点不变 |
| 移除后下载失败时用户还有什么路径 | ✅ 可重试 | 弹窗不关闭，用户可点"立即更新"重试，下载器会自动缓存检查 |
| `Intent` import 是否仍被使用 | ❌ 移除后无用 | 仅浏览器下载处用到，删除后 import 行需移除 |
| `Uri` import 是否仍被使用 | ❌ 移除后无用 | 同上 |
| 弹窗布局是否完整 | ✅ | 移除了 `TextButton` 块 + 前后的 `Spacer`，column 内剩余元素自洽 |
| L171 错误提示引用浏览器下载 | ✅ | 改成`"下载成功，但安装失败：系统未找到安装器，请手动安装 APK 文件"` |

#### 改动 A — 删除 TextButton

```kotlin
// 删除 L133-L151:
// Spacer(modifier = Modifier.height(12.dp))    ← 删除
// TextButton(... "在浏览器中下载" ...)         ← 删除
```

#### 改动 B — 修改错误提示（L171）

改前：
```kotlin
downloadErrorMsg = "下载成功，但安装失败：系统未找到安装器，请点击「在浏览器中下载」重新下载后手动安装"
```
改后：
```kotlin
downloadErrorMsg = "下载成功，但安装失败：系统未找到安装器，请手动安装 APK 文件"
```

#### 改动 C — 移除未使用的 import

```kotlin
import android.content.Intent    // ← 删除
import android.net.Uri            // ← 删除
```

---

### 2. system.py — 移除 QR Code 接口

| 检查项 | 状态 | 说明 |
|:-------|:----:|:------|
| Android 端是否调用了 `/api/app-version/qrcode` | ✅ 无引用 | 全局搜索 `qrcode`，Android 端无调用 |
| 删除后是否会中断其他功能 | ✅ 不会 | 仅 admin.py 前端 JS 调用了此接口，同步删除对应前端代码即可 |
| `generate_qr_base64` import 是否仍被使用 | ❌ 移除后无用 | 此导入仅被该路由使用，删除后需移除 import 行 |

**改动**：删除 L27 的 import + L159-L170 的整个函数定义。

---

### 3. admin.py — 移除 APK 下载二维码

| 检查项 | 状态 | 说明 |
|:-------|:----:|:------|
| `id="apkQrCode"` 出现多少次 | 2 处 | `loadApk()` L811 和 `renderApkCard()` L877，都是 `<div id="apkQrCode"></div>` |
| 删除 JS 后 `api('/api/app-version/qrcode')` 调用 | 1 处 | L817 的 JS fetch 调用 |
| 是否影响 PDA 配置二维码 | ✅ 不影响 | 仪表盘配置二维码（`{qr_html}`）由独立的 `_build_admin_html` 生成，与 APK 二维码无关 |
| 是否影响上传/分发功能 | ✅ 不影响 | `uploadApk()`、`publishApk()`、以及 APK 管理卡片的信息展示全部保留 |

**改动**：
1. 删除 L816-L822 的 JS 代码块（发起 qrcode 请求并渲染）
2. 删除 L811 和 L877 的 `<div id="apkQrCode"></div>` 占位元素

---

### 4. 保留确认

| 功能 | 保留确认 | 路径 |
|:-----|:--------:|:-----|
| OTA 版本检查接口 `/api/app-version` | ✅ | system.py L86-L114 |
| OTA APK 下载接口 `/api/app-version/download` | ✅ | system.py L117-L156 |
| APK 上传 `POST /api/app-version/upload` | ✅ | admin.py L33-L99 |
| APK 分发 `POST /api/app-version/publish` | ✅ | admin.py L102-L118 |
| admin.html 上传表单 + 分发按钮 | ✅ | `uploadApk()` + `publishApk()` |
| 仪表盘 PDA 配置二维码 | ✅ | `_build_admin_html()` L147，使用独立的 `generate_qr_base64` |
| `AppUpdateManager` OTA 核心 | ✅ | downloadState + installApk 不变 |
| `SettingsScreen` 更新弹窗 | ✅ | 无浏览器下载按钮，不受影响 |
| `SystemApiService.getAppVersion()` | ✅ | Android 端唯一 OTA 调用入口 |

---

### 5. 回归风险结论

**所有检查项通过，零回归风险。**

被删除的功能（浏览器下载 + QR 码下载）与 OTA 更新是完全独立的两个路径。OTA 核心链路不受影响：

```
OTA 链路（保留）：
  管理后台上传 → 分发 → PDA 启动 GET /api/app-version → 对比版本
    → AppUpdateManager.downloadApk() [HTTP下载] → installApk() [FileProvider安装]

浏览器下载链路（删除）：
  admin.html QR码 → 扫码打开浏览器 → GET /api/app-version/download
    → 浏览器下载 → 用户手动安装
  MainActivity "在浏览器中下载"按钮 → Intent.ACTION_VIEW → 同上
```

两条链路共享 `/api/app-version/download` 端点，该端点保留不动。
---

## 版本号

2.34 → 2.35（不构建 APK，等通知）
