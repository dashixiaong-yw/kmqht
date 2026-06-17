# 快麦取货通

PDA端取货系统，对接快麦开放平台，支持离线操作、图片上传、扫码取货。

---

## 文档索引（AI/开发者入口）

| 文档 | 路径 | 说明 |
|:-----|:----|:-----|
| 📖 快麦凭证配置说明 | [docs/快麦凭证配置说明.md](docs/快麦凭证配置说明.md) | 快麦开放平台appKey/appSecret/session凭证的配置与替换方法 |
| 🔧 部署就绪报告 | [.trae/documents/deployment-readiness-report.md](.trae/documents/deployment-readiness-report.md) | 部署前最终检查清单、Docker构建、APK安装步骤 |
| 🐳 Docker部署方案 | [.trae/documents/DOCKER_DEPLOYMENT_EXPERIENCE.md](.trae/documents/DOCKER_DEPLOYMENT_EXPERIENCE.md) | Docker部署经验记录 |
| 📋 变更日志 | [CHANGELOG.md](CHANGELOG.md) | 版本历史与修复记录 |
| 📐 docker-compose方案 | [.trae/documents/DOCKER_COMPOSE_YML_VS_YAML.md](.trae/documents/DOCKER_COMPOSE_YML_VS_YAML.md) | docker-compose配置文件说明 |

### 部署相关

| 资源 | 说明 |
|:-----|:-----|
| `docker-deploy/Dockerfile` | 后端单阶段构建镜像 |
| `docker-deploy/docker-compose.yml` | Docker编排配置（端口8900） |
| `docker-deploy/.env` | 环境变量（API_KEY、SERVER_URL等，**不提交Git**） |
| `backend/kuaimai.json` | 快麦凭证文件（**不提交Git**） |
| `app/build/outputs/apk/debug/app-debug.apk` | PDA端APK |

---

## 架构速览

```
PDA (Android)                                   NAS/服务器
┌──────────────────┐                            ┌─────────────────┐
│ KuaimaiInterceptor│  ── 快麦API签名 ──────→   │ 快麦开放平台     │
│ (OKHttp拦截器)    │  ── router?method= ──→   │ gw.superboss.cc  │
└──────────────────┘                            └─────────────────┘
        │                                                ↑
        │ 后端API (REST)                                  │ 后端代理调用
        ↓                                                │
┌──────────────────┐                            ┌─────────────────┐
│ Retrofit         │  ── createOrder/addItem ─→ │ FastAPI (后端)   │
│ OrderApiService  │  ←── response ─────────── │ 端口 8900       │
└──────────────────┘                            └─────────────────┘
```

---

## 快速部署

```bash
cd docker-deploy
cp .env.docker.example .env   # 修改 API_KEY 和 SERVER_URL
docker-compose up -d

# PDA安装
adb install app-debug.apk
```
