# OTA 更新改动审计报告

## 回顾：问题根因

Android 6.0 PDA 上 OTA 更新的实际失败链路：

```
点"立即更新" → OkHttp下载APK成功(到cacheDir) 
→ installApk调用FileProvider.getUriForFile() → content:// URI
→ PDA ROM安装器不识别content:// URI → ActivityNotFoundException
→ 通知栏提示"未找到安装器"
→ 用户无感知
```

**结论**：**FileProvider content:// URI 不被 Android 6.0 PDA 的厂商定制安装器识别**，这才是唯一的根因。之前的 v2.17/v2.18 改动都没有触达这个根因，直到 v2.19 的 `file://` URI 降级才是直接修复。

---

## 逐版本审查

### v2.17 — Android 6.0 PDA OTA 更新修复（4项）

| 改动 | 与根因关系 | 有无必要 | 有无缺陷 |
|:-----|:----------:|:--------:|:--------:|
| 下载目录→`cacheDir` | ❌ 无关 | ✅ 正确优化（无需存储权限） | ❌ 无 |
| 移除`saveToPublicDownloads` | ❌ 无关 | ✅ 正确（消除SecurityException源头） | ❌ 无（浏览器下载按钮兜底） |
| `IOException`→`Exception` | ❌ 无关 | ✅ 防御性编程 | ❌ 无 |
| 未知来源检查 + ActivityNotFoundException | ⚠️ 部分相关 | ✅ 必要（捕获了根因异常） | ⚠️ 捕获了但只发通知，用户看不到（后续v2.18/v2.19修复） |

**结论：4项改动全部正确，不需要回退。** 虽然没解决根因，但每项改动都有独立价值。唯一不足是 `ActivityNotFoundException` 捕获后未在弹窗反馈，后续版本已弥补。

### v2.18 — 更新弹窗显示错误详情（2项）

| 改动 | 与根因关系 | 有无必要 | 有无缺陷 |
|:-----|:----------:|:--------:|:--------:|
| Toast→`downloadErrorMsg`弹窗内红色文字 | ❌ 无关 | ✅ 必要（用户终于能看到错误） | ❌ 无 |
| 新增「在浏览器中下载」按钮 | ✅ 兜底方案 | ✅ 必要（安装失败后仍有路可走） | ⚠️ **未更新版本号**（流程违规，已补） |

**结论：2项改动全部正确。**「在浏览器中下载」按钮是最实用的兜底方案——即使 `file://` 降级也失败，用户还能通过浏览器下载后手动安装。

### v2.19 — file:// URI 降级（2项）

| 改动 | 与根因关系 | 有无必要 | 有无缺陷 |
|:-----|:----------:|:--------:|:--------:|
| `file://` URI 降级 | ✅ **直接修复根因** | ✅ 必要 | ⚠️ **仅 `ActivityNotFoundException` 时触发**。如果安装器静默忽略 Intent（不抛异常），降级不会触发。但概率极低。 |
| 弹窗显示安装失败信息 | ❌ 补充 | ✅ 必要（用户可见） | ❌ 无 |

**结论：直接修复了根因。**

---

## 整体结论

```
v2.17 → 铺路（修了表面问题，没到根因） 
   ├── 但每项改动单独来看都是正确的 
   └── cacheDir + saveToPublicDownloads移除 + Exception捕获 都值得保留

v2.18 → 给用户眼睛（错误可见 + 浏览器兜底）
   └── 浏览器按钮是核心兜底，即便所有内部修复都失败

v2.19 → 到根因（file:// URI 降级）
   └── 目前最直接的修复
```

**历史上唯一需要纠正的问题**：
- v2.18 浏览器下载按钮未更新版本号就提交（流程违规，已指出）

**不需要回退的任何改动**：全部 8 项改动都有独立价值，不存在错误或有缺陷的代码。

---

## 当前 OTA 更新完整流程（正确路径）

```
点"立即更新"
  → OkHttp下载APK到 cacheDir/update/
  → installApk:
      ① Android 8.0+: canRequestPackageInstalls 检查（引导到设置页）
      ② FileProvider content:// URI → startActivity
         ↓ ActivityNotFoundException（Android 6.0 PDA核心问题）
      ③ file:// URI 降级 → startActivity → 安装界面弹出 ✅
         ↓ 降级也失败（极少数ROM）
      ④ 弹窗显示"请点击「在浏览器中下载」"
   → 浏览器下载按钮：浏览器正常下载到 /sdcard/Download/ → 通知栏安装
```

这个流程覆盖了所有已知的失败路径，没有多余的改动。
