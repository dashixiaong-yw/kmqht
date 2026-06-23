# Android 6.0 (API 23) 兼容性修复方案

## 一、问题分析

### 症状

Android 6.0（API 23）PDA 上安装 APK 时提示"解析软件包时出现问题"，无法安装。

### 根因

**问题双重**：

1. **`minSdk = 24`**（`app/build.gradle.kts` L37）：APK 声明最低为 Android 7.0（Nougat），Android 6.0 设备直接拒绝安装。

2. **APK 缺少 v1 签名（JAR signing）**：AGP 7.0+ 当 `minSdk >= 24` 时默认禁用 v1 签名。Android 6.0（API 23）**仅支持 v1 签名方案**（v2 方案从 API 24 起支持），无 v1 签名的 APK 无法被 Android 6.0 解析→报"解析软件包时出现问题"。

| 签名方案 | 支持起始 | minSdk=24 时 | minSdk=23 时 |
|:---------|:--------:|:------------:|:------------:|
| v1 (JAR signing) | API 1+ | AGP **禁用** ❌ | AGP **自动启用** ✅ |
| v2 (APK Signature Scheme v2) | API 24+ | 启用 | 启用 |
| v3 (APK Signature Scheme v3) | API 28+ | 启用 | 启用 |

**两个问题均可通过降低 `minSdk` 到 23 一次性解决**：既允许安装，又让 AGP 自动开启 v1 签名使 APK 可解析。

### 兼容性验证

| 检查项 | 结果 |
|:-------|:----:|
| minSdk 当前值 | `24`（Android 7.0） |
| 目标设备 API | `23`（Android 6.0） |
| Compose 要求 | minSdk 21+，API 23 完全支持 |
| `material3:1.3.0` 要求 | minSdk 21+，API 23 完全支持 |
| `activity-compose:1.9.0` 要求 | minSdk 21+，API 23 完全支持 |
| `room:2.6.1` 要求 | minSdk 21+，API 23 完全支持 |
| 所有其他依赖库 | 均要求 minSdk 21+，API 23 完全支持 |
| 项目中 API 24+ 特有代码 | 零处（已逐行搜索 `VERSION_CODES.N`/`SDK_INT >= 24`，无匹配） |
| NDK/ABI 配置 | 未配置 ABI filters，无原生代码依赖，32/64 位均兼容 |

**结论**：`minSdk = 23` 在所有依赖库和代码层面完全兼容，无任何风险。

## 二、修改内容

### 唯一修改：`app/build.gradle.kts`

**文件**：`app/build.gradle.kts`

**改动**：L37 `minSdk = 24` → `minSdk = 23`

```kotlin
defaultConfig {
    applicationId = "com.kuaimai.pda"
    minSdk = 23        // ← 从 24 改为 23，兼容 Android 6.0
    targetSdk = 34
    versionCode = 173
    versionName = "1.93"
}
```

### 新增兼容性测试（可选低优先级）

Android 6.0（API 23）PDA 存在以下已知差异，但当前代码已正确处理：

| 特性 | API 23 上的行为 | 当前代码状态 |
|:-----|:---------------|:------------|
| `Vibrator` | `VIBRATOR_SERVICE` 返回 `Vibrator` | `ScannerManager.kt` L73-78 已分版本处理 ✅ |
| `ConnectivityManager` | `getActiveNetworkInfo()`（已废弃） | `NetworkMonitor.kt` 使用 `registerNetworkCallback`（API 21+） ✅ |
| `SoundPool.Builder` | API 21+ 可用 | 直接使用 ✅ |
| `FileProvider` | API 23 支持 | 已配置 `file_paths.xml` ✅ |
| 外部存储权限 | 运行时权限模型区分于 API 23 APK | 仅使用缓存目录 ✅ |

## 三、回归风险分析

| 风险 | 分析 | 等级 |
|:-----|:-----|:----:|
| 运行时崩溃 | 依赖库均支持 API 23，代码中无 API 24+ 特有调用 | 低 |
| ProGuard/R8 混淆 | Lint 未报错，R8 兼容 API 23 | 低 |
| Compose 渲染 | Compose BOM 2024.09.00 支持 minSdk 21+ | 低 |
| 验证签名 | 使用 `kuaimai-release.keystore`，签名方式与 minSdk 无关 | 低 |

## 四、验证步骤

1. `./gradlew lint` — 确保无新增警告
2. `./gradlew assembleRelease` — 构建 APK
3. 将 APK 传至 Android 6.0 PDA
4. 点击 APK 文件安装
5. 确认安装成功（不再提示"解析软件包时出现问题"）
6. 安装后打开 App，确认所有页面和功能正常
7. Android 7.0+ 设备上安装使用不受影响（低 minSdk 不会影响高版本设备）
