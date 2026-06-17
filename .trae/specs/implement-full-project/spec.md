# 快麦取货通 - 全量开发规格

## Why
根据已完成的头脑风暴和设计文档，将快麦取货通Android PDA App从设计阶段推进到实施阶段，实现扫码取货、商品信息查看、离线操作等核心功能。

## What Changes
- 创建Android Kotlin + Compose项目骨架（Hilt + Retrofit + Room + WorkManager）
- 实现FastAPI后端服务（SQLite + 图片上传 + API Key认证）
- 实现PDA扫码模块（广播模式 + ML Kit降级）
- 实现取货单CRUD + 扫码添加待办行
- 实现商品详情 + 供应商关联修改 + 规格备注修改
- 实现图片上传（库区图/装箱图）
- 实现离线操作队列 + WorkManager同步
- Docker部署 + Tailscale组网

## Impact
- Affected code: 全新项目，涉及 `app/`（Android源码）和 `backend/`（FastAPI后端）
- Affected docs: 计划文档中的设计决策需在代码中落地

## 前端UI设计权威来源

**所有前端UI必须严格按照 [prototype/index.html](file:///d:/trea项目/快麦取货通/prototype/index.html) 原型文件实现。**

原型文件是唯一UI设计参考，包含4个可交互页面（主页/取货列表/取货单详情/商品详情）和所有交互逻辑。开发时必须遵循：

1. **CSS变量→Compose Color映射**：16个CSS变量必须映射到 `ui/theme/Color.kt`，禁止直接写色值
2. **组件规格映射**：9类组件（模块卡片/取货单卡片/扫码输入框/供应商Chip/待办行/商品详情/弹窗/Toast/网络状态条）的CSS规格必须精确映射到Compose实现
3. **页面交互规格**：扫码自动聚焦/完成动画/供应商筛选/图片预览等8种交互必须按原型实现
4. **原型与开发对照表**：8个页面/弹窗的映射关系（HomeScreen/PickListScreen/PickDetailScreen/ProductScreen + 4个弹窗）

详细映射规格参见计划文档"前端UI原型规范"章节。

## 开发阶段规划

按计划文档Phase 1-5分阶段实施，每个Phase完成后验证再进入下一阶段。

---

## ADDED Requirements

### Requirement: Phase 1 - 项目搭建（基础骨架）

#### Scenario: Android项目创建
- **WHEN** 开始Phase 1开发
- **THEN** 创建Kotlin + Compose项目，配置Hilt/Retrofit/Room/WorkManager/Coil依赖（版本号参见计划文档"关键依赖"章节），项目结构符合计划文档定义；minSdk=24（API 24 Android 7.0，覆盖99%+ PDA设备）

#### Scenario: FastAPI后端搭建
- **WHEN** 开始Phase 1开发
- **THEN** 创建FastAPI项目，实现SQLite连接池+PRAGMA初始化、API Key认证中间件、健康检查接口、Docker部署配置

#### Scenario: 快麦API签名拦截器
- **WHEN** 发起快麦ERP API请求
- **THEN** KuaimaiInterceptor自动计算HMAC-MD5签名并附加到请求参数

#### Scenario: 会话管理
- **WHEN** accessToken即将过期（3-5天内）
- **THEN** 自动调用session.refresh刷新Token（F16会话过期预警）；刷新失败时弹窗提示用户重新授权

#### Scenario: F35 Token刷新失败处理
- **WHEN** 快麦API返回session过期错误
- **THEN** OkHttp Authenticator自动检测401并调用session.refresh；刷新成功→重试原请求；刷新失败→弹窗提示"会话已过期，请重新授权"（弹窗不可关闭，防止用户忽略）→提供"一键跳转快麦后台重新授权"按钮（WebView或外部浏览器打开快麦ERP授权页面）→刷新期间暂停所有写操作（离线队列接管）→新Token获取后自动重试之前失败的请求

#### Scenario: 加密存储
- **WHEN** App首次启动配置服务器地址和API Key
- **THEN** 使用EncryptedSharedPreferences加密存储敏感信息（API Key、服务器地址、快麦Session），扫码配置使用普通DataStore（非敏感信息无需加密）

### Requirement: Phase 2 - 扫码模块

#### Scenario: PDA硬件扫码
- **WHEN** 用户按下PDA扫码键
- **THEN** PdaScannerReceiver接收广播，提取条码数据，回调给ScannerManager；300ms防抖处理；Activity生命周期管理：onResume注册广播、onPause注销广播

#### Scenario: PDA设备自动识别
- **WHEN** App启动
- **THEN** 根据Build.MANUFACTURER自动匹配广播Action和Key（iData/Urovo/新大陆/通用4种设备），支持用户自定义配置（DataStore存储）

#### Scenario: 摄像头扫码降级
- **WHEN** PDA硬件扫码不可用
- **THEN** 使用ML Kit Barcode Scanning实现摄像头扫码

#### Scenario: 条码格式兼容
- **WHEN** 扫码结果含控制字符或空格
- **THEN** 后端clean_barcode清洗后验证格式，异常条码返回友好提示

#### Scenario: 连续扫码模式
- **WHEN** 用户在取货单详情页开启连续扫码
- **THEN** 扫码→自动添加待办行→光标回到扫码框→等待下一个扫码；连续扫码模式下不自动标记完成，完成操作仍需手动点击；重复SKU时中等振动+高亮提示但不中断连续扫码

### Requirement: Phase 3 - 取货单功能

#### Scenario: 新建取货单
- **WHEN** 用户点击"新建"按钮
- **THEN** 弹出拣货区选择弹窗，选择后自动生成单号（yyyyMMdd-拣货区X格式）；同一拣货区当天第1单不加序号，第2单起加（1）（2）递增

#### Scenario: 取货单列表
- **WHEN** 进入取货列表页
- **THEN** 显示未完成的取货单（按创建时间倒序），已完成的从列表消失；GET /api/orders/{id}支持supplierName筛选参数

#### Scenario: 主页设计
- **WHEN** 进入App主页
- **THEN** 不使用底部导航Tab，主页直接显示三个模块入口卡片（取货列表 + 商品详情 + 设置），点击进入对应页面

#### Scenario: 扫码添加待办行
- **WHEN** 在取货单详情页扫码
- **THEN** App发1次请求给后端（POST /api/orders/{id}/items），后端查询快麦API+创建待办行+缓存SKU信息；扫码后输入框内容不消失，下次扫码直接替换为新编码

#### Scenario: 供应商筛选
- **WHEN** 在取货单详情页点击供应商筛选Chip
- **THEN** 根据当前取货单已添加商品的供应商自动生成筛选列表（去重），默认选中"全部"；选择某供应商后仅显示该供应商的商品行

#### Scenario: 查看库区图/装箱图（取货单详情页）
- **WHEN** 在取货单详情页点击待办行的库区图或装箱图按钮
- **THEN** 弹窗展示对应图片，点击关闭弹窗

#### Scenario: 重复扫码检测
- **WHEN** 扫码的SKU在当前取货单中已存在
- **THEN** 中等振动+高亮提示"已存在"，不添加重复行

#### Scenario: 完成/恢复待办行
- **WHEN** 点击"完成"按钮
- **THEN** 调用后端API更新状态，成功后更新本地Room缓存，待办行自动移到已完成组

#### Scenario: 全部完成
- **WHEN** 点击"全部完成"按钮
- **THEN** 后端批量SQL一次更新所有待办行（非逐条更新）

#### Scenario: 12小时超时自动完成
- **WHEN** 取货单创建超过12小时且仍有未完成待办行
- **THEN** 后端定时任务自动标记完成（completion_type=1）；12小时超时是后台逻辑，前端不显示倒计时

#### Scenario: 取货单删除
- **WHEN** 长按取货单卡片选择删除
- **THEN** 弹窗二次确认，确认后删除取货单+级联删除所有待办行

#### Scenario: 待办行删除
- **WHEN** 长按待办行选择删除
- **THEN** 删除该待办行，不可恢复

#### Scenario: 取货单自动完成检测
- **WHEN** 取货单内所有待办行都标记完成
- **THEN** 取货单自动标记为已完成（completion_type=0手动完成）；恢复一个待办行时取货单恢复为进行中

#### Scenario: 已完成取货单查看（F21）
- **WHEN** 用户在取货列表页点击"查看已完成"入口
- **THEN** 展示最近7天内已完成的取货单列表，按完成时间倒序

#### Scenario: 取货单排序（F24）
- **WHEN** 进入取货列表页
- **THEN** 取货单按创建时间倒序排列（最新在上），同一拣货区的单据相邻显示

#### Scenario: 长按操作（F25）
- **WHEN** 长按待办行
- **THEN** 弹出操作菜单（删除、查看商品详情）；长按取货单卡片弹出删除选项

#### Scenario: PDA触摸优化（F17）
- **WHEN** 渲染所有可交互元素
- **THEN** 所有可点击元素最小触摸目标56dp×56dp（视觉尺寸可以更小如按钮56dp×40dp，但触摸热区必须≥56dp×56dp），供应商名称等关键信息字体20sp+

#### Scenario: 并发冲突处理
- **WHEN** 多台PDA同时操作同一取货单
- **THEN** 两个PDA同时完成同一待办行→后端幂等返回成功；一个PDA完成行另一个PDA删除同一行→后端先到先得，后到返回409 Conflict，App端刷新数据；一个PDA删除取货单另一个PDA在操作→App端检测到404，提示"取货单已被删除"并返回列表

### Requirement: Phase 4 - 商品详情与图片

#### Scenario: 商品详情查看
- **WHEN** 点击待办行进入商品详情页
- **THEN** 显示规格图、规格名称、供应商名称（20sp Bold #DC2626）、库区图/装箱图、规格备注；待办行不显示规格编码；商品详情页顶部也有扫码输入框，用于快速切换查看其他SKU：扫码后查询该SKU信息并替换当前页面内容（无需返回取货单详情页再扫码）

#### Scenario: 供应商关联修改
- **WHEN** 点击供应商名称
- **THEN** 弹出供应商选择列表（通过supplier.list.query获取）→选择后弹窗二次确认→回传快麦ERP→成功后立即更新本地supplier_name/code字段+后端缓存失效；供应商主数据（编码/名称等）的创建和修改仍需在快麦ERP后台操作

#### Scenario: 规格备注修改
- **WHEN** 编辑备注后点击保存
- **THEN** 弹窗二次确认→回传快麦ERP→成功后立即更新本地remark字段+后端缓存失效

#### Scenario: 图片上传
- **WHEN** 点击图片上传位
- **THEN** 压缩图片至200KB左右→上传到后端→显示上传进度→成功后更新图片展示

#### Scenario: 图片删除/替换
- **WHEN** 点击已上传的图片
- **THEN** 可选择重新上传替换（先删旧图再传新图）或删除

### Requirement: Phase 5 - 优化与发布

#### Scenario: 离线操作队列
- **WHEN** 网络断开时执行操作
- **THEN** 操作写入Room的pending_operations表，网络恢复后WorkManager自动同步，按取货单分组并行；同步成功后立即从Room删除；同步失败重试最多3次，仍失败则标记为冲突，提示用户手动处理

#### Scenario: 网络状态指示
- **WHEN** 网络状态变化
- **THEN** 全局显示在线/离线/弱网状态，离线时操作按钮标记"待同步"

#### Scenario: 扫码反馈
- **WHEN** 扫码成功/失败
- **THEN** 成功→短振动+提示音；失败→长振动+错误音；设置页可开关声音和振动

#### Scenario: 屏幕常亮
- **WHEN** 进入取货单详情页/商品详情页
- **THEN** 保持屏幕常亮，退出时恢复

#### Scenario: 冷启动优化
- **WHEN** App冷启动
- **THEN** Application.onCreate仅初始化Hilt，首页用Room缓存秒加载，目标<2秒

#### Scenario: 首次使用引导（F23）
- **WHEN** 用户首次启动App
- **THEN** 显示引导页：配置服务器地址→选择扫码方式→完成；引导完成后不再显示

#### Scenario: App启动连通性检测
- **WHEN** App启动时
- **THEN** 自动检测服务器连通性，无法连接时提示检查网络或服务器地址

#### Scenario: Docker部署
- **WHEN** 运行sync-to-docker-deploy.ps1 + docker-compose up
- **THEN** 后端服务正常启动，健康检查通过，图片持久化到NAS存储

#### Scenario: Tailscale组网
- **WHEN** PDA与NAS不在同一网络
- **THEN** 通过Tailscale虚拟网络访问后端API（100.64.x.x:8900）；Tailscale基于WireGuard协议端到端加密，即使使用HTTP传输数据在隧道内已加密，无需额外配置HTTPS

### Requirement: 后端API安全

- 所有后端API请求需携带X-API-Key Header
- 图片静态资源（/images路径）不需要API Key认证
- App端OkHttp拦截器自动添加X-API-Key Header

### Requirement: 快麦ERP字段同步策略

- 权威数据源为快麦API：supplier_name/code、skuRemark等字段的权威数据来自快麦ERP，App端不能绕过快麦ERP直接修改本地字段
- F18修改供应商关联：回传快麦ERP成功后立即更新本地supplier_name/code字段+后端缓存失效
- F4备注修改：回传快麦ERP成功后立即更新本地备注字段+后端缓存失效
- 快麦ERP后台修改了供应商/备注：下次扫码该SKU时后端缓存失效后拉取最新值更新pick_items表
- 已完成的取货单中的字段不会更新：已完成取货单是历史快照，不随快麦ERP数据变化而变化

### Requirement: App端数据策略

| 数据 | 来源 | 本地缓存 | 说明 |
|---|---|---|---|
| 取货单/待办行 | 后端API | Room缓存+操作队列 | 优先读缓存，操作时先写后端再更新缓存；断网时写入本地操作队列 |
| 商品信息 | 后端API（后端代理快麦API，带缓存层） | Room缓存 | App只请求后端，后端查缓存或调快麦API；App端Room缓存24小时 |
| 商品主图/SKU图 | 快麦API | Coil自动缓存（非Room） | 商品主图和SKU图从快麦API获取，Coil自动做内存+磁盘缓存（无需手动Room缓存），仅展示使用 |
| 规格备注 | 快麦ERP（权威数据源） | Room缓存（存于pick_items.remark字段） | 修改后回传快麦ERP，成功后立即更新本地字段+后端缓存失效 |
| 库区图/装箱图 | 后端服务器 | 无需缓存 | 每次从后端获取URL，Coil加载 |

### Requirement: 多PDA共享架构

- 取货单、待办行、图片数据存储在后端服务器，所有PDA访问同一后端，数据实时同步
- App端Room仅作为离线缓存，非主数据源
- 后端SQLite WAL模式支持3-5台PDA并发读写

### Requirement: 图片上传策略

1. 拍照/选图后先压缩：最大宽度1024px，JPEG质量80%，约200KB
2. 显示上传进度条
3. 上传失败自动重试（最多3次）
4. 上传期间可继续其他操作（异步上传）

### Requirement: 快麦API限流与App端缓存

- OkHttp连接池：5个连接，30秒keep-alive
- OkHttp超时：connectTimeout 10秒、readTimeout 15秒
- 令牌桶限流：每秒最多5次请求，防止快麦API超频
- App端Room缓存策略：SKU信息缓存24小时、供应商列表缓存24小时、取货单/待办行使用Room缓存但每次进入页面时从后端刷新（优先展示缓存+后台静默刷新）、accessToken存储30天
- 缓存失效：用户下拉刷新时清除对应缓存，强制重新请求

### Requirement: 离线操作队列设计

**PendingOperationEntity完整字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| operationType | String | 操作类型：ADD_ITEM / COMPLETE_ITEM / RESTORE_ITEM / COMPLETE_ALL / DELETE_ITEM / DELETE_ORDER |
| orderId | Long | 关联取货单ID（方便按取货单维度查询和管理待同步操作） |
| targetId | Long | 目标ID（取货单ID或待办行ID） |
| payload | String | 请求参数JSON |
| createdAt | Long | 创建时间 |
| retryCount | Int | 重试次数，最多3次 |

**同步策略**：
1. 网络可用时：操作直接走后端API，成功后更新本地缓存
2. 网络不可用时：操作写入本地Room操作队列 + 更新本地缓存（乐观更新UI）
3. 网络恢复时：WorkManager按顺序同步操作队列，冲突时后端数据优先
4. 同步失败时：重试最多3次，仍失败则标记为冲突，提示用户手动处理
5. 同步成功后：立即从Room删除对应记录

### Requirement: 数据备份方案

| 数据 | 位置 | 备份方式 |
|---|---|---|
| SQLite数据库 | ./data/kuaimai.db | NAS定时任务每日备份到其他目录 |
| 商品图片 | ./data/product_images/ | NAS快照/RAID保护 |
| .env配置 | ./.env | 手动备份，勿提交Git |

- SQLite WAL模式：启用WAL模式支持并发读写，容器重启后-wal和-shm文件自动清理
- Docker volume挂载整个./data目录（非单个文件），确保附属文件可正常写入

### Requirement: 快麦ERP API映射（7个接口）

| 功能 | API接口 | method名 | 说明 |
|---|---|---|---|
| 获取商品列表 | 查询商品列表 | `item.list.query` | 获取picPath、outerId、barcode等 |
| 获取SKU详情 | 查询商品SKU列表V2 | `erp.item.sku.list.get` | 获取skuRemark、skuPicPath等 |
| 获取供应商 | 查询商品关联供应商信息V2 | `erp.item.supplier.list.get` | 获取supplierName、supplierCode |
| 查询供应商列表 | 查询供应商列表 | `supplier.list.query` | 获取所有供应商编码和名称，用于选择供应商 |
| 修改规格备注 | 修改/新增商品V2 | `erp.item.general.addorupdate` | 传入skus[].remark回写 |
| 修改商品供应商关联 | 修改/新增商品V2 | `erp.item.general.addorupdate` | 传入id+suppliers[].code更换关联供应商 |
| 刷新会话 | 刷新会话(必接) | `session.refresh` | 保持accessToken有效 |

- App端通过DTO映射层隔离快麦API字段名，映射文件收敛到 `data/api/dto/mapper/` 目录下
- 快麦字段变化时只改映射文件，不影响业务逻辑

### Requirement: 对齐方式统一

- 所有页面统一使用 `Modifier.align() + Arrangement` 控制对齐，禁止混用padding偏移模拟对齐
- 对齐常量收敛到 `ui/theme/Alignment.kt`，页面只引用常量
- AppAlignment对象包含：RowBetween/RowCenter/ItemStart/ButtonEnd/ColumnCenter

### Requirement: App端项目结构补充文件

- `ImageUploadService.kt` — 图片上传API（OkHttp Multipart）
- `ProductImageDao.kt` — 商品图片DAO
- `PendingOperationDao.kt` — 离线操作队列DAO
- `ImageRepository.kt` — 图片上传Repository
- `AuthRepository.kt` — 会话管理Repository
- `TimeUtils.kt` — 时间工具（北京时间转换）

### Requirement: 后端数据库约束

- 所有表字段添加NOT NULL/DEFAULT/CHECK约束
- pick_orders表order_no UNIQUE NOT NULL
- pick_orders表completed_at在status=1时NOT NULL
- pick_orders表total_count CHECK(total_count >= 0)
- pick_orders表completed_count CHECK(completed_count >= 0)
- pick_orders表completion_type CHECK(completion_type IN (0,1))
- pick_areas表name UNIQUE NOT NULL
- pick_items表UNIQUE(order_id, sku_outer_id)防止重复SKU
- pick_items表order_id外键ON DELETE CASCADE
- pick_items表completed_at在status=1时NOT NULL
- product_images表UNIQUE(sku_outer_id, image_type)
- product_images表file_path字段（后端独有，存储服务器本地路径）
- image_type字段CHECK IN ('area','box')
- status字段CHECK IN (0,1)

### Requirement: 后端数据库排序规范

- App端Room查询和后端API查询必须使用完全相同的ORDER BY规则
- 取货单列表：ORDER BY created_at DESC
- 待办行（完整排序）：ORDER BY status ASC, CASE WHEN status=0 THEN created_at ELSE completed_at END DESC
- 拣货区列表：ORDER BY created_at ASC
- 离线操作队列：ORDER BY created_at ASC
- 排序逻辑收敛到各自DAO层，禁止在ViewModel/Service层二次排序

### Requirement: 后端快麦API缓存层

- sku_cache表缓存SKU信息24小时
- F18/F4修改后调用invalidate_sku_cache()删除缓存记录
- 多PDA共享缓存，减少快麦API调用

### Requirement: 数据清理策略

- 已完成取货单保留30天后自动清理（每天凌晨3:00）
- sku_cache缓存24小时过期（每小时清理）
- crash日志保留30天（每天凌晨4:00）
- 孤立图片7天安全期后删除
- 商品图片文件清理：取货单删除时检查关联SKU是否还有其他取货单引用，无引用则删除图片文件+记录
- 离线操作队列（App端）：同步成功后立即从Room删除
- App端Room缓存：超过24小时的缓存数据下次请求时覆盖
- 12小时超时检查：后端每分钟检查expire_at < 当前时间的未完成取货单

### Requirement: 快麦API凭证独立存储

- 凭证从.env分离到kuaimai.json
- kuaimai.json文件路径：`/data/kuaimai.json`（Docker volume挂载目录内）
- kuaimai.json结构：`{"app_key": "", "app_secret": "", "session": "", "updated_at": ""}`
- 后端启动时读取kuaimai.json，运行时监听文件变化自动热更新（使用watchfiles库或定时轮询文件修改时间），无需重启容器
- SESSION过期预警（每24小时检查，距过期3-5天时日志预警）
- .env精简为仅API_KEY和SERVER_PORT
- App端API_KEY从EncryptedSharedPreferences读取，禁止硬编码

### Requirement: 后端crash_logs表

- crash_logs表存储App端crash日志
- 字段：id(PK)、app_version(VARCHAR)、device_model(VARCHAR)、error_message(TEXT)、stack_trace(TEXT)、created_at(DATETIME)
- 后端提供POST /api/crash-report接口接收ACRA上报
- 后端提供GET /api/app-version接口返回最新版本号和APK下载URL
- 后端提供GET /api/images/{skuOuterId}接口获取某规格的库区图+装箱图URL
- 后端提供GET /api/orders/{id}/suppliers接口获取取货单内的供应商列表（去重）

### Requirement: CSS变量→Compose Color映射（16个变量）

原型中定义的CSS变量必须映射到 `ui/theme/Color.kt`，禁止在Compose代码中直接写色值：

| CSS变量 | 色值 | Compose属性名 | 用途 |
|---------|------|--------------|------|
| `--primary` | `#2563EB` | `BrandBlue` | 品牌蓝，标题/Tab选中 |
| `--primary-light-bg` | `#DBEAFE` | `PrimaryLightBg` | 主操作按钮底色 |
| `--primary-light-text` | `#1D4ED8` | `PrimaryLightText` | 主操作按钮文字 |
| `--success-bg` | `#DCFCE7` | `SuccessBg` | 完成按钮底色 |
| `--success-text` | `#15803D` | `SuccessText` | 完成按钮文字 |
| `--danger-bg` | `#FEE2E2` | `DangerBg` | 危险操作底色 |
| `--danger-text` | `#B91C1C` | `DangerText` | 危险操作文字 |
| `--supplier` | `#DC2626` | `SupplierRed` | 供应商名称红色 |
| `--warning` | `#EAB308` | `WarningYellow` | 警告色 |
| (Toast警告底色) | `#FEF9C3` | `WarningBg` | Toast/Snackbar警告底色 |
| `--bg` | `#FFFFFF` | `SurfaceWhite` | 主背景 |
| `--bg-secondary` | `#F3F4F6` | `SurfaceGray` | 次要区域背景 |
| `--text` | `#111827` | `TextPrimary` | 主文字 |
| `--text-secondary` | `#6B7280` | `TextSecondary` | 次要文字 |
| `--text-muted` | `#9CA3AF` | `TextMuted` | 禁用/占位文字 |
| `--border` | `#E5E7EB` | `BorderGray` | 边框/分割线 |

### Requirement: 9类组件规格映射（CSS→Compose）

开发时必须严格按照原型CSS规格实现Compose组件，关键映射：

1. **模块卡片**（主页）：圆角12dp + elevation 2dp + 图标框52dp/圆角14dp + 间距14dp
2. **取货单卡片**（列表页）：padding 16dp×12dp + 圆角12dp + 单号18sp SemiBold + 进度14sp Medium + 状态标签圆角20dp + 进度点10dp
3. **扫码输入框**：边框2dp BrandBlue + 圆角8dp + padding 16dp×12dp + 字号16sp + 聚焦效果
4. **供应商筛选Chip**：FlowRow间距8dp + padding 14dp×6dp + 圆角20dp + 字号13sp Medium + 选中态BrandBlue白字 + 未选中态BorderGray边框
5. **待办行**：最小高度72dp + padding 12dp×10dp + 圆角12dp + 规格图52dp/圆角8dp + 规格图标注10sp + 规格名16sp Medium + 供应商20sp Bold #DC2626 + 库区图40dp/圆角6dp + 完成按钮56dp宽 + 已完成态alpha 0.55+删除线
6. **商品详情页**：SKU图72dp/圆角10dp + 规格名18sp SemiBold + 编码14sp + 供应商20sp Bold #DC2626 + 备注框圆角8dp + 上传位aspectRatio(1f)+虚线边框+最小120dp高 + 2列网格间距12dp
7. **弹窗**：遮罩alpha 0.6 + 弹窗圆角16dp + 内边距24dp + 拣货区按钮minWidth 100dp + 设置行分割线BorderGray
8. **Toast/Snackbar**：顶部对齐 + 圆角10dp + 字号14sp Medium + 成功色SuccessBg/SuccessText + 错误色DangerBg/DangerText + 警告色WarningBg/WarningText(#A16207)
9. **网络状态条**：高度3dp + 在线色#16A34A + 圆角2dp

### Requirement: 8种页面交互规格

| 交互 | 行为 | Compose实现 |
|------|------|------------|
| 页面切换 | Tab切换/点击卡片导航 | NavController.navigate() + Fade过渡(200ms) |
| 扫码自动聚焦 | 进入页面光标在扫码框 | LaunchedEffect { focusRequester.requestFocus() } |
| 扫码替换 | 新扫码内容替换旧内容 | textFieldValue = scannedCode |
| 完成动画 | 行变灰+移到底部 | animateColorAsState + animateContentSize |
| 供应商筛选 | 点击Chip过滤列表 | filteredList = if(selected=="全部") all else all.filter{it.supplier==selected} |
| 图片预览 | 点击弹窗放大 | Dialog + Image(painter=rememberAsyncImagePainter(url)) |
| 新建取货单 | 弹窗选择拣货区→自动生成单号 | AlertDialog + 动态拣货区列表 |
| 设置管理 | 增删拣货区 | AlertDialog + LazyColumn + 添加/删除按钮 |

### Requirement: 原型与开发对照表（8个页面/弹窗）

| 原型页面 | Compose Screen | 导航路由 |
|---------|---------------|---------|
| 主页(Page 0) | `HomeScreen` | `home` |
| 取货列表(Page 1) | `PickListScreen` | `pickList` |
| 取货单详情(Page 2) | `PickDetailScreen` | `pickDetail/{orderId}` |
| 商品详情(Page 3) | `ProductScreen` | `product` |
| 设置弹窗 | `SettingsDialog` | (HomeScreen内弹窗) |
| 新建取货单弹窗 | `NewOrderDialog` | (PickListScreen内弹窗) |
| 供应商选择弹窗 | `SupplierSelectDialog` | (ProductScreen内弹窗，F18) |
| 图片预览弹窗 | `ImagePreviewDialog` | (通用组件) |

### Requirement: PickOrderEntity完整字段

- id: Long (PK) 自增主键
- orderNo: String 取货单单号（如20260614-拣货区A）
- status: Int 0-进行中 1-已完成
- completionType: Int 0-手动完成 1-超时自动完成
- totalCount: Int 总商品行数
- completedCount: Int 已完成商品行数
- createdAt: Long 创建时间（北京时间）
- completedAt: Long? 完成时间
- expireAt: Long 过期时间 = createdAt + 12小时

### Requirement: PickItemEntity完整字段

- id: Long (PK) 自增主键
- orderId: Long 关联取货单ID（外键）
- skuOuterId: String 规格编码（扫码获取的编码）
- sysItemId: Long 系统主商品ID
- sysSkuId: Long 系统SKU ID
- propertiesName: String 规格名称（如红色/XL）
- picPath: String SKU图片URL（来自快麦）
- status: Int 0-待办 1-已完成
- supplierName: String 供应商名称
- supplierCode: String 供应商编码（F18修改供应商时需传入此值）
- remark: String 规格备注（来自快麦ERP，默认空字符串，F4修改后回传快麦并立即更新本地）
- createdAt: Long 创建时间（北京时间）
- completedAt: Long? 完成时间

### Requirement: ProductImageEntity完整字段

- id: Long (PK) 自增主键
- skuOuterId: String 规格编码（关联SKU，非主商品编码）
- imageType: String 图片类型：area（库区图）/ box（装箱图），严格区分
- imageUrl: String 图片访问URL
- createdAt: Long 创建时间

> App端Entity结构与后端product_images表保持一致（窄表方案），同一规格编码最多2条记录（一条area、一条box）。

### Requirement: 后端数据库完整字段定义

**pick_orders（取货单）**：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| order_no | VARCHAR(30) | UNIQUE NOT NULL | 取货单单号 |
| status | INTEGER | NOT NULL DEFAULT 0 | 0-进行中 1-已完成，CHECK(status IN (0,1)) |
| completion_type | INTEGER | NOT NULL DEFAULT 0 | 0-手动完成 1-超时自动完成，CHECK(completion_type IN (0,1)) |
| total_count | INTEGER | NOT NULL DEFAULT 0 | CHECK(total_count >= 0) |
| completed_count | INTEGER | NOT NULL DEFAULT 0 | CHECK(completed_count >= 0) |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |
| completed_at | DATETIME | | 完成时间（status=1时NOT NULL） |
| expire_at | DATETIME | NOT NULL | 过期时间 = created_at + 12小时 |

**pick_items（取货商品行）**：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| order_id | INTEGER | NOT NULL FK→pick_orders(id) ON DELETE CASCADE | 关联取货单ID |
| sku_outer_id | VARCHAR(64) | NOT NULL | 规格编码 |
| sys_item_id | INTEGER | NOT NULL | 快麦系统主商品ID |
| sys_sku_id | INTEGER | NOT NULL | 快麦系统SKU ID |
| properties_name | VARCHAR(128) | NOT NULL DEFAULT '' | 规格名称 |
| pic_path | VARCHAR(512) | NOT NULL DEFAULT '' | SKU图片URL |
| status | INTEGER | NOT NULL DEFAULT 0 | 0-待办 1-已完成，CHECK(status IN (0,1)) |
| supplier_name | VARCHAR(128) | NOT NULL DEFAULT '' | 供应商名称 |
| supplier_code | VARCHAR(64) | NOT NULL DEFAULT '' | 供应商编码 |
| remark | VARCHAR(512) | DEFAULT '' | 规格备注 |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |
| completed_at | DATETIME | | 完成时间（status=1时NOT NULL） |

> UNIQUE(order_id, sku_outer_id) — 同一取货单内不允许重复SKU

**pick_areas（拣货区）**：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| name | VARCHAR(32) | UNIQUE NOT NULL | 拣货区名称 |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |

**product_images（商品图片）**：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| sku_outer_id | VARCHAR(64) | NOT NULL | 规格编码 |
| image_type | VARCHAR(10) | NOT NULL CHECK(image_type IN ('area','box')) | 图片类型 |
| image_url | VARCHAR(512) | NOT NULL | 图片访问URL |
| file_path | VARCHAR(512) | NOT NULL | 服务器本地存储路径（后端独有） |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |

> UNIQUE(sku_outer_id, image_type) — 同一规格同一类型只能有一条记录，上传时INSERT OR REPLACE

**sku_cache（快麦API缓存）**：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| sku_outer_id | VARCHAR(64) | PK NOT NULL | 规格编码 |
| properties_name | VARCHAR(128) | NOT NULL DEFAULT '' | 规格名称 |
| pic_path | VARCHAR(512) | NOT NULL DEFAULT '' | SKU图片URL |
| supplier_name | VARCHAR(128) | NOT NULL DEFAULT '' | 供应商名称 |
| supplier_code | VARCHAR(64) | NOT NULL DEFAULT '' | 供应商编码 |
| remark | VARCHAR(512) | NOT NULL DEFAULT '' | 规格备注 |
| sys_item_id | INTEGER | NOT NULL | 快麦主商品ID |
| sys_sku_id | INTEGER | NOT NULL | 快麦SKU ID |
| cached_at | DATETIME | NOT NULL | 缓存时间（24小时过期） |

**crash_logs（崩溃日志）**：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| app_version | VARCHAR(32) | NOT NULL | App版本号 |
| device_model | VARCHAR(64) | NOT NULL | 设备型号 |
| error_message | TEXT | NOT NULL | 错误消息 |
| stack_trace | TEXT | NOT NULL | 完整堆栈信息 |
| created_at | DATETIME | NOT NULL | 上报时间（北京时间） |

> crash日志保留30天，每天凌晨4:00自动清理

### Requirement: 后端环境变量配置

- .env文件包含：API_KEY（后端认证密钥）、SERVER_PORT、SERVER_URL（服务器地址，用于生成扫码配置二维码，格式如`http://NAS_IP:8900`）
- 快麦凭证已分离到kuaimai.json（参见"快麦API凭证独立存储"Requirement）
- .env文件禁止提交到Git，.dockerignore已忽略所有.env*文件
- .env.docker.example模板文件供部署时复制并填入真实值（含SERVER_URL示例）

### Requirement: Room Migration策略

- 每次数据库schema变更时编写Migration对象
- 禁止使用fallbackToDestructiveMigration()，避免用户数据丢失
- 迁移逻辑在DatabaseModule.kt中统一管理
- 发布前在测试设备上验证迁移路径

### Requirement: OpenAPI Contract-First流程

- 先编写api.yaml（唯一契约），再从规范自动生成前后端代码
- 使用openapi-generator生成Python FastAPI后端代码和Android Kotlin客户端代码
- 确保前后端函数名、字段名、类型完全一致
- 快麦外部API通过DTO映射层隔离

**api.yaml核心Schema定义**：

| Schema | 字段 | 说明 |
|--------|------|------|
| OrderResponse | id/orderNo/status/totalCount/completedCount/createdAt | 取货单响应 |
| AddItemRequest | skuOuterId(必填) | 扫码添加待办行请求 |
| ItemResponse | id/skuOuterId/propertiesName/supplierName/status | 待办行响应 |
| BaseResponse | success/message | 通用响应 |
| UploadImageRequest | file(binary)/skuOuterId/imageType(enum:area,box) | 图片上传请求(multipart) |

**生成命令**：
```bash
openapi-generator generate -i api.yaml -g python-fastapi -o backend/ --additional-properties=packageName=kuaimai_server
openapi-generator generate -i api.yaml -g kotlin -o android-client/ --additional-properties=library=jvm-retrofit2,packageName=com.kuaimai.pda.api
```

### Requirement: 开源参考项目

| 项目 | 地址 | 参考价值 |
|---|---|---|
| ZXing | https://github.com/zxing/zxing | 条码解码核心库（备用参考） |
| Odoo | https://github.com/odoo/odoo | ERP+扫码架构参考 |
| FastAPI | https://github.com/fastapi/fastapi | 图片上传后端框架 |
| Coil | https://github.com/coil-kt/coil | Compose图片加载 |

### Requirement: 错误上报与App版本更新

- 集成ACRA（Application Crash Reports for Android），crash日志发送到后端POST /api/crash-report
- 禁止使用第三方SaaS（Bugly/Crashlytics），数据必须留在自己的服务器
- ANR检测：主线程阻塞5秒以上记录到本地日志
- 后端提供GET /api/app-version接口返回最新版本号和APK下载URL
- App启动时检查版本，有新版本时弹窗提示更新
- 下载APK到本地后调用系统安装器（需REQUEST_INSTALL_PACKAGES权限）
- APK文件存储在后端/data/apk/目录，通过静态文件接口分发

### Requirement: Room批量写入与SQLite并发

- Room批量写入使用@Transaction + insertAll一次性写入，避免逐条插入
- syncOrderWithItems事务：insertOrder + insertAll批量写入
- SQLite WAL模式允许读写并行，但写写仍互斥
- 当前3-5台PDA场景下WAL模式足够（实测TPS约50-100）
- 如果未来超过10台PDA同时操作，需考虑升级PostgreSQL
- 后端已配置PRAGMA busy_timeout=5000，写锁冲突时等待5秒

### Requirement: 页面状态规范

| 状态 | 表现 |
|---|---|
| Loading | 顶部LinearProgressIndicator（Material3标准） |
| 空状态 | 纯文字提示，无插画。如"暂无进行中的取货单，点击右上角+新建" |
| 错误 | 顶部Snackbar红色+文字说明，不阻断操作 |
| 网络离线 | 顶部薄条显示"已离线，操作将在网络恢复后同步" |
| 网络弱网 | 顶部薄条黄色"网络不稳定" |

### Requirement: 过渡与动效规范

| 场景 | 动效 | 时长 |
|---|---|---|
| 页面切换 | Fade过渡（无滑动，PDA性能考虑） | 200ms |
| 完成点击 | 行内打勾动画→移动到已完成组 | 300ms |
| 扫码成功 | 顶部Snackbar绿色提示+短振动 | 1500ms |
| 扫码失败 | 顶部Snackbar红色提示+长振动 | 2000ms |
| 网络切换 | 顶部薄条渐变（绿/黄/红） | 300ms |

### Requirement: 间距与字体系统

**间距系统**：
- 水平间距（屏幕两边）: 16dp
- 水平间距（组件之间）: 12dp
- 垂直间距（卡片之间）: 8dp
- 垂直间距（内部行之间）: 4dp
- 内边距（卡片内部）: 12dp
- 圆角（卡片）: 12dp
- 圆角（按钮）: 8dp

**字体系统**：
- 页面标题: 20sp Bold #111827
- 供应商名称: 20sp Bold #DC2626
- 商品/规格名称: 18sp Medium #111827
- 单号/编码: 16sp Regular #6B7280
- 按钮文字: 18sp Medium #1D4ED8
- 辅助信息: 14sp Regular #6B7280
- 占位符: 16sp Regular #9CA3AF

**触摸目标尺寸**：
- 按钮: 56dp × 40dp（含"完成""恢复""全部完成""保存"）
- 图片上传位: 64dp × 64dp
- 待办行: 72dp 高
- 筛选Chip: 40dp 高

### Requirement: F32冷启动优化细节

- Application.onCreate仅初始化Hilt，不做网络/数据库操作
- 首页数据：Room缓存秒加载，后台异步刷新（用户看到缓存的旧数据，同时静默请求最新数据）
- 数据库预填充：首次安装时预填充空数据库，避免首次启动时建表耗时
- 布局优化：首页使用LazyColumn，避免一次性加载所有取货单卡片
- 图片加载：Coil内存缓存+磁盘缓存，避免每次启动重新下载图片

### Requirement: F37 Web管理后台

#### Scenario: 管理后台访问与认证
- **WHEN** 管理员通过浏览器访问 `http://NAS_IP:8900/admin`
- **THEN** 显示管理后台页面，页面加载时弹出输入框要求输入API Key；验证通过后显示管理内容，API Key存储在sessionStorage中；所有API请求通过X-API-Key头认证；未认证时仅显示认证输入框，不显示管理内容

#### Scenario: 仪表盘概览
- **WHEN** 管理员进入管理后台仪表盘标签页
- **THEN** 显示系统概览统计卡片（进行中取货单数、用户数、拣货区数）；显示扫码配置二维码（内容为`kuaimai://setup?server=<URL>&apikey=<KEY>`），PDA扫码可自动填入服务器地址和API Key；未配置SERVER_URL时显示红色提示

#### Scenario: 用户管理
- **WHEN** 管理员进入用户管理标签页
- **THEN** 显示用户列表（用户名/权限代码/启用状态/操作按钮）；支持新增用户（输入用户名+密码+选择权限）；支持编辑用户权限（5种权限代码：settings/update_supplier/update_remark/manage_area_image/manage_box_image）；支持启用/禁用用户（禁用后该用户Token失效无法登录）；删除用户需二次确认

#### Scenario: 拣货区管理
- **WHEN** 管理员进入拣货区管理标签页
- **THEN** 显示拣货区列表；支持新增拣货区（输入名称）；支持删除拣货区（二次确认弹窗）；操作通过API同步到后端数据库，所有PDA共享同一份拣货区配置

#### Scenario: 快麦配置
- **WHEN** 管理员进入快麦配置标签页
- **THEN** 显示当前快麦凭证状态（app_key/session有效期/剩余天数/refresh_token状态）；提供"刷新Session"按钮（调用/api/kuaimai/refresh-session）；提供手动更新凭证表单（app_key/app_secret/session/refresh_token，调用/api/kuaimai/update-credentials）

#### Scenario: 系统配置
- **WHEN** 管理员进入系统配置标签页
- **THEN** 显示当前API Key（脱敏显示）和服务器地址（SERVER_URL，只读展示）；系统配置通过.env文件修改，Web页面仅展示当前值

#### Scenario: 图片查看
- **WHEN** 管理员进入图片查看标签页
- **THEN** 提供SKU编码搜索框；输入SKU后显示该SKU关联的库区图和装箱图；图片只读查看，不可编辑或删除（图片上传/删除操作在App端进行）

### Requirement: F38 扫码配置

#### Scenario: 扫码配置页面
- **WHEN** 管理员通过浏览器访问 `http://NAS_IP:8900/setup`
- **THEN** 显示包含服务器地址+API Key的二维码图片；二维码内容格式为`kuaimai://setup?server=<URL>&apikey=<KEY>`；同时显示服务器地址和API Key配置状态文字；未配置SERVER_URL时从请求Host推断地址，仍无法确定时显示错误提示和配置说明

#### Scenario: PDA扫码配置解析
- **WHEN** PDA在GuideScreen或SettingsScreen点击扫码配置按钮并扫描/setup页面的二维码
- **THEN** SetupQrParser工具类解析二维码内容；支持`kuaimai://setup?server=xxx&apikey=xxx`协议格式；兼容纯URL格式（`http://...`），解析时自动识别；解析成功后自动填入服务器地址和API Key到对应输入框
