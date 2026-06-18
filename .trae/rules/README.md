# 项目规则

> 快麦取货通 - Android PDA App 操作规范

---

## 一、核心原则

⚠️

1. **所有代码修改必须在项目 `app/` 目录进行，切勿修改 `prototype/` 等非代码产物（除非明确要求更新原型）**
2. **Android 项目禁止手动修改 `build/`、`.gradle/` 等构建产物目录**
3. **修改前必须查阅本项目专用知识图谱（kuaimai-memory MCP），确保不遗漏已确认的设计决策**
4. **项目文档索引见下方「十一、项目文档索引」章节，AI 首次进入项目时优先查阅此索引**

---

## 二、开发修改流程

```
[查阅知识图谱] → [修改代码(可循环)] → [验证+构建(失败回退)] → 全部完成 → [更新版本号 → 更新知识图谱 → 同步docker-deploy → Git提交推送]
```

**步骤（禁止跳过任何步骤）**：

| Step | 阶段 | 内容 | 说明 |
|:----:|:----:|------|------|
| 1 | 开发 | **查阅知识图谱** | 首次修改前必须查阅；批量任务中后续任务按需查阅 |
| 2 | 开发 | 修改代码 | 在 `app/` 目录修改，**支持批量完成多个任务后再进入收尾** |
| 3 | 开发 | 验证代码 | `./gradlew lint` 必须通过；**失败则回到Step 2修复** |
| 4 | 开发 | 构建APK | `./gradlew assembleRelease`（签名+混淆）构建成功；**失败则回到Step 2修复** |
| 5 | 收尾 | 更新版本号（含Docker BUILD_VERSION） | **⚠️ 进入收尾后禁止再修改代码**；**先读取6处当前版本号取最大值，再+1递增**，更新6处并验证一致 |
| 6 | 收尾 | **更新知识图谱** | 将本次所有变更的设计决策同步到知识图谱 |
| 7 | 收尾 | 同步到docker-deploy | 运行 `.\scripts\sync-to-docker-deploy.ps1 -Force` 同步后端部署文件 |
| 8 | 收尾 | Git 提交推送 | `git add .` → `git commit -m "v版本号: 变更描述"` → `git push` |

> **知识图谱操作说明**：
> - **查询时**：优先用 `search_nodes` 按关键词搜索，或用 `read_graph` 读取全量图谱；已知节点名时用 `open_nodes(names=[...])`
> - **更新时**：新增实体用 `create_entities`，新增关系用 `create_relations`，补充观察用 `add_observations`
> - **必须操作**：每次开发流程中Step1和Step6为强制步骤，禁止跳过

**收尾阶段准入条件（进入Step 5前必须同时满足）**：

- ✅ 所有任务代码修改已完成（逐项确认，无遗漏）
- ✅ `./gradlew lint` 通过
- ✅ `./gradlew assembleRelease`（分发签名APK）构建成功

**收尾阶段禁止回退**：进入Step 5后禁止再修改任何代码文件。若发现遗漏，必须完成当前收尾（Step 5-8），再以新版本号重新走完整流程。

**批量任务必须合并提交**：多个任务（无论P0/P1/P2）必须在同一版本中合并提交，禁止每个任务单独提交，版本号只递增一次。

**封版规则（Git 提交后强制执行）**：

| 规则 | 说明 |
|------|------|
| **开发阶段可循环** | Step 1-4可重复执行，直到所有任务完成且验证构建通过 |
| **收尾阶段不可逆** | 进入Step 5后禁止再修改任何代码，必须完成Step 5-8 |
| **批量任务合并提交** | 多个任务必须在同一版本中合并提交，版本号只递增一次 |
| 禁止同版本二次修改 | Git 提交后，该版本代码封版 |
| 新变更必须新版本 | 提交后发现需要修改，必须递增版本号，重新走完整流程 |
| 禁止重复版本号 | CHANGELOG.md 中同一版本号只能出现一次 |

---

## 三、版本号管理

**格式**：`主版本.次版本`，次版本 1-99，满 99 后主版本+1、次版本归 1

**递增示例**：1.1 → 1.2 → ... → 1.99 → 2.1 → ...

**多会话防冲突规则**：更新版本号时，必须先读取6处当前版本号（build.gradle.kts + CHANGELOG.md + gradle.properties + backend/docker-compose.yml BUILD_VERSION + docker-deploy/docker-compose.yml BUILD_VERSION + docker-deploy/docker-compose.yaml BUILD_VERSION），取最大值后再+1递增。禁止基于记忆中的版本号直接递增，必须以文件实际内容为准。

**6处必须一致**（仅需手动更新 build.gradle.kts + CHANGELOG.md + gradle.properties + backend/docker-compose.yml 共4处，其余2处由同步脚本自动生成）：

| 位置 | 格式 | 示例 | 更新方式 |
|------|------|------|:--------:|
| [app/build.gradle.kts](file:///d:/trea项目/快麦取货通/app/build.gradle.kts) | versionName | `versionName = "1.10"` | 手动 |
| [CHANGELOG.md](file:///d:/trea项目/快麦取货通/CHANGELOG.md) | 文件顶部追加新版本 | `## 1.10 (2026-06-15)` | 手动 |
| [gradle.properties](file:///d:/trea项目/快麦取货通/gradle.properties) | 备注更新版本号 | `# Version: 1.10` | 手动 |
| [backend/docker-compose.yml](file:///d:/trea项目/快麦取货通/backend/docker-compose.yml) | build.args.BUILD_VERSION | `BUILD_VERSION: v1.10` | 手动 |
| [docker-deploy/docker-compose.yml](file:///d:/trea项目/快麦取货通/docker-deploy/docker-compose.yml) | build.args.BUILD_VERSION | `BUILD_VERSION: v1.10` | 同步脚本从 backend 复制 |
| [docker-deploy/docker-compose.yaml](file:///d:/trea项目/快麦取货通/docker-deploy/docker-compose.yaml) | build.args.BUILD_VERSION | `BUILD_VERSION: v1.10` | 同步脚本从 backend 复制 |

**CHANGELOG 格式**：

```markdown
## 版本号 (YYYY-MM-DD)

### 新增
- 新增内容

### 修改
- 修改内容

### 修复
- 修复内容
```

---

## 四、Git 操作

**远程仓库**：`https://github.com/dashixiaong-yw/kmqht`
**默认分支**：`master`

**Commit 消息格式**：`v版本号: 变更描述`
**示例**：`v1.5: 取货单管理模块 - 新建取货单弹窗选择拣货区`

**PowerShell 限制**：

- ❌ **禁止使用 heredoc 语法**（`<<'EOF'`/`@'...'@`），PowerShell 5.1 不支持
- ✅ commit 消息使用单行字符串：`git commit -m "v1.3: 修复描述"`
- ✅ 多行内容用分号或空格连接为单行

**禁止事项**：

- ❌ 禁止提交 `.env` 或密钥文件
- ❌ 禁止提交 `build/`、`.gradle/` 等构建产物
- ❌ 禁止提交 `local.properties`
- ❌ 禁止 force push 到主分支
- ❌ 禁止修改已推送的 commit 历史

---

## 五、代码规范

### 通用规范

- **变量/函数名**使用小驼峰，类名使用 PascalCase
- **代码注释**使用中文
- **单个文件**不超过 500 行，**函数**不超过 40 行，**嵌套**不超过 3 层
- **所有函数**必须包含类型注解，禁止使用 `Any`
- **异步操作**必须 try-catch，禁止空 catch
- **时间格式**必须使用北京时间（UTC+8），所有时间戳使用 Long 类型

### Kotlin/Compose 专项规范

- 所有页面统一使用 `Modifier.align() + Arrangement` 控制对齐，禁止混用 padding 偏移模拟对齐
- 对齐常量收敛到 `ui/theme/Alignment.kt`，页面只引用常量
- 颜色值引用 `ui/theme/Color.kt` 中的命名常量，禁止直接写色值
- 供应商名称：20sp + Bold + `#DC2626`（红色强调），参见 UI 设计规范
- 按钮使用语义色（浅底深字），不直接使用白色文字

### 后端规范

- Python FastAPI + Pydantic 数据模型
- 所有接口遵循 `api.yaml` OpenAPI 契约定义
- 使用北京时间（UTC+8）存储时间
- 图片上传严格区分 `image_type: area/box`

---

## 六、设计规范速查

参见完整的 [UI设计规范章节](file:///d:/trea项目/快麦取货通/.trae/documents/kuaimai-pda-app-plan.md#UI设计规范)。

| 项目 | 值 |
|------|------|
| 主色 | `#2563EB`（品牌蓝） |
| 供应商名称 | `#DC2626`（红色加强）20sp Bold |
| 按钮主操作 | `#DBEAFE`（浅蓝底） `#1D4ED8`（深蓝字） |
| 按钮完成 | `#DCFCE7`（浅绿底） `#15803D`（深绿字） |
| 字体 | Noto Sans SC |
| 待办行高 | 72dp |
| 可点击元素 | 最小 56dp×56dp |
| 对齐方式 | `Modifier.align() + Arrangement` |

---

## 七、项目结构速览

```
快麦取货通/
├── .trae/                   ← AI 上下文
│   ├── documents/           ← 计划/设计文档
│   │   └── kuaimai-pda-app-plan.md  ← 主计划文档
│   ├── memory/              ← 专用知识图谱数据
│   └── rules/README.md      ← 本文件（唯一规则入口）
├── app/                     ← Android 源代码（主工作区）
│   ├── src/main/java/com/kuaimai/pda/
│   │   ├── App.kt           ← @HiltAndroidApp
│   │   ├── MainActivity.kt  ← 单Activity
│   │   ├── di/              ← 依赖注入
│   │   ├── data/            ← API/DTO/Room/Repository
│   │   ├── scanner/         ← PDA扫码模块
│   │   ├── ui/              ← Compose UI
│   │   └── util/            ← 工具函数
│   ├── build.gradle.kts     ← 项目构建配置
│   ├── proguard-rules.pro   ← R8 混淆规则
│   └── kuaimai-release.keystore ← APK 签名证书（禁止提交，仅本地构建用）
├── backend/                 ← FastAPI 图片上传服务
├── scripts/                 ← 运维脚本
│   └── sync-to-docker-deploy.ps1  ← 同步部署文件脚本
├── docker-deploy/           ← Docker部署包（同步脚本生成，勿手动修改）
├── prototype/               ← HTML UI 原型（设计参考）
│   └── index.html           ← 可交互原型
├── mcp-server/README.md     ← kuaimai-memory 使用说明
├── CHANGELOG.md             ← 变更日志
├── gradle.properties        ← 版本号配置
└── gradle/                  ← Gradle Wrapper
```

---

## 八、开发命令

| 命令 | 说明 |
|------|------|
| `./gradlew assembleRelease` | 构建 Release APK（签名+混淆，PDA安装用） |
| `./gradlew lint` | 代码检查 |
| `./gradlew test` | 运行单元测试 |
| `cd backend && docker-compose up -d --build` | 部署后端服务 |

---

## 九、常用知识图谱查询

```text
# 查询单个节点
mcp_kuaimai-memory_open_nodes(names=["F2_扫码待办"])

# 搜索关键词
mcp_kuaimai-memory_search_nodes(query="取货单")
```

---

## 十、验证清单（每次修改后检查）

**开发阶段**：

- [ ] 已查阅知识图谱确认设计决策
- [ ] 所有任务代码修改已完成（批量任务时逐项确认无遗漏）
- [ ] 代码修改在 `app/` 目录完成
- [ ] 代码验证通过（lint）
- [ ] APK构建成功（release 构建产出 `release/快麦取货通-版本号.apk`）

**收尾阶段**：

- [ ] 6处版本号一致（build.gradle.kts + CHANGELOG.md + gradle.properties + backend/docker-compose.yml + docker-deploy/docker-compose.yml + docker-deploy/docker-compose.yaml BUILD_VERSION）
- [ ] 已更新知识图谱（本次所有变更的设计决策已同步）
- [ ] 已同步到docker-deploy（运行 `.\scripts\sync-to-docker-deploy.ps1 -Force`）
- [ ] Git commit 消息符合格式 `v版本号: 变更描述`
- [ ] CHANGELOG.md 已更新

---

## 十一、项目文档索引

> 以下文档供 AI 和开发者查阅，首次进入项目时按需浏览。

### 配置与部署

| 文档 | 路径 | 内容 |
|:-----|:-----|:------|
| 🔑 快麦凭证配置说明 | [docs/快麦凭证配置说明.md](../../docs/快麦凭证配置说明.md) | appKey/appSecret/session 的配置与替换方法，3种修改方式（文件/管理后台/热加载） |
| 🐳 Docker部署方案 | [.trae/documents/DOCKER_DEPLOYMENT_EXPERIENCE.md](../documents/DOCKER_DEPLOYMENT_EXPERIENCE.md) | Docker部署经验与踩坑记录 |
| 📦 部署就绪报告 | [.trae/documents/deployment-readiness-report.md](../documents/deployment-readiness-report.md) | 部署前检查清单、完整部署步骤 |
| 📐 docker-compose说明 | [.trae/documents/DOCKER_COMPOSE_YML_VS_YAML.md](../documents/DOCKER_COMPOSE_YML_VS_YAML.md) | docker-compose.yml 与 .yaml 区别说明 |

### 审计与修复

| 文档 | 路径 | 内容 |
|:-----|:-----|:------|
| 📋 变更日志 | [CHANGELOG.md](../../CHANGELOG.md) | 全部版本历史与修复记录（v1.1-v1.18，共65个修复） |
| 🔍 第七次审计计划 | [.trae/documents/final-audit-round7-plan.md](../documents/final-audit-round7-plan.md) | 最终收尾审计：getImageUrls修复补提交 + 清理旧目录 |
| 🔍 第六次审计计划 | [.trae/documents/final-audit-round6-plan.md](../documents/final-audit-round6-plan.md) | getImageUrls运行时读取、OkHttp日志降级 |
| 🔍 第五次审计计划 | [.trae/documents/final-audit-round5-plan.md](../documents/final-audit-round5-plan.md) | 图片URL拼接、触摸热区、Alignment常量 |
| 🔍 第四次审计计划 | [.trae/documents/fourth-audit-plan.md](../documents/fourth-audit-plan.md) | XSS修复、WorkManager、PDA扫码注册 |
| 🔍 第三次审计计划 | [.trae/documents/third-audit-round3-plan.md](../documents/third-audit-round3-plan.md) | 扫码音效、广播崩溃、离线4xx |
| 🔍 部署审计计划 | [.trae/documents/first-deploy-audit-plan.md](../documents/first-deploy-audit-plan.md) | 首次部署前安全审计 |
| 🔍 首次完整审计 | [.trae/documents/full-code-audit-plan-v2.md](../documents/full-code-audit-plan-v2.md) | 首次全面代码审计 |

### 功能设计

| 文档 | 路径 | 内容 |
|:-----|:-----|:------|
| 📐 主计划文档 | [.trae/documents/kuaimai-pda-app-plan.md](../documents/kuaimai-pda-app-plan.md) | 项目整体设计、UI规范、路由、功能规格 |
| 🔄 凭证同步修复 | [.trae/documents/kuaimai-credentials-sync-plan.md](../documents/kuaimai-credentials-sync-plan.md) | PDA登录后自动同步快麦凭证的实现方案 |
| 🔄 Token刷新修复 | [.trae/documents/kuaimai-token-refresh-plan.md](../documents/kuaimai-token-refresh-plan.md) | 401自动刷新快麦session的完整方案 |
| 🔄 快麦API修复v2 | [.trae/documents/kuaimai-api-comprehensive-fix-plan.md](../documents/kuaimai-api-comprehensive-fix-plan.md) | 快麦API签名、超时重试、日志 |
| 🔄 快麦API修复v3 | [.trae/documents/kuaimai-api-final-defect-plan.md](../documents/kuaimai-api-final-defect-plan.md) | 快麦API最终缺陷修复 |
| 🔒 权限审计 | [.trae/documents/user-permission-review-v4.md](../documents/user-permission-review-v4.md) | 用户权限审计与锁定 |