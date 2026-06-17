# 部署就绪确认报告

> 日期：2026-06-17
> 背景：8次审计修复65个问题，快麦凭证已配置，所有依赖已验证。

---

## 部署前检查清单

### 1️⃣ 后端部署（Docker）
| 项目 | 状态 | 说明 |
|------|:----:|------|
| docker-compose.yml | ✅ | 端口8900, 内存512M, 健康检查 |
| Dockerfile（单阶段） | ✅ | `FROM python:3.12-alpine` |
| requirements.txt | ✅ | fastapi, uvicorn, httpx, Pillow等10个依赖 |
| kuaimai.json | ✅ | 已填入真实凭证 |
| .env（需用户修改） | ⏳ | `cp .env.docker.example .env` 修改API_KEY和SERVER_URL |

### 2️⃣ 快麦凭证
```json
appKey:       1981991413
appSecret:    54279f7a085e405ebeb6af0d5e2cd68e
session:      f9eae7b99b14478ea13e640a1be05fab
refreshToken: 9a9d5d6da2f24636b206636fa34489f3
```
- ✅ 凭证有效期30天
- ✅ 自动刷新：每7天通过 `open.token.refresh` 自动刷新
- ✅ 过期预警：提前5天在管理后台显示警告
- ✅ 安全保护：`kuaimai.json` 已加入 `.gitignore`，不会泄漏到Git

### 3️⃣ 后端关键功能
| 功能 | 状态 | 说明 |
|------|:----:|------|
| 用户登录限流 | ✅ | 5次失败锁定5分钟 |
| Token过期 | ✅ | 7天自动过期，清理无效token |
| Session自动刷新 | ✅ | APScheduler每7天调用open.token.refresh |
| 配置热重载 | ✅ | watchfiles监控kuaimai.json变更 |
| 管理后台 | ✅ | /admin 路由，API Key认证 |

### 4️⃣ PDA端（APK）
| 项目 | 状态 | 说明 |
|------|:----:|------|
| APK构建 | ✅ | `assembleDebug` 成功 |
| 签名算法 | ✅ | MD5排序拼接，前后端一致 |
| 服务器地址配置 | ✅ | GuideScreen扫码/手动配置 |
| PDA硬件扫码 | ✅ | ScannerManager已注册 |
| 离线同步 | ✅ | WorkManager+OrderSyncWorker |
| 图片上传 | ✅ | 压缩+重试+相对路径拼接 |

### 5️⃣ 部署步骤

```bash
# ===== 宿主机（NAS/服务器） =====
cd docker-deploy

# 1. 配置环境变量
cp .env.docker.example .env
# 修改 .env 中的：
#   API_KEY=你的随机密钥
#   SERVER_URL=http://你的NAS_IP:8900

# 2. 创建数据目录
mkdir data
cp kuaimai.json data/kuaimai.json    # 快麦凭证

# 3. 构建并启动
docker-compose up -d

# 4. 验证
curl http://localhost:8900/health
# 预期: {"status":"ok"}

curl http://localhost:8900/setup
# 预期: 显示配置二维码页面和服务器访问地址


# ===== PDA =====
# 5. 安装APK
adb install app-debug.apk

# 6. 首次配置
# 方法一：PDA → 打开App → 扫码配置二维码（/setup页面）
# 方法二：PDA → 打开App → 手动输入服务器地址

# 7. 登录
# 用户名: admin
# 密码: admin123
# （首次登录会提示强制改密）
```

### 6️⃣ 默认账户

| 账户 | 说明 |
|------|------|
| admin / admin123 | 管理员（首次登录需改密） |
| 其他用户 | 通过管理后台 `/admin` 创建 |

---

## 结论

✅ **系统已就绪，可以部署上线。**
