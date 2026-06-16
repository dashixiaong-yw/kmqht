# 快麦取货通 - 变更日志

## 0.3 (2026-06-16)

### 新增
- PDA扫码模块完善：4种设备配置(iData/Urovo/新大陆/通用)、自动识别、300ms防抖、Activity生命周期管理
- 摄像头扫码降级方案：CameraScanScreen(CameraX + ML Kit BarcodeScanning)
- 取货单列表页(PickListScreen)：未完成列表、新建弹窗(拣货区选择)、查看已完成入口、长按删除
- 取货单详情页(PickDetailScreen)：扫码输入框(2dp BrandBlue边框/8dp圆角/自动聚焦)、供应商筛选Chips(FlowRow)、完成/恢复、全部完成、连续扫码模式、下拉刷新
- PickOrderCard组件：16dp×12dp padding、12dp圆角、单号18sp SemiBold、长按删除
- PickItemRow组件：72dp行高、规格名16sp Medium、供应商20sp Bold #DC2626、完成态alpha 0.55+删除线、触摸热区≥56dp
- PickListViewModel：加载进行中/已完成取货单、新建/删除取货单
- PickDetailViewModel：扫码添加明细(含重复检测)、完成/恢复/批量完成、供应商过滤、连续扫码、下拉刷新
- 后端API DTO：OrderApiService(10个端点)、AreaApiService、OrderDto、AreaDto
- 商品详情页(ProductScreen)：扫码输入、SKU信息卡(72dp图片+18sp规格名+20sp供应商红色)、备注编辑、图片上传2列网格
- 供应商选择对话框(SupplierSelectDialog)：搜索过滤、确认切换
- ProductViewModel：SKU信息加载、扫码切换、备注编辑(确认对话框)、供应商切换(确认对话框)、图片上传
- 图片上传服务增强：进度回调、失败重试(最多3次指数退避)、解析响应URL
- ImageUploadSection组件重写：2列网格、1:1宽高比、虚线边框2dp、圆角12dp、已上传图片替换/删除菜单
- 离线操作队列：OrderSyncWorker(WorkManager)、按orderId分组并行/串行同步、重试3次后标记冲突
- PendingOperationEntity增强：支持9种操作类型
- PendingOperationDao增强：getAllPending、getPendingByOrder、getConflicts
- PickOrderRepository离线逻辑：乐观更新本地+写入离线队列
- 网络状态指示器(NetworkStatusIndicator)：在线绿条/离线红条/弱网黄条
- NetworkMonitor工具：ConnectivityManager监听网络变化
- 扫码反馈增强(ScannerManager)：成功50ms震动/失败200ms震动/重复100ms震动、声音反馈、开关设置
- 首次引导页(GuideScreen)：3步引导(服务器地址/扫码方式/完成)
- ANR检测(App.kt)：主线程阻塞>5秒记录本地文件
- Token刷新认证器(TokenAuthenticator)：401自动刷新session、失败通知UI显示"会话已过期"对话框
- SessionExpiredEvent事件总线：TokenAuthenticator→UI通知
- 导航增强：Product路由支持skuOuterId参数、PickDetailScreen支持onNavigateToProduct
- HomeScreen网络状态指示器集成、冷启动优化(先Room缓存后后台刷新)
- 屏幕常亮：ProductScreen和PickDetailScreen FLAG_KEEP_SCREEN_ON

### 修改
- 后端图片上传：已有图片不再返回409，改为替换旧图片(删除旧文件+旧记录后重新插入)
- 后端删除取货单：增加SKU引用检查，清理不被其他订单引用的SKU图片文件
- ItemRepository增加querySupplierList方法

## 0.2 (2026-06-16)

### 新增
- Android项目骨架：Kotlin + Jetpack Compose + Hilt + Retrofit2 + Room + WorkManager + Coil
- 依赖注入模块：NetworkModule（OkHttp+Retrofit+API Key拦截器+限流）、DatabaseModule（Room+DAO+Migration）、RepositoryModule
- 数据层：API服务（7个快麦API端点）、HMAC-MD5签名拦截器、图片上传服务、4个DTO
- 数据库层：4个Entity（PickOrder/PickItem/ProductImage/PendingOperation）、4个DAO、AppDatabase
- Repository层：ItemRepository、PickOrderRepository、ImageRepository、AuthRepository
- PDA扫码模块：ScannerManager、PdaScannerReceiver、PdaDeviceConfig（支持iData/Urovo/Zebra/Newland）
- UI层：Material3主题（16色设计规范）、对齐常量、导航（5个路由）、主页（3模块入口卡片）
- UI组件：PickOrderCard、PickItemRow、ImageUploadSection
- 工具类：TimeUtils（北京时间UTC+8）、SignUtils（HMAC-MD5签名）
- EncryptedSharedPreferences存储敏感配置

## 0.1 (2026-06-15)

### 新增
- 项目初始化：需求分析、技术选型、UI设计规范
- 可交互HTML原型（4个页面：主页/取货列表/取货单详情/商品详情）
- 专用知识图谱MCP（kuaimai-memory）
- Docker部署同步脚本（scripts/sync-to-docker-deploy.ps1）
- 项目规则文档（.trae/rules/README.md）
