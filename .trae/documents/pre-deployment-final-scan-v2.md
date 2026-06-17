# 最终部署就绪扫描：全部剩余Bug清单

> 三次并行扫描覆盖：后端FastAPI(21文件) + Android核心层(28文件) + Compose UI(19文件)
> 本次扫描确认v1.22~v1.24已修复项，列出**全部剩余未修复Bug**

---

## 🛑 已确认v1.22-v1.24已修复项（本次不处理）

| 问题 | 版本 | 状态 |
|:-----|:----|:----:|
| proguard通配规则移除 | v1.22 | ✅ |
| PickOrderEntity/PendingOperationEntity索引 | v1.22 | ✅ |
| ANR日志移到Dispatchers.IO | v1.22 | ✅ |
| shrinkResources + resConfigs | v1.22 | ✅ |
| R8 full mode + 构建缓存 | v1.22 | ✅ |
| ProductUiState @Immutable | v1.22 | ✅ |
| save_kuaimai_config保存app_key/app_secret | v1.23 | ✅ |
| 离线图片payload escapeJson | v1.23 | ✅ |
| APK下载先collect再download | v1.23 | ✅ |
| 签名密码移至keystore.properties | v1.23 | ✅ |
| images.py路径遍历防护（delete） | v1.23 | ✅ |
| orders.py已完成status=1校验 | v1.23 | ✅ |
| completeAllItems原子操作 | v1.23 | ✅ |
| sync脚本ErrorActionPreference | v1.23 | ✅ |
| **`_config_lock = threading.Lock()`定义** | v1.24 | ✅ |
| PickListScreen嵌套Scaffold移除 | v1.24 | ✅ |
| proguard sealed/enum规则 | v1.24 | ✅ |
| ImageUploadService Thread.sleep→delay() | v1.24 | ✅ |
| PickDetailScreen innerPadding传递 | v1.24 | ✅ |
| getImageUrls serverUrl未配置时不拼接 | v1.24 | ✅ |
| errorMessage计数器key | v1.24 | ✅ |
| PickOrderCard进度点上限20个 | v1.24 | ✅ |
| LoginScreen userId=0L检查 | v1.24 | ✅ |
| GuideScreen重启提示改"立即生效" | v1.24 | ✅ |

---

## 🔴 CRASH — 部署阻塞（4项）

| # | 层 | 文件 | 行 | 问题 | 影响 |
|:-:|:--|:-----|:-:|------|------|
| 1 | **后端** | `backend/app/routers/admin.py` | L10 | **`Request`未导入**——`from fastapi import ...`缺少`Request`，L25函数签名使用后`NameError` | **服务器无法启动，每次启动抛NameError** |
| 2 | **App** | `app/.../data/OrderSyncWorker.kt` | L42-51 | **Worker缺少`@HiltWorker`注解**——7个依赖通过构造函数注入但WorkManager反射实例化失败 | 离线同步Worker触发`InstantiationException`崩溃 |
| 3 | **App** | `app/build.gradle.kts` | 依赖区 | **缺少`hilt-work`依赖**——仅有`work-runtime-ktx`，无`androidx.hilt:hilt-work:1.2.0` | Hilt无法生成Worker辅助类 |
| 4 | **部署** | `docker-deploy/docker-compose.yml` | L11 | **env_file引用`.env`但部署包只有`.env.docker.example`**——需要运维手动重命名** | 首次部署100%失败 |

---

## 🟠 HIGH — 功能性严重缺陷（4项）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 5 | **Compose** | `PickItemRow.kt` L99 + `ProductScreen.kt` L336 | **SKU picPath缺serverUrl前缀**——后端返回相对路径直接传AsyncImage，PDA上图片全不显示 |
| 6 | **后端** | `backend/app/routers/images.py` L91 | **图片上传路径穿越**——`skuOuterId`直接嵌入文件名未过滤`../`，可写入任意目录 |
| 7 | **后端** | `docker-deploy/Dockerfile` | **Alpine + bcrypt Rust编译**——Alpine使用musl libc，bcrypt 4.2.0需预编译wheel或Rust工具链，否则pip install失败 |
| 8 | **后端** | `backend/app/routers/users.py` L32 | **弱默认API Key**——`.env.docker.example`硬编码`zxf199333`，运维不修改即生产使用 |

---

## 🟡 MEDIUM — 功能性/性能缺陷（8项）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 9 | **后端** | `config.py` L104-L120 | **`_config_lock`只保护读端不保护写端**——`load_kuaimai_config()`直接修改字段无锁，读写竞争导致签名错误 |
| 10 | **App** | `app/.../di/NetworkModule.kt` L230-248 | **RateLimitInterceptor Thread.sleep阻塞OkHttp分发线程** |
| 11 | **App** | `app/.../update/AppUpdateManager.kt` L78 | **原始Thread下载APK**——不受协程生命周期管理，无法取消 |
| 12 | **App** | `app/.../util/TimeUtils.kt` L24-36 | **SimpleDateFormat非线程安全**——多协程并发调用返回错误时间 |
| 13 | **后端** | `backend/app/services/kuaimai_api.py` L40 / config.py L114 | **凭证写端无锁保护**——同#9 |
| 14 | **后端** | `backend/app/database.py` L20-45 | **SQLite连接永不关闭**——长连接WAL文件膨胀 |
| 15 | **后端** | `docker-deploy/Dockerfile` | **Dockerfile CMD硬编码--port 8000**——不读取`SERVER_PORT`环境变量 |
| 16 | **后端** | `docker-deploy/.env.docker.example` L2 | **弱默认API Key**——同#8 |

---

## 🔵 LOW — 轻微问题（6项）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 17 | App | `ImageCompressor.kt` L67 | `parentFile`可能为null |
| 18 | App | `ImageUploadService.kt` | deleteImage/fetchImages无重试 |
| 19 | App | `NetworkModule.kt` | KuaimaiInterceptor请求体重建假设非JSON格式静默丢失 |
| 20 | 后端 | `users.py` L32 | `_LOGIN_FAIL_COUNTS`类型注解错误(`dict[str,str]`应为`dict[str,int]`) |
| 21 | 后端 | `images.py` L32 | `_upload_counts`永不清理 |
| 22 | 后端 | `config.py` L170 | `asyncio.get_event_loop()` Python 3.12兼容 |

---

## 建议本次修复（共16项）

| 顺序 | 文件 | 修改内容 | 难度 |
|:----:|:-----|----------|:----:|
| 1 | `backend/app/routers/admin.py` L10 | `from fastapi import ... Request` | 2秒 |
| 2 | `docker-deploy/docker-compose.yml` L11 | `env_file: .env.docker.example` 或同步脚本生成.env | 2秒 |
| 3 | `app/.../data/OrderSyncWorker.kt` L42 | 添加 `@HiltWorker` 注解 | 5分钟 |
| 4 | `app/build.gradle.kts` 依赖区 | 添加 `implementation("androidx.hilt:hilt-work:1.2.0")` | 2秒 |
| 5 | `PickItemRow.kt` L99 + `ProductScreen.kt` L336 | picPath 拼接 serverUrl | 10分钟 |
| 6 | `backend/app/routers/images.py` L91 | skuOuterId路径穿越过滤 | 2分钟 |
| 7 | `docker-deploy/Dockerfile` | 安装Rust工具链或改用slim镜像 | 2分钟 |
| 8 | `backend/app/routers/users.py` L32 | 修复类型注解 | 2秒 |
| 9 | `backend/app/config.py` L114 | `load_kuaimai_config` 加锁保护写端 | 2分钟 |
| 10 | `docker-deploy/.env.docker.example` L2 | 注释说明需修改API Key | 2秒 |
| 11 | `docker-deploy/Dockerfile` | CMD改用`${SERVER_PORT:-8000}` | 2分钟 |

---

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. Python后端启动测试：`cd backend && uvicorn main:app --port 8000` 无报错
4. Docker构建测试：`cd docker-deploy && docker compose build`
5. 扫码安装APK到PDA，功能完整回归
6. 图片上传/查看正常
