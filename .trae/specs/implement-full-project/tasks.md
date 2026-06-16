# Tasks

## Phase 1：项目搭建（基础骨架）

- [ ] Task 1: 创建Android项目骨架
  - [ ] 1.1: 创建Kotlin + Compose项目，配置build.gradle.kts（compileSdk/minSdk/dependencies）
  - [ ] 1.2: 创建项目目录结构（di/data/api/db/repository/scanner/ui/util）
  - [ ] 1.3: 配置Hilt（App.kt @HiltAndroidApp + MainActivity.kt @AndroidEntryPoint）
  - [ ] 1.4: 配置Navigation Compose（AppNavigation.kt + 单Activity架构）
  - [ ] 1.5: 实现Theme/Color/Alignment常量文件

- [ ] Task 2: 配置网络层与DI
  - [ ] 2.1: NetworkModule.kt（OkHttp连接池+ApiKeyInterceptor+RateLimitInterceptor+Retrofit）
  - [ ] 2.2: DatabaseModule.kt（Room Database + DAO提供 + Migration策略）
  - [ ] 2.3: RepositoryModule.kt（Repository绑定：ItemRepository/PickOrderRepository/ImageRepository/AuthRepository）

- [ ] Task 3: 创建FastAPI后端项目
  - [ ] 3.1: 创建backend/目录结构（main.py + requirements.txt + Dockerfile + docker-compose.yml）
  - [ ] 3.2: 实现SQLite连接池（init_db + get_db + PRAGMA + busy_timeout）
  - [ ] 3.3: 实现API Key认证中间件
  - [ ] 3.4: 实现健康检查接口（GET /health）
  - [ ] 3.5: 实现建表SQL（pick_orders/pick_items/pick_areas/product_images/sku_cache/crash_logs + 约束）
  - [ ] 3.6: 创建.env.example（仅API_KEY+SERVER_PORT）+ kuaimai.example.json模板（app_key/app_secret/session/updated_at）
  - [ ] 3.7: 配置Docker多阶段构建+BuildKit缓存+时区+健康检查
  - [ ] 3.8: 编写api.yaml（OpenAPI契约），使用openapi-generator生成前后端代码骨架

- [ ] Task 4: 实现快麦API签名与拦截器
  - [ ] 4.1: SignUtils.kt（HMAC-MD5签名计算）
  - [ ] 4.2: KuaimaiInterceptor.kt（自动签名+参数排序）
  - [ ] 4.3: KuaimaiApiService.kt（Retrofit接口定义：7个API — item.list.query/erp.item.sku.list.get/erp.item.supplier.list.get/supplier.list.query/erp.item.general.addorupdate(备注)/erp.item.general.addorupdate(供应商)/session.refresh）
  - [ ] 4.4: DTO映射层（ItemListResponse/SkuListResponse/SupplierListResponse/ItemUpdateRequest + mapper目录）

- [ ] Task 5: 实现加密存储与设置页
  - [ ] 5.1: EncryptedSharedPreferences工具类（API Key/服务器地址/Session加密存储）
  - [ ] 5.2: DataStore配置（扫码配置非加密存储）
  - [ ] 5.3: SettingsScreen.kt + SettingsViewModel.kt（服务器地址配置+扫码方式选择+首次引导+启动连通性检测）

## Phase 2：扫码模块

- [ ] Task 6: 实现PDA扫码模块
  - [ ] 6.1: PdaDeviceConfig.kt（iData/Urovo/新大陆/通用设备配置表，4种广播Action/Key映射）
  - [ ] 6.2: PdaScannerReceiver.kt（广播接收器+条码提取+Activity生命周期管理：onResume注册/onPause注销）
  - [ ] 6.3: ScannerManager.kt（统一扫码管理+300ms防抖+生命周期管理）
  - [ ] 6.4: ML Kit摄像头扫码降级方案

- [ ] Task 7: 后端条码清洗
  - [ ] 7.1: clean_barcode() + validate_barcode()（去除控制字符/零宽字符/格式验证）
  - [ ] 7.2: add_item接口集成条码清洗逻辑

## Phase 3：取货单功能

- [ ] Task 8: Room数据库实体与DAO
  - [ ] 8.1: PickOrderEntity + PickItemEntity + ProductImageEntity + PendingOperationEntity（含索引+约束+完整字段定义）
  - [ ] 8.2: AppDatabase.kt（@Database + @TypeConverter + Migration策略，禁止fallbackToDestructiveMigration）
  - [ ] 8.3: PickOrderDao.kt + PickItemDao.kt + ProductImageDao.kt + PendingOperationDao.kt（CRUD + 排序查询 + @Transaction批量操作，排序规则与后端一致）

- [ ] Task 9: 后端取货单API
  - [ ] 9.1: POST /api/orders（创建取货单+自动生成单号）
  - [ ] 9.2: GET /api/orders（列表查询+状态筛选+排序）
  - [ ] 9.3: GET /api/orders/{id}（详情查询+supplierName筛选参数）
  - [ ] 9.4: POST /api/orders/{id}/items（扫码添加待办行+后端查快麦API+缓存）
  - [ ] 9.5: PUT /api/orders/{id}/items/{itemId}/complete（完成待办行）
  - [ ] 9.6: PUT /api/orders/{id}/items/{itemId}/restore（恢复待办行）
  - [ ] 9.7: PUT /api/orders/{id}/complete-all（批量SQL全部完成）
  - [ ] 9.8: DELETE /api/orders/{id}（删除取货单+CASCADE）
  - [ ] 9.9: DELETE /api/orders/{id}/items/{itemId}（删除待办行）
  - [ ] 9.10: GET /api/orders/{id}/suppliers（获取取货单内供应商列表，去重）
  - [ ] 9.11: GET/POST/DELETE /api/areas（拣货区管理）
  - [ ] 9.12: 12小时超时自动完成定时任务（每分钟检查）
  - [ ] 9.13: 后端快麦API缓存层（sku_cache + invalidate_sku_cache）
  - [ ] 9.14: 并发冲突处理（幂等完成+409 Conflict+404处理）

- [ ] Task 10: 取货单UI
  - [ ] 10.1: HomeScreen.kt（主页卡片入口）
  - [ ] 10.2: PickListScreen.kt + PickListViewModel.kt（取货列表+新建弹窗+长按删除+"查看已完成"入口+F24排序）
  - [ ] 10.3: PickDetailScreen.kt + PickDetailViewModel.kt（扫码框+待办行列表+供应商筛选+完成/恢复+全部完成+连续扫码模式+F25长按操作菜单）
  - [ ] 10.4: PickOrderCard.kt + PickItemRow.kt（通用组件，F17触摸优化56dp+供应商20sp Bold #DC2626）

## Phase 4：商品详情与图片

- [ ] Task 11: 商品详情页
  - [ ] 11.1: ProductScreen.kt + ProductViewModel.kt（商品信息展示+扫码输入框（快速切换查看其他SKU）+供应商修改+备注修改）
  - [ ] 11.2: 供应商选择弹窗（SupplierSelectDialog）+ 二次确认
  - [ ] 11.3: 备注编辑 + 二次确认
  - [ ] 11.4: 后端供应商修改API（调用快麦erp.item.general.addorupdate + 缓存失效）

- [ ] Task 12: 图片上传功能
  - [ ] 12.1: 后端图片上传API（POST /api/upload + 图片压缩+存储）
  - [ ] 12.2: 后端图片查询API（GET /api/images/{skuOuterId}）
  - [ ] 12.3: 后端图片删除API（DELETE /api/images/{id}）
  - [ ] 12.4: ImageUploadSection.kt（上传+进度条+替换/删除）
  - [ ] 12.5: Coil图片加载（内存缓存+磁盘缓存）

## Phase 5：优化与发布

- [ ] Task 13: 离线操作队列
  - [ ] 13.1: PendingOperationEntity（完整字段：id/operationType/orderId/targetId/payload/createdAt/retryCount，6种操作类型）+ PendingOperationDao
  - [ ] 13.2: OrderSyncWorker.kt（WorkManager + 按取货单分组并行同步 + 同步成功即删 + 失败重试3次+标记冲突）
  - [ ] 13.3: Repository层离线逻辑（写操作→后端API成功→更新本地 / 离线→写入pending_operations+乐观更新UI）

- [ ] Task 14: 用户体验优化
  - [ ] 14.1: 网络状态指示（在线/离线/弱网 + "待同步"标记）
  - [ ] 14.2: 扫码反馈（成功短振动+提示音 / 失败长振动+错误音 / 设置页可开关）
  - [ ] 14.3: 屏幕常亮（取货单详情页/商品详情页）
  - [ ] 14.4: 下拉刷新（取货单详情页并行请求 + 清除缓存强制重新请求）
  - [ ] 14.5: 首次使用引导页（F23：配置服务器地址→选择扫码方式→完成，引导后不再显示）
  - [ ] 14.6: 冷启动优化（F32：Application.onCreate仅初始化Hilt + 首页Room缓存秒加载 + LazyColumn布局优化）

- [ ] Task 15: 后端数据清理与运维
  - [ ] 15.1: 定时清理任务（已完成取货单30天/凌晨3:00 + sku_cache 24小时/每小时 + crash日志30天/凌晨4:00 + 孤立图片7天 + 商品图片文件清理：取货单删除时检查关联SKU引用）
  - [ ] 15.2: 快麦凭证热更新（kuaimai.json位于/data/目录，watchfiles库或定时轮询文件修改时间，无需重启容器）
  - [ ] 15.3: SESSION过期预警（每24小时检查）
  - [ ] 15.4: 后端crash-report接口（POST /api/crash-report + crash_logs表）
  - [ ] 15.5: 后端app-version接口（GET /api/app-version + APK分发 + /data/apk/目录 + REQUEST_INSTALL_PACKAGES权限）
  - [ ] 15.6: ACRA错误上报集成（App端，禁止第三方SaaS如Bugly/Crashlytics）
  - [ ] 15.7: ANR检测（主线程阻塞5秒以上记录到本地日志）

- [ ] Task 16: Token刷新失败处理（F35）
  - [ ] 16.1: OkHttp Authenticator自动检测401+调用session.refresh
  - [ ] 16.2: 刷新失败弹窗（不可关闭）+一键跳转快麦后台重新授权（WebView或外部浏览器）
  - [ ] 16.3: 刷新期间暂停所有写操作（离线队列接管）
  - [ ] 16.4: 新Token获取后自动重试之前失败的请求

- [ ] Task 17: Docker部署与Tailscale
  - [ ] 17.1: 运行sync-to-docker-deploy.ps1验证同步
  - [ ] 17.2: docker-compose up验证后端服务启动
  - [ ] 17.3: Tailscale Docker部署配置
  - [ ] 17.4: App端Tailscale IP配置测试

# Task Dependencies
- Task 1 → Task 2 → Task 4, 5
- Task 3 → Task 7, 9
- Task 8 → Task 10
- Task 6 → Task 10
- Task 9 → Task 10
- Task 10 → Task 11, 12
- Task 11, 12 → Task 13, 14
- Task 13, 14 → Task 15, 16, 17
