# 预置 FRP 地址 + 简化引导页 (v1.52)

## 问题

APK 首次安装后直接显示登录页，但服务器地址为空，Retrofit 使用占位 URL `http://localhost:1/`，导致 `ConnectException` → "无法连接服务器，请检查网络"。

## 修复方案

### 改动 1：AppConstants.kt — 预置 FRP 地址

**文件**: [AppConstants.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/util/AppConstants.kt) L15

```diff
- const val DEFAULT_SERVER_URL = ""
+ const val DEFAULT_SERVER_URL = "https://frp-off.com:64623"
```

所有使用 `DEFAULT_SERVER_URL` 的地方自动生效：
- NetworkModule Retrofit baseUrl → 使用 FRP 地址
- ProductViewModel 图片URL拼接 → 使用 FRP 地址

### 改动 2：GuideScreen.kt — 简化 Step1

**文件**: [GuideScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/guide/GuideScreen.kt)

Step1 改为**只读展示**预置的 FRP 地址 + 保留扫码配置按钮（用于切换内网地址）：

```kotlin
// Step 1 改为只读展示服务器地址
Text("服务器地址已自动配置")
Text("https://frp-off.com:64623")
// 保留扫码按钮供高级用户切换内网地址
Button("扫码更换地址") { showCameraScan = true }
// 移除手动输入框和API Key输入框
// 默认直接保存已配置地址
```

## 实现步骤

| Step | 操作 | 文件 |
|:----:|------|------|
| 1 | 修改 DEFAULT_SERVER_URL | AppConstants.kt L15 |
| 2 | 简化引导页 Step1 | GuideScreen.kt |
| 3 | 构建 APK v1.52 | `assembleRelease` |
| 4 | 版本号 1.51→1.52（6处） | 全项目 |
| 5 | 知识图谱 | mcp_kuaimai-memory |
| 6 | 同步 docker-deploy | sync-to-docker-deploy.ps1 |
| 7 | Git 提交推送 | |

## 验证

1. 模拟器清除数据 → 首次打开 → 登录页 → 直接输入密码登录（不再卡在"无法连接服务器"）
2. 登录后 → 引导页 → Step1 显示预置 FRP 地址（只读）→ 下一步选择扫码方式 → 完成
3. 所有后端功能正常（取货单、图片上传、OTA）
