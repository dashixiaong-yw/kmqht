# v1.27 最终三重扫描结果 — 全部剩余Bug清单

> 并行扫描覆盖：后端FastAPI + Android核心层 + Compose UI
> 基准版本：v1.27 (e05916d — 端口统一/SimpleDateFormat/ProGuard/AIO修复)
> 扫描时间：2026-06-18

---

## ✅ v1.27已确认修复（本次不处理）

| 修复项 | 文件 | 状态 |
|:-------|:-----|:----:|
| TimeUtils.kt ThreadLocal 3个SimpleDateFormat | `TimeUtils.kt` L25-43 | ✅ |
| proguard Room Entity keep + sealed $* 子类 + 无\$错误转义 | `proguard-rules.pro` | ✅ |
| OrderSyncWorker current.retryCount + current==null continue | `OrderSyncWorker.kt` L89-98 | ✅ |
| backend/Dockerfile EXPOSE 8900 + CMD --port 8900 + rust cargo | `backend/Dockerfile` | ✅ |
| docker-deploy/Dockerfile 同上一致 | `docker-deploy/Dockerfile` | ✅ |
| backend/.env.docker.example SERVER_PORT=8900 | `backend/.env.docker.example` | ✅ |
| docker-deploy/.env.docker.example SERVER_PORT=8900 | `docker-deploy/.env.docker.example` | ✅ |
| admin.py JS camelCase (filePath/imageUrl/imageType) | `admin.py` L698 | ✅ |
| images.py image_url无前导/ | `images.py` L118 | ✅ |
| kuaimai_api.py refresh_session with _config_lock | `kuaimai_api.py` L202-211 | ✅ |
| NetworkMonitor init register + @ApplicationContext | `NetworkMonitor.kt` | ✅ |
| HomeScreen DisposableEffect + AppNavigation传递 | `HomeScreen.kt` + `AppNavigation.kt` | ✅ |

---

## 🔴 CRASH — 部署阻断/运行时崩溃（3项）

| # | 位置 | 行 | 问题 |
|:-:|:-----|:--:|:------|
| **1** | backend + docker-deploy `docker-compose.yml` | L9 | **端口映射仍为`"8900:8000"`**——Dockerfile EXPOSE 8900 + CMD默认8900 + env SERVER_PORT=8900，但映射8900:8000导致主机流量打到容器8000口无人监听 | **服务完全不可访问** |
| **2** | `KuaimaiInterceptor.kt` | L94-98 | **401重试时请求体已被消费**——extractBodyString调用body.writeTo(buffer)消耗body，TokenAuthenticator重试新建请求时body已空；重试请求只有公共参数无业务参数 | 快麦API 401后重试签名失败 |
| **3** | `AppUpdateManager.kt` | L52/L77/L126 | **`_isDownloading`从未set(true)**——`AtomicBoolean`变量定义但入口守卫还是用`_downloadState.value`检查，finally中set(false)是死代码。TOCTOU竞争条件仍在 | 快速双击下载按钮启动两个下载线程 |

---

## 🟠 HIGH — 功能严重缺陷（7项）

| # | 位置 | 行 | 问题 |
|:-:|:------|:--:|:------|
| **4** | `NetworkModule.kt` RateLimitInterceptor | L238-247 | **synchronized + Thread.sleep阻塞OkHttp分发线程池**——所有请求串行化；持有锁时sleep阻塞分发线程 |
| **5** | `ScannerManager.kt` scanResult | L36 | **未使用asStateFlow()**——直接暴露MutableStateFlow引用，可被外部绕过封装写入 |
| **6** | `config.py` save_kuaimai_config | L136-144 | **session/refresh_token在_config_lock外读取**——update_kuaimai_credentials完全未加锁 | 凭证并发写入不一致 |
| **7** | `admin.py` admin.js imageUrl | L698 | **filePath为空时回退imageUrl导致双`images/`前缀**——filePath为null时URL变为`/images/images/xxx.jpg` | 图片404 |
| **8** | `PickDetailScreen.kt` | L249 | **Spacer用padding而非width**——`padding(horizontal=4dp)`产生8dp宽度非预期的4dp | 扫码-相机间距翻倍 |
| **9** | `PickItemRow.kt` | L83-84 | **Row固定72dp高度裁剪内部56dp按钮**——padding vertical 10dp后可用空间仅52dp，按钮56dp底部4dp被裁 | PDA大字体模式更严重 |
| **10** | `ProductScreen.kt` uriToFile | L644-656 | **拍照临时文件永不清理**——每次拍照在cacheDir创建`upload_*.jpg`，从未删除 | 长期占用磁盘 |

---

## 🟡 MEDIUM — 功能性缺陷（10项）

| # | 位置 | 行 | 问题 |
|:-:|:------|:--:|:------|
| 11 | `PickDetailScreen.kt` 屏幕常亮 | L116-124 | LaunchedEffect异步addFlags + DisposableEffect onDispose不同步→标志位竞态 |
| 12 | `PickOrderCard.kt` 进度点 | L129-149 | totalCount>20时前20项全绿+"..."看起来100%但实际还有未完成项 |
| 13 | `GuideScreen.kt` 成功消息颜色 | L278 | "配置已保存"用`error`红色→语义错误误导用户 |
| 14 | `PickDetailViewModel.kt` 供应商filter | L116-126 | API失败时_suppliers重置但_currentSupplier未重置→筛选标签全部未选中 |
| 15 | `system.py` + `models.py` health | L38-53 | 健康状况缺total_orders→仪表盘订单数始终为0 |
| 16 | `images.py` _upload_counts | L32-47 | 上传限流字典不活跃用户key永不清理→内存缓慢增长 |
| 17 | `users.py` 登录失败计数器 | L32-36 | 登录失败字典永不清除→可被攻击者放大 |
| 18 | `ScannerManager.kt` SoundPool泄漏 | L76 | 每次register()创建新SoundPool但不释放旧的→Activity异常恢复时泄漏 |
| 19 | `DatabaseModule.kt` DAO无@Singleton | L49-67 | 4个DAO未标@Singleton→Hilt每次注入新建实例 |
| 20 | `ImageUploadService.kt` 无用prefs | L29 | prefs参数未被任何方法使用→冗余注入 |

---

## 🔵 LOW — 轻微问题（5项）

| # | 位置 | 行 | 问题 |
|:-:|:------|:--:|:------|
| 21 | config.py save_kuaimai_config | L141-144 | session/refresh_token空值时if保护→无法通过API清空凭证 |
| 22 | images.py 空文件上传 | L97-102 | 未检查len(content)==0→允许空文件 |
| 23 | PickDetailViewModel + ProductViewModel | URL拼接 | serverUrl末尾/与imageUrl开头的/组合不处理双斜杠 |
| 24 | AppNavigation.kt loginRequired | L104-108 | collect()短时间多次发射可能触发回退栈异常 |
| 25 | system.py setup_page | L173 | 307重定向语义不准确→应302 |

---

## 建议修复优先级

### 🚨 P0 — 必须立即修复（看门狗级）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 1 | docker-compose.yml `"8900:8000"`→`"8900:8900"`（同步后重做） | `backend/docker-compose.yml` + `docker-deploy/docker-compose.yml` | 1分钟 + sync |
| 2 | AppUpdateManager `_isDownloading.compareAndSet(false, true)`替代L77-78 | `AppUpdateManager.kt` L75-78 | 2分钟 |

### ⚠️ P1 — 建议修复

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 3 | config.py session/rf读取移入_config_lock + update加锁 | `config.py` + `system.py` | 5分钟 |
| 4 | admin.js imageUrl后备逻辑移除→仅用img.filePath | `admin.py` L698 | 1分钟 |
| 5 | ProductScreen.kt uriToFile try-finally删除temp | `ProductScreen.kt` | 5分钟 |
| 6 | PickItemRow.kt height 72→80dp | `PickItemRow.kt` L84 | 2秒 |
| 7 | PickDetailScreen.kt Spacer padding→width | `PickDetailScreen.kt` L249 | 2秒 |
| 8 | GuideScreen.kt error→成功色 | `GuideScreen.kt` L278 | 2秒 |

### 📝 P2 — 可选修复

| # | 修改内容 | 文件 |
|:-:|----------|:-----|
| 9 | health加total_orders | `system.py` + `models.py` |
| 10 | _upload_counts删空列表key | `images.py` |
| 11 | 登录失败计数器清理过期条目 | `users.py` |
| 12 | ScannerManager scanResult→asStateFlow | `ScannerManager.kt` |
| 13 | DatabaseModule DAO加@Singleton | `DatabaseModule.kt` |
| 14 | ImageUploadService删prefs | `ImageUploadService.kt` |

---

## 验证步骤

1. `cd backend; git diff --name-only` 确认修改文件列表
2. `.\gradlew :app:compileReleaseKotlin` 编译通过
3. `.\gradlew assembleRelease` 完整构建成功
4. `.\scripts\sync-to-docker-deploy.ps1 -Force` 同步后验证docker-deploy端口
5. `git add -A && git commit -m "v1.28: ..." && git push`
