# Android App HTTPS 连接失败修复计划 — FRP 场景

## 问题

APK 下载正常（浏览器通过 `https://frp-off.com:64623` 成功），但 PDA 安装后打开 App 登录时提示 "无法连接服务器"（`ConnectException`）。

## 根因

**`network_security_config.xml` 只信任系统证书（`<certificates src="system" />`），未信任用户安装的证书。**

PDA 设备（尤其是 Android 7.x 老版本）的系统 CA 信任列表中可能缺少 Let's Encrypt 根证书（ISRG Root X1 — Android 8.0 才内置）。而 SakuraFRP 的 HTTPS 使用 Let's Encrypt 证书，导致 OkHttp SSL 握手失败，抛出 `ConnectException`。

浏览器能下载 APK 是因为：
- 现代 Android WebView/Chrome 使用 Google 的证书透明度机制
- 部分浏览器有独立 CA 存储（如 Firefox）

但 App 内 OkHttp 严格遵循系统 CA 存储 + `network_security_config.xml` 配置。

## 修复方案

### 改动 1 个文件

#### [network_security_config.xml](file:///d:/trea项目/快麦取货通/app/src/main/res/xml/network_security_config.xml)

新增 `<certificates src="user" />`，允许 PDA 信任用户安装的 CA 证书：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### 改动解释

| 配置 | 作用 | 场景 |
|:-----|:-----|:------|
| `src="system"` | 信任系统预装 CA（保留原有） | 无 FRP、内网 HTTP 场景 |
| `src="user"` | **新增** — 信任用户安装的 CA | FRP HTTPS、自签名证书场景 |
| `cleartextTrafficPermitted="true"` | 允许明文 HTTP | 内网部署场景（保持不变） |

### 验证方式

1. `./gradlew lint` 通过（语法检查）
2. `./gradlew assembleRelease` 构建成功
3. 构建后 APK 安装到 PDA，通过 FRP HTTPS 登录正常

## 不修改其他文件的原因

| 文件 | 不修改的原因 |
|:-----|:------------|
| NetworkModule.kt | OkHttp 默认使用系统 SSL/TLS 配置，无需自定义 SSLSocketFactory |
| LoginScreen.kt / LoginViewModel.kt | 逻辑正确，`ConnectException` 由 SSL 握手失败触发 |
| GuideScreen.kt | URL 校验已支持 `https://` 前缀 |
| backend 端 | 服务端证书由 SakuraFRP 自动管理，无需修改 |
