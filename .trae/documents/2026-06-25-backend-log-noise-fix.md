# 后端日志噪音清理方案

> 2026-06-25

## 一、背景

后端服务运行日志中存在两类噪音，每 30 秒 ~ 1 分钟产生大量无意义日志行，干扰问题排查：

1. `watchfiles.main: 1 change detected` — 每 30 秒（与 Docker healthcheck 周期同步）
2. `数据库连接已建立: /data/kuaimai.db (thread=...)` — 健康检查/定时任务每次线程切换都打印

两类日志均为**正常行为**，但日志噪音过多。

## 二、修改方案

### 2.1 watchfiles 日志噪音

**根因**：`config.py` 中的 `start_config_watcher()` 用 `watchfiles.awatch("/data/")` 监控凭证文件变更。Docker healthcheck 每 30 秒执行 `SELECT 1` → SQLite 创建 WAL 文件 → overlay2 文件系统事件被 watchfiles 捕获。

现有 mtime 防抖已正确过滤掉无效的凭证重载（`load_kuaimai_config()` 只在 `kuaimai.json` 真实变化时执行），但 `watchfiles.main` 库自身的 INFO 日志仍每 30 秒输出一行。

**修改**（`config.py`）：
- 在 `start_config_watcher()` 开头设置 `logging.getLogger("watchfiles.main").setLevel(logging.WARNING)`，抑制 INFO/DEBUG 级别日志
- 将 `awatch` 循环中的日志改为仅当变更文件为 `kuaimai.json` 时才记录，其他文件变更静默

### 2.2 数据库连接日志噪音

**根因**：`database.py` 的 `get_db()` 使用 `threading.local()` 按线程缓存连接。每次新线程首次调用时都打印一次 INFO 日志。

- 健康检查（AnyIO worker thread）每 30 秒调用一次
- 定时任务（ThreadPoolExecutor-0_0）每分钟调用一次

但同一线程的后续复用不会再打印，仅首次建立连接时打印。

**修改**（`database.py`）：
- 连接日志改为 `logger.debug()` 级别（正常运行时不可见）
- 增加 `_local.logged_connection` 标志，同一线程仅首次连接打印一次（`info` 级别），后续复用不再打印

## 三、涉及文件

| 文件 | 修改内容 |
|:-----|:---------|
| [backend/app/config.py](file:///d:/trea项目/快麦取货通/backend/app/config.py) | 抑制 watchfiles.main 日志级别 + 优化 awatch 过滤日志 |
| [backend/app/database.py](file:///d:/trea项目/快麦取货通/backend/app/database.py) | 连接日志改为 debug + 首次连接才 info |

## 四、成功标准

- 后端运行时**不再出现** `watchfiles.main: 1 change detected` 日志
- 后端运行时**不再出现** `数据库连接已建立: /data/kuaimai.db` 日志（除首次外）
- `load_kuaimai_config()` 在凭证文件真实修改时仍正常触发（功能无退化）
- SQLite 连接行为不变（仅日志级别调整）

## 五、备注

- 本次仅修改 `backend/`，不涉及 `app/` Android 端，因此**不需要更新版本号、不需要构建 APK、不需要同步 docker-deploy、不需要 Git 提交版本标签**
- 修改完成后可直接重启后端服务验证
