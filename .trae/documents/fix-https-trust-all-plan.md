# v1.60: HTTPS + OkHttp信任所有证书

## 结论

SakuraFRP 强制 HTTPS，HTTP 被拒（501）。唯一方案：HTTPS URL + OkHttp 层信任所有证书。公司内部自用无外部用户，风险可控。**仅对后端 Retrofit 宽信任，快麦 API 保持严格校验。**

## 改动（3处）

### 1. AppConstants.kt — 恢复 https

`http://` → `https://`

### 2. .env.docker.example — 恢复 https

`SERVER_URL=http://` → `SERVER_URL=https://`

### 3. NetworkModule.kt — 后端 Retrofit 信任所有证书

`provideBackendRetrofit()` 中，对传入的 OkHttpClient 克隆一份，加 trust-all SSL：

```kotlin
val trustAllClient = client.newBuilder()
    .hostnameVerifier { _, _ -> true }
    .sslSocketFactory(unsafeSslSocketFactory, unsafeTrustManager)
    .build()
```

文件顶部加懒加载的 unsafeTrustManager 和 unsafeSslSocketFactory。

## 步骤

| Step | 操作 |
|:----:|------|
| 1 | AppConstants.kt http→https |
| 2 | .env.docker.example http→https |
| 3 | NetworkModule.kt 加 trust-all SSL |
| 4 | 构建 APK v1.60 |
| 5 | 版本号 1.59→1.60 + 知识图谱 + sync + Git |

## 验证

PDA 首次安装 → 登录 → HTTPS 连接 → 证书自签但 OkHttp 信任 → 登录成功
