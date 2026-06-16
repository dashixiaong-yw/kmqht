# Docker 部署经验与踩坑方案

## 一、核心架构

### 1.1 双服务架构

```
┌─────────────────────────────────────────────────────────┐
│                     Docker Host (NAS)                   │
│                                                         │
│  ┌─────────────────────┐    ┌─────────────────────────┐│
│  │ online-fund-management│    │ fund-refresh-cron       ││
│  │   (Next.js 主服务)   │    │   (定时任务服务)         ││
│  │   Port: 3000        │    │                         ││
│  └─────────────────────┘    └─────────────────────────┘│
│           ↑                           ↑                 │
│           │___________________________│                 │
│                     依赖关系                           │
└─────────────────────────────────────────────────────────┘
```

- **主服务** (`online-fund-management`)：Next.js Web 应用，处理用户请求
- **定时服务** (`fund-refresh-cron`)：独立的 Node.js 脚本，负责数据刷新

### 1.2 部署包结构

```
项目根目录/
├── docker-deploy/          # ✅ 完整可部署包，上传至 NAS
│   ├── src/                # 源代码
│   ├── public/             # 静态资源
│   ├── Dockerfile          # 主服务镜像配置
│   ├── Dockerfile.cron     # 定时任务镜像配置
│   ├── docker-compose.yml  # Docker Compose 配置
│   ├── .env.docker         # 预配置环境变量
│   ├── package.json
│   ├── pnpm-lock.yaml
│   └── ...
└── scripts/
    └── sync-to-docker-deploy.ps1  # 同步脚本
```

---

## 二、关键配置

### 2.1 Next.js standalone 模式

`next.config.ts` 必须配置 `output: 'standalone'`：

```typescript
const nextConfig: NextConfig = {
  output: 'standalone',
  reactStrictMode: true,
};
```

**作用**：生成独立的可部署目录，Docker 只需复制 `standalone` 目录即可运行。

### 2.2 Dockerfile（主服务）

采用**多阶段构建**，分离构建环境与运行环境：

```dockerfile
FROM node:20-alpine AS base
WORKDIR /app

# ========== 构建阶段 ==========
FROM base AS builder
RUN corepack enable && corepack prepare pnpm@9.0.0 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

ARG BUILD_VERSION  # 版本号参数，变化时触发重新构建

COPY . .

# ✅ 使用 BuildKit 缓存持久化 .next/cache，实现增量编译
RUN --mount=type=cache,target=/app/.next/cache \
    pnpm run build

# ========== 运行阶段 ==========
FROM base AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN apk add --no-cache curl  # 健康检查需要 curl

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
```

### 2.3 Dockerfile.cron（定时任务）

```dockerfile
FROM node:20-alpine
WORKDIR /app

RUN npm install node-fetch@3 --production

# ✅ 设置时区（北京时间）
RUN apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

ARG BUILD_VERSION

COPY cron-refresh.js /app/cron-refresh.js

CMD ["node", "/app/cron-refresh.js"]
```

### 2.4 docker-compose.yml

```yaml
services:
  online-fund-management:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        BUILD_VERSION: v1.4.96  # 与 version.ts 同步
    container_name: online-fund-management
    restart: unless-stopped
    ports:
      - "3000:3000"
    env_file:
      - .env
    environment:
      - DOCKER_BUILDKIT=1  # ✅ 必须启用 BuildKit
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  fund-refresh-cron:
    build:
      context: .
      dockerfile: Dockerfile.cron
      args:
        BUILD_VERSION: v1.4.96
    container_name: online-fund-management-cron
    restart: unless-stopped
    env_file:
      - .env
    environment:
      - APP_URL=http://online-fund-management:3000
      - DOCKER_BUILDKIT=1
    depends_on:
      - online-fund-management
```

### 2.5 .dockerignore

```
.git
.gitignore
.env
.env.local
.env.*.local
node_modules
.next
out
dist
*.log
README.md
AGENTS.md
docs
scripts
templates
assets
*.md
.trae
coze
*.png
*.jpg
*.jpeg
*.gif
```

---

## 三、踩坑记录与解决方案

### ⚠️ 踩坑 1：强制删除 .next 导致冷编译 15min+

**问题**：
```dockerfile
# ❌ 错误做法
RUN rm -rf .next && pnpm run build
```

**后果**：每次构建都清空缓存，即使代码未变也要重新编译，在 NAS 上可能需要 15+ 分钟。

**正确做法**：
```dockerfile
# ✅ 正确做法 - 利用 BuildKit 缓存
RUN --mount=type=cache,target=/app/.next/cache \
    pnpm run build
```

**原理**：BuildKit 缓存挂载到 `/app/.next/cache`，源代码变化时只重新编译变更部分。

---

### ⚠️ 踩坑 2：.babelrc 导致构建降级

**问题**：项目存在 `.babelrc` 文件，导致 Next.js 强制降级为 Babel 编译。

**后果**：在 NAS 上构建时间从几分钟增加到 20+ 分钟。

**解决**：删除 `.babelrc` 文件，使用默认的 SWC 编译器。

---

### ⚠️ 踩坑 3：docker-compose.yaml 与 .yml 不同步

**问题**：同时维护 `docker-compose.yml` 和 `docker-compose.yaml`，容易混淆。

**解决**：
- 只维护 `.yml` 文件
- 使用同步脚本 `sync-to-docker-deploy.ps1` 自动生成 `.yaml`
- `.gitignore` 中只忽略 `.yml`，提交 `.yaml`

---

### ⚠️ 踩坑 4：环境变量泄漏

**问题**：`.env` 文件包含敏感信息（Supabase 密钥）被提交到 Git。

**解决**：
1. `.env.docker` 包含预配置环境变量（不含敏感值或占位符）
2. `.dockerignore` 忽略所有 `.env` 文件
3. 在 NAS 上复制 `.env.docker` 为 `.env` 后手动填入敏感值

---

### ⚠️ 踩坑 5：版本号不一致

**问题**：Docker 镜像版本与代码版本不同步。

**解决**：三处必须同时更新：

| 文件 | 内容 |
|------|------|
| `src/lib/version.ts` | `APP_VERSION` |
| `docker-compose.yml` | `BUILD_VERSION`（两处：主服务+cron） |
| `docker-deploy/docker-compose.yml` | 同上（由同步脚本保持一致） |

---

## 四、版本更新流程

```
┌─────────────────────────────────────────────────────────┐
│  Step 1: 修改代码                                        │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Step 2: 更新版本号（version.ts + changelog.ts）         │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Step 3: 同步到 docker-deploy                            │
│  .\scripts\sync-to-docker-deploy.ps1 -Force             │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Step 4: 验证 BUILD_VERSION 一致性                      │
│  Select-String "BUILD_VERSION" docker-deploy/*.{yml,yaml}│
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Step 5: Git 提交推送                                    │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Step 6: NAS 上重新构建                                  │
│  cd /path/to/online-fund-management                     │
│  docker-compose up -d --build                           │
└─────────────────────────────────────────────────────────┘
```

---

## 五、新项目快速上手模板

### 5.1 必须文件清单

| 文件 | 作用 |
|------|------|
| `Dockerfile` | 主服务镜像配置 |
| `Dockerfile.cron` | 定时任务镜像（如需要） |
| `docker-compose.yml` | 容器编排配置 |
| `.env.docker.example` | 环境变量示例 |
| `.dockerignore` | 构建忽略文件 |
| `next.config.ts` | 必须包含 `output: 'standalone'` |

### 5.2 最短 Dockerfile 模板（Next.js）

```dockerfile
FROM node:20-alpine AS base
WORKDIR /app

FROM base AS builder
RUN corepack enable && corepack prepare pnpm@9.0.0 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
ARG BUILD_VERSION
COPY . .
RUN --mount=type=cache,target=/app/.next/cache \
    pnpm run build

FROM base AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN apk add --no-cache curl
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"
CMD ["node", "server.js"]
```

### 5.3 docker-compose.yml 最小模板

```yaml
services:
  your-app:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        BUILD_VERSION: v1.0.0
    container_name: your-app
    restart: unless-stopped
    ports:
      - "3000:3000"
    env_file:
      - .env
    environment:
      - DOCKER_BUILDKIT=1
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

---

## 六、NAS 部署检查清单

- [ ] Docker 已安装并启动
- [ ] docker-deploy 文件夹已上传到 NAS
- [ ] `.env.docker` 已复制为 `.env` 并填入敏感值
- [ ] `DOCKER_BUILDKIT=1` 环境变量已设置
- [ ] 首次构建完成（耐心等待 10-30 分钟）
- [ ] 健康检查通过 (`curl http://localhost:3000/`)
- [ ] 防火墙/端口已配置允许访问

---

## 七、常用命令

```bash
# 查看容器状态
docker-compose ps

# 查看实时日志
docker-compose logs -f

# 停止容器
docker-compose stop

# 重启容器
docker-compose restart

# 更新应用（从 Git 拉取后）
git pull
docker-compose up -d --build

# 进入容器调试
docker exec -it your-container-name /bin/sh
```
