# 全面系统扫描：Bug与风险清单

> 四次并行扫描覆盖：Compose UI（22项）+ 后端FastAPI（20项）+ Android App（9项）+ 部署配置（7项）
> 共发现 **58项** 问题，按P0/P1/P2分级

---

## P0 — 阻止部署/数据损失（6项）

| # | 层 | 文件 | 行 | 问题 | 影响 |
|:-:|:--|:-----|:-:|------|------|
| 1 | **后端** | `backend/kuaimai.json` | 全文件 | **真实快麦凭证被Git跟踪**——每次`git push`将appKey/appSecret/session上传到GitHub公开仓库 | 凭证被盗→快麦API被滥用→数据泄露 |
| 2 | **后端** | `backend/app/routers/system.py` | L155 | `save_kuaimai_config()`只保存`api_base`和`is_multi_warehouse`，**不保存`app_key`和`app_secret`**——管理后台修改凭证后重启容器凭证丢失 | 部署后通过管理后台设置的凭证重启即丢失 |
| 3 | **Compose** | `ProductViewModel.kt` | L362 | **JSON手动拼接未完全转义**——`file_path`中的反斜杠`\`未调用`escapeJson()` | 离线图片上传队列payload解析失败，图片永远无法上传 |
| 4 | **Compose** | `SettingsViewModel.kt` | L110-L124 | **APK下载完成状态可能丢失**——`downloadApk(path)`在`downloadState.collect{}`之前调用，已完成状态发出时还没开始收集 | 自动更新功能部分情况下完全失效 |
| 5 | **后端** | `backend/app/database.py` | L30 | **SQLite autocommit模式**——`isolation_level=None`使每条SQL语句自动提交，事务`BEGIN/COMMIT`对autocommit模式无效 | 多个INSERT/UPDATE原子操作无法回滚，部分失败导致数据不一致 |
| 6 | **构建** | `app/build.gradle.kts` | L13-L17 | **Release签名密码硬编码**——`storePassword="kuaimai2024"`和`keyPassword="kuaimai2024"`明文写在构建文件中 | 任何能访问Git仓库的人都能使用签名密钥 |

---

## P1 — 功能性Bug（12项）

| # | 层 | 文件 | 行 | 问题 | 影响 |
|:-:|:--|:-----|:-:|------|------|
| 7 | **后端** | `backend/app/services/kuaimai_api.py` | L40 | 全局`_global_kuaimai_config`对象**无锁保护**——多线程并发调用时可能读到部分更新的配置 | 并发API请求使用混合的旧/新凭证，签名计算错误导致请求失败 |
| 8 | **后端** | `backend/app/routers/images.py` | L50-L70 | 图片上传**无尺寸/类型限制**——超大图片直接加载到内存，无流式处理 | PDA上传高分辨率图片时后端OOM崩溃 |
| 9 | **后端** | `backend/app/routers/images.py` | L35 | 图片查看API路径拼接**未做路径遍历防护**——`file_path`直接拼接到`IMAGE_DIR` | 恶意请求可读取`/etc/passwd`等系统文件 |
| 10 | **Compose** | `PickItemRow.kt` | L269 | **"完成"按钮触摸热区44dp**——违反56dp最小触摸规范 | PDA触屏精度低，用户点击完成时容易误触其他区域 |
| 11 | **Compose** | `PickDetailViewModel.kt` | L232-L236 | **completeAllItems并发安全问题**——`items.value`快照后遍历，遍历期间新扫描的item被遗漏 | 全部完成操作漏掉正在被扫码添加的明细 |
| 12 | **Compose** | `PickDetailViewModel.kt` | L106-L111 | **订单数据非响应式**——`_order`是一次性查询，后台修改状态后TopAppBar显示旧数据 | 订单状态更新后页面标题显示过时信息 |
| 13 | **后端** | `backend/app/routers/orders.py` | 若干 | 取货单API除`complete`外**缺少状态校验**——已完成取货单仍可添加/删除明细 | 已完成的订单被人为篡改，数据与快麦ERP不一致 |
| 14 | **App** | `app/src/main/java/.../data/OrderSyncWorker.kt` | L127-L131 | 4xx错误仅标记冲突不记录详细错误信息——**用户和管理员无法知道具体错误原因** | 离线队列冲突后用户看到"失败"但不知道原因 |
| 15 | **部署** | `scripts/sync-to-docker-deploy.ps1` | 开头 | `$ErrorActionPreference`未设置——PowerShell默认`Continue`，错误**静默继续执行** | 同步脚本中的文件复制错误被静默忽略 |
| 16 | **Compose** | `ProductScreen.kt` | L643-L654 | **临时文件未清理**——`createTempFile()`创建的图片上传缓存文件从未删除 | cacheDir持续增长，长期使用可能占满PDA存储 |
| 17 | **Compose** | `PickDetailViewModel.kt` | L165-L166 | **insertItem异常后loadSuppliers被跳过**——try块最后一行不执行 | 手动添加明细失败后供应商筛选列表不刷新 |
| 18 | **后端** | `backend/app/routers/admin.py` | L44 | 管理后台HTML模板中`SERVER_URL`未通过`escapeHtml()` | 管理后台页面可能因SERVER_URL含HTML标签而渲染异常 |

## P2 — 品质/体验优化（18项）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 19 | Compose | PickDetailScreen.kt | L321-L327 | LazyColumn items内的LaunchedEffect在滚动时大量并发Room查询 |
| 20 | Compose | ImageUploadSection.kt | 全部 | 死代码，未被引用，与ProductScreen中ImageUploadGrid功能重复 |
| 21 | Compose | LoginScreen.kt | L296 | HTTP非401错误无中文翻译，直接显示英文原始消息 |
| 22 | Compose | ProductScreen.kt | L335-L341 | AsyncImage无placeholder/error占位 |
| 23 | Compose | PickOrderCard.kt | L129 | 进度点可能水平溢出（100个明细时1400dp） |
| 24 | Compose | SupplierSelectDialog.kt | L113 | supplierCode作为key有重复风险 |
| 25 | App | UserRepository.kt | L81 | appScope使用Dispatchers.Main发网络请求 |
| 26 | App | ImageUploadService.kt | L65 | Thread.sleep阻塞IO线程 |
| 27 | App | KuaimaiInterceptor.kt | L93-98 | body.writeTo不支持MultipartBody |
| 28 | App | AuthRepository.kt | L49-64 | refreshSession每次都调API，无本地过期判断 |
| 29 | App | PickOrderRepository.kt | L153,168 | JSON payload字符串模板拼接 |
| 30 | 部署 | docker-deploy/docker-compose.yml | L14 | 端口注释写`8900:8900`但实际映射`8900:8000` |
| 31 | 部署 | backend/Dockerfile | 末尾 | `COPY docker-deploy/`和`COPY backend/`两个COPY指令产生两个镜像层 |
| 32 | 部署 | docker-deploy/.env.docker.example | 全文件 | 缺少`KUAIMAI_CONFIG_PATH`/`IMAGE_DIR`/`DB_PATH`等可选配置的示例 |
| 33 | 部署 | docker-deploy/ | 缺失 | 缺少README部署说明文档 |
| 34 | 后端 | backend/app/database.py | L17 | AppDatabase注释写`version=1`实际version=2 |
| 35 | Compose | HomeScreen.kt | L84-L107 | 会话警告非响应式，用户刷新session后警告条不自动消失 |
| 36 | Compose | GuideScreen.kt | L276 | 配置保存后提示用户手动重启App，体验差 |

---

## 确认已修复项（不重复处理）

| 问题 | 已在v1.22修复 | 本次无需再改 |
|:-----|:-------------:|:------------:|
| proguard通配规则 | ✅ | 不重复 |
| PickOrderEntity缺索引 | ✅ | 不重复 |
| PendingOperationEntity缺索引 | ✅ | 不重复 |
| ANR日志主线程I/O | ✅ | 不重复 |
| shrinkResources + resConfigs | ✅ | 不重复 |
| R8 full mode + 构建缓存 | ✅ | 不重复 |

---

## 建议本次修复的P0/P1项（共18项）

| 文件 | 修改内容 |
|:-----|----------|
| `backend/kuaimai.json` | **添加到.gitignore**，从Git历史中删除（含真实凭证） |
| `backend/.gitignore` | 新增`kuaimai.json`条目 |
| `backend/app/routers/system.py` L155 | `save_kuaimai_config()`新增保存`app_key`和`app_secret` |
| `app/.../ProductViewModel.kt` L362 | 对`file_path`也调用`TimeUtils.escapeJson()` |
| `app/.../SettingsViewModel.kt` L110-L124 | 先collect下载状态再调用downloadApk |
| `backend/app/database.py` L30 | 移除`isolation_level=None`，使用默认事务模式 |
| `app/build.gradle.kts` L13-L17 | 签名密码移至`keystore.properties`文件（.gitignore） |
| `backend/app/services/kuaimai_api.py` L40 | `_global_kuaimai_config`添加`threading.Lock`保护 |
| `backend/app/routers/images.py` L50-L70 | 图片上传添加尺寸限制 + 流式处理 |
| `backend/app/routers/images.py` L35 | 图片路径添加遍历防护（`os.path.basename` + allowed目录校验） |
| `app/.../PickItemRow.kt` L269 | "完成"按钮高度44dp→56dp |
| `app/.../PickDetailViewModel.kt` L232 | completeAllItems添加同步锁 |
| `app/.../PickDetailViewModel.kt` L106 | order改为响应式Flow |
| `backend/app/routers/orders.py` | 已完成订单的路由添加status=0校验 |
| `scripts/sync-to-docker-deploy.ps1` | 开头添加`$ErrorActionPreference = "Stop"` |
| `app/.../ProductScreen.kt` L643 | 图片上传完成/失败后删除临时文件 |

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. Docker部署测试：`docker-compose up -d --build`
4. 验证后端健康检查：`curl http://localhost:8900/health`
5. 验证API Key认证：不带Key返回401
6. 验证快麦凭证持久化：通过管理后台保存后重启容器，凭证不丢失
7. 验证Git凭证安全：`kuaimai.json`不在`git status`中显示
8. 验证离线队列：断网后操作→恢复网络→自动同步成功
