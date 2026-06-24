# 审计结论：性能无瓶颈 + 仅需预置服务器地址

## 性能审计结果

全部检查完毕，**无性能瓶颈，无需要移除的非必要流程。**

| 检查项 | 结论 |
|:-------|:-----|
| App 启动流程（Application/MainActivity/AppNavigation） | ✅ 纯本地操作，无网络等待 |
| `restoreFromCache` + `isTokenLocallyValid` 双校验 | ✅ 各司其职，非冗余 |
| `syncKuaimaiCredentials` 每次登录调用 | ✅ fire-and-forget 异步，无开销 |
| HomeScreen session预警 | ✅ 功能正确，无性能影响 |
| 登录页记住密码/登录历史 | ✅ 用户便利功能 |

## App 初始化后的配置项清单

审查发现，App 安装后需要用户操作的配置项只有 **1 个**：

| 配置项 | 当前方式 | 能否预置？ |
|:-------|:---------|:----------:|
| **后端服务器地址** (`server_url`) | 管理员在管理后台生成 QR 码，PDA 扫码配置 | ✅ 编译时硬编码到 APK |
| 快麦凭证 (`appKey/appSecret/session`) | 登录后自动 `syncKuaimaiCredentials()` | ✅ **已自动获取** |
| Settings 权限 | 后端自动注入 | ✅ **已自动赋予** |

## 结论：只需1行改动

`AppConstants.DEFAULT_SERVER_URL` 已有一个默认值 `https://frp-off.com:64623`（FRP 内网穿透演示地址），但你的实际部署地址不同，导致 PDA 仍需手动扫码配置。

**改法**：将 `DEFAULT_SERVER_URL` 改为你实际的服务器地址，PDA 安装后直接登录即可使用，无需扫码配置。

---

需要我直接执行这个改动吗？如果是的话，请告诉我你的服务器地址，格式如 `http://192.168.1.100:8900` 或 `https://your-domain.com`。
