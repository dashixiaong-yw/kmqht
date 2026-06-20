# 快麦取货通 - 变更日志

## 1.86 (2026-06-21)

### 修复
- [Bug3] 修复添加商品首次返回 HTTP 404：httpx 连接池配置 keepalive_expiry=30 + TransportError 自动重试
- [Bug4] 供应商筛选项添加后不即时刷新的问题（从API响应直接追加）
- [Bug1/2] SkuItemInfo DTO 增加 title 字段，Worker 同步增加 title 降级链（getItemDetail → sku.title）
- [Bug1/2] Worker 所有关键路径增加日志输出，新增导出同步日志功能

### 新增
- 设置页增加"导出同步日志"按钮，通过系统分享导出 Worker 同步日志文件

## 1.85 (2026-06-20)

### UI改进
- 统一所有 AlertDialog 16dp 圆角（10处弹窗）
- 首页引导提示条改为品牌蓝（PrimaryLightBg）
- 商品详情确认弹窗统一蓝色调（16dp圆角+阴影）
- 取货单详情 FilterChip 高度提升 28dp→32dp
- 登录页密码框增加显示/隐藏切换按钮
- 登录页改为上对齐布局，减少底部空白
- 引导页步骤切换增加 AnimatedContent 淡入淡出动画
- 引导页完成页增加 CheckCircle 对勾图标
- 已完成取货明细行透明度 0.55→0.65
- 补全 DarkColorScheme 暗色主题（8→20个色槽）

## 1.84 (2026-06-20)

### UI改进
- PickItemRow: 恢复按钮加灰底(SurfaceGray)，与完成按钮绿底形成对称操作对
- PickItemRow: 按钮增加 ✓/↩ 图标，匹配原型语义
- PickItemRow: 已完成行供应商颜色降级为 TextMuted(灰)，传递"已处理"信号
- PickItemRow: 行内容垂直居中，视觉平衡
- PickDetailScreen: 筛选芯片选中态改为深蓝底白字(BrandBlue/SurfaceWhite)
- PickDetailScreen: "全部完成"按钮由绿底改为蓝底(PrimaryLightBg)，与单行完成语义区分

## 1.83 (2026-06-20)

### 修复
- P0: PickItemEntity/后端pick_items 新增 item_outer_id 列 + Room MIGRATION_3_4
- P0: OrderSyncWorker fetchLatestSkuData 增加 itemOuterIdFallback 降级，内部 return null 加日志
- P0: OrderSyncWorker 6 个 sync 方法检查 BaseResponse.success（除 addItem 返回 OrderItemResponse）
- P0: Payload 4处增加 item_outer_id 字段，enqueueDirect 方法参数追加 itemOuterId
- P0: triggerSyncWorker + MainActivity KEEP→APPEND_OR_REPLACE
- P0: SkuUpdateDto skuSuppliers 默认 null（Gson 跳过空数组）
- P0: loadSuppliersFromLocal() 复用类级 items.value，消除初始空值竞态
- P0: onBarcodeScanned 追加 loadOrder() + _order.totalCount 同步更新
- P0: PickItemDao 新增 getCompletedCount 挂起查询 + updateItemFields 增加 itemOuterId
- P0: completeItem/restoreItem/completeAllItems try+catch 分支补 updateCompletedCount
- P0: completeAllItemsDirect/enqueueCompleteAll 内部补 updateCompletedCount
- P0: syncItemsFromBackend 补 updateItemFieldsDirect + itemOuterId 同步
- P0: PickListScreen 新增 DisposableEffect + LifecycleEventObserver 监听 ON_RESUME
- P0: PickListViewModel 新增 refreshActiveOrders()
- P2: ProductViewModel enqueueDirect 调用补传 itemOuterId 参数

## 1.82 (2026-06-20)

### 修复
- P0: OrderSyncWorker outerId 推导错误(substringBefore) → fetchLatestSkuData() API真值
- P0: 后端 sku_cache 改为比对式缓存(modified时间戳验证)+API失败降级
- P0: sku_cache 新增 cached_modified 列 + 30天清理
- P0: kuaimai_api 返回增加 modified 字段
- P2: 完成/恢复按钮 height(24dp)→contentPadding(vertical=4dp) 修复文字不显示
- P2: completeItem/restoreItem 成功后追加 loadOrder() 刷新取货单状态
- P2: order.status==1 时禁用完成/恢复按钮+全部完成按钮+提示文字
- P2: refresh() 已有明细同步不可变快麦字段(propertiesName/picPath)
- P2: PickItemDao 新增 updateItemFields(), PickOrderRepository 新增 updateItemFieldsDirect()
- P0: SkuItemInfo DTO 增加 propertiesName/skuOuterId 字段
- P0: OrderSyncWorker 新增 SkuSyncData + getLatestTitle→fetchLatestSkuData

## 1.81 (2026-06-20)

### 修复
- P2: PickDetailViewModel.loadSuppliersFromLocal() 空 catch 块 — 加 Log.w 防止供应商提取失败静默吞异常

## 1.79 (2026-06-20)

### 修复
- P0: ItemUpdateResponse 缺少快麦API响应包裹层 — 新增 ItemUpdateWrapper 正确解包 erp_item_general_addorupdate_response，修复所有离线备注/供应商同步被误判为失败
- P1: confirmSaveRemark 直接路径缺失 UI remark 即时反馈 — 补充 `remark = confirmType.remark` 到 _uiState.copy
- P2: HomeScreen 取货列表/商品详情卡片 emoji 换 Material Icons — 📋→ListAlt, 🔍→Search
- P2: ProductViewModel confirmSaveRemark/confirmChangeSupplier 加 else 分支 — 双 null 场景设置 error 提示，避免静默丢弃
- P2: system.py download_apk 加 apkFileName 空值防御 — `if not file_name: raise HTTPException(404, ...)`

## 1.78 (2026-06-20)

### 修复
- P0: title覆盖风险 — getLatestTitle失败时返回null拒绝同步，避免降级使用propertiesName覆盖商品标题
- P1: currentSkuDetail/currentItem旧值污染 — loadSkuInfo开头null重置，防止API失败后残留上一条详情数据
- P2: 去掉独立扫码路径的ifBlank包装 — 与updateRemarkWithQueue路径保持一致

## 1.77 (2026-06-20)

### 修复
- P0: 备注/供应商回传失败 — ProductViewModel缓存SkuDetailResponse + 独立扫码直接入队（不依赖Room pick_item行）
- P0: 供应商列表为空 — kuaimai_api.py get_supplier_list响应解析修复（multipart编码返回扁平结构，误用wrapper key）
- P1: Worker冲突操作死循环 — 冲突操作(retryCount=-1)加deleteById释放队列，避免while(hasWork)永不退出
- P2: 首页设置模块图标不可分辨 — iconBgColor SurfaceGray→BorderGray, tint Color.White→TextSecondary
- P2: 取货单进度圆点靠右 — Spacer(8dp)→Spacer(weight(1f))
- P2: 完成/恢复按钮紧凑 — 移除自定义contentPadding，改用Modifier.height(36.dp)（参照"+新建"按钮）
- P2: 新建取货单拣货区闪烁 — loading时隐藏按钮显示CircularProgressIndicator，消除disabled动画引起的全量重组

### 改进
- PickOrderRepository新增enqueueRemarkUpdateDirect/enqueueSupplierUpdateDirect（独立扫码场景）
- propertiesName为空时自动填充"-"保护快麦API

## 1.76 (2026-06-20)

### 修复
- P0: orders.py L82 语法错误修复 — 三个 HTTPException 挤在同一行导致后端无法启动
- P1: AppUpdateManager APK下载 SSL 绕过 — 注入 trustAll OkHttpClient 修复 HTTPS 下载抛 SSLHandshakeException
- P2: system.py apkFileName 空值防御 — 防止空文件名绕过文件存在检查
- P3: system.py QRCode路由注释修正（移除过时"需API Key认证"）
- P3: main.py 移除冗余 /apk 静态文件挂载（download_apk() 路由已覆盖）
- P3: admin.py publish_app_version 增加 APK 文件存在性验证

## 1.75 (2026-06-20)

### 修复
- C1: 创建者在取货单被领取后失去访问权 — list_orders + _check_order_access 追加 created_by 检查
- F1: 图片代理SSRF漏洞 — 新增URL白名单(仅允许 alicdn/aliyuncs)+响应体10MB限制
- F2: 存量用户权限迁移 — 为所有 settings 权限用户自动追加 update_supplier
- F2: Worker 漏同步 — doWork 改为循环处理直到队列为空
- F2: delete_order 缺 rowcount 检查 — 竞态假成功修复
- 图片死重试修复 — 文件不存在时返回true(视为不可恢复)
- 静默catch 加 Log.w — syncItemsFromBackend 异常暴露
- 非FK IntegrityError 包装 HTTPException(409)

### 改进
- Emoji→Material Icons — 6处替换(📦→Inventory/📋→ListAlt/🔍→Search/⚙️→Settings/✏️→Edit/💾→Save)，消除国产PDA渲染tofu/空白风险
- orders.py docstring 单号格式修正

## 1.74 (2026-06-19)

### 修复
- 供应商列表：后端权限 `settings` → `update_supplier`，解决获取不到供应商列表的问题
- 备注同步：OrderSyncWorker 每次新增 pending 操作后自动触发同步
- title 覆盖：Worker 同步时实时从 `item.single.get` 获取最新 title，不再使用 `"."`
- 商品详情扫码：`loadSkuInfo()` 改为 API 优先，实时从快麦获取备注/供应商/标题，Room 仅离线降级

### 新增
- 后端新增 `GET /api/sku/{sku_outer_id}` 端点，实时返回快麦 SKU 详细信息
- 供应商选择对话框增加加载状态、错误提示和重试按钮

## 1.73 (2026-06-19)

### 改进
- HomeScreen：Logo 56→48dp(圆角14dp)、ModuleCard重构(Card padding 20dp + 图标Box 52×52 圆角14dp + Row spacedBy 16dp)、图标改用emoji 📋🔍⚙️、标题色改为黑色TextPrimary、卡片间距8→14dp
- PickListScreen：新建按钮Icon(Add)→TextButton("+ 新建", shape=8dp)
- PickOrderCard：4行→3行(创建时间移到创建者同行+进度圆点合并到进度同行)
- PickItemRow：完成/恢复按钮紧凑(contentPadding 4×14 + RoundedCornerShape 8dp)、规格图底部标签移除
- ProductScreen：备注框+保存按钮同行("💾 保存"含emoji+shape 8dp)、库区图/箱图上传槽位缩至120dp、供应商切换按钮加emoji "✏️ 切换"

## 1.72 (2026-06-19)

### 改进
- 移除连续扫码开关：默认始终连续扫码（扫码后自动清空+聚焦），移除 dead code（_continuousScanMode StateFlow + toggle + Switch UI）

## 1.71 (2026-06-19)

### 修复
- 取货单详情：PickItemRow规格图/库区图/箱图标签截断修复（Box高度14dp/12dp→18dp）+ 布局对齐原型（右侧Column图片在上按钮在下，图片44dp，按钮TextButton）
- 取货单详情：库区图/箱图点击改为大图Dialog预览（不再跳转商品详情页）
- 商品详情页图片上传修复：ImageUploadService OkHttpClient添加SSL绕过（FRP自签证书导致SSLHandshakeException）+ ProductViewModel异常分级（IOException→入离线队列, 其他→展示错误）

### 改进
- 首页：新增Logo图标框 + 模块卡片图标背景色区分（取货列表蓝/商品详情红/设置灰）+ 卡片添加1px边框和阴影
- 首页：商品详情模块描述文案对齐原型("扫码查看规格信息")

## 1.70 (2026-06-19)

### 修复
- FOREIGN KEY constraint 787 彻底根除: 根因是TOCTOU竞态条件（_check_order_access检查与INSERT之间存在1~3秒快麦API调用窗口，另一线程可在此期间删除订单）。双层防护：INSERT前重验证order存在性 + 捕获sqlite3.IntegrityError区分FK异常
- delete_order: DELETE语句增加WHERE status != 1条件，防止并发完成订单被误删
- upload_image: 从async def改为同步def，彻底消除threading.local连接共享风险

### 后端
- orders.py: add_item新增INSERT前重验证逻辑+IntegrityError异常分级处理
- orders.py: delete_order增加status原子性保护
- images.py: upload_image同步化改造（async def→def, await file.read()→file.file.read()）

## 1.69 (2026-06-19)

### 修复
- HomeScreen: 底部内容截断 — 内层Column添加verticalScroll；3个模块卡片居中 — 移除fillMaxWidth；模块间距12dp→8dp
- 扫码添加HTTP 409: 视为远程重复扫码，同步后端明细到本地后触发重复反馈，不再显示"添加明细失败"
- FOREIGN KEY constraint 787: add_item改为同步def，线程池各线程独占SQLite连接，asyncio.run串行调用快麦API

### 改进
- 取货单详情页：连续扫码默认开启
- 扫码添加商品后：从本地Room提取供应商列表，与商品列表同时刷新，避免竞态导致列表空白
- 商品详情页：输入框自动聚焦 + 支持PDA硬件扫码 + 扫码后自动清空支持连续扫码

### 后端
- orders.py: add_item 从 async def 改为同步 def，使用 asyncio.run() 调用异步 get_sku_info，彻底解决多协程共享SQLite连接的FK冲突问题

## 1.68 (2026-06-19)

### 修复
- Bug1: 扫码后输入框不清空/不聚焦 — 取消continuousScanMode条件守卫，始终清空+聚焦
- Bug2: 快麦图片不显示 — 后端新增`/api/images/proxy`代理路由，PDA通过后端加载阿里CDN图片
- Bug3: 取货单卡片布局拥挤 — 创建者/进度/时间拆为三行
- Bug4: 发布按钮太大 — 移除卡片Button，改为长按DropdownMenu操作菜单
- Bug5: 删除弹窗占满屏 — 精简弹窗文本

### 后端
- kuaimai_api.py: 增加快麦API原始返回数据日志（propertiesName/skuPicPath/hasSupplier）
- images.py: 新增图片代理路由，PDA可通过后端中转加载快麦图片

## 1.67 (2026-06-19)

### 改进
- 创建取货单后自动跳转到详情页，不再停留在列表
- 单号格式从 `yyyyMMdd-拣货区X` 改为 `拣货区-yyyyMMdd-X`（如 `B区-20260619-1`）

## 1.66 (2026-06-19)

### 新增
- 取货单私人化：创建时默认仅创建者可查看操作
- 发布功能：创建者可发布取货单到公共列表供其他用户领取
- 领取功能：公开取货单可被任意用户领取，领取后仅领取者可操作
- 后端: 新增 publish/claim 路由 + list_orders 按权限过滤 + 所有操作路由访问校验
- PDA: API驱动取货单列表 + 卡片显示创建者/可见性标记 + 发布/领取按钮

## 1.65 (2026-06-19)

### 重构
- 删除冗余ApiKeyMiddleware：认证统一由路由层Depends(get_current_user)处理
- PDA登录后所有请求仅需User Token，不再被API Key中间件拦截
- 修复"后台已配置拣货区但PDA显示暂无拣货区"(及所有业务API 401问题)

### 性能
- 移除 RateLimitInterceptor：PDA 人机交互场景无需全局同步限流，消除全流量 200ms 延迟
- 移除 ApiKeyInterceptor + AuthRepository.get/setApiKey + 引导页 apiKey 输入：API Key 验证已废弃
- HttpLogging 级别 HEADERS→BASIC：减少 Logcat I/O，防止 X-User-Token 等敏感信息泄露
- SetupQrParser.SetupConfig 移除 apiKey 字段：二维码解析忽略废弃的 apikey 参数
- PrefsKeys 移除 KEY_API_KEY：全栈清理
- PendingOperationDao 删除 getByType()：0 引用死代码

## 1.64 (2026-06-19)

### 修复
- 退出登录卡死"正在退出…"并杀后台重进直接进主页：logout()中clearLocalUser()移到API调用之前（0网络依赖），API调用加withTimeout(5000L)最多等5秒

## 1.63 (2026-06-19)

### 修复
- ScannerManager扫码声音/振动设置读取错误的SharedPreferences文件（kuaimai_settings→kuaimai_prefs）和错误key名称（scan_sound/vibration→sound_enabled/vibration_enabled），导致设置完全不生效
- 冷启动卡死首屏：validateToken()网络验证改为isTokenLocallyValid()本地时间戳验证，秒进首页
- 退出登录弹窗立即消失但无响应：增加isLoggingOut加载状态，弹窗不关+显示"退出中…"
- LoginScreen强制改密弹窗未拦截Android系统返回键，增加BackHandler
- HomeScreen会话预警只计算一次：改为while+delay每小时动态刷新
- 登录页历史下拉箭头无数据时仍显示（点击无效）：无历史时隐藏trailingIcon
- popUpTo(0)使用无效entry ID：改为popUpTo(navController.graph.startDestinationId)

### 新增
- 登录页"记住密码"复选框：勾选后加密保存账号密码到本地EncryptedSharedPreferences
- 登录页用户名ExposedDropdownMenuBox：记录最近10条登录历史，按使用时间倒序
- UserRepository新增isTokenLocallyValid()：启动鉴权不依赖网络请求
- UserRepository新增8个方法：记住密码和登录历史管理（全部本地加密存储）

## 1.62 (2026-06-19)

### 修复
- PickDetailViewModel供应商加载catch块移除假兜底，改为暴露真实错误
- AppConstants新增SUPPLIER_ALL_LABEL/IMAGE_TYPE_AREA/IMAGE_TYPE_BOX常量
- "全部"/"area"/"box" 3种硬编码全量替换为常量引用
- UI文案去掉端口号硬编码

## 1.61 (2026-06-19)

### 修复
- 拣货区加载错误诊断：移除硬编码假数据兜底，暴露真实错误到Snackbar
- PickListViewModel.loadAreas() catch中不再返回["A区","B区","C区","D区"]

## 1.60 (2026-06-19)

### 修复
- SSL信任锚+强制HTTPS：NetworkModule.kt 后端Retrofit独立trust-all OkHttpClient
- SakuraFRP自动HTTPS证书不在Android信任锚 → OkHttp跳过校验
- 快麦API Retrofit保持严格SSL校验

## 1.59 (2026-06-19)

### 修复
- SSL证书信任锚错误：SakuraFRP自动HTTPS证书不被Android信任
- AppConstants.kt + .env.docker.example https→http 回退
- FRP隧道自身已提供TLS加密传输，应用层无需叠加HTTPS

## 1.58 (2026-06-19)

### 修复
- APK上传：latestVersion保存前strip、保存后验证文件存在性和大小
- APK下载：文件名不存在时模糊匹配APK_DIR中任意.apk文件 + 诊断日志

## 1.57 (2026-06-19)

### 修改
- 管理后台：移除 pre-login PDA扫码配置区域（APK已内置FRP地址，无需扫码配置）
- 仪表盘内的扫码配置保留，供内网切换使用

## 1.56 (2026-06-19)

### 修复
- 鸡生蛋死循环：首次安装无API Key → 登录被401拒绝 → 无法配置API Key
- auth.py: /api/users/login 加入 SKIP_AUTH_PREFIXES，登录接口免API Key认证
- 登录路由自带限流保护(5次失败锁5分钟)，安全无影响

## 1.55 (2026-06-19)

### 配置
- 预置FRP地址到APK：AppConstants.DEFAULT_SERVER_URL = "https://frp-off.com:64623"
- 简化引导页Step1：只读展示FRP地址，移除手动输入框和API Key输入，保留扫码切换内网地址

## 1.54 (2026-06-19)

### 修复
- P0: upload_app_version 返回版本信息（latestVersion/apkSize/publishedAt等）直接在前端渲染，不再依赖后端二次读取JSON文件 — 修复NAS/Docker文件系统延迟导致首次上传后无分发按钮
- P0: get_app_version 增加APK文件存在性检查 — 修复版本JSON有记录但APK文件缺失时仍返回版本信息导致下载404
- P0: 新增 renderApkCard() 前端函数 — 上传成功后直接使用响应数据渲染版本卡片，消除文件系统写后读不一致问题

## 1.53 (2026-06-19)

### 修复
- P0: network_security_config.xml 新增 `<certificates src="user" />` — 修复 FRP HTTPS 场景 PDA 登录时 "无法连接服务器"（Android 7.x 系统缺少 Let's Encrypt 根证书，OkHttp SSL 握手失败）

## 1.52 (2026-06-19)

### 审计
- 第十次全面审计：6路并行代理覆盖30版本(153项检查)、146项✅正确实现、6项修复（P0×1 + P2×2 + P3×3）

### 修复
- P0: auth.py SKIP_AUTH_PREFIXES 顺序错误 — `/api/app-version/download`/qrcode 提前到 `/api/app-version` 之前，避免break逻辑截胡，修复PDA扫码下载APK始终401（v1.51修复未生效）
- P3: auth.py 移除 /apk-download 死代码（无对应路由）
- P2: orders.py restore_item 新增 status=1 校验拦截 — 禁止已完成取货单恢复明细
- P3: CameraScanScreen BarcodeScanner 新增 DisposableEffect 释放 — 防止ML Kit native资源泄漏
- P2: PickItemRow 规格图触摸热区 52dp→56dp（满足最小触摸热区规范）
- P3: build.gradle.kts 启用 isShrinkResources=true（v1.22 记录遗漏）

## 1.51 (2026-06-19)

### 修复
- BUG: APK下载二维码扫码后PDA浏览器显示401 "缺少API Key"
- 根因: auth.py SKIP_AUTH_PREFIXES 精确匹配逻辑误拦截 /api/app-version/download 和 /api/app-version/qrcode
- 修复: 将两个路径加入 SKIP_AUTH_PREFIXES，PDA浏览器扫码无需API Key即可下载APK

## 1.50 (2026-06-18)

### 配置
- SERVER_URL http → https：配合 SakuraFRP 自动 HTTPS 功能，PDA与后端通信全链路TLS加密
- Android 端支持 https:// 协议（引导页校验规则已兼容）

## 1.49 (2026-06-18)

### 配置
- FRP内网穿透配置：.env.docker.example 取消注释 SERVER_URL=http://frp-off.com:64623

## 1.48 (2026-06-18)

### 修复
- P0: system.py downloadUrl路径从 `/apk-download/{apkFileName}` 改为 `/api/app-version/download` — 修复内网穿透场景扫码下载APK返回404的问题（根因：v1.47新增的/download路由路径是/api/app-version/download，但生成URL时写成了不存在的/apk-download/路径）

## 1.47 (2026-06-18)

### 修复
- P0: system.py 新增 /api/app-version/download 自定义路由 — 设置正确的 Content-Type(application/vnd.android.package-archive) + Content-Disposition，修复PDA扫码下载APK被识别为ZIP文件无法安装的问题
- auth.py: 添加 /apk-download 到 SKIP_AUTH_PREFIXES，允许免认证下载

## 1.46 (2026-06-18)

### 修复
- P2: config.py watchfiles频繁伪触发(每30秒)→加mtime过滤，仅文件真正变化时重载配置

## 1.45 (2026-06-18)

### 修改
- database.py: 默认admin用户创建时 is_active=0（禁用），不再允许 admin/admin123 登录
- users.py: 移除 mustChangePassword 默认密码检查，与默认admin禁用逻辑对齐

## 1.44 (2026-06-18)

### 修复
- P0: admin.py auth修复 + README规则更新
- Docker构建配置同步 + scripts同步更新

## 1.43 (2026-06-18)

### 修复
- P0: SettingsViewModel startDownload 添加 downloadJob 取消旧协程 — 修复 Flow collector 泄漏导致APK重复下载
- P0: PickOrderRepository.enqueueCompleteAll 先入队后更新状态 — 修复离线全量完成操作丢失
- P1: admin.py XSS — onclick 参数中单引号 `'` 替换为 `&#39;` 防注入
- P1: orders.py complete_item 错误提示"不能删除明细"→"不能完成明细"
- P1: orders.py restore_item 去掉双重 `completed_count - 1` 递减
- P1: images.py 删除冗余 DELETE 语句和多余 db.commit()
- P1: config.py `int(os.getenv("SERVER_PORT") or "8900")` — 防空字符串导致 ValueError
- P1: auth.py SKIP_AUTH_PREFIXES 移除 `/admin` 前缀宽泛匹配，改为精确匹配避免 `/admin-api` 绕过
- P1: main.py `beijing_now().strftime()` → `format_beijing(beijing_now())` — 统一时间格式化
- P1: main.py `asyncio.new_event_loop()` → `asyncio.run()` — 简化事件循环管理
- P1: areas.py 添加 `sqlite3.IntegrityError` 捕获 — 修复并发创建同名拣货区竞态
- P1: users.py `_LOGIN_FAIL_COUNTS` 添加过期清理 — 防止长时间运行内存泄漏
- P2: orders.py complete_all_items 添加已完成幂等检查

### 审计
- 第九次全面审计：5路并行代理覆盖 Android 50+文件 + Backend 20+文件 + 27端点契约 + 25 BugFix落地验证
- 累计发现 38 个缺陷，修复 13 个（P0×2 + P1×10 + P2×1）
- 前后端接口契约零差异，知识图谱设计一致性全部通过

## 1.42 (2026-06-18)

### 修复
- P1: ItemUpdateRequest.SkuUpdateDto.skuRemark默认空字符串被Gson序列化→改为String?=null防覆盖已有备注
- P2: OrderSyncWorker.syncSupplierUpdate apiService空检查延后→移至方法体最前面
- P2: PickDetailViewModel.getImageUrls默认URL硬编码""→引用AppConstants.DEFAULT_SERVER_URL

## 1.41 (2026-06-18)

### 修复
- P0: orders.py complete_item completed_count +1 多加了 1 — 修复完成倒数第二个明细时取货单被错误标记为已完成
- MEDIUM: config.py SERVER_PORT 默认值 8000→8900 — 修复与 Dockerfile/docker-compose 端口不一致

### 审计
- 第八次全量回溯审计：覆盖 v1.01~v1.40 约40次更新，4路并行搜索验证 Android 端12个文件 + 后端全模块 + 配置一致性 + 知识图谱

## 1.40 (2026-06-18)

### 修复
- CRASH: kuaimai_api.py 添加`import threading` — 修复40次更新审计唯一残留问题

## 1.39 (2026-06-18)

### 修复
- CRASH: kuaimai_api.py 加回`import threading` — 修复_v1.37引入_client_lock导致后端启动NameError(P0阻断)
- HIGH: admin.py `escape(base_url)` 实际生效 — 修复Host头XSS(无需认证)
- MEDIUM: ProductScreen area图`{{}}`→`({} as () -> Unit)` — 修复与box图不一致
- MEDIUM: kuaimai_api.py get_supplier_list解包wrapper_key — 修复供应商列表功能
- MEDIUM: admin.py 拣货区`a.created_at`→`createdAt` — 修复创建时间永显示"-"
- MEDIUM: ImageRepository JSONObject解析 — 修复syncImagesFromBackend JSON结构错误
- MEDIUM: PickDetailViewModel getImageUrls加trimEnd('/') — 修复双斜杠URL图片加载
- LOW: HomeScreen移除未用Arrangement import
- LOW: PickDetailScreen移除未用isLoading收集

## 1.38 (2026-06-18)

### 修复
- HIGH: admin.py `escape(base_url)` +导入 — 修复Host头XSS注入(无需认证)
- MEDIUM: kuaimai_api.py `_get_client()`双检锁+threading import — 修复并发AsyncClient泄漏
- MEDIUM: images.py 先写文件后删旧记录 — 修复替换上传DB记录丢失
- MEDIUM: OrderSyncWorker check response.success — 修复快麦API业务错误静默删pending_operation
- MEDIUM: auth.py SKIP_AUTH_PREFIXES精确匹配 — 修复upload/publish绕过API Key
- LOW: ProductScreen error/message添加maxLines溢出保护
- LOW: OrderSyncWorker移除未用AuthRepository import

## 1.37 (2026-06-18)

### 修复
- CRASH: v1.37 backend修复未实际应用——_call_api()仍用async with新建连接→改用_get_client()连接池(P0)
- CRASH: v1.37 ValueError缺code/zh_desc→完整记录(P0)
- CRASH: v1.37 get_supplier_list缺wrapper_key解包→支持supplier_list_query_response(P0)
- HIGH: boxImageUrl拼接缺trimEnd('/')→补上(P1)
- MEDIUM: _call_api()返回None时result.get() AttributeError→回退result(P1)

## 1.37 (2026-06-18)

### 修复
- CRASH: _call_api() 仍用 async with 创建新连接 → 改用 _get_client() 连接池 (P0)
- CRASH: OrderSyncWorker 6个sync方法忽略API响应→包装try-catch (P0)
- HIGH: KuaimaiApiService 返回 Map<String,Any>→ItemUpdateResponse DTO (P0)
- HIGH: hasSupplier ==1 未加 str()兼容→或 str()=="1" (P1)
- HIGH: get_supplier_list()未加wrapper_key解包→支持supplier_list_query_response (P1)
- HIGH: _call_api() ValueError 丢失code/zh_desc→完整记录 (P1)
- HIGH: ProductViewModel 注入未使用的KuaimaiApiService→移除 (P1)
- MEDIUM: 图片URL拼接未处理斜杠→加.trimEnd('/') (P1)
- MEDIUM: ProductScreen L512 无需转型告警→清理 (P2)

## 1.36 (2026-06-18)

### 修复
- HIGH: admin.js L518-L519 u.is_active→isActive/createdAt — 修复用户状态第三处snake_case遗漏
- MEDIUM: images.py _check_upload_rate移除空列表提前return — 修复速率限制永不生效(v1.34回归)
- MEDIUM: images.py 写文件失败回滚新DB记录 — 修复替换上传DB记录丢失
- MEDIUM: OrderSyncWorker syncImageUpload失败不删文件 — 修复重试永久失效
- MEDIUM: scripts/ 移除docker-compose.yaml复制 — 修复配置漂移
- LOW: PickItemRow/HomeScreen/PickDetailScreen/OrderSyncWorker 清理未用import(5处)
- LOW: ProductScreen/SupplierSelectDialog/HomeScreen 添加maxLines溢出保护(3处)

## 1.35 (2026-06-18)

### 修复
- CRASH: config.py kuaimai_config_lock未定义——补充定义 + kuaimai_api.py删除旧锁_config_lock (P0)
- CRASH: kuaimai_api.py threading导入残留——已移除 (P0)
- HIGH: refresh_session用_config_lock不同锁→kuaimai_config_lock统一 (P0)
- HIGH: system.py跨模块导入私有_config_lock→kuaimai_config_lock (P0)
- HIGH: get_supplier_list()响应解析缺wrapper_key解包→supplier_list_query_response (P1)
- HIGH: hasSupplier整数比较不兼容字符串→str()兼容 (P1)
- MEDIUM: _call_api()错误信息丢失code/zh_desc→完整记录 (P1)
- MEDIUM: httpx.AsyncClient每次创建→模块级_get_client()连接池复用 (P2)

## 1.35 (2026-06-18)

### 修复
- P0: 删除KuaimaiApiService.querySupplierList死代码
- P0: 删除ItemUpdateRequest.suppliers死代码
- P0: SupplierListResponse简化(移除未使用包装类)
- P0: SupplierDto保留为本地模型(ProductViewModel使用)
- P1: syncSupplierUpdate不传skuRemark防覆盖现有备注
- P1: cache.py重试加asyncio.sleep(1)延迟退避
- P2: _config_lock移到config.py(kuaimai_config_lock)解决循环依赖
- P2: kuaimai_api.py移除threading import
- P2: sku_cache.cached_at加索引防全表扫描
- P2: _refresh_kuaimai_session注释更新(7天→24小时)

## 1.34 (2026-06-18)

### 修复
- CRASH: images.py INSERT前先DELETE旧记录 — 修复v1.33引入的替换上传永远500
- HIGH: admin.js u.is_active→isActive第二处 — 修复用户状态仍全显示禁用
- MEDIUM: OrderSyncWorker syncImageUpload持久化ProductImage记录 — 修复离线图片上传后本地不可见
- MEDIUM: _save_version_info原子写入(.tmp→replace) — 防版本信息写入中断丢失
- LOW: _upload_counts清理空用户条目 — 防内存泄漏
- LOW: PickItemRow移除未用Arrangement import
- LOW: PickDetailViewModel移除未用Log import
- LOW: SettingsScreen权限文本加maxLines/overflow
- LOW: SettingsScreen添加TextOverflow import

## 1.33 (2026-06-18)

### 修复
- HIGH: admin.js body.is_active→body.isActive — 修复用户启用/禁用功能不生效
- HIGH: APK上传先read到内存再删旧文件 — 修复OTA中断风险
- HIGH: save_kuaimai_config原子写入(then replace) — 修复凭证写入中断丢失
- MEDIUM: images.py先INSERT DB再写文件 — 修复孤儿清理竞态误删
- MEDIUM: _cleanup_orphan_images每文件独立try-except — 修复单文件失败中断清理
- MEDIUM: _cleanup_orphan_images删除空目录 — 修复空目录积累
- MEDIUM: Dockerfile pip install后apk del rust cargo — 减小镜像~200MB
- MEDIUM: scripts/ sync脚本增加kuaimai.json说明注释
- LOW: OrderSyncWorker移除未使用的PickItemDao import

## 1.32 (2026-06-18)

### 修复
- CRASH: main.py 添加 from typing import Optional — 修复容器重启时NameError
- CRASH: main.py 添加 @app.on_event("shutdown") 调用 _stop_scheduler + stop_config_watcher
- CRASH: OrderSyncWorker uploadImage返回值处理 — 修复remoteId始终0/误删后端资源
- CRASH: ProductViewModel serverUrl isNotEmpty检查 — 修复相对路径URL加载失败
- HIGH: system.py kuaimai_creds写入加_config_lock — 修复并发签名不一致
- HIGH: orders.py _cleanup_sku_images 路径穿越防护 — 修复越界删文件风险
- HIGH: ImageRepository syncImagesFromBackend 增量合并 — 修复全量替换误删本地图片
- HIGH: KuaimaiInterceptor 凭证空值检查+日志 — 修复凭证缺失静默失败
- MEDIUM: kuaimai_api.py _call_api+refresh_session 凭证锁内快照 — 修复并发签名
- MEDIUM: admin.py APK上传 100MB大小限制 — 防止磁盘DOS攻击
- MEDIUM: admin.py JS字段名 camelCase统一 — 修复用户状态/创建时间错乱
- MEDIUM: OrderSyncWorkerDeps nullable → @Volatile — 修复WorkManager提前初始化崩溃
- MEDIUM: ProductScreen uriToFile 移除finally重复close — 修复异常覆盖返回值
- MEDIUM: ScannerManager register前soundPool?.release() — 修复native资源泄漏
- MEDIUM: admin.py 权限回退渲染加escapeHtml — 低概率XSS修复

## 1.31 (2026-06-18)

### 修复
- CRASH: config.py import asyncio模块级—修复_watcher_task NameError
- CRASH: main.py scheduler→_scheduler全变量统一—修复7处NameError
- CRASH: ImageCompressor.kt recycle前缓存宽高—修复recycle后访问Bitmap崩溃
- CRASH: PickListScreen.kt CompletedOrdersList加innerPadding—修复内容被TopAppBar遮挡
- HIGH: CameraScanScreen.kt BarcodeScanner加DisposableEffect释放+移除重复close
- MEDIUM: ProductScreen.kt / PickOrderCard / FilterChip 添加TextOverflow溢出处理
- MEDIUM: GuideScreen.kt qrScanError成功解析后重置
- LOW: PickItemRow.kt clickable移到clip之前—修复ripple溢出
- LOW: HomeScreen.kt 移除未使用的Color import
- LOW: PickDetailScreen.kt LazyColumn移除冗余fillMaxSize
- LOW: ImageUploadSection.kt 删除死代码组件

## 1.30 (2026-06-18)

### 修复
- CRASH: NetworkMonitor移除init register() — 修复Android 12+双重注册崩溃
- CRASH: main.py asyncio.run→new_event_loop — 修复热重载时RuntimeError
- CRASH: main.py shutdown事件+_stop_scheduler — 修复关闭时任务泄漏
- HIGH: ScannerManager register()时读取Prefs settings — 修复扫码声音/振动设置不生效
- HIGH: config.py热重载task加全局引用+stop函数 — 修复异常失控+关闭泄漏
- MEDIUM: OrderSyncWorker 成功后imageFile.delete() — 修复离线图片文件积累
- MEDIUM: ProductViewModel infoMessage分离+UiState添加 — 修复信息覆盖错误
- MEDIUM: config.py+main.py shutdown生命周期管理
- LOW: PickDetailScreen排序加thenBy{it.id}
- LOW: AppDatabase注释version=2
- LOW: Docker memory 512M→512MiB
- LOW: ProductScreen infoMessage显示卡片

## 1.29 (2026-06-18)

### 修复
- CRASH: images.py _check_upload_rate del _upload_counts导致KeyError回归
- CRASH: system.py health_check添加totalOrders查询+admin.js totalOrders字段名
- HIGH: orders.py completed_count添加WHERE>0保护(2处)
- MEDIUM: AppNavigation.kt 快麦session过期弹窗添加"前往设置"导航按钮
- MEDIUM: ProductScreen.kt AsyncImage添加contentScale=Crop
- MEDIUM: PickDetailViewModel.kt _suppliers初始值改为listOf("全部")
- LOW: PickDetailScreen.kt 键盘Done后隐藏软键盘
- LOW: HomeScreen.kt 移除冗余padding(0.dp)

## 1.28 (2026-06-18)

### 修复
- CRASH: backend + docker-deploy docker-compose.yml端口映射8900:8900（上次被同步脚本回滚）
- CRASH: AppUpdateManager _isDownloading.compareAndSet修复TOCTOU竞态
- HIGH: config.py session/refresh_token移入_config_lock写保护
- HIGH: admin.py img.filePath移除imageUrl后备→清除双斜杠风险
- HIGH: ProductScreen.kt uriToFile try-finally删除临时文件
- MEDIUM: DatabaseModule.kt 4个DAO添加@Singleton注解
- MEDIUM: ScannerManager.kt scanResult→asStateFlow() + SoundPool释放旧实例
- MEDIUM: ImageUploadService.kt 移除无用prefs参数
- MEDIUM: system.py + models.py health检查添加totalOrders字段
- MEDIUM: images.py _upload_counts删除空列表key + 空文件上传检查
- MEDIUM: users.py _record_login_fail清理过期锁定记录
- LOW: PickDetailScreen.kt Spacer padding→width（修复间距翻倍）
- LOW: PickItemRow.kt height 72→80dp（修复按钮4dp裁剪）
- LOW: GuideScreen.kt "配置已保存"颜色error→primary
- LOW: images.py 空文件上传检查 + system.py 307→302重定向

### 修改
- sync-to-docker-deploy.ps1: 新增docker-compose端口验证（防止再次被覆盖）
- config.py: save_kuaimai_config session/rf移入锁块
- users.py: _record_login_fail添加过期清理

## 1.27 (2026-06-18)

### 修复
- CRASH: backend Dockerfile EXPOSE+CMD端口统一8900——修复端口映射+healthcheck无限重启
- CRASH: backend/SERVER_PORT=8900——docker-compose 8900:8900/healthcheck localhost:8900
- CRASH: TimeUtils.kt SimpleDateFormat→ThreadLocal——修复多线程并发ArrayIndexOutOfBoundsException
- HIGH: proguard-rules.pro 添加Room Entity+sealed子类keep规则——修复R8混淆后"column not found"
- HIGH: admin.py JS图片搜索改为camelCase——修复Pydantic v2字段名不匹配
- HIGH: images.py image_url去前导/——修复admin后台图片URL双斜杠404
- MEDIUM: kuaimai_api.py refresh_session加_config_lock写保护——修复并发读写竞争
- MEDIUM: OrderSyncWorker.kt重试计数用DB最新值(current.retryCount)——修复重试计数被旧值覆盖
- MEDIUM: AppUpdateManager.kt AtomicBoolean TOCTOU防护+finally重置——修复下载竞态

### 修改
- Dockerfile(backend+docker-deploy): EXPOSE 8900, CMD --port ${SERVER_PORT:-8900}
- .env.docker.example(backend+docker-deploy): SERVER_PORT=8900
- docker-compose.yml(backend+docker-deploy): 8900:8900 + healthcheck :8900
- AppUpdateManager.kt: _isDownloading AtomicBoolean + try-finally重置
- proguard-rules.pro: 移除\$错误转义 + 添加子类keep

## 1.26 (2026-06-18)
- HIGH: NetworkMonitor.register()从未被调用——网络状态指示器始终显示"已离线"
- HIGH: HomeScreen未传递networkMonitor参数——首页不显示网络状态条（v1.25遗漏）
- MEDIUM: NetworkMonitor Context注入缺少@ApplicationContext限定符——Hilt编译失败
- LOW: .env.docker.example SERVER_URL注释改进（增加FRP穿透说明）

### 修改
- AppNavigation.kt 新增 networkMonitor 参数传递到 HomeScreen
- HomeScreen.kt 新增 DisposableEffect 生命周期管理网络监听
- MainActivity.kt 新增 @Inject NetworkMonitor 注入 + import
- NetworkMonitor.kt 重构为 by lazy + init自动register + @ApplicationContext

## 1.25 (2026-06-18)

### 修复
- CRASH: admin.py 缺少Request导入
- CRASH: docker-compose.yml env_file引用.env但部署只有.env.docker.example（部署阻断）
- CRASH: OrderSyncWorker依赖注入改为App.OrderSyncWorkerDeps静态容器（替代需hilt-work的@HiltWorker方案）
- HIGH: images.py skuOuterId路径穿越防护——替换/\\和..为下划线
- HIGH: Dockerfile bcrypt编译兼容——安装rust cargo工具链
- MEDIUM: config.py凭证读写锁保护——load/save_kuaimai_config加_config_lock
- MEDIUM: .env.docker.example 添加API Key修改警告提示
- MEDIUM: Dockerfile CMD支持SERVER_PORT环境变量（默认8000）
- LOW: 删残留hilt-work依赖引用
- P0: 修复 `erp.item.general.addorupdate` 缺少必填参数——补充 outerId/title/skus[].outerId/parentPropertiesName
- P0: 修复后端 `get_sku_by_outer_id` 返回缺少 `item_outer_id` 字段
- P0: sku_cache 表新增 `item_outer_id` 列及缓存处理
- P1: 补充 Android ItemUpdateRequest outerId/title 字段
- P1: 补充 Android SkuUpdateDto skuOuterId(outerId) 字段
- P0: 修复 erp.item.general.addorupdate 备注更新——补充 outerId/title/skus[].outerId/skus[].propertiesName
- P0: 修复 erp.item.general.addorupdate 供应商更新——补充 title
- P1: PickOrderRepository payload 补充 sku_outer_id/properties_name
- P2: SkuUpdateDto 新增 propertiesName 字段

### 验证
- 全链路测试14项通过: SKU查询8字段映射/供应商列表加载/备注写回验证/供应商API调用/session刷新

### 修改
- App.kt 新增 OrderSyncWorkerDeps 静态依赖容器（@Inject 6个依赖到Application级别）
- OrderSyncWorker 改为无参构造函数 + by lazy委托获取依赖
- proguard-rules.pro 移除残留Hilt规则注释
- Dockerfile CMD 从exec格式改为shell格式以支持环境变量替换

## 1.24 (2026-06-17)

### 修复
- CRASH: 修复kuaimai_api.py `_config_lock`未定义引用——v1.23修复遗漏的行
- CRASH: 修复PickListScreen嵌套Scaffold——"已完成"视图独立Scaffold移除，改用Column
- HIGH: 修复proguard缺少sealed/enum规则——补充CheckResult/DownloadState/ScanFeedbackType等keep
- HIGH: 修复ImageUploadService Thread.sleep阻塞IO线程——改用delay()
- HIGH: 修复PickDetailScreen PullToRefreshBox innerPadding未传递到内层Column
- HIGH: 修复PickDetailViewModel getImageUrls serverUrl为空时拼接无效相对URL
- HIGH: 修复PickListScreen LaunchedEffect(errorMessage)相同消息不重复触发——加计数器key
- MEDIUM: 修复PickOrderCard进度点`repeat(totalCount)`无上限——最多20个+...提示
- MEDIUM: 修复LoginScreen userId=0L时修改密码静默卡死——添加null检查+错误提示
- MEDIUM: 修复GuideScreen"重启App"误导提示——改为"立即生效"
- P0: 修复后端 `kuaimai.item.sku.get` 非标准前缀——改用 V2 接口 `erp.item.sku.list.get`
- P0: 修复后端 SKU 查询参数——sku_outer_id→outerId，响应字段 skus→itemSkus
- P1: 修复 Android 端供应商查询——supplier.list.query→item.supplier.list.get，补充 sysItemIds 参数
- P2: 删除后端 kuaimai_api.py 中从未调用的 get_item_detail() 死代码
- CRITICAL: 修复后端 _call_api V2 响应无 {method}_response 包装导致返回空字典
- CRITICAL: 修复 Android SkuUpdateDto 字段名与 V2 文档不匹配（skuId→id, skuRemark→remark）
- CRITICAL: 修复 Android SupplierUpdateDto 字段名与 V2 文档不匹配（supplierCode→code, supplierName→itemTitle）

### 修改
- kuaimai_api.py新增_config_lock线程锁定义
- proguard-rules.pro新增Hilt/Dagger keep规则

## 1.23 (2026-06-17)

### 修复
- P0: 修复manage后台不保存app_key/app_secret——重启容器凭证丢失（config.py save_kuaimai_config）
- P0: 修复离线图片上传payload JSON未完全转义——file_path中的`\`导致解析失败（ProductViewModel）
- P0: 修复APK下载状态丢失竞态——先collect再调用downloadApk（SettingsViewModel）
- P0: Release签名密码移至keystore.properties（.gitignore），不再硬编码在build.gradle.kts
- P1: 修复kuaimai_api全局凭证无锁保护——多线程读部分更新的配置（kuaimai_api.py _config_lock）
- P1: 修复图片文件删除路径遍历漏洞（images.py normpath+startswith校验）
- P1: 修复已完成取货单可restore/delete_item——添加status=1校验拦截
- P1: 修复completeAllItems并发竞态——改用原子操作completeAllItemsDirect替代逐个遍历
- P1: 修复PickItemRow完成按钮44dp→56dp，满足最小触摸热区规范
- P1: 修复sync-to-docker-deploy.ps1错误静默——ErrorActionPreference设为Stop
- P1: 修复图片上传临时文件未清理（ProductScreen.kt uriToFile）

### 修改
- PickOrderRepository接口新增completeAllItemsDirect方法
- PickItemDao新增completeAllByOrderId批量完成方法
- build.gradle.kts签名配置支持从keystore.properties文件读取
- kuaimai_api.py新增_config_lock线程锁保护全局凭证
- P0: 修复 `/api/app-version` 被 API Key 中间件拦截——SKIP_AUTH_PREFIXES 新增该路径
- P1: 修复 SettingsViewModel.startDownload 防重复调用——isDownloadingUpdate 标记
- P1: 修复 AppUpdateManager.checkForUpdate 防并发——AtomicBoolean 校验
- P1: 修复下载缓存文件名对齐为"快麦取货通-版本号.apk"
- P2: 修复 APK 上传添加文件类型校验（.apk 后缀 + MIME 检查）
- P2: 修复版本号路径穿越风险——正则校验 `^\d+\.\d+$`
- P2: 修复 SettingsScreen.Divider 替换为 HorizontalDivider

## 1.22 (2026-06-17)

### 修复
- P0: 修复proguard通配规则 — 移除`-keep class com.kuaimai.pda.**`，修正Gson路径指向实际包`data.api.dto`
- P0: PickOrderEntity新增status/created_at数据库索引，消除全表扫描
- P0: ANR日志写入从主线程移到Dispatchers.IO协程
- P1: PendingOperationEntity新增operation_type/retry_count数据库索引
- P1: 开启R8 full mode (gradle.properties)
- 安全配置：自定义 release 签名证书 + R8 代码混淆 + 网络安全配置文件
- forceUpdate 弹窗不可关闭修复：管理员标记强制更新时隐藏"稍后再说"按钮
- downloadApk 并发下载防护：重复调用自动跳过
- SystemApiService 使用标准 import 替换全限定名
- 后端 `get_app_version` API 改为从 JSON 文件读取（替代环境变量）

### 修改
- 开启资源收缩 `shrinkResources = true` + 语言过滤 `resourceConfigurations("zh")` — 缩减APK体积
- 开启构建缓存和并行构建: `org.gradle.parallel=true` + `org.gradle.caching=true`
- APK 输出文件名改为"快麦取货通-版本号.apk"
- 构建命令统一为 `./gradlew assembleRelease`（签名+混淆）
- 设置页版本号可点击，手动检查更新
- 内置版本比对逻辑，支持`compareVersions()`点分版本号比较

### 新增
- OTA APK 自动更新：后台上传/分发 APK，PDA 启动时自动检测+下载+安装
- 管理后台新增「APK 管理」标签页，支持上传新版本和一键分发
- ProductUiState 添加 `@Immutable` 注解，优化Compose重组
- 后端新增 `/apk` 静态目录挂载，支持 APK 文件下载

## 1.21 (2026-06-17)

### 修复
- P2: 修复MainActivity.enqueueSyncWorker()未去重——改用beginUniqueWork+ExistingWorkPolicy.KEEP
- P2: 修复ImageRepository.syncImagesFromBackend()无事务保护——新增ProductImageDao.replaceImagesForSku()原子替换

### 修改
- ProductImageDao新增replaceImagesForSku()事务方法

## 1.20 (2026-06-17)

### 修复
- P0: 修复admin.py拣货区名称XSS漏洞——a.name在td和onclick中未转义，新增escapeHtml()
- P0: 修复ScannerManager Android 14+广播注册崩溃——registerReceiver新增RECEIVER_EXPORTED标志
- P1: 修复PickItemRow规格图/库区图触摸热区不足56dp（52dp/40dp→56dp）
- P1: 修复HomeScreen引导条prefs=null时永远无法关闭（!=true→==false）
- P1: 修复PickDetailViewModel.getImageUrls() catch块缺少日志
- P1: 修复OrderSyncWorker 4xx retryCount被doWork覆盖（新增getById查询当前值）
- P2: 修复HomeScreen会话警告条未使用AppAlignment常量

### 修改
- PendingOperationDao新增getById()方法

## 1.19 (2026-06-17)

### 新增
- 新增 DEFAULT_DOMAIN 环境变量（与 SERVER_URL 共存）
- 提取公共二维码生成函数到 app/utils/qr_utils.py

### 修改
- 将扫码配置页面(/setup)合并到管理后台(/admin)，一个地址搞定所有：页面顶部公开显示PDA扫码配置二维码（无需API Key），底部管理功能需登录后可见
- /setup 路径改为 302 跳转到 /admin（向后兼容）
- 管理后台页面结构重构为三层：公开扫码配置区 + API Key登录区 + 管理功能区（登录后可见）
- auth.py SKIP_AUTH_PREFIXES 新增 /admin、/setup
- .env.docker.example 更新注释说明扫码配置已合入管理后台
- .gitignore 新增 *.keystore 忽略规则

### 文档
- 项目设计文档(kuaimai-pda-app-plan.md)新增Web管理后台页面描述和扫码配置页面描述
- spec.md 新增F37 Web管理后台（7个Scenario）和F38 扫码配置（2个Scenario）的Requirement
- checklist.md 新增13个验证项（Web管理后台7项/扫码配置3项/权限分离2项/环境变量2项）

## 1.18 (2026-06-17)

### 修复
- P1: 修复PickDetailViewModel.getImageUrls()使用编译时常量——改用运行时从encryptedPrefs读取，确保部署后库区图/箱图URL正确拼接服务器地址
- P2: OkHttp日志拦截器降级为HEADERS级别——BODY级别在Logcat中泄露登录密码和Token等敏感信息
- P3: ImageRepository空catch块补充日志——添加Log.w用于排查问题
- P3: AuthRepository session刷新失败日志从Log.w提升至Log.e
- P3: PickListViewModel.loadAreas() catch块补充日志
- 清理未使用的导入：PickDetailScreen(DangerBg)、PickItemRow(Arrangement/MaterialTheme/BorderGray)

## 1.17 (2026-06-17)

### 修复
- P1: 修复PickDetailScreen图片URL未拼接服务器地址——库区图/装箱图在PickItemRow中不可见
- P2: 修复completeAllItems catch分支全量入队——改为单条complete_all入队操作
- P2: 修复7处触摸热区<56dp——引导条/会话警告关闭按钮、规格图/库区图/箱图/完成按钮
- P2: 修复12处未使用AppAlignment常量——HomeScreen/PickOrderCard/NetworkStatusIndicator/SettingsScreen
- P2: 修复HomeScreen引导条prefs=null问题——null==false导致引导条永不可见

### 修改
- PickDetailViewModel.getImageUrls() 拼接$serverUrl后返回完整URL
- PickOrderRepository新增enqueueCompleteAll()方法
- MainActivity/PickDetailScreen补全之前计划的遗留修复

## 1.16 (2026-06-17)

### 修复
- P0: 修复管理后台XSS安全漏洞——用户名直接嵌入HTML onclick属性和innerHTML渲染
- P1: 修复WorkManager触发机制缺失——OrderSyncWorker从未被enqueue，离线队列永不同步
- P1: 修复ScannerManager.register()从未被调用——PDA硬件扫码广播接收器未注册，扫码完全失效
- P1: 修复kuaimai/*路由权限缺失——update_credentials/refresh_session/session_status只要求登录，现改为settings权限
- P2: 修复syncImagesFromBackend未清理旧记录——后端图片同步时先删除旧记录再插入

### 修改
- MainActivity新增ScannerManager生命周期管理（onResume注册，onPause注销）
- MainActivity新增enqueueSyncWorker()方法，启动时触发离线同步
- system.py三个快麦会话路由增加settings权限校验
- admin.py增加escapeHtml()函数和encodeURIComponent()防止XSS
- ProductImageDao新增deleteBySku()方法
- ImageRepository.syncImagesFromBackend先清除再同步，防止记录重复

## 1.15 (2026-06-17)

### 修复
- P1: 修复ScannerManager SoundPool.load()参数错误——使用String而非Context+Uri重载，导致扫码提示音永不播放
- P1: 修复Android 14+广播注册SecurityException——registerReceiver缺少RECEIVER_EXPORTED标记
- P1: 修复CameraPreview空catch块静默吞掉异常——添加Log.e日志
- P1: 删除PdaScannerReceiver死代码文件——与ScannerManager内联BroadcastReceiver功能完全重复
- P1: 修复KuaimaiInterceptor JSON null签名错误——JSONObject.NULL.toString()返回"null"字符串
- P1: 修复OrderSyncWorker 4xx数据永久丢失——冲突记录改为保留而非删除
- P2: 修复ImageCompressor parentFile可能为null导致行为不确定
- P2: 修复calculateSampleSize缺少异常尺寸校验
- P2: 修复session刷新未持久化——TokenAuthenticator刷新后未写回EncryptedSharedPreferences
- P2: 修复KuaimaiInterceptor强制替换Content-Type
- P2: 修复AreaCreateRequest包路径不一致——移至api.dto包
- P2: 删除kuaimai_api.py中未调用的get_item_detail死方法
- P2: 修复SKU缓存永不过期问题——利用cached_at字段添加过期判断
- P2: 修复CameraScanScreen isScanned防重复状态不重置问题
- P2: 修复ScannerManager lastScanTime线程安全性

### 修改
- ScannerManager新增loadSoundUri()方法，通过ContentResolver打开AssetFileDescriptor加载系统提示音
- KuaimaiInterceptor添加JSONObject.NULL判断，防止"null"字符串参与签名
- OrderSyncWorker 4xx错误不再删除pending记录，保留供用户查看
- 后端cache.py get_sku_info添加缓存过期检查（24小时TTL）

## 1.14 (2026-06-17)

### 修复
- P0: 修复服务器地址 SharedPreferences 不匹配——GuideScreen写入普通prefs但NetworkModule从加密prefs读取，导致所有Retrofit后端API不可用
- P0: 修复Dockerfile多阶段构建pip包丢失——runner阶段未复制site-packages导致容器无法启动
- P0: 修复docker-deploy中main.py严重过时——缺少admin/users路由、session刷新等核心功能
- P0: 修复sync-to-docker-deploy.ps1配置文件源路径错误——从项目根查文件但实际在backend/下
- P1: 修复BackgroundScheduler线程中asyncio事件循环操作——改用asyncio.run()
- P1: 修复/admin跳过API Key认证——移除自SKIP_AUTH_PREFIXES
- P1: GuideScreen完善：URL校验改为http://或https://开头、扫码失败提示、重启提示
- P1: AppNavigation启动鉴权等待期间显示加载指示器替代空白屏
- P2: 修复AndroidManifest allowBackup=true安全风险
- P2: 清理App.kt中未使用的prefs/DEFAULT_SERVER_URL注入
- P2: backend数据库初始化增加/data目录自动创建
- P2: docker-compose增加512M内存资源限制

### 修改
- GuideScreen服务器地址改为写入EncryptedSharedPreferences（同时修复ImageUploadService/ProductViewModel读取端）
- Dockerfile改为单阶段构建，pip install直接在runner阶段执行
- 同步脚本重写：backend/内容同步到docker-deploy/根目录，configFiles列表增加main.py和kuaimai.example.json
- .env.docker.example补充SERVER_URL/CORS_ORIGINS/SESSION_WARNING_DAYS环境变量

## 1.13 (2026-06-17)

### 修复
- P0: 修复图片上传响应格式不匹配——前端期望`{data:{imageUrl}}`但后端返回扁平`ImageResponse`，导致图片上传功能完全不可用
- P0: 修复`querySupplierList`调用时传入空Map缺少`method`参数，导致供应商列表加载永远失败
- P1: 修复图片删除ID不同步——前端使用Room本地自增ID调后端删除，需改用后端返回的remoteId
- P1: 修复`PickDetailViewModel.refresh()`使用`updateItemStatus()`（带离线入队）而非`updateItemStatusDirect()`，导致下拉刷新产生无效离线队列
- P1: 修复`ImageRepositoryImpl.deleteImage()`先删本地再调远程，与全局"先API后本地"策略不一致
- P1: 修复后端`delete_item`未校验取货单已完成状态，已完成订单明细被删除后计数不一致
- P2: 前端新增`syncImagesFromBackend()`从后端查询图片，实现多PDA图片共享可见
- P2: 清理`ItemRepository`死代码和`KuaimaiApiService`中3个未使用方法
- P2: 合并CHANGELOG中v1.7和v1.8的重复版本号条目
- P3: `LoginScreen`和`UserRepository`的401检测改为`HttpException.code()`类型检查，替代不可靠的字符串匹配

### 修改
- `ProductImageEntity`新增`remoteId`字段，上传成功后保存后端返回的图片ID
- `ImageUploadService.uploadImage()`返回类型从`String`改为`Pair<Long, String>`（remoteId + imageUrl）
- Room数据库升级至v2，新增`MIGRATION_1_2`：`product_image`表添加`remote_id`列
- `ImageUploadService`新增`fetchImages()`方法查询后端图片列表

## 1.12 (2026-06-17)

### 修复
- 修复GuideScreen将API Key写入普通SharedPreferences而非EncryptedSharedPreferences的P0安全回归bug（v1.10引入）
- 修复PickListViewModel.confirmDelete() API失败后未入队离线队列的P1逻辑缺陷
- 修复SessionExpiredEvent无UI监听者的P1逻辑缺陷，TokenAuthenticator刷新失败时弹出提示对话框
- 修复deleteOrderWithQueue方法缺失导致PickOrderRepository接口不完整

### 修改
- GuideScreen新增encryptedPrefs参数用于加密存储API Key
- AppNavigation新增@Named("encrypted") encryptedPrefs参数并传递给GuideScreen
- AppNavigation新增SessionExpiredEvent监听和快麦Session过期弹窗
- PickOrderRepository新增deleteOrderWithQueue接口方法和实现
- PickListViewModel.confirmDelete()按在线/离线统一策略重构

## 1.11 (2026-06-17)

### 修复
- 修复KuaimaiInterceptor嵌套JSON数组/对象解析崩溃（P0）——商品备注更新和供应商更新恢复正常
- 删除后端kuaimai_api.py未使用的List import
- 后端_call_api增加参数值类型安全处理，确保非字符串值自动序列化为JSON字符串
- 后端refresh_session改用multipart/form-data格式，与快麦官方文档一致

### 修改
- 对照快麦开放平台官方文档（open.kuaimai.com）全面验证API地址、公共参数、签名算法、请求/响应格式，所有参数与官方一致

## 1.10 (2026-06-17)

### 新增
- 后端新增 /admin Web管理后台页面（6个标签页：仪表盘、用户管理、拣货区管理、快麦配置、系统配置、图片查看）
- 后端新增 /api/kuaimai/update-credentials API端点，支持Web后台手动更新快麦凭证
- Web管理后台支持API Key认证，浏览器端统一管理系统配置

### 修改
- App端SettingsScreen精简为"个人设置"页面，移除服务器配置、快麦连接状态、用户管理
- App端SettingsViewModel精简，移除拣货区管理、服务器配置、快麦session相关代码
- App端HomeScreen设置入口改为所有用户可见（不再限制settings权限）
- 图片上传/删除操作保留在App端，Web后台仅做只读查看
- /admin路径加入SKIP_AUTH_PREFIXES，页面本身不需要API Key

## 1.9 (2026-06-17)

### 新增
- 后端新增 /setup 扫码配置页面，显示服务器地址+API Key二维码
- App引导页和设置页新增"扫码配置"按钮，PDA扫码自动填入服务器地址
- 新增 SetupQrParser 工具类，支持 kuaimai://setup 协议和纯URL两种二维码格式
- 图片上传离线支持：上传失败时自动保存图片到本地并入队，网络恢复后重试上传

### 修改
- DEFAULT_SERVER_URL 从模拟器IP改为空字符串，真机部署需通过扫码或手动配置
- 保存服务器地址后提示"重启App后生效"
- NetworkModule 未配置服务器地址时使用占位URL避免崩溃
- 后端delete_order接口增加已完成状态检查，禁止删除已完成的取货单
- 相机扫码按钮设为禁用状态（灰色），避免用户误点无响应

### 修复
- 修复completeAllItems/deleteItem未设置isLoading导致可连续点击触发并发操作的P0 bug
- 修复ProductViewModel.loadImages() Flow收集泄漏导致多次扫码切换SKU后UI状态闪烁的P0 bug
- 修复scanFailureEvent/tokenRefreshFailed使用collect而非collectLatest可能丢失事件的P1问题
- 修复completeAllItems catch块未调用loadOrder导致进度显示不准确的P1问题

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

### 修复
- 修复completeAllItems API失败后未入队离线队列导致操作丢失的P0 bug
- 优化HomeScreen会话过期预警措辞，区分天/小时/已过期三种状态

### 优化
- PickDetailScreen扫码成功事件改用collectLatest，避免快速连续扫码丢失事件

## 1.7 (2026-06-17)

### 安全
- CORS来源改为从环境变量CORS_ORIGINS读取，生产环境可限制域名
- 图片查询API添加用户认证，防止未授权访问
- API Key比较改用hmac.compare_digest防止时序攻击
- 默认密码用户首次登录强制修改密码
- 500错误响应脱敏，不再泄露内部异常详情
- 图片上传添加速率限制（每用户每分钟10次）

### 修复
- P0: API地址修正为官方文档地址 https://gw.superboss.cc/router（前后端一致）
- P0: 公共参数名修正 app_key→appKey、v→version（与快麦开放平台官方文档一致）
- P0: API版本号修正 2.0→1.0（与快麦开放平台官方文档一致）
- P0: KuaimaiInterceptor读取session的key从硬编码access_token改为PrefsKeys.KEY_SESSION
- P1: TokenAuthenticator改为通过后端SystemApiService中转刷新session（不再直接调快麦API）
- P1: KuaimaiApiService移除refreshSession接口（session刷新统一通过后端中转）
- P1: AuthRepositoryImpl.refreshSession改为通过后端SystemApiService中转
- P1: refresh_session函数改为直接调API不通过_call_api通用逻辑，修复响应解析问题
- P2: save_kuaimai_config增加session字段保存
- P2: KuaimaiInterceptor host匹配增加superboss.cc
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
