# 深度审计：全部发现的Bug与性能缺陷

> 三次递进检查完成。共发现 **27项** 问题，按影响面分级。

---

## P0 - 必须修复（3项）

| # | 严重度 | 类型 | 文件 | 行 | 问题 |
|:-:|:------:|:----|:----|:-:|------|
| 1 | P0 | **数据库** | PickOrderEntity.kt | L10 | `pick_order` 表**完全无索引**——全表扫描所有列表查询 |
| 2 | P0 | **构建** | proguard-rules.pro | L8 | `-keep class com.kuaimai.pda.**` 通配规则让R8几乎失效，Gson路径指向不存在的包，隐藏了2个后续问题 |
| 3 | P0 | **线程** | App.kt | L67-93 | ANR检测本身在主线程做文件I/O——ANR时雪上加霜 |

---

## P1 - 建议修复（9项）

| # | 类型 | 文件 | 行 | 问题 |
|:--|:----|:-----|:-:|------|
| 4 | 资源泄漏 | CameraScanScreen.kt | L129 | `addListener` 无对应 `removeListener`——每次进出相机页面泄漏回调 |
| 5 | 资源泄漏 | CameraScanScreen.kt | L122 | `BarcodeScanner` 从未调用 `close()`——Native内存持续增长 |
| 6 | 查询性能 | PickDetailViewModel.kt | L286-313 | `refresh()` 中遍历明细逐个查 Room（N+1模式） |
| 7 | 架构 | ProductViewModel.kt | L68-69 | 绕过Repository直接注入DAO——违规范式 |
| 8 | 网络 | NetworkModule.kt | L144-180 | 两个Retrofit共享同一OkHttpClient——拦截器互相干扰 |
| 9 | 构建 | OrderSyncWorker.kt | L42-51 | 无@HiltWorker——移除通配规则后WorkManager无法实例化 |
| 10 | 启动 | MainActivity.kt | L71-85 | `onCreate` 中自动检查更新并下载APK——竞争启动资源 |
| 11 | 数据库 | PendingOperationEntity.kt | L14-17 | 缺 `operation_type` 和 `retry_count` 索引 |
| 12 | 协程 | UserRepository.kt | L81 | `appScope` 使用 `Dispatchers.Main` 发网络请求 |

---

## P2 - 可选修复（10项）

| # | 类型 | 文件 | 行 | 问题 |
|:--|:----|:-----|:-:|------|
| 13 | 性能 | ImageUploadService.kt | L65 | `Thread.sleep` 阻塞 IO 线程，应改用 `delay()` |
| 14 | 性能 | NetworkModule.kt | L243 | `RateLimitInterceptor` 的 `Thread.sleep` 阻塞 OkHttp 分发线程 |
| 15 | 网络 | NetworkModule.kt | L117-138 | 无重试机制——仓库不稳定的网络下无指数退避重试 |
| 16 | 性能 | PickOrderRepository.kt | L118-184 | 4处入队操作各多一次无谓的 `getById` 二次查询 |
| 17 | 架构 | PickOrderRepository.kt | L153,168 | JSON payload 字符串模板拼接——维护脆弱 |
| 18 | 网络 | AuthRepository.kt | L49-64 | `refreshSession()` 每次都调API——无本地过期检查 |
| 19 | 网络 | KuaimaiInterceptor.kt | L93-98 | `body.writeTo` 不支持 `MultipartBody`——未来图片上传可能静默失败 |
| 20 | 构建 | build.gradle.kts | L41 | 未开启 `isShrinkResources = true` 和 `resConfigs("zh")` |
| 21 | 构建 | gradle.properties | - | 未开启 R8 full mode、并行构建、构建缓存 |
| 22 | Compose | 3个Entity文件 | - | 无`@Immutable`注解——LazyColumn无法跳过重组 |

---

## P3 - 低优先级（5项）

| # | 类型 | 文件 | 行 | 问题 |
|:--|:----|:-----|:-:|------|
| 23 | 启动 | App.kt | L41-56 | ANR检测每秒向主线程发消息——持续微小开销 |
| 24 | 代码质量 | ProductViewModel.kt | L352-372 | uploadImage嵌套try-catch超过3层 |
| 25 | 代码质量 | AppDatabase.kt | L17 | 注释写 `version=1` 实际 `version=2` |
| 26 | 代码质量 | AndroidManifest.xml | L21 | 未设置 `android:largeHeap="true"` |
| 27 | 代码质量 | CameraScanScreen.kt | L192-207 | 高频帧处理产生大量临时Task对象 |

---

## 项目无过渡动画

确认：整个代码库零动画API（无AnimatedVisibility/无animate*AsState/无Transition）。即时切换UI是PDA工具型App的合理设计。**无需修改。**

---

## 建议执行的修复

全部27项里，建议本次执行以下**8项**（覆盖P0全部3项 + 关键P1 5项）：

| 顺序 | 对应的# | 文件 | 修改内容 |
|:----:|:-------:|:-----|----------|
| 1 | #2 | proguard-rules.pro | 移除通配规则、修正Gson路径、删除冗余Hilt规则、添加Worker keep |
| 2 | #9 | OrderSyncWorker.kt | 通配规则移除后，加keep行保护Worker（不改@HiltWorker，避免添加hilt-work依赖） |
| 3 | #1 | PickOrderEntity.kt | 添加`@Index("status")` 和 `@Index("created_at")` |
| 4 | #11 | PendingOperationEntity.kt | 添加 `@Index("operation_type")` 和 `@Index("retry_count")` |
| 5 | #3 | App.kt | ANR日志写入移到 `Dispatchers.IO` |
| 6 | #20 | build.gradle.kts | 添加 `isShrinkResources = true` + `resConfigs("zh")` |
| 7 | #21 | gradle.properties | 添加 R8 full mode、并行构建、构建缓存 |
| 8 | #22 | PickItemEntity.kt + PickOrderEntity.kt + ProductViewModel.kt | 添加 `@Immutable` |

其余P1/P2/P3项目（CameraScan泄漏、N+1、架构违规、Retrofit共享、重试机制等）涉及较大重构或对当前功能无直接影响，建议后续版本按需处理。
