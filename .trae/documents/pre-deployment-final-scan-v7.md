# v1.29 最终四路扫描 — 全部剩余Bug与修复计划

> 四路并行扫描：后端FastAPI + Android核心层 + Compose UI + 安全/数据流/运行时
> 基准版本：v1.29 (c55d41b)
> 扫描时间：2026-06-18

---

## ✅ v1.29已验证修复

| 修复项 | 状态 | 说明 |
|:-------|:----:|:------|
| images.py del _upload_counts移除 → KeyError回归修复 | ✅ | 已删除L45-46的del |
| system.py health_check totalOrders查询 + return | ✅ | 已添加 |
| admin.py L477: totalOrders 字段名 | ✅ | total_orders→totalOrders |
| orders.py completed_count WHERE>0 (2处) | ✅ | restore_item + delete_item |
| AppNavigation "前往设置" 导航按钮 | ✅ | dismissButton已添加 |
| ProductScreen ContentScale.Crop | ✅ | 已添加import+参数 |
| PickDetailViewModel suppliers初始值 | ✅ | emptyList→listOf("全部") |
| PickDetailScreen 键盘隐藏 | ✅ | InputMethodManager已添加 |
| HomeScreen padding(0.dp)移除 | ✅ | 已清理 |

---

## 🔴 CRASH — 必须立即修复（3项）

| # | 位置 | 行 | 问题 | 扫描来源 |
|:-:|:-----|:--:|:------|:--------:|
| **1** | `NetworkMonitor.kt` L25-27 + `HomeScreen.kt` L79 | L25-27/L79 | **NetworkCallback双重注册导致Android 12+崩溃**——Singleton的`init { register() }`在构造时注册callback，HomeScreen DisposableEffect又调用`register()`。Android 12+的`registerNetworkCallback`对同一callback实例抛出`IllegalArgumentException: callback already registered` | Android核心 |
| **2** | `main.py` _refresh_kuaimai_session | L331 | **asyncio.run()不支持嵌套调用**——APScheduler线程调用`asyncio.run(_do_refresh())`，若热重载或嵌套调度时抛出`RuntimeError: asyncio.run() cannot be called from a running event loop` | 后端扫描 |
| **3** | `admin.py` deleteArea等JS操作 | L577+ | **部分admin.js操作缺少元素存在性检查**——DOM操作直接`getElementById().value`，若元素不存在返回null导致TypeError | 后端扫描 |

---

## 🟠 HIGH — 功能严重缺陷（3项）

| # | 位置 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **4** | `ScannerManager.kt` + `SettingsViewModel.kt` | L51-52/L71-82 | **扫码声音/振动设置写入Prefs但从未被ScannerManager读取**——Settings写KEY_SCAN_SOUND/KEY_SCAN_VIBRATION，ScannerManager不读这些值，始终用默认true | Android核心 |
| **5** | `admin.py` refreshSession按钮无loading状态 | L555-558 | 用户点击后无视觉反馈，若后端响应慢用户会重复点击 | 后端扫描 |
| **6** | `config.py`热重载watch任务失控重启 | L153-177 | `loop.create_task`未保存引用，异常退出后无法恢复，关闭时无法cancel | 后端扫描 |

---

## 🟡 MEDIUM — 功能性缺陷（8项）

| # | 位置 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| 7 | `OrderSyncWorker.kt` 图片上传 | L212-223 | 离线图片上传成功后不删除本地`pending_images/`文件→长期占用存储 | Android核心 |
| 8 | `ProductViewModel.kt` error字段覆盖 | L369-378 | 离线入队消息通过error字段显示→覆盖真实错误 | Android核心 |
| 9 | `config.py` SAVE_KUAIMAI_CONFIG锁 | L130-148 | 写入`kuaimai.json`文件时未锁住文件→多线程并发写入导致JSON损坏 | 后端扫描 |
| 10 | `admin.py` loadUsers数据不刷新 | L507 | 添加/删除用户后用户列表未自动刷新（需手动切Tab） | 后端扫描 |
| 11 | `admin.py` JS错误清除 | L274 | 操作失败后错误提示未在成功后自动清除 | 后端扫描 |
| 12 | Docker healthcheck memory limit格式 | docker-deploy | `memory: 512M`应大写(MiB)而非(MB) | 后端扫描 |
| 13 | UI多处AsyncImage缺占位图 | 多处 | 网络失败时空白无反馈 | UI扫描 |
| 14 | PickDetailScreen排序缺少id | L183-187 | 相同createdAt时排序不稳定 | Android核心 |

---

## 🔵 LOW — 轻微问题（4项）

| # | 位置 | 行 | 问题 |
|:-:|:-----|:--:|:------|
| 15 | AppDatabase.kt 注释 version=1 实为2 | L16-17 | 注释过时 |
| 16 | KuaimaiInterceptor.kt UTF-8编码假设 | L97 | 硬编码UTF-8 |
| 17 | ProductScreen.kt as? Activity静默失败 | L632 | 非Activity上下文常亮失效无提示 |
| 18 | admin.py server_url未HTML转义 | L215/L259/L326 | 环境变量渲染到HTML |

---

## 修复顺序

### P0 — 2项（3分钟）

| # | 修改内容 | 估算 |
|:-:|----------|:----:|
| 1 | NetworkMonitor: 移除`init { register() }`（Singleton+HomeScreen共用一个DisposableEffect）| 1分钟 |
| 2 | main.py asyncio.run→new_event_loop | 2分钟 |

### P1 — 4项（10分钟）

| # | 修改内容 | 估算 |
|:-:|----------|:----:|
| 3 | ScannerManager读取Prefs配置 | 5分钟 |
| 4 | OrderSyncWorker uploaded imageFile.delete() | 2分钟 |
| 5 | admin.py refreshSession按钮loading + deleteArea判空 | 2分钟 |
| 6 | config.py热重载task加全局引用+shutdown cancel | 5分钟 |

### P2 — 8项（10分钟）

| # | 修改内容 |
|:-:|----------|
| 7 | config.py SAVE_KUAIMAI_CONFIG文件写锁 |
| 8 | admin.py loadUsers自动刷新 |
| 9 | ProductViewModel error→infoMessage分离 |
| 10 | Docker healthcheck memory单位 |
| 11 | PickDetailScreen排序加`thenBy { it.id }` |
| 12 | AppDatabase注释版本号 |
| 13 | admin.js错误清除 |
| 14 | ProductScreen AsyncImage占位图 |

---

## 验证

1. `cd backend && python -c "from app.main import app; print('OK')"`
2. `.\gradlew :app:compileReleaseKotlin`
3. `.\scripts\sync-to-docker-deploy.ps1 -Force && Select-String 8900 docker-deploy/docker-compose.yml`
4. `git add -A && git commit -m "v1.30: ..." && git push`
