# SSL/HTTPS 网络调用全面审计报告

## 摘要

对项目中所有涉及网络请求的代码路径进行全面审计，确保 FRP 自签证书场景下无遗漏。

---

## 审计范围与结果

### 一、所有 AsyncImage / Coil 图片加载调用（共 8 处）✅

| # | 文件 | 行号 | 加载内容 | URL来源 | SSL策略 | 状态 |
|:-:|:-----|:----:|:---------|:--------|:--------|:----:|
| 1 | PickItemRow.kt | L93 | SKU规格图 | `picPath`（快麦CDN `alicdn.com`） | ImageLoaderFactory → trustAll | ✅ |
| 2 | PickItemRow.kt | L144 | 库区图 | `areaImageUrl`（后端拼接URL） | ImageLoaderFactory → trustAll | ✅ |
| 3 | PickItemRow.kt | L162 | 箱图 | `boxImageUrl`（后端拼接URL） | ImageLoaderFactory → trustAll | ✅ |
| 4 | ProductScreen.kt | L447 | SKU主图 | `picPath.ifBlank { null }`（快麦CDN） | ImageLoaderFactory → trustAll | ✅ |
| 5 | ProductScreen.kt | L696 | 上传图片槽位 | `imageUrl`（后端拼接URL） | ImageLoaderFactory → trustAll | ✅ |
| 6 | PickDetailScreen.kt | L419 | 大图预览弹窗 | `url`（后端拼接URL） | ImageLoaderFactory → trustAll | ✅ |

**说明**：v1.97 `App.kt` 已实现 `ImageLoaderFactory` 接口，所有 `AsyncImage` 自动使用 trustAll OkHttpClient。以上 8 处无需额外修改。

### 二、后端 API 调用（Retrofit + OkHttp）✅

| Service | Retrofit Client | SSL策略 | 状态 |
|:--------|:----------------|:--------|:----:|
| `KuaimaiApiService` | `@Named("kuaimai")` | 标准 SSL（快麦 `superboss.cc` 有正式证书） | ✅ |
| `UserApiService` | `@Named("backend")` | trustAll（FRP 自签证书） | ✅ |
| `SystemApiService` | `@Named("backend")` | trustAll（FRP 自签证书） | ✅ |
| `OrderApiService` | `@Named("backend")` | trustAll（FRP 自签证书） | ✅ |
| `AreaApiService` | `@Named("backend")` | trustAll（FRP 自签证书） | ✅ |
| `ImageUploadService` | `@Named("trustAll")` | trustAll（FRP 自签证书） | ✅ |

### 三、直接 HTTP 调用（HttpURLConnection / URL / WebView / DownloadManager）✅

| 搜索项 | 匹配结果 |
|:-------|:---------|
| `HttpURLConnection` | 零处 |
| `URL(.`、`openConnection` | 零处 |
| `WebView` | 零处 |
| `DownloadManager` | 零处 |

**结论**：项目中不存在绕过 OkHttp/trustAll 的直接 HTTP 调用，无遗漏。

### 四、后端服务 SSL 配置 ⚠️ 信息确认

后端 `Dockerfile` 中 `CMD uvicorn main:app --host 0.0.0.0 --port ${SERVER_PORT:-8900}` **无 SSL 参数**。HTTPS 由 FRP 隧道在服务器端终止后转 HTTP 到后端。后端 `/images` 静态文件挂载工作正常。

**结论**：后端无需任何修改。

---

## 需清理项

### 待删除：`CoilModule.kt`（遗留死代码）

**文件**：`app/src/main/java/com/kuaimai/pda/di/CoilModule.kt`

**原因**：该文件是 v1.97 开发初期创建的文件（通过 `CompositionLocalProvider` + Hilt 注入 ImageLoader），之后改为 `App.kt` 实现 `ImageLoaderFactory` 接口的更简洁方案。但 `CoilModule.kt` 未被删除，目前是死代码：

| 检查项 | 结论 |
|:-------|:-----|
| 是否有类注入 `ImageLoader` | 否（`@Inject lateinit var imageLoader: ImageLoader` 不存在） |
| 是否在 `MainActivity.kt` 中使用 | 否（`CompositionLocalProvider` 已被删除） |
| Hilt 编译是否报错 | 否（`@Provides @Singleton` 无冲突，但绑定未被消费） |
| APK 中是否被包含 | R8/minify 会移除无用代码，不影响 APK 大小 |

**操作**：删除 `CoilModule.kt`（1 个文件删除，无其他代码修改）。

---

## 完整路线图

```
┌─ 用户操作 ────────────────────────────────────────────┐
│  拍照/相册选择 → 上传到后端 → 后端存储成功 → 可以查看 │
└──────────────────────┬────────────────────────────────┘
                       ▼
┌─ 图片URL ──────────────────────────────────────────────┐
│  "https://frp-off.com:64623/images/20260623/xxx.jpg"  │
│  v1.96: 修复URL格式缺/ → 格式正确 ✅                   │
└──────────────────────┬────────────────────────────────┘
                       ▼
┌─ 加载图片 ──────────────────────────────────────────────┐
│  AsyncImage(model = fullUrl)                           │
│  v1.96: Coil默认使用系统SSL → FRP自签证书不被信任 ❌   │
│  v1.97: App实现ImageLoaderFactory → 使用trustAll ✅    │
└─────────────────────────────────────────────────────────┘
```

---

## 验证步骤（唯一操作）

1. 删除 `CoilModule.kt`
2. `./gradlew lint` — 确认 0 errors
3. `./gradlew assembleRelease` — 确认构建成功
4. `git add -A && git commit -m "清理: 删除CoilModule.kt遗留死代码"`
5. `git push`
