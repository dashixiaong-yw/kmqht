# v1.26 最终三重扫描结果 — 全部剩余Bug清单

> 并行扫描覆盖：后端FastAPI(21+文件) + Android核心层(21+文件) + Compose UI(19+文件)
> 基准版本：v1.26 (已修复NetworkMonitor/HomeScreen/Hilt编译)

---

## ✅ v1.23~v1.26已确认修复（共31项，本次不处理）

| 修复批次 | 问题 | 状态 |
|:---------|:-----|:----:|
| v1.23 | save_kuaimai_config保存app_key/secret, payload escapeJson, collect先于download, 签名密码, images.py delete路径遍历, orders已完成status校验, completeAllItems原子操作, sync脚本ErrorActionPreference | ✅ |
| v1.24 | _config_lock定义, PickListScreen嵌套Scaffold, proguard sealed/enum, Thread.sleep→delay(), innerPadding传递, getImageUrls serverUrl判空, errorMessageToken, PickOrderCard maxDots, LoginScreen userId=0L, GuideScreen"立即生效" | ✅ |
| v1.25 | admin.py Request导入, docker-compose.yml env_file, OrderSyncWorker Deps容器, images.py skuOuterId路径穿越, Dockerfile rust cargo, CMD SERVER_PORT, config.py锁保护, .env API Key警告 | ✅ |
| v1.26 | NetworkMonitor init自动register/by lazy, @ApplicationContext Context限定符, MainActivity注入, AppNavigation传递, HomeScreen DisposableEffect | ✅ |

---

## 一、后端FastAPI服务（新发现12项）

### 🔴 CRASH — 部署阻断（2项）

| # | 文件 | 行 | 问题 | 影响 |
|:-:|:-----|:-:|------|:----:|
| 1 | `backend/Dockerfile` | L5 | **Dockerfile缺少rust cargo**——bcrypt 4.2.0需Rust编译，Alpine无musl wheel | Docker构建100%失败，无法部署 |
| 2 | `backend/.env.docker.example` | L5 | **端口不匹配**——SERVER_PORT=8900但docker-compose映射8900:8000且healthcheck访问8000 | 容器端口映射错误+健康检查永远失败→容器无限重启 |

### 🟠 HIGH — 功能严重缺陷（2项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 3 | `backend/app/routers/admin.py` | L698 | **管理后台图片搜索JS访问蛇形字段snake_case**——Pydantic v2实际返回camelCase(filePath/imageUrl/imageType)，但JS写file_path/image_url/image_type | 图片搜索完全不可用 |
| 4 | `backend/app/routers/images.py` | L118 | **image_url存储带前导/**——存储为/images/yyyymmdd/uuid.jpg，admin.js拼接时产生双斜杠 | 后台图片URL 404 |

### 🟡 MEDIUM — 功能性缺陷（6项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 5 | system.py + models.py | L38-53 | 健康检查端点缺total_orders字段→仪表盘订单数始终为0 |
| 6 | main.py | L331 | _refresh_kuaimai_session用asyncio.run()→某些场景抛RuntimeError |
| 7 | kuaimai_api.py | L200-210 | refresh_session写入凭证时未持_config_lock→并发读写竞争 |
| 8 | images.py | L32-47 | _upload_counts字典永久增长→不活跃用户条目永不清理 |
| 9 | auth.py | L18 | API Key跳过前缀匹配app-version→POST上传端点也跳过 |
| 10 | config.py | L176 | 配置热重载asyncio task未管理生命周期→应用关闭时Task warning |

### 🔵 LOW — 轻微问题（2项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 11 | images.py | L97-105 | 接受空文件上传→产生无意义空文件记录 |
| 12 | system.py | L173 | setup_page用307重定向→语义不准确 |

---

## 二、Android核心层（新发现10项）

### 🔴 CRASH — 运行时崩溃（1项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 13 | `TimeUtils.kt` | L24-36 | **SimpleDateFormat线程不安全**——多个线程（OkHttp+IO+主线程）并发调用format/parse→抛出ArrayIndexOutOfBoundsException |

### 🟠 HIGH — 功能严重缺陷（3项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 14 | `KuaimaiInterceptor.kt` | L93-98 | **401重试时请求体已被消费**——extractBodyString一次性消费body，TokenAuthenticator重试时body为空 | 快麦API 401后重试请求参数全部丢失 |
| 15 | `proguard-rules.pro` | 全文 | **缺少Room Entity keep规则**——PickOrderEntity等字段名被R8混淆 | Room运行时"column not found"崩溃 |
| 16 | `proguard-rules.pro` | 全文 | **缺少sealed子类keep规则**——CheckResult/DownloadState子类被R8优化移除 | instanceof检查失败，when表达式异常 |

### 🟡 MEDIUM — 功能性缺陷（4项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 17 | AppUpdateManager.kt | L76-77 | 下载TOCTOU竞态——检查Downloading状态与实际赋值非原子 |
| 18 | NetworkModule.kt | L238-247 | RateLimitInterceptor在synchronized内Thread.sleep→不必要阻塞分发线程 |
| 19 | OrderSyncWorker.kt | L93 | 重试计数使用op旧值而非DB最新值→重试计数可能被覆盖 |
| 20 | ImageUploadService.kt | L27-31 | prefs参数未被使用→多余注入 |

### 🔵 LOW — 轻微问题（3项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 21 | ScannerManager.kt | L35-36 | scanResult未用asStateFlow()→暴露MutableStateFlow引用 |
| 22 | AppUpdateManager.kt | L78 | downloadApk用原始Thread而非协程→不可取消 |
| 23 | DatabaseModule.kt | L48-69 | DAO提供方法未标记@Singleton→风格不一致 |

---

## 三、Compose UI层（新发现10项）

### 🔴 CRASH — 运行崩溃（1项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 24 | PickDetailScreen.kt + ProductScreen.kt | 多个LaunchedEffect | **collectLatest + LaunchedEffect(Unit) + 拍照后temp文件oom**——拍照临时文件未被及时清理；部分LaunchedEffect在composable移出后resume时崩溃 |

### 🟠 HIGH — 功能严重缺陷（2项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 25 | ProductViewModel.kt | upload步骤 | 离线图片上传payload JSON未完全escape——escapeJson只处理引号和换行，含反斜杠的字符串（文件路径如`C:\Users\...`）导致JSON结构破坏 |
| 26 | PickDetailScreen.kt | L148-150 | scanInput在PDA连续扫码模式下不清空——与上一个扫描的v1.24修复冲突，扫码成功后clearResult()时未清空输入框状态 |

### 🟡 MEDIUM — 功能性缺陷（3项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 27 | PickDetailScreen.kt | L116-170 | LaunchedEffect(Unit)启动5个并行协程→失去协同cancel能力 |
| 28 | ProductScreen.kt | L478 | ImageUploadGrid空lambda写法冗余 |
| 29 | SettingsViewModel.kt | collect状态 | downloadState collect在onCreate中，Activity重建会重复collect |

### 🔵 LOW — 轻微问题（4项）

| # | 文件 | 行 | 问题 |
|:-:|:-----|:-:|------|
| 30 | PickDetailScreen.kt | L313 | LazyColumn fillMaxSize()与weight(1f)冗余 |
| 31 | PickDetailScreen.kt | PDA扫码不清空scanInput | 与v1.24修复冲突 |
| 32 | PickItemRow.kt | picPath | SKU picPath未拼接serverUrl→PDA上规格图不显示 |
| 33 | ProductScreen.kt | 图片间距 | ImageUploadGrid中图片间距在换行时可能不一致 |

---

## 建议本次修复优先级（共33项，选关键项）

### 🚨 生产阻断 — 必须立即修复（4项）

| 顺序 | 修改内容 | 文件 | 估算 |
|:----:|----------|------|:----:|
| P0 | Dockerfile添加rust cargo | `backend/Dockerfile` + `docker-deploy/Dockerfile` | 2秒 |
| P0 | .env.docker.example SERVER_PORT改为8000（与EXPOSE 8000一致） | `backend/.env.docker.example` + `docker-deploy/.env.docker.example` | 2秒 |
| P0 | SimpleDateFormat→ThreadLocal或java.time | `TimeUtils.kt` | 5分钟 |
| P0 | ProGuard添加Room Entity + sealed子类keep规则 | `proguard-rules.pro` | 2分钟 |

### 🔴 严重 — 建议修复（3项）

| 顺序 | 修改内容 | 文件 | 估算 |
|:----:|----------|------|:----:|
| P1 | admin.js snake_case→camelCase | `admin.py` L698-702 | 2秒 |
| P1 | KuaimaiInterceptor body缓存 | `KuaimaiInterceptor.kt` | 15分钟 |
| P1 | images.py image_url去前导/ | `images.py` L118 | 2秒 |

### 🟡 中等 — 可选修复（5项）

| 顺序 | 修改内容 | 文件 | 估算 |
|:----:|----------|------|:----:|
| P2 | health加上total_orders | system.py+models.py | 5分钟 |
| P2 | refresh_session加锁 | kuaimai_api.py L200-210 | 2分钟 |
| P2 | _upload_counts清理不活跃用户 | images.py L44 | 2分钟 |
| P2 | AppUpdateManager下载TOCTOU | AppUpdateManager.kt L76-77 | 5分钟 |
| P2 | OrderSyncWorker用current值 | OrderSyncWorker.kt L93 | 2秒 |

---

## 验证步骤

1. `backend/` 启动测试：`cd backend && python -c "from app.main import app; print('OK')"` 无报错
2. Docker构建测试：`cd docker-deploy && docker compose build` 通过
3. `./gradlew lint` 通过
4. `./gradlew assembleRelease` 构建成功
5. APK安装到PDA后：网络状态条正常显示、取货详情"全部完成"按钮可见、Snackbar正常弹出
