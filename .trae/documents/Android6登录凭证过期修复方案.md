# Android 6.0 登录提示凭证过期修复方案

## 一、问题分析

### 症状

Android 6.0 (API 23) PDA 安装 v1.94 APK，登录后显示"凭证过期"弹窗，无法正常使用。

### 根因

`NetworkModule.kt` L70-72 创建 `EncryptedSharedPreferences` 时使用 `MasterKey.KeyScheme.AES256_GCM`：

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)  // ← 问题根源
    .build()
```

**`MasterKey.KeyScheme.AES256_GCM` 依赖 Android Keystore 硬件后端**。部分 Android 6.0 设备（尤其是国产 PDA 定制 ROM）的 Keystore 实现不完整或存在 bug，导致：

- **存储阶段失败**：`EncryptedSharedPreferences` 创建或写入时静默抛出异常 → token 实际未写入
- **读取阶段失败**：即便写入成功，后续读取时 Keystore 解密异常 → `getString(key, "")` 返回默认值 `""`

**完整错误链路**：

```
EncryptedSharedPreferences 读 token 失败 → getToken() 返回 ""
  → login() 写入 token 成功（内存中 _currentUser 已设置）
  → 后续 API 调用使用 getToken() → 空 token 发往后端
  → 后端 /api/users/me 返回 401
  → handleAuthError(401) → clearLocalUser() + _loginRequired.emit()
  → AppNavigation 监听到 → 跳转回登录页（或弹"凭证过期"）
```

### 为什么之前（minSdk=24）没问题

以前 APK 仅安装在 Android 7.0+ 设备上。`AES256_GCM` 在 API 24+ 上的 Keystore 实现更成熟稳定。

### MasterKey 方案对比

| 方案 | 依赖 | API 23 兼容性 | 安全性 |
|:-----|:-----|:-------------|:-------|
| `AES256_GCM`（当前） | Android Keystore 硬件后端 | ❌ 国产 PDA ROM 不稳定 | ⭐⭐⭐ |
| `PBKDF2_SHA256`（推荐） | 纯软件 PBKDF2 密钥派生 | ✅ 所有 API 23+ 设备 | ⭐⭐⭐（应用内存储场景足够） |

## 二、修改内容

### 修改 1：加密方案变更（`NetworkModule.kt`）

**文件**：`app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt`

**改动**：L71 `AES256_GCM` → `PBKDF2_SHA256`

```kotlin
// 修改前
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

// 修改后
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.PBKDF2_SHA256)
    .build()
```

### 修改 2：数据迁移保护（同一方法，`NetworkModule.kt`）

**为什么需要**：加密方案变更后，旧 `AES256_GCM` 加密的文件无法被 `PBKDF2_SHA256` 读取。如果用户是从旧 APK 升级安装（而非全新安装），`EncryptedSharedPreferences.create()` 在打开旧文件时会抛出 `GeneralSecurityException`，导致 Hilt 注入失败 → App 启动崩溃。

**改动**：用 try-catch 包裹 `create`，捕获异常后删除旧文件重建

```kotlin
@Provides
@Singleton
@Named("encrypted")
fun provideEncryptedSharedPreferences(
    @ApplicationContext context: Context
): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.PBKDF2_SHA256)
        .build()
    return try {
        EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // 加密方案变更/Keystore损坏 → 删除旧文件重建
        Log.w("NetworkModule", "加密存储打开失败，重建: ${e.message}")
        context.deleteSharedPreferences(PREFS_NAME)
        EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
```

### `PBKDF2_SHA256` 原理

- 使用默认 passphrase + 随机 salt → `PBKDF2WithHmacSHA256` 密钥派生
- 纯软件计算，**不依赖 Android Keystore 硬件后端**
- salt 存储在 SharedPreferences 文件旁（自动管理）
- 安全级别：对 PDA 本地 token 存储场景足够（远高于明文存储）

## 三、回归风险分析

| 风险 | 分析 | 等级 |
|:-----|:-----|:----:|
| 数据迁移（旧文件不可读） | try-catch 捕获后删除重建，用户仅需重新登录一次 | ✅ 已防护 |
| 安全性 | `PBKDF2_SHA256` 虽比 `AES256_GCM` 弱，但对抗 PDA 本地离线攻击已足够。token 7天过期 | 可接受 |
| 性能 | PBKDF2 首次初始化有数百毫秒延迟，不影响 UX | 低 |
| 其他使用 `@Named("encrypted")` 的组件 | `KuaimaiInterceptor`、`TokenAuthenticator`、`UserRepositoryImpl` 等 5 个组件均只通过 `SharedPreferences` 接口读写，底层实现透明替换 | 无影响 |

## 四、验证步骤

1. 修改代码后运行 `./gradlew lint` — 确保无新增警告
2. `./gradlew assembleRelease` — 构建 APK
3. 将 APK 传至 Android 6.0 PDA
4. 打开 App → 输入账号密码 → 登录
5. 确认登录成功、进入主页
6. 关闭 App 重新打开 → 确认自动恢复登录状态
7. 执行扫码取货操作 → 确认 API 调用正常（无"凭证过期"弹窗）
8. Android 7.0+ 设备同样测试一次 → 确认无退化
