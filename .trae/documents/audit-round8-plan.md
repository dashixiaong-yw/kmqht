# 第八次审计计划：近40次更新全量回溯审计

## 概要

本次审计对 v1.01~v1.40（约40次版本更新）进行全面回溯审查，通过 4 路并行搜索代理分别覆盖 **Android 端代码**、**后端代码**、**配置一致性**、**知识图谱**，确保：

1. 所有修复已正确应用（无遗漏）
2. 修复本身无逻辑缺陷（无回归）
3. 前后端引用一致（无字段名/类型/接口不匹配）
4. 知识图谱已完整记录设计决策

---

## 审计方法

| 维度 | 覆盖范围 | 方法 |
|:-----|:---------|:-----|
| Android 端 | 12个关键文件 | 逐一检查近期修复项是否已正确应用 |
| 后端 | 所有路由/服务/工具 | 逐模块检查代码逻辑 + 近期修复验证 |
| 配置一致性 | 版本号/端口/依赖/R8/同步脚本 | 交叉对比所有配置文件和产线环境 |
| 知识图谱 | 全量134实体+73关系 | 搜索设计决策、修复记录、回归记录 |

---

## 审计结果

### ✅ Android 端（App）— 全部通过

| 文件 | 关键修复项 | 状态 |
|:-----|:----------|:----:|
| PickDetailViewModel.kt | getImageUrls trimEnd、serverUrl 运行时读取 | 已正确应用 |
| PickDetailScreen.kt | 图片URL拼接、触摸热区 | 已正确应用 |
| ProductScreen.kt | 图片上传、删除确认 | 已正确应用 |
| ProductViewModel.kt | loadImages trimEnd、离线图片入队 | 已正确应用 |
| OrderSyncWorker.kt | 重试计数(MAX_RETRY=3)、冲突标记、响应检查 | 已正确应用 |
| ImageRepository.kt | JSONObject解析、防重复同步 | 已正确应用 |
| ScannerManager.kt | 注册/注销、声音池生命周期、防抖 | 已正确应用 |
| KuaimaiInterceptor.kt | 签名流程、null处理、JSON NULL | 已正确应用 |
| HomeScreen.kt | 引导条、网络状态、AppAlignment常量 | 已正确应用 |
| PickOrderRepository.kt | 离线入队、乐观更新策略 | 已正确应用 |
| AppNavigation.kt | SessionExpiredEvent监听、导航参数类型 | 已正确应用 |
| NetworkMonitor.kt | register/unregister、try-catch保护 | 已正确应用 |

### ⚠️ 后端 — 发现 1 个 P0 Bug + 1 个配置缺陷

#### P0 Bug: orders.py complete_item 中 completed_count 多加了 1

- **文件**: [orders.py:248-249](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L248-L249)
- **问题**: `complete_item` 函数中，第 242-244 行已经执行 `UPDATE pick_orders SET completed_count = completed_count + 1` 递增了一次计数。但第 248-249 行 SELECT 时又写了 `completed_count + 1 as new_completed`，导致 `new_completed` 比实际完成数多了 1。
- **后果**: 当完成**倒数第二个**明细时，`new_completed >= total_count` 条件提前成立，取货单被错误标记为**已完成**。例如：`total_count=5`，完成第 4 项时实际只完成 4/5，但 SELECT 计算 `4+1=5`，条件 `5 >= 5` 成立，订单被标记为完成。
- **修复**: 将第 248 行的 `completed_count + 1 as new_completed` 改为 `completed_count as new_completed`（去掉 `+ 1`）。

#### ⚠️ 配置缺陷: config.py SERVER_PORT 默认值不一致

- **文件**: [config.py:26](file:///d:/trea项目/快麦取货通/backend/app/config.py#L26)
- **问题**: `SERVER_PORT` 默认值写死了 `"8000"`，但 Dockerfile、docker-compose.yml、.env.docker.example 全部使用 `8900`。
- **风险**: 如果 `.env` 未正确加载（例如部署时遗漏），config.py 中 SERVER_PORT 会退化为 8000，而 uvicorn 实际启动在 8900，导致配置二维码 URL、CORS 等拼接路径不正确。
- **修复**: 将第 26 行 `int(os.getenv("SERVER_PORT", "8000"))` 改为 `int(os.getenv("SERVER_PORT", "8900"))`。

### ✅ 配置一致性 — 其余全部通过

| 检查项 | 状态 |
|:-------|:----:|
| 版本号 (build.gradle.kts / CHANGELOG.md / gradle.properties) | 一致 (1.40) |
| 后端依赖 | 全部固定版本，无冲突 |
| R8 混淆规则 | 覆盖全面（Retrofit/Gson/Room/Sealed/Hilt/CameraX/Worker） |
| 同步脚本端口校验 | 一致 (8900) |
| 同步脚本覆盖范围 | 完整 (9个文件+app目录) |
| 签名配置 | 合理回退 |
| 数据库索引 | PickOrder/PendingOperation 已添加索引 |
| R8 full mode 已开启 | 已启用 |

### ✅ 知识图谱 — 完整

- 134 个实体节点，73 条关系边
- 31 个 BugFix 节点均已记录
- 16 个设计决策节点完整
- 版本/里程碑节点完整覆盖

---

## 修复方案

### 修复 1：orders.py completed_count 计算错误

**文件**: [orders.py:247-249](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L247-L249)

```python
# 当前（错误）：
cursor.execute(
    "SELECT total_count, completed_count + 1 as new_completed FROM pick_orders WHERE id = ?",
    (order_id,)
)
# 修复后：
cursor.execute(
    "SELECT total_count, completed_count as new_completed FROM pick_orders WHERE id = ?",
    (order_id,)
)
```

### 修复 2：config.py SERVER_PORT 默认值

**文件**: [config.py:26](file:///d:/trea项目/快麦取货通/backend/app/config.py#L26)

```python
# 当前：
SERVER_PORT: int = int(os.getenv("SERVER_PORT", "8000"))
# 修复后：
SERVER_PORT: int = int(os.getenv("SERVER_PORT", "8900"))
```

---

## 验证步骤

1. `./gradlew lint` — Android 端代码检查（虽然只改后端，但需确保无影响）
2. 后端语法校验：`python -c "import backend.app.routers.orders; import backend.app.config"`
3. 构建验证：`./gradlew assembleRelease`

---

## 收尾（审批后执行）

1. 应用上述 2 处修复
2. `./gradlew lint` 检查
3. `./gradlew assembleRelease` 构建
4. 更新版本号至 1.41
5. 更新知识图谱
6. 同步到 docker-deploy
7. Git 提交推送
