# "重复造轮子"全面审查报告

两个搜索代理并行审查后端和 Android 端，共检查 **17 个维度**。

---

## 后端（8 项）

| # | 检查项 | 结论 |
|:-:|:-------|:-----|
| 1 | time_utils.py — 手工 `timezone(timedelta(hours=8))` | 轻微 — 改用 `zoneinfo.ZoneInfo("Asia/Shanghai")` 更语义化，但当前功能正确 ✅ |
| 2 | qr_utils.py — 二维码生成 | 22 行薄包装，核心委托给 `qrcode` 库，合理 ✅ |
| 3 | barcode.py — 条码清理/校验 | 已正确使用 `re` 标准库，合理 ✅ |
| 4 | auth.py — 自定义权限检查 | 已是标准的 FastAPI Depends 链模式，合理 ✅ |
| 5 | cache.py — 自定义缓存 | 业务驱动的 API 故障降级方案，不是通用缓存，合理 ✅ |
| 6 | database.py — 线程本地连接 | SQLite 线程模型的必然结果，合理 ✅ |
| **7** | **config.py — 手动 os.getenv()** | **⚠️ 显著重复造轮子** |
| 8 | Pydantic vs Android DTO | 跨语言 API 契约的必然重复，合理 ✅ |

---

## Android 端（9 项）

| # | 检查项 | 结论 |
|:-:|:-------|:-----|
| 1 | ImageCompressor — 图片压缩 | 上传场景需要，Coil 不能替代，合理 ✅ |
| 2 | TimeUtils — 时间解析 | minSdk=23 < API 26，无法用 java.time，合理 ✅ |
| 3 | SessionExpiredEvent — 事件总线 | 就是 Kotlin Flow 标准用法，合理 ✅ |
| 4 | PickItemRow vs PickOrderCard | 数据模型/布局/交互完全不同，无重复 ✅ |
| 5 | ViewModel 抽取 BaseViewModel | 收益有限，强行统一反而增加复杂度 ✅ |
| 6 | Repository 薄包装 | 每个 Repository 有离线策略/WorkManager 逻辑，合理 ✅ |
| 7 | OrderSyncWorker 简化 | **手动 retryCount 可改用 getRunAttemptCount() + 空 catch 违规** ⚠️ |
| 8 | PdaDeviceConfig — 设备识别 | 已使用 Build.MANUFACTURER，无更好 API 替代 ✅ |
| 9 | UI 模板代码重复 | **TopAppBar(4处) / 删除弹窗(3处) / 错误提示不统一** ⚠️ |

---

## 需要修复的问题

### 🔴 config.py — 手动 os.getenv()（后端唯一显著问题）

**现状**：15+ 行 `os.getenv()` + 手动 `int(str())` 类型转换，无自动校验。

```python
SERVER_PORT: int = int(os.getenv("SERVER_PORT") or "8900")
DB_PATH: str = os.getenv("DB_PATH", "/data/kuaimai.db")
# ... 共 10+ 行相同模式
```

**改动**：迁移到 `pydantic-settings` 的 `BaseSettings`（项目已依赖 Pydantic，只需安装 `pydantic-settings`）。

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    server_port: int = 8900
    db_path: str = "/data/kuaimai.db"
    # ... 自动从 .env/环境变量读取，自动类型转换

    class Config:
        env_file = ".env"

settings = Settings()
```

### 🟡 TopAppBar 公共组件（Android 端）

**现状**：PickDetailScreen/PickListScreen/ProductScreen/SettingsScreen 4 处重复写相同的 BrandBlue + ArrowBack + SurfaceWhite TopAppBar。

**改动**：抽取 `StandardTopAppBar` 公共组件，约减少 40 行模板代码。

### 🟡 删除确认弹窗公共组件

**现状**：PickDetailScreen/PickListScreen/ProductScreen 3 处重复的 AlertDialog 删除确认。

**改动**：抽取 `ConfirmDeleteDialog` 公共组件，约减少 30 行模板代码。

### 🟢 OrderSyncWorker 空 catch

**现状**：62 行 `catch (_: Exception) { }`。
**改动**：改为 `catch (e: Exception) { Log.w(...) }`。

---

## 不修的问题（已论证无重复或收益低）

| 问题 | 不修原因 |
|:-----|:---------|
| time_utils 改用 zoneinfo | 功能正确，修改需同步 6 个文件，收益低 |
| BaseViewModel 抽取 | 两个 ViewModel 数据流模式已不同 |
| Repository 改用 DAO 直连 | 破坏架构，且 Repository 有离线/WorkManager 逻辑 |
| OrderSyncWorker retryCount | 当前逻辑正确，改成内置 API 不减少代码 |

---

## 改动清单

| 优先级 | 文件 | 改动 | 行数变化 |
|:------:|:-----|:-----|:--------:|
| **高** | config.py | 迁移到 pydantic-settings BaseSettings | ~10 |
| 中 | OrderSyncWorker.kt | 空 catch 加日志 | +1 |
| 低 | PickDetailScreen.kt + 3 个 Screen | 抽取 StandardTopAppBar | **-40** |
| 低 | PickDetailScreen.kt + 2 个 Screen | 抽取 ConfirmDeleteDialog | **-30** |

## 版本号

2.33 → 2.34（仅迁移 config.py 的改动需同步到 docker-deploy）
