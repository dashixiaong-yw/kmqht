# 项目规则

> 快麦取货通 - Android PDA App 操作规范

---

## 一、核心原则

⚠️

1. **所有代码修改必须在项目 `app/` 目录进行，切勿修改 `prototype/` 等非代码产物（除非明确要求更新原型）**
2. **Android 项目禁止手动修改 `build/`、`.gradle/` 等构建产物目录**
3. **修改前必须查阅本项目专用知识图谱（kuaimai-memory MCP），确保不遗漏已确认的设计决策**

---

## 二、开发修改流程

```
查阅知识图谱 → 修改代码 → 验证(lint/build) → 更新版本号(3处一致+验证) → 更新知识图谱 → 同步到docker-deploy → Git提交推送
```

**步骤（禁止跳过任何步骤）**：

| Step | 内容 | 说明 |
|:----:|------|------|
| 1 | **查阅知识图谱** | 使用 `mcp_kuaimai-memory_search_nodes` 或 `mcp_kuaimai-memory_read_graph` 查询本项目的知识图谱，确认相关设计决策后再开始修改代码 |
| 2 | 修改代码 | 在 `app/` 目录修改，禁止动 `prototype/` 等非代码文件 |
| 3 | 验证代码 | `./gradlew lint`、`./gradlew assembleDebug` 必须通过 |
| 4 | 更新版本号 | 更新 app/build.gradle.kts + CHANGELOG.md + gradle.properties，**并验证3处版本号完全一致** |
| 5 | **更新知识图谱** | 使用 `mcp_kuaimai-memory_add_observations` 或 `mcp_kuaimai-memory_create_entities/relations` 将本次变更的设计决策同步到知识图谱 |
| 6 | 同步到docker-deploy | 运行 `.\scripts\sync-to-docker-deploy.ps1 -Force` 同步后端部署文件 |
| 7 | Git 提交推送 | `git add .` → `git commit -m "v版本号: 变更描述"` → `git push` |

> **知识图谱操作说明**：
> - **查询时**：优先用 `search_nodes` 按关键词搜索，或用 `read_graph` 读取全量图谱；已知节点名时用 `open_nodes(names=[...])`
> - **更新时**：新增实体用 `create_entities`，新增关系用 `create_relations`，补充观察用 `add_observations`
> - **必须操作**：每次开发流程中Step1和Step5为强制步骤，禁止跳过

**封版规则（Git 提交后强制执行）**：

| 规则 | 说明 |
|------|------|
| 禁止同版本二次修改 | Git 提交后，该版本代码封版，禁止再修改任何文件 |
| 新变更必须新版本 | 提交后发现需要修改，必须递增版本号，重新走完整流程 |
| 禁止重复版本号 | CHANGELOG.md 中同一版本号只能出现一次 |
| 提交前确认完整性 | 更新版本号前，必须确认所有代码修改已完成，不再有遗漏 |
| **批量修复原则** | **同一任务的多个级别修复必须全部完成后再提交** |

---

## 三、版本号管理

**格式**：`主版本.次版本`，次版本 1-99，满 99 后主版本+1、次版本归 1

**递增示例**：1.1 → 1.2 → ... → 1.99 → 2.1 → ...

**3处必须一致**：

| 位置 | 格式 | 示例 |
|------|------|------|
| [app/build.gradle.kts](file:///d:/trea项目/快麦取货通/app/build.gradle.kts) | versionName | `versionName = "1.10"` |
| [CHANGELOG.md](file:///d:/trea项目/快麦取货通/CHANGELOG.md) | 文件顶部追加新版本 | `## 1.10 (2026-06-15)` |
| [gradle.properties](file:///d:/trea项目/快麦取货通/gradle.properties) | 备注更新版本号 | `# Version: 1.10` |

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
│   └── build.gradle.kts     ← 项目构建配置
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
| `./gradlew assembleDebug` | 构建 Debug APK |
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

- [ ] 已查阅知识图谱确认设计决策
- [ ] 代码修改在 `app/` 目录完成
- [ ] 修改已验证通过（lint / build）
- [ ] 3处版本号一致（build.gradle.kts + CHANGELOG.md + gradle.properties）
- [ ] 已更新知识图谱（本次变更的设计决策已同步）
- [ ] 已同步到docker-deploy（运行 `.\scripts\sync-to-docker-deploy.ps1 -Force`）
- [ ] Git commit 消息符合格式 `v版本号: 变更描述`
- [ ] CHANGELOG.md 已更新