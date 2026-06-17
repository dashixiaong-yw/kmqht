# 最终系统扫描：部署就绪Bug清单

> 三次并行扫描覆盖：后端FastAPI + Android核心层 + Compose UI + 部署配置
> 共发现 **16项** 未修复问题，已剔除v1.23已修复项

---

## CRASH级（5项 — 部署前必须修复）

| # | 层 | 文件 | 行 | 问题 | 影响 |
|:-:|:--|:-----|:-:|------|------|
| 1 | **后端** | `backend/app/services/kuaimai_api.py` | L37 | **`_config_lock`未定义即引用**——v1.23修复遗漏，`_config_lock = threading.Lock()`未写入 | 快麦API每次调用抛`NameError`崩溃 |
| 2 | **App** | `app/.../data/OrderSyncWorker.kt` | L42-51 | **Worker缺少@HiltWorker注解**——6个依赖通过构造函数注入但WorkManager反射实例化失败 | 离线同步Worker每次触发抛`InstantiationException` |
| 3 | **App** | `app/.../App.kt` | L36-40 | **缺少WorkManager初始化**——未注入`HiltWorkerFactory`，即使加注解也不会被识别 | 同上，Worker永远无法启动 |
| 4 | **App** | `app/build.gradle.kts` | 依赖区 | **缺少`hilt-work`依赖**——`androidx.hilt:hilt-work:1.2.0`未添加 | 编译时无错误，运行时hilt无法生成Worker辅助类 |
| 5 | **Compose** | `PickListScreen.kt` | L100+L269 | **嵌套Scaffold**——点击"查看已完成"后两个TopAppBar堆叠 | 双标题栏+内容双重内边距偏移 |

---

## HIGH级（6项 — 强烈建议修复）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 6 | **App** | `proguard-rules.pro` | 全部 | **缺少sealed/enum/Hilt keep规则**——`CheckResult`/`DownloadState`/`ScanFeedbackType`在R8全模式下可能被混淆 |
| 7 | **App** | `ImageUploadService.kt` | L65 | **`Thread.sleep`阻塞IO线程**——应改用`delay()` |
| 8 | **Compose** | `PickDetailScreen.kt` | L210-213 | **PullToRefreshBox innerPadding未正确传递**——内层Column用独立`fillMaxSize()`不继承padding |
| 9 | **Compose** | `PickItemRow.kt` + `ProductScreen.kt` | L96/L336 | **SKU picPath缺serverUrl前缀**——后端返回相对路径直接传AsyncImage，Coil无法加载 |
| 10 | **Compose** | `PickDetailViewModel.kt` | L371-381 | **getImageUrls默认serverUrl为空**——未配置serverUrl时拼接相对URL，图片全不显示 |
| 11 | **Compose** | `PickListScreen.kt` + `PickDetailScreen.kt` | L74/L173 | **LaunchedEffect(errorMessage)相同消息不重复触发**——key用String值，两次相同错误收不到Snackbar |

---

## MEDIUM级（5项 — 建议修复）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 12 | **Compose** | `PickOrderCard.kt` | L128-138 | 进度点`repeat(totalCount)`无上限——100+明细时1400dp水平溢出 |
| 13 | **Compose** | `PickDetailScreen.kt` | L321-327 | LazyColumn items内LaunchedEffect并发Room查询 |
| 14 | **Compose** | `LoginScreen.kt` | L261 | `userId = 0L`时修改密码静默失败——用户卡死在不可关闭的密码修改弹窗 |
| 15 | **Compose** | `GuideScreen.kt` | L276 | "重启App"提示与实际行为矛盾——serverUrl运行时从DataStore读取无需重启 |
| 16 | **Compose** | `ProductViewModel.kt` | L362 | Windows路径反斜杠JSON转义（v1.23修复需验证） |

---

## v1.23已确认修复项（本次不重复处理）

| 问题 | 状态 |
|:-----|:----:|
| save_kuaimai_config 保存app_key/app_secret | ✅ v1.23 |
| APK下载先collect再download | ✅ v1.23 |
| Release签名密码移至keystore.properties | ✅ v1.23 |
| images.py路径遍历防护 | ✅ v1.23 |
| orders.py已完成订单status=1校验 | ✅ v1.23 |
| completeAllItems原子操作 | ✅ v1.23 |
| PickItemRow完成按钮44→56dp | ✅ v1.23 |
| sync脚本ErrorActionPreference=Stop | ✅ v1.23 |

---

## 建议本次修复的16项

| 顺序 | 文件 | 修改内容 |
|:----:|:-----|----------|
| 1 | `backend/app/services/kuaimai_api.py` L17后 | 添加 `_config_lock = threading.Lock()` |
| 2 | `app/.../data/OrderSyncWorker.kt` | 添加`@HiltWorker`注解 + `@AssistedInject`构造函数 |
| 3 | `app/.../App.kt` L36-40 | 注入`HiltWorkerFactory` + `WorkManager.initialize()` |
| 4 | `app/build.gradle.kts` | 添加`implementation("androidx.hilt:hilt-work:1.2.0")` |
| 5 | `PickListScreen.kt` L269 | 移除`CompletedOrdersList`内层Scaffold，改用Column |
| 6 | `proguard-rules.pro` | 补充sealed/enum/Hilt keep规则 |
| 7 | `ImageUploadService.kt` L65 | `Thread.sleep` → `delay()` |
| 8 | `PickDetailScreen.kt` L213 | innerPadding移到内层Column |
| 9 | `PickItemRow.kt` L96 + `ProductScreen.kt` L336 | picPath拼接serverUrl |
| 10 | `PickDetailViewModel.kt` L371-381 | serverUrl兜底使用DEFAULT_SERVER_URL |
| 11 | `PickListScreen.kt` L74 + `PickDetailScreen.kt` L173 | errorMessage LaunchedEffect添加递增计数器key |
| 12 | `PickOrderCard.kt` L128 | 进度点添加上限(20个)+..."提示 |
| 13 | `LoginScreen.kt` L261 | userId=0L时从loginResult兜底 |
| 14 | `GuideScreen.kt` L276 | 修改提示文字为"配置已保存，立即生效" |

---

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. Docker部署测试：`docker-compose up -d --build`
4. 快麦API调用验证（确认`_config_lock`正常）
5. 扫码PDA安装APK，离线同步Worker验证
6. 图片加载验证（picPath/serverUrl）
7. 全部完成/恢复/删除功能回归
