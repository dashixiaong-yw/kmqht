# 快麦取货通 - 变更日志

## 1.8 (2026-06-17)

### 新增
- 后端KuaimaiCredentials增加refresh_token字段存储
- 后端新增save_kuaimai_config()方法，刷新后持久化updated_at到kuaimai.json
- 后端kuaimai_api.py新增refresh_session()函数，调用快麦open.token.refresh接口
- 后端main.py新增每7天自动刷新快麦session定时任务
- 后端system.py新增GET /api/kuaimai/session-status接口查询session状态
- 后端system.py新增POST /api/kuaimai/refresh-session接口手动刷新session
- 前端SettingsScreen新增"快麦连接状态"Card，显示session状态、剩余天数、刷新按钮
- 前端新增SystemApiService和KuaimaiSessionDto

### 修改
- kuaimai.example.json增加refresh_token字段
- KuaimaiCredentials新增has_refresh_token()和get_days_left()方法

## 1.7 (2026-06-17)

### 安全
- CORS来源改为从环境变量CORS_ORIGINS读取，生产环境可限制域名
- 图片查询API添加用户认证，防止未授权访问
- API Key比较改用hmac.compare_digest防止时序攻击
- 默认密码用户首次登录强制修改密码
- 500错误响应脱敏，不再泄露内部异常详情
- 图片上传添加速率限制（每用户每分钟10次）

### 修复
- 修复session_expire_time从未写入导致会话过期预警失效的bug
- 删除sendCrashReport空壳方法
- 会话预警天数改用SESSION_WARNING_DAYS常量，前后端统一

### 优化
- 删除5个未使用的快麦API方法
- 创建PrefsKeys统一常量类，消除6处重复key定义

## 1.6 (2026-06-17)

### 修复
- 修复在线模式completeItem/restoreItem/completeAllItems重复入队离线队列，导致OrderSyncWorker重复执行已完成的操作（P0 BUG-01+06）
- 修复PickDetailScreen未监听PDA硬件扫码结果，F2扫码待办在PDA硬件扫码模式下完全失效（P1 BUG-02）
- 修复deleteItem在线模式不调API仅入队，与completeItem/restoreItem逻辑不一致（P1 BUG-03）
- 修复uriToFile未安全关闭inputStream，copyTo异常时资源泄漏（P2 BUG-04）
- 修复SettingsScreen配置remember在Card内部定义，重组时可能状态不一致（P2 BUG-05）

### 新增
- PickOrderRepository新增updateItemStatusDirect/deleteItemDirect方法（只更新本地不入队）
- PickDetailViewModel在线/离线模式统一策略：API成功→Direct方法，API失败→入队方法

## 1.5 (2026-06-17)

### 修复
- 修复ImageUploadService不传X-User-Token导致图片上传/删除返回401不可用（P0）
- 修复AreaApiService缺少createArea/deleteArea方法，拣货区管理不持久化到后端（P1）
- 修复后端允许用户禁用自己导致永久锁定（P1）
- 修复后端允许管理员剥夺自己的settings权限导致无法管理用户（P1）
- 修复UserEditDialog不支持启用/禁用用户账户（P1）
- 修复UserRepository使用普通SharedPreferences存储token等敏感数据，改用加密存储（P1）
- 修复updateUser()本地缓存更新不完整，未处理isActive变更（P2）

## 1.4 (2026-06-17)

### 新增
- 设置页添加服务器地址/API Key配置UI（F23: OutlinedTextField+保存按钮）
- 设置页添加扫码方式选择（RadioButton: PDA硬件/相机/手动）
- 设置页添加声音/振动反馈开关（Switch）
- 商品详情页图片上传功能（F5: PickVisualMedia图片选择器+uriToFile转换）
- Product路由支持orderId可选参数，精确关联当前订单SKU
- AppConstants添加SESSION_WARNING_DAYS常量

### 修复
- 修复GuideScreen引导页不保存配置到SharedPreferences，引导完成后配置丢失（P0 BUG-01）
- 修复PickDetailScreen下拉刷新只刷新订单信息不刷新明细数据，多PDA场景看不到其他PDA添加的明细（P0 BUG-02）
- 修复ProductScreen图片上传按钮onClick为空lambda，无法上传图片（P0 BUG-03）
- 修复Product路由缺少orderId参数，从取货详情进入商品详情可能查到错误订单的同SKU商品（P0 BUG-04）
- 修复连续扫码模式下PDA硬件扫码不清空输入框不回位光标（P1 BUG-05）
- 修复重复扫码只显示Snackbar不滚动到重复行（P1 BUG-06）
- 修复取货单列表未按拣货区分组排序，不符合F24设计要求（P1 BUG-08）
- 修复OrderSyncWorker所有API错误统一重试，4xx错误应标记冲突不再重试（P1 BUG-09）
- 修复completeItem/restoreItem API失败时本地状态不回滚（P1 BUG-10）
- 修复LoginScreen网络错误提示用字符串匹配，改用类型检查（P2 BUG-12）
- 修复后端_check_order_timeout第二条SQL可能将手动完成订单的未完成明细误标记（P2 BUG-13）
- 修复HomeScreen会话过期预警天数硬编码，提取为AppConstants常量（P2 BUG-14）
- 修复SettingsScreen中StateFlow.value在组合中调用，改用collectAsState()

## 1.3 (2026-06-17)

### 修复
- 修复PickDetailScreen未传入库区图/装箱图URL给PickItemRow，导致待办行小方块只显示文字占位（P1）
- 修复库区图/装箱图点击回调未处理，改为跳转商品详情页（P1）
- 修复下拉刷新动画不显示，改用viewModel.isRefreshing StateFlow替代本地状态（P2）
- 修复images.py缺少sqlite3导入导致图片查询接口崩溃（P0）
- 修复UserRepository.kt引用未定义KEY_USER_ID常量导致编译失败（P0）
- 修复handleAuthError使用GlobalScope反模式，改用应用级CoroutineScope（P0）
- 修复AppNavigation未监听loginRequired事件，token过期后不会自动跳转登录页（P1）
- 修复restoreFromCache未恢复用户id，导致SettingsScreen删除按钮判断失效（P1）
- 修复禁用用户时未清理其token，被禁用用户7天内仍可使用系统（P1）
- 修复登录接口无暴力破解防护，添加5次失败锁定5分钟限流（P1）
- 修复修改自己权限后本地缓存不更新，需重新登录才能生效（P1）
- 删除auth.py中未使用的SKIP_USER_TOKEN_PREFIXES死代码（P2）
- 修复HomeScreen引导提示对所有用户显示，改为仅settings权限用户可见（P2）
- 修复LoginScreen网络异常提示不友好，添加中文友好提示（P2）
- 修复ProductScreen无图片权限时图片区域完全隐藏，改为只读显示（P2）
- 修复users.py中timedelta导入在函数内部，移到文件头部（P2）

## 1.2 (2026-06-17)

### 新增
- 图片压缩上传（F10: 压缩到1024px宽/质量80%约200KB）
- PickDetailScreen下拉刷新（F15: PullToRefreshBox）
- 待办行库区图/装箱图小方块按钮（40×40dp+底部标注）
- 扫码反馈统一使用ScannerManager（F8: 成功/失败/重复）
- 商品详情图片删除/替换功能（F22: 长按删除+点击替换+确认弹窗）
- 首次使用引导页集成到导航（F23: GUIDE路由+启动检查）
- API Key加密存储（F28: EncryptedSharedPreferences）
- 会话过期预警UI（F16: 距过期<5天显示黄色警告条）
- Token刷新失败弹窗处理（F35: AlertDialog+跳转设置）
- 设置页版本号动态读取BuildConfig

### 修复
- 后端超时检查从仅记录日志改为实际UPDATE数据库标记超时订单已完成（F3）
- 取货单卡片进度点尺寸8dp→10dp匹配原型
- 供应商筛选Chip字号12sp→13sp匹配原型
- 待办行规格图标注字号8sp→10sp匹配原型
- ModuleCard背景色改为白色+左侧蓝色图标框匹配原型
- 完成/恢复按钮高度36dp→44dp满足PDA触摸优化（F17）

## 1.1 (2026-06-17)

### 修复
- 修复后端login接口参数类型错误（CreateUserRequest→LoginRequest）
- 修复SettingsScreen缺少userRepository和onLogout参数导致编译失败
- 修复SettingsScreen使用不存在的CompleteBg/CompleteText颜色常量（改为SuccessBg/SuccessText）
- 修复SettingsScreen中@Composable在非Composable上下文调用的问题（改为状态驱动弹窗）
- 修复SettingsScreen中createUser参数类型String?→String不匹配

## 1.0 (2026-06-17)

### 新增
- 设置页面完整实现：拣货区管理、服务器地址、API Key、扫码方式、反馈开关、版本信息
- 首页首次使用引导提示条（黄色背景，可关闭，点击跳转设置）
- 待办行左侧52dp规格图显示（带底部"规格图"标注，点击跳转商品详情）
- 取货单详情页屏幕常亮（离开页面自动取消）
- 取货单详情页长按待办行弹出删除确认弹窗
- 取货单卡片底部进度点指示器（绿色=已完成，灰色=未完成）
- PickItemDao.deleteById()方法
- PickOrderRepository.deleteItemWithQueue()方法（乐观更新+离线队列）

### 修改
- 首页布局改为居中Logo+副标题"扫码取货·高效管理"+水平卡片布局（匹配HTML原型）
- 待办行按状态+时间自动排序（未完成在上，已完成在下，同状态按时间倒序）
- 供应商筛选Chips始终显示（去掉suppliers.size > 2条件）
- 底部进度文字格式改为"进度 X/Y"（匹配HTML原型）
- 扫码框placeholder改为"按PDA扫码键扫描规格编码"（匹配HTML原型）
- 备注保存按钮颜色改为浅蓝底+深蓝字（主操作色，匹配HTML原型）
- Color.kt新增WarningText色彩常量

### 修复
- 图片上传区域添加提示文字"点击上传/替换 · 长按删除 · 上传前自动压缩"

## 0.9 (2026-06-17)

### 新增
- 用户登录功能：用户名+密码登录，token有效期7天
- 权限控制系统：5个功能点独立控制（设置管理、修改供应商、修改备注、库区图管理、箱规图管理）
- 用户管理界面：设置页内可添加/编辑/删除用户、分配权限
- 登录页面：App启动时自动验证token，过期则跳转登录页
- 后端用户管理API：登录/用户CRUD/权限校验/退出登录
- 默认管理员用户：admin/admin123，拥有全部5个权限

### 修复
- 修复PickOrderCard.kt缺少BorderGray导入导致编译失败
- 修复PickDetailViewModel.deleteItem()调用不存在的loadItems()方法

## 0.9 (2026-06-17)

### 修复
- 修复OrderSyncWorker仅处理3种操作类型，6种操作被静默删除导致离线完成/恢复/删除操作永远不会同步到后端（严重）
- 修复KuaimaiInterceptor缺少format/v/sign_method公共参数，与后端_build_common_params不一致
- 修复ProductViewModel.loadImages()使用Flow.collect导致多次调用时Flow收集泄漏，改用collectLatest
- 修复AuthRepository.refreshSession()异常被静默吞掉不记录日志
- 修复NetworkMonitor.unregister()空catch块静默吞掉异常

### 修改
- OrderSyncWorker新增6种操作类型同步方法（complete_item/restore_item/add_item/complete_all/delete_item/delete_order）
- 操作类型命名风格统一为小写下划线（原COMPLETE_ITEM→complete_item等）
- 未知操作类型不再静默删除，改为标记冲突保留记录
- 删除commons-codec依赖（SignUtils使用java.security.MessageDigest，无需commons-codec）
- 删除ItemUpdateRequest.kt和ItemListResponse.kt未使用的SerializedName导入
- 删除cache.py未使用的asyncio导入
- 后端辅助函数添加参数类型注解（_row_to_image_response/_cleanup_sku_images/_cache_row_to_dict）

## 0.8 (2026-06-17)

### 修复
- 修复App.kt硬编码DEFAULT_SERVER_URL，改为引用AppConstants
- 修复App.kt logAnr手动创建SimpleDateFormat，改为使用TimeUtils统一时间格式化
- 修复ImageUploadService手动字符串搜索解析JSON，改为使用JSONObject解析
- 修复kuaimai_api.py _sign函数注释仍写"HMAC-MD5"（与实际MD5签名不一致）

### 修改
- 删除PickOrderRepository中重复的updateItemRemark/updateItemSupplier方法（与WithQueue版本完全相同）
- 删除PickOrderDao中未使用的getByOrderNo()和deleteById()
- 删除PickItemDao中未使用的getItemsByOrder()和delete()方法
- 删除ProductImageDao中未使用的delete()方法
- 删除models.py中未使用的datetime导入

## 0.7 (2026-06-17)

### 修复
- 修复后端delete_item查询缺少status字段导致KeyError（严重）
- 修复前端AreaListResponse与后端areas.py返回格式不匹配导致拣货区列表始终为空（严重）
- 修复KuaimaiInterceptor对所有请求都添加快麦签名导致后端请求可能失败（严重）
- 修复后端main.py重复定义_BEIJING_TZ（第四轮审查漏删）
- 修复AuthRepository硬编码URL与AppConstants不一致（api.kuaimai.com vs openapi.kuaimai.com）
- 修复OrderSyncWorker extractPayloadValue无法处理转义字符（改用JSONObject解析）
- 修复后端init_db()重复PRAGMA设置
- 修复后端kuaimai_api.py注释"HMAC-MD5"与实际MD5签名不一致

### 修改
- escapeJson方法收敛到TimeUtils工具类，消除PickOrderRepository和ProductViewModel重复代码
- ProductViewModel离线队列操作收敛到PickOrderRepository（新增updateRemarkWithQueue/updateSupplierWithQueue）
- 后端areas.py返回包裹格式AreaListResponse，与OrderListResponse保持一致
- 后端models.py新增AreaListResponse模型
- 删除ProductImageDao重复方法getImagesBySku()
- 删除PickOrderDao重复方法getActiveOrders()和未使用的Flow版本getOrderById()
- 删除PickItemDao未使用的Flow版本getItemById()

## 0.6 (2026-06-17)

### 修改
- 创建AppConstants统一常量类，集中管理服务器地址等配置常量
- TimeUtils添加DEFAULT_EXPIRE_MS和COMPLETED_ORDER_RANGE_MS命名常量替代魔法数字
- NetworkModule/ImageUploadService/ProductViewModel引用AppConstants统一常量替代硬编码URL
- 后端KUAIMAI_API_BASE改为环境变量读取
- AreaResponse字段名从snake_case改为camelCase（createdAt）
- PickOrderRepository添加JSON转义防止payload注入
- OrderDetailResponse添加toOrderResponse()转换方法消除重复字段映射
- 删除database.py中未使用的get_db_ctx()函数
- 删除ItemRepositoryImpl中未使用的3个DAO注入
- config.py改用time_utils的beijing_now/parse_beijing替代本地_BEIJING_TZ

## 0.5 (2026-06-17)

### 修复
- 修复OrderSyncWorker离线同步3个方法全部为空实现（备注/供应商/图片同步现在会实际调用API）
- 修复KuaimaiInterceptor签名拦截器完全未实现（现在会解析请求体、添加公共参数、计算MD5签名）
- 修复DatabaseModule注册空MIGRATION_1_2但AppDatabase version=1（删除空迁移防止后续升级数据丢失）
- 修复ProductViewModel使用全局SKU查询而非当前订单（改为精确查询当前订单下的SKU）
- 修复ProductViewModel JSON payload注入风险（备注/供应商含双引号时格式被破坏）
- 修复后端delete_item删除已完成明细后不恢复取货单状态
- 修复后端complete_all_items空取货单可被标记为已完成
- 修复后端create_order单号LIKE查询SQL通配符未转义
- 修复后端images.py上传图片先删旧后存新中间失败会丢失图片（改为先存新后删旧）
- 清理orders.py中未使用的_BEIJING_TZ和timezone导入

## 0.4 (2026-06-17)

### 修复
- 修复后端add_item调用async函数缺少await导致SKU信息为coroutine对象的致命bug
- 修复后端restore_item恢复明细后不将取货单状态从已完成恢复为进行中
- 修复App端重复扫码检查可能漏检（改为精确查询当前订单下的SKU）
- 修复后端单号生成并发冲突无重试机制
- 修复后端图片上传无文件大小限制（添加2MB上限）
- 修复App端ImageUploadService硬编码image/jpeg MediaType（改为根据扩展名动态设置）
- 修复PickOrderEntity/PickItemEntity status注释与后端语义不一致
- 修复后端completion_type CHECK约束与App端定义不一致
- 修复后端单号格式与项目规范不一致（统一为yyyyMMdd-拣货区X）

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
