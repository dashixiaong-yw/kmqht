# 图片仍然无法显示排查与修复方案

## 一、排查结论

### 确认无误的地方

通过用户提供的后台链接 `https://frp-off.com:64623/images/20260623/AYGYC_area_2921712a.jpg` 确认：

| 检查项 | 结果 |
|:-------|:-----|
| URL 格式（v1.96 修复） | `...com:64623/images/...` — **格式正确** ✅ |
| 后端静态文件挂载 `/images` | 正常工作，URL 可访问 ✅ |
| 后端服务 | **不需要重新部署** |

### 问题持续的原因

**Coil（AsyncImage）使用系统默认 SSL 验证**，该验证链不信任 FRP 隧道的自签证书。而 Android 6.0（API 23）的证书信任链相比新版本更严格，导致：

```
Coil AsyncImage model = "https://frp-off.com:64623/images/..."
  → Coil 内部默认 OkHttpClient（系统 SSL 校验）
  → FRP 自签证书 ❌ Android 6.0 不信任
  → SSL 握手失败 → 图片加载失败 → 空白
```

**对比其他成功场景**：

| 场景 | 使用的 Client | SSL 校验 | 结果 |
|:-----|:-------------|:---------|:----:|
| 图片上传 | `@Named("trustAll")` | 信任所有主机/证书 | ✅ 上传成功 |
| APK 下载 | `@Named("trustAll")` | 信任所有主机/证书 | ✅ 下载成功 |
| API 调用 | `@Named("trustAll")` | 信任所有主机/证书 | ✅ API 正常 |
| **图片显示** | **Coil 默认（系统 SSL）** | **校验证书链** | ❌ **显示空白** |

## 二、修改内容

### 修改 1：新增 `CoilModule.kt`（Hilt 模块）

**文件**：`app/src/main/java/com/kuaimai/pda/di/CoilModule.kt`（新建）

提供 `ImageLoader` 单例，使用现有的 `@Named("trustAll")` OkHttpClient：

```kotlin
package com.kuaimai.pda.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("trustAll") okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient { okHttpClient }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.1)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}
```

### 修改 2：`MainActivity.kt`（注入 ImageLoader 到 Compose 树）

**文件**：`app/src/main/java/com/kuaimai/pda/MainActivity.kt`

新增 import：

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalImageLoader
import coil.ImageLoader
```

注入 ImageLoader：

```kotlin
@Inject
lateinit var appUpdateManager: AppUpdateManager

@Inject
lateinit var networkMonitor: NetworkMonitor

@Inject
lateinit var imageLoader: ImageLoader   // ← 新增
```

`setContent` 块包裹 `CompositionLocalProvider`：

```kotlin
// 修改前
setContent {
    KuaimaiTheme {
        // ... LaunchedEffect, AlertDialog, AppNavigation ...
    }
}

// 修改后
setContent {
    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        KuaimaiTheme {
            // ... LaunchedEffect, AlertDialog, AppNavigation（不变）...
        }
    }
}
```

### 工作原理解释

```
CompositionLocalProvider(LocalImageLoader provides imageLoader)
  → 所有 Composable 组件（包括 AsyncImage）访问 LocalImageLoader.current
  → 返回我们提供的 ImageLoader（使用 trustAll OkHttpClient）
  → SSL 握手通过 FRP 自签证书 ✅
  → 图片正常加载 ✅
```

修改范围极小：**1 个新文件 + 1 个文件中约 5 行代码**。不改变任何业务逻辑，不触及后端。

## 三、回归风险分析

| 风险 | 分析 | 等级 |
|:-----|:-----|:----:|
| Coil `@Named("trustAll")` 依赖 | 已有完整提供链：`unsafeTrustManager` + `unsafeSslSocketFactory` + `@Named("trustAll") OkHttpClient` | 无风险 |
| `LocalImageLoader` API 兼容性 | Coil 2.4+ 稳定版，2.7.0 完全支持 | 无风险 |
| `CompositionLocalProvider` | Compose 原生 API，API 1+ | 无风险 |
| 内存缓存 | 0.1% 堆内存，避免频繁网络请求 | 优化 |
| 磁盘缓存 | 2% 存储空间，提升离线场景重复查看性能 | 优化 |
| 安全性降低 | 与现有 API 调用（已使用 trustAll）保持一致，未引入新风险 | 可接受 |
| 后端影响 | 零修改 | 无影响 |

## 四、验证步骤

1. `./gradlew lint` — 确保无新增警告
2. `./gradlew assembleRelease` — 构建 APK
3. 将 APK 安装到 Android 6.0 PDA
4. 登录 → 商品页拍摄/上传库区图 → 确认缩略图立即显示
5. 点击缩略图 → 大图预览正常加载
6. 取货单详情 → 库区图缩略图正常显示
7. 点击放大 → 大图正常显示
8. Android 7.0+ 设备同样验证一次 → 确认无退化
