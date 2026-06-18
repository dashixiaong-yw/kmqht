# FRP 内网穿透对 PDA 与 Docker 服务器交互的影响分析

## 核心结论

**可以工作，但有 3 个关键配置点和 4 个性能/功能风险需要注意。**

---

## 当前架构回顾

| 组件 | 网络位置 | 地址 |
|------|:--------:|------|
| Docker 后端服务 | 内网服务器 | `http://内网IP:8900` |
| Android PDA | 外网/同一内网 | 连接 `SERVER_URL` |
| FRP 隧道 | 公网中转 | `frp-off.com:64623` → 内网 `:8900` |

PDA 通过 `SERVER_URL` 配置的地址访问后端全部功能：
- 图片上传/下载
- 取货单 API（CRUD、完成、恢复、删除）
- OTA APK 下载
- 快麦凭证同步
- 离线操作队列同步

---

## ✅ 兼容性分析

### 1. 协议支持

| 后端监听 | FRP 转发 | PDA 配置 | 兼容？ |
|:--------:|:---------:|:---------:|:------:|
| HTTP 8900 | TCP 隧道 | `http://frp-off.com:64623` | ✅ |

FRP 的 TCP 隧道对上层协议透明，HTTP 请求可以正常穿透。

### 2. Docker 内健康检查不受影响

[docker-compose.yml](file:///d:/trea项目/快麦取货通/backend/docker-compose.yml#L21) 的健康检查在**容器内部**访问 `localhost:8900`，不经过 FRP，完全不受影响。

### 3. SERVER_URL 环境变量必须设置

[SERVER_URL](file:///d:/trea项目/快麦取货通/backend/app/config.py#L41) 默认值为 `""`，为空时后端自动从请求头获取地址。**但通过 FRP 时自动获取的地址可能是内网 IP，必须显式设置。**

### 4. Android 端完全支持自定义 URL

[GuideScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/guide/GuideScreen.kt) 引导页支持输入任意 `http://` 或 `https://` URL（含自定义端口），并存储到 [EncryptedSharedPreferences](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/util/PrefsKeys.kt#L23)。[ImageUploadService](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/ImageUploadService.kt) 和 [ProductViewModel](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) 中拼接 URL 时使用 `trimEnd('/')` 兼容带尾随斜杠。

---

## 🟡 配置要点（需手动操作）

### 1. Docker 端：必须设置 SERVER_URL

文件: [backend/.env.docker.example](file:///d:/trea项目/快麦取货通/backend/.env.docker.example)（或直接在 docker-compose.yml 的 environment 中）

```dotenv
SERVER_URL=http://frp-off.com:64623
```

不设置时：
- 管理后台二维码生成 `kuaimai://setup?server=http://内网IP:8900` → PDA 连接内网 IP 失败
- OTA APK 下载 URL 也是内网地址 → PDA 无法下载
- 二维码配置流程断裂

### 2. PDA 端：引导页配置

在首次引导页或通过管理后台生成的二维码配置 `http://frp-off.com:64623`。

### 3. 管理后台二维码会正确包含 FRP 地址

[admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) 生成配置二维码时使用 `SERVER_URL` 环境变量。设置后二维码内容即为 `kuaimai://setup?server=http://frp-off.com:64623&apikey=...`，PA直接用手机扫码即可完成配置。

---

## 🔴 性能与功能风险

### 风险 1：图片上传/下载延迟（高影响）

PDA 的核心操作之一是在取货时拍摄库区图（area）和装箱图（box）：

```
PDA → FRP(frp-off.com:64623) → 内网服务器:8900 → 写入磁盘
                              ↓
                         图片URL: http://frp-off.com:64623/uploads/xxx.jpg
                              ↓
PDA → FRP(frp-off.com:64623) → 内网服务器:8900 → 读取磁盘 → 返回图片
```

每张图片经过 **2 次 FRP 隧道往返**（上传 + 显示）。FRP 的带宽和延迟直接影响 PDA 的取货效率。建议：
- 如果 FRP 服务带宽 < 5Mbps，图片上传可能慢
- 生产环境建议将图片服务独立部署或使用 CDN

### 风险 2：FRP 隧道稳定性

FRP 免费服务可能有：
- 每日流量限制
- 连接数限制
- 定期断开重连

后端 API 调用（取货单同步、离线队列）在网络中断时通过 PDA 内置的 [OrderSyncWorker](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) 离线队列机制容错，**功能不会丢失但会延迟同步**。

### 风险 3：管理后台 Web 操作延迟

管理员通过浏览器访问 `http://frp-off.com:64623/admin` 操作管理后台时：
- 路径：`浏览器 → FRP → 内网服务器`
- 每次页面操作（查询订单、更新凭证、上传 APK）都经过隧道
- 如果 FRP 延迟 > 200ms，管理员体验会明显变差

### 风险 4：HTTPS 缺失

`frp-off.com:64623` 是纯 HTTP。图片上传、凭证同步等敏感数据**明文传输**。如果 FRP 服务端被中间人攻击，快麦 API 凭证（appKey/appSecret/session）会泄露。

---

## 冷热数据路径分析

| 数据 | 路径 | 频次 | 延迟影响 |
|:-----|:-----|:----:|:--------:|
| 取货单列表 | PDA → FRP → API | 频繁 | 中等 |
| 图片上传 | PDA → FRP → API → 磁盘 | 频繁 | **高** |
| 图片显示 | PDA → FRP → API → 磁盘 | 频繁 | **高** |
| 离线队列同步 | PDA → FRP → API | 按需 | 中等 |
| 备注/供应商更新 | PDA → 快麦 API（直连） | 按需 | **无**（不经过 FRP） |
| OTA APK 下载 | PDA → FRP → API → 磁盘 | 低 | 一次性的 |
| 快麦凭证同步 | PDA → FRP → API → 凭证文件 | 低 | 中等 |

**备注/供应商更新不经过 FRP**：`syncRemarkUpdate` 和 `syncSupplierUpdate` 通过 [KuaimaiApiService](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/KuaimaiApiService.kt) 直接调用快麦 API `gw.superboss.cc/router`，不走后端服务器。

---

## 验证清单

如果决定使用 FRP，需要验证以下内容：

| # | 检查项 | 验证方法 |
|:-:|:------|:---------|
| 1 | FRP 隧道可用 | `curl http://frp-off.com:64623/health` 返回 200 |
| 2 | Docker 端 SERVER_URL 已设置 | `docker-compose config \| grep SERVER_URL` |
| 3 | 管理后台二维码地址正确 | 访问 `/admin`，扫码测试 |
| 4 | PDA 图片上传功能 | 导购页面拍一张图并上传 |
| 5 | PDA OTA 更新 | APK 版本号页面检查下载 URL |
| 6 | 延迟可接受 | `ping frp-off.com` 或 `curl -w %{time_total}` 实测 |
| 7 | 带宽足够 | 上传一张 >= 500KB 的图片，测量耗时 |

---

## 建议

| 等级 | 建议 |
|:----:|:------|
| **必须** | docker-compose.yml 中设置 `SERVER_URL=http://frp-off.com:64623` |
| **必须** | 管理后台引导页生成的二维码需要重新生成 |
| **强烈建议** | 使用带 HTTPS 的 FRP 隧道或 Nginx 反向代理 + Let's Encrypt 加密传输 |
| **建议** | 先在生产环境实测图片上传速度，如果单张 > 5 秒考虑优化 |
| **建议** | 设置 FRP 客户端自动重连参数（`login_fail_exit = false`） |
