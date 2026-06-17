# 首次部署全面代码审计计划

> 生成日期：2026-06-17
> 背景：系统为首次构建，尚未部署测试。本次审计在上次 v1.13 修复基础之上，重点检查初始化流程、部署配置、Docker 构建、前后端启动链路的完整性。

---

## 一、检查摘要

经过对 3 个维度的深度探索（初始化/启动流程、Room Schema/迁移、部署配置/基础设施），发现以下问题：

| 严重级别 | 数量 | 说明 |
|:--------:|:----:|------|
| **P0（致命）** | 4 | 系统完全不可用：Docker容器无法启动、后端API全失败、部署产物严重过时、共享配置断裂 |
| **P1（高）** | 7 | 功能异常或安全隐患：白屏、session刷新、认证跳过、配置缺失 |
| **P2（中）** | 7 | 体验或安全改进：签名、备份、资源限制、校验不足 |
| **已验证无问题** | - | Room Schema v1→v2迁移正确、Entity字段一致、索引覆盖完整 |

---

## 二、P0 致命缺陷（系统完全不可用）

### P0-1：服务器地址存储与读取 SharedPreferences 实例不匹配

| 项目 | 内容 |
|------|------|
| **描述** | GuideScreen 将服务器地址写入普通 SharedPreferences（`prefs`），但 NetworkModule.provideBackendRetrofit() 从加密 SharedPreferences（`@Named("encrypted")`）读取 |
| **影响** | 加密 SharedPreferences 中永远不存在 KEY_SERVER_URL，Retrofit 始终使用占位 URL `http://localhost:1/`。**所有后端 API（取货单/用户/拣货区/系统）全部失败** |
| **涉及文件** | GuideScreen.kt:L92 → NetworkModule.kt:L168 |
| **根因** | 与 v1.12 修复的 API_KEY 问题同类型：GuideScreen 错误使用 `prefs` 而非 `encryptedPrefs` 保存配置 |
| **修复方案** | GuideScreen L92 改为 `encryptedPrefs.edit().putString(PrefsKeys.KEY_SERVER_URL, ...)`。同时 ImageUploadService 和 ProductViewModel 也从 plain prefs 读取 SERVER_URL，需同步改为从 encryptedPrefs 读取 |

### P0-2：Dockerfile 多阶段构建导致 pip 包全部丢失

| 项目 | 内容 |
|------|------|
| **描述** | Dockerfile 使用多阶段构建：builder 阶段 `pip install`，runner 阶段仅 `COPY --from=builder /app /app`，不复制 site-packages |
| **影响** | **容器启动时 `uvicorn` 命令不存在，容器立即崩溃退出**。所有依赖（fastapi/uvicorn/httpx 等）全部丢失 |
| **涉及文件** | backend/Dockerfile:L1-L20 |
| **修复方案** | 改为单阶段构建：直接在 runner 阶段 `pip install -r requirements.txt`（利用 Docker BuildKit 的 pip 缓存挂载加速） |

### P0-3：docker-deploy 中 main.py 为严重过时版本

| 项目 | 内容 |
|------|------|
| **描述** | 同步脚本将 backend/ 复制到 docker-deploy/backend/（嵌套子目录），但 Dockerfile 的 `context: .` 指向 docker-deploy/ 根目录。运行时加载 docker-deploy/main.py（根层级），该文件严重过时 |
| **影响** | docker-deploy 构建的镜像**缺少 admin（管理后台）和 users（用户管理）两个核心路由**；CORS 硬编码为 `["*"]`；超时订单仅记录日志不自动完成；缺少 session 刷新定时任务；缺少 session 预警定时任务 |
| **涉及文件** | docker-deploy/main.py（过时版） vs backend/main.py（正确版） |
| **修复方案** | 修同步脚本，将 backend/ 内容同步到 docker-deploy/ 根目录（而非嵌套子目录）；同时在 configFiles 列表中增加 `main.py` 和 `kuaimai.example.json` |

### P0-4：同步脚本配置文件源路径错误

| 项目 | 内容 |
|------|------|
| **描述** | sync-to-docker-deploy.ps1 中 configFiles 列表（docker-compose.yml/Dockerfile/.dockerignore/.env.docker.example/requirements.txt）从 `$ProjectRoot`（项目根目录）查找，但这些文件实际位于 `backend/` 子目录 |
| **影响** | Test-Path 始终为 false，continue 跳过，**docker-deploy 根目录的核心配置文件永不更新**，保留的是历史残留旧版本 |
| **涉及文件** | scripts/sync-to-docker-deploy.ps1:L126-L132 |
| **修复方案** | configFiles 的源路径改为 `Join-Path $ProjectRoot "backend"`；补充 `main.py` 和 `kuaimai.example.json` 到列表；同步时目标路径改为 docker-deploy 根目录 |

---

## 三、P1 高级别缺陷

### P1-1：Retrofit Singleton 的 baseUrl 不随配置变更自动更新

| 项目 | 内容 |
|------|------|
| **描述** | NetworkModule 中 Retrofit 是 `@Singleton`，baseUrl 在构建时一次性读取。用户修改服务器地址后 Retrofit 实例不重建 |
| **影响** | 用户修改服务器地址后，所有 Retrofit API 调用仍指向旧地址（或占位地址），**必须重启 App 才生效** |
| **涉及文件** | NetworkModule.kt:L167-L180 |
| **修复方案** | 引导页保存后，添加 Toast 提示"请重启App使配置生效"（已有类似提示但不够明确）；或在引导完成后触发 App 进程重启 |

### P1-2：validateToken() 网络请求无超时导致启动白屏

| 项目 | 内容 |
|------|------|
| **描述** | AppNavigation 启动时 `validateToken()` 发起网络请求验证 token，OkHttp readTimeout 为 15 秒。服务器不可达时用户看到长达 15 秒空白屏幕 |
| **影响** | 首次启动或服务器离线时体验极差 |
| **涉及文件** | AppNavigation.kt:L78-L92 |
| **修复方案** | 在 `isCheckingAuth` 等待期间显示加载指示器（CircularProgressIndicator），而非空白屏幕 |

### P1-3：BackgroundScheduler 线程中操作 asyncio 事件循环可能失败

| 项目 | 内容 |
|------|------|
| **描述** | `_refresh_kuaimai_session` 在 APScheduler 的后台线程中执行，使用 `asyncio.get_event_loop()` + `ensure_future()` |
| **影响** | 线程中不存在 FastAPI 的事件循环，`ensure_future` 可能抛出 RuntimeError（外层有 try-catch 兜底但会静默失败） |
| **涉及文件** | backend/main.py:L308-L331（backend 版本，docker-deploy 版本中此函数不存在） |
| **修复方案** | 改用 `asyncio.run()` 独立运行异步函数，或在调用处管理事件循环生命周期 |

### P1-4：/admin 和 /docs 跳过 API Key 认证

| 项目 | 内容 |
|------|------|
| **描述** | auth.py 中 SKIP_AUTH_PREFIXES 包含 `/admin` 和 `/docs`，任何人无需认证即可访问管理后台和 Swagger 文档 |
| **影响** | 管理后台暴露配置二维码（含 serverUrl+apiKey）；Swagger 文档暴露 API 结构和参数 |
| **涉及文件** | backend/app/auth.py:L18 |
| **修复方案** | `/docs` 应该跳过认证（开发便利）；但 `/admin` 应添加基本认证或在进入时要求 API Key |

### P1-5：首次部署无 .env 和 kuaimai.json 引导

| 项目 | 内容 |
|------|------|
| **描述** | 项目仅提供 `.env.docker.example` 和 `kuaimai.example.json` 模板，无首次部署文档或 init 脚本 |
| **影响** | 用户不知道需要创建这些文件，容器以空配置启动，API Key 为空（认证中间件不启用）、快麦 API 全部失败 |
| **涉及文件** | backend/.env.docker.example、backend/kuaimai.example.json |
| **修复方案** | 在 docker-compose.yml 同级目录添加 `init.sh` 脚本；或在 main.py 启动时检测并生成默认 .env；同时补充 docker-deploy/README.md 部署说明 |

### P1-6：.env.docker.example 缺少必要环境变量

| 项目 | 内容 |
|------|------|
| **描述** | 模板缺少 `SERVER_URL`（扫码配置页生成二维码需要）、`CORS_ORIGINS`（生产应限定）、`SESSION_WARNING_DAYS` |
| **影响** | `/setup` 页面生成的二维码地址可能为容器内部地址（172.x.x.x），PDA 无法连接 |
| **涉及文件** | backend/.env.docker.example |
| **修复方案** | 补充 `SERVER_URL`、`CORS_ORIGINS`、`SESSION_WARNING_DAYS` 到模板，带注释说明 |

### P1-7：docker-deploy 的 kuaimai.example.json 缺少 refresh_token 字段

| 项目 | 内容 |
|------|------|
| **描述** | docker-deploy/kuaimai.example.json 中缺少 `refresh_token` 字段（源文件 backend/ 中已包含） |
| **影响** | session 自动刷新功能无法工作 |
| **涉及文件** | docker-deploy/kuaimai.example.json |
| **修复方案** | 修复同步脚本后自动解决（P0-4）；或手动更新 docker-deploy 版本 |

---

## 四、P2 中级别缺陷

### P2-1：Android APK 无正式签名配置

| 项目 | 内容 |
|------|------|
| **描述** | build.gradle.kts 中无 `signingConfigs` 块，Release 构建使用默认 debug 签名 |
| **影响** | 正式签名 APK 才能在真机上自动更新（OTA），debug 签名安装会因签名不一致失败 |
| **涉及文件** | app/build.gradle.kts |

### P2-2：AndroidManifest.xml allowBackup 未关闭

| 项目 | 内容 |
|------|------|
| **描述** | `android:allowBackup="true"` 允许自动备份应用数据 |
| **影响** | 本地 SQLite 数据库（含用户密码哈希、API Key 等敏感数据）可能被自动备份到云端 |
| **涉及文件** | app/src/main/AndroidManifest.xml |

### P2-3：GuideScreen 服务器地址校验过于宽松

| 项目 | 内容 |
|------|------|
| **描述** | 仅检查 `serverUrl.startsWith("http")`，输入 `"http"` 三个字符即可通过 |
| **影响** | 用户可能输入无效 URL 导致后续连接失败，无明确错误提示 |
| **涉及文件** | GuideScreen.kt:L193 |

### P2-4：GuideScreen 扫码配置解析失败无提示

| 项目 | 内容 |
|------|------|
| **描述** | SetupQrParser.parse() 返回 null 时（无效二维码），静默返回，serverUrl/apiKey 保持空值 |
| **影响** | 用户扫码后无任何反馈，困惑"为什么没反应" |
| **涉及文件** | GuideScreen.kt:L63-L69 |

### P2-5：App.kt 注入未使用的 SharedPreferences 字段

| 项目 | 内容 |
|------|------|
| **描述** | `@Inject lateinit var prefs: SharedPreferences` 在整个 App.kt 中从未引用 |
| **影响** | 轻微：增加启动时的依赖解析开销 |
| **涉及文件** | App.kt:L24-L25 |

### P2-6：backend 数据库连接无目录写权限检查

| 项目 | 内容 |
|------|------|
| **描述** | get_db() 直接 sqlite3.connect(DB_PATH)，若 /data 目录不存在或不可写，静默失败 |
| **影响** | 容器启动后看似正常但数据库操作全部失败 |
| **涉及文件** | backend/app/database.py |

### P2-7：docker 容器无资源限制

| 项目 | 内容 |
|------|------|
| **描述** | docker-compose.yml 无 `deploy.resources.limits` 配置 |
| **影响** | NAS 等资源受限设备上可能 OOM |
| **涉及文件** | backend/docker-compose.yml |

---

## 五、已验证无问题的方面（无需修改）

| 检查项 | 结果 |
|--------|:--:|
| Room Schema v1→v2 迁移 SQL | ✅ 正确（`ALTER TABLE product_image ADD COLUMN remote_id INTEGER NOT NULL DEFAULT 0`） |
| 4个 Entity 共35个字段与 Schema 一致性 | ✅ 完全一致 |
| 索引覆盖（pick_item 4个索引、product_image 1个UNIQUE） | ✅ 完整 |
| 外键 Cascade 约束 | ✅ 正确 |
| NetworkModule 占位URL `http://localhost:1/` 安全性 | ✅ 安全（特权端口，连接拒绝） |
| 前次 v1.13 修复的 12 个问题 | ✅ 无回归 |

---

## 六、修复优先级与影响矩阵

| 优先级 | 问题 | 复杂度 | 涉及文件数 | 如果修了能解决什么 |
|:------:|------|:------:|:----------:|------|
| **1** | P0-1 服务器地址 SP 不匹配 | 低 | 4 | App 所有后端 API 可用 |
| **2** | P0-2 Dockerfile pip 包丢失 | 低 | 1 | 容器能正常启动 |
| **3** | P0-3 + P0-4 同步脚本+main.py | 中 | 2 | 部署产物功能完整 |
| 4 | P1-1 Retrofit baseUrl 提示 | 低 | 1 | 用户知道要重启 |
| 5 | P1-2 启动白屏加载指示器 | 低 | 1 | 启动体验改善 |
| 6 | P1-3 asyncio 事件循环 | 低 | 1 | session 刷新可靠 |
| 7 | P1-4 /admin 认证 | 中 | 2 | 安全 |
| 8 | P1-5 部署引导 | 中 | 2 | 首次部署顺畅 |
| 9 | P1-6/P1-7 环境变量模板 | 低 | 2 | 配置模板完整 |
| 10 | P2-1~P2-7 | 低 | 6 | 完善 |

---

## 七、验证标准

完成后需验证：

1. **Docker 构建**：`cd docker-deploy && docker-compose build` 成功，容器启动后 `/health` 返回 200
2. **Lint**：`./gradlew lint` 通过
3. **APK 构建**：`./gradlew assembleDebug` 成功
4. **端到端流程**：
   - App 引导页配置服务器地址 → 通过 Retrofit API 获取拣货区列表成功（验证 P0-1）
   - Docker 构建镜像启动后在浏览器访问 /admin 和 /api/areas
5. **同步脚本**：运行后 `docker-deploy/main.py` 包含 admin/users 路由
