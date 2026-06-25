# 项目规则

> 快麦取货通 - Android PDA App 操作规范

---

## 一、核心原则

⚠️

1. **所有代码修改必须在项目 `app/` 目录进行，切勿修改 `prototype/` 等非代码产物（除非明确要求更新原型）**
2. **Android 项目禁止手动修改 `build/`、`.gradle/` 等构建产物目录**
3. **修改前必须查阅本项目专用知识图谱（kuaimai-memory MCP），确保不遗漏已确认的设计决策**
4. **项目文档索引见下方「十二、项目文档索引」章节，AI 首次进入项目时优先查阅此索引**

---

## 二、计划模式（Plan Mode）衔接规范

> 本节说明 AI 在 Plan Mode（系统提示词中的/plan指令）结束后，如何正确接入下方的开发修改流程。

1. **Plan Mode 结束 = 进入执行阶段**：用户批准的方案锁定全部需求。批准后 AI 自动转入「三、开发修改流程」的 Step 1，严格遵守 7 步流程，禁止跳过任何一步
2. **方案批准后禁止新增需求**：发布阶段（Step 5-7）禁止再修改任何代码。若用户提出新需求，必须完成当前版本收尾，再以新版本号重新走完整流程
3. **版本号递增**：不论变更规模（即使 1 行代码），进入执行阶段后必须先递增版本号（Step 3）再走完整流程，**禁止在构建APK之后才更新版本号**。`git commit` 必须使用 `v版本号:` 前缀
4. **Plan Mode 生成的方案文档需包含完整流程规划**：方案中必须体现 7 步流程（知识图谱查阅、代码修改、版本号、构建、知识图谱更新、同步、Git 提交）

---

## 三、开发修改流程

```
[查阅知识图谱] → [修改代码(可循环)] → [更新版本号] → [构建APK(含lint)] → [更新知识图谱] → [同步docker-deploy] → [Git提交推送]
```

**步骤表**：

| Step | 阶段 | 内容 | 说明 |
|:----:|:----:|------|------|
| 1 | 开发 | **查阅知识图谱** | 首次修改前必须查阅；批量任务中后续任务按需查阅 |
| 2 | 开发 | 修改代码 | 在 `app/` 目录修改，**支持批量完成多个任务后再更新版本号** |
| 3 | 版本号 | **更新版本号**（含Docker BUILD_VERSION） | **先读取6处当前版本号取最大值，再+1递增**，更新6处并验证一致。**此步骤完成后版本号锁定，后续回退不改版本号** |
| 4 | 构建 | **构建APK** | 执行 `./gradlew assembleRelease`（自动包含 lintVitalRelease）。**必须先完成Step 3再执行本步骤，否则APK文件名与版本号不一致。构建成功 → 此APK为最终发布版本（文件名含新版本号）。构建失败 → 回到Step 2修复代码（版本号不递增，已锁定的版本号保持不变）** |
| 5 | 发布 | **更新知识图谱** | 将本次所有变更的设计决策同步到知识图谱 |
| 6 | 发布 | 同步到docker-deploy | 运行 `.\scripts\sync-to-docker-deploy.ps1 -Force` 同步后端部署文件 |
| 7 | 发布 | Git 提交推送 | `git add .` → `git commit -m "v版本号: 变更描述"` → `git push` |

> **⚠️ AI TodoList 必须严格对应 Step 1-7 顺序**：TodoList 中的每一项应分别对应一个 Step，禁止将「更新版本号(Step 3)」与「构建APK(Step 4)」合并为同一项。Step 3 完成后才能进入 Step 4。

> **知识图谱操作说明**：
> - **查询时**：优先用 `search_nodes` 按关键词搜索，或用 `read_graph` 读取全量图谱；已知节点名时用 `open_nodes(names=[...])`
> - **更新时**：新增实体用 `create_entities`，新增关系用 `create_relations`，补充观察用 `add_observations`
> - **必须操作**：每次开发流程中Step 1和Step 5为强制步骤，禁止跳过

### 阶段边界与回退规则

```
开发阶段(Step 1-2) ← → 版本号阶段(Step 3) ← → 构建阶段(Step 4) →→→ 发布阶段(Step 5-7) 不可逆
```

| 规则 | 说明 |
|------|------|
| **开发阶段可循环** | Step 1-2 可重复执行，直到所有任务完成 |
| **版本号锁定规则** | Step 3 完成后版本号锁定，后续任何回退都不重递增版本号 |
| **构建失败回退** | Step 4 失败 → 回到 Step 2（修改代码），**版本号不变**。修复后从 Step 4 继续执行 |
| **发布阶段不可逆** | Step 4 成功后进入 Step 5-7，禁止再修改任何代码 |
| **遗漏处理** | Step 5-7 中发现遗漏，必须完成当前版本发布，再以新版本号重新走完整流程 |
| **批量任务合并提交** | 多个任务（无论P0/P1/P2）在同一版本中合并提交，版本号只递增一次 |

### 封版规则（Git 提交后强制执行）

- 禁止同版本二次提交（CHANGELOG.md 中同一版本号只能出现一次）
- 提交后发现需要修改，必须递增版本号重新走完整流程
- 禁止重复版本号

### 关键约束

| 约束 | 说明 |
|:-----|:------|
| **Step 4 失败回退不改版本号** | 版本号在 Step 3 已锁定，回退时不可重递增，不可回退到旧版本号 |
| **asembleRelease 包含 lint** | 无需单独运行 `./gradlew lint`，`assembleRelease` 内部自动执行 `lintVitalRelease` |
| **进入发布阶段时 Step 4 必须已成功** | Step 4 成功后版本号 + APK 均已确定，进入发布阶段后代码冻结 |
| **Step 3(更新版本号) 必须在 Step 4(构建APK) 之前** | APK 文件名自动取自 `versionName`，必须先更新版本号再构建，否则文件名与 Git 提交的版本号不一致 |

---

## 四、版本号管理

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

## 五、Git 操作

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

## 九、开发命令

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

## 十一、验证清单（每次修改后检查）

**开发阶段**：

- [ ] 已查阅知识图谱确认设计决策
- [ ] 所有任务代码修改已完成（批量任务时逐项确认无遗漏）
- [ ] 代码修改在 `app/` 目录完成

**冻结阶段**：

- [ ] 版本号已更新（4处手动 + 6处一致）

**发布阶段**：

- [ ] APK构建成功（release 构建产出 `release/快麦取货通-版本号.apk`）
- [ ] 已更新知识图谱（本次所有变更的设计决策已同步）
- [ ] 已同步到docker-deploy（运行 `.\scripts\sync-to-docker-deploy.ps1 -Force`）
- [ ] Git commit 消息符合格式 `v版本号: 变更描述`
- [ ] CHANGELOG.md 已更新

---

## 十二、项目文档索引

> 以下文档供 AI 和开发者查阅，首次进入项目时按需浏览。

### 配置与部署

| 文档 | 路径 | 内容 |
|:-----|:-----|:------|
| 🔑 快麦API接口规范 | [rules/kuaimai-api-spec.md](kuaimai-api-spec.md) | **必读** — 快麦开放平台所有接口的请求/响应格式、编码差异、常见错误 |
| 🔑 快麦凭证配置说明 | [docs/快麦凭证配置说明.md](../../docs/快麦凭证配置说明.md) | appKey/appSecret/session 的配置与替换方法，3种修改方式（文件/管理后台/热加载） |
| 🐳 Docker部署方案 | [.trae/documents/DOCKER_DEPLOYMENT_EXPERIENCE.md](../documents/DOCKER_DEPLOYMENT_EXPERIENCE.md) | Docker部署经验与踩坑记录 |
| 📦 部署就绪报告 | [.trae/documents/deployment-readiness-report.md](../documents/deployment-readiness-report.md) | 部署前检查清单、完整部署步骤 |
| 📐 docker-compose说明 | [.trae/documents/DOCKER_COMPOSE_YML_VS_YAML.md](../documents/DOCKER_COMPOSE_YML_VS_YAML.md) | docker-compose.yml 与 .yaml 区别说明 |
| 📋 权限审计 | [.trae/documents/user-permission-review-v4.md](../documents/user-permission-review-v4.md) | 用户权限审计与锁定 |

### 流程改进

| 文档 | 路径 | 内容 |
|:-----|:-----|:------|
| 🔄 流程审查与改进 | [.trae/documents/项目规则流程审查与改进方案.md](../documents/项目规则流程审查与改进方案.md) | Plan Mode衔接规范的必要性与分析 |