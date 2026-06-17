# 快麦取货通 - Android PDA App 开发计划

## 项目概述

开发一款运行在PDA手持终端上的Android App，通过快麦ERP开放平台API实现扫码取货、商品信息查看、规格备注修改等功能。核心概念为**取货单**，每个取货单包含多条待办商品行。

## 需求分析

### 功能需求

| # | 功能 | 描述 |
|---|---|---|
| F1 | 取货单管理 | 新建取货单（自动生成单号如20260614-拣货区A），查看未完成取货单列表 |
| F2 | 扫码待办 | 在取货单内扫码商家编码生成待办行，可点击完成/恢复 |
| F3 | 取货单自动完成 | 取货单内所有待办行完成后，取货单自动标记完成；12小时未全部完成则自动完成 |
| F4 | 商品详情 | 查看商品信息（图片、编码、供应商等），修改规格备注需二次确认后同步至快麦ERP |
| F5 | 图片上传 | 商品详情页可上传库区图和装箱图，存储在服务器本地 |
| F6 | PDA扫码适配 | 适配PDA硬件扫描头（广播模式）+ 摄像头扫码降级方案 |
| F7 | 离线操作队列 | 断网时操作写入本地Room队列，网络恢复后通过WorkManager自动同步后端，冲突策略后端优先 |
| F8 | 扫码反馈 | 扫码成功→短振动+提示音；扫码失败（商品未找到）→长振动+错误音；设置中可开关 |
| F9 | 重复扫码检测 | 同一取货单内重复扫同一SKU时提示并高亮已存在的行，不静默添加重复行 |
| F10 | 图片压缩上传 | 上传前压缩到1024px宽/质量80%（约200KB），显示上传进度条 |
| F11 | 后端API安全认证 | FastAPI后端添加API Key认证，App端请求携带token，防止未授权访问 |
| F12 | 网络状态指示 | 全局显示在线/离线/弱网状态，离线时操作按钮标记"待同步" |
| F13 | 屏幕常亮 | 取货单详情页/商品详情页保持屏幕常亮，退出时恢复 |
| F14 | 全部完成按钮 | 一键将取货单内所有待办行标记完成 |
| F15 | 下拉刷新 | 取货单详情页支持下拉刷新，同步其他PDA的操作 |
| F16 | 会话过期预警 | accessToken过期前3-5天提醒用户刷新，而非过期后才提示 |
| F17 | PDA触摸优化 | 所有可点击元素最小触摸热区56dp×56dp（视觉尺寸可以更小，如按钮56dp×40dp，但触摸热区必须≥56dp×56dp），供应商名称等关键信息字体20sp+ |
| F18 | 供应商关联修改 | 商品详情页点击供应商名称可修改商品与供应商的关联关系，选择后弹窗二次确认，确认后回传快麦ERP更换关联供应商，成功后立即更新本地supplier_name/code字段+后端缓存失效 |
| F19 | 取货单删除 | 可删除未完成的取货单（确认弹窗二次确认），删除后不可恢复 |
| F20 | 待办行删除 | 可删除误扫的待办行，长按待办行弹出删除选项，删除后不可恢复 |
| F21 | 已完成取货单查看 | 取货列表页底部提供"查看已完成"入口，展示最近7天内已完成的取货单 |
| F22 | 图片删除/替换 | 已上传的库区图/装箱图可点击重新上传替换或删除，替换时先删旧图再传新图 |
| F23 | 首次使用引导 | 首次启动App显示引导页：配置服务器地址→选择扫码方式→完成，引导完成后不再显示 |
| F24 | 取货单排序 | 取货列表按创建时间倒序排列（最新在上），同一拣货区的单据相邻显示 |
| F25 | 长按操作 | 长按待办行弹出操作菜单（删除、查看商品详情），长按取货单卡片弹出删除选项 |
| F27 | 连续扫码模式 | 取货单详情页可开启连续扫码模式：扫码→自动添加待办行→光标回到扫码框→等待下一个扫码，免去手动点击，提升仓库作业效率 |
| F28 | API Key加密存储 | App端使用EncryptedSharedPreferences加密存储API Key等敏感信息（非DataStore），防止PDA丢失后数据泄露 |
| F31 | 条码格式兼容 | 后端对扫码结果做清洗（trim、去除控制字符），支持纯数字、EAN-13、Code128、QR码等多种格式，异常条码友好提示 |
| F32 | App冷启动优化 | 避免Application.onCreate中做耗时操作，首页数据用Room缓存秒加载，目标冷启动<2秒 |
| F35 | Token刷新失败处理 | 快麦API Token刷新失败→弹窗提示"会话已过期，请重新授权"→提供"一键跳转快麦后台重新授权"入口→刷新期间暂停写操作 |
| F36 | 用户权限控制 | 用户登录认证（UUID Token，7天有效期），5种权限代码控制功能访问（settings/update_supplier/update_remark/manage_area_image/manage_box_image），管理员可增删改查用户及分配权限，登录限流（5次失败锁定5分钟），Token过期自动跳转登录页 |
| F37 | Web管理后台 | 后端/admin页面提供统一的浏览器端系统管理，包含6个标签页：仪表盘（系统概览+扫码配置二维码）、用户管理（增删改查+权限分配+启用禁用）、拣货区管理（增删）、快麦配置（凭证状态+刷新+手动更新）、系统配置（API Key+服务器地址）、图片查看（按SKU只读查看）。API Key认证，sessionStorage存储。App与Web权限分离：Web后台负责系统管理，App端负责日常业务（取货单、图片上传/删除、供应商/备注修改、扫码方式/反馈开关） |
| F38 | 扫码配置 | 后端/setup页面显示配置二维码（kuaimai://setup?server=xxx&apikey=xxx协议），PDA扫码自动填入服务器地址和API Key。App端GuideScreen和SettingsScreen支持扫码配置按钮，SetupQrParser工具类统一解析。兼容纯URL格式 |

### 快麦ERP API映射

| 功能 | API接口 | method名 | 说明 |
|---|---|---|---|
| 获取商品列表 | 查询商品列表 | `item.list.query` | 获取picPath、outerId、barcode等 |
| 获取SKU详情 | 查询商品SKU列表V2 | `erp.item.sku.list.get` | 获取skuRemark、skuPicPath等 |
| 获取供应商 | 查询商品关联供应商信息V2 | `erp.item.supplier.list.get` | 获取supplierName、supplierCode |
| 查询供应商列表 | 查询供应商列表 | `supplier.list.query` | 获取所有供应商编码和名称，用于选择供应商 |
| 修改规格备注 | 修改/新增商品V2 | `erp.item.general.addorupdate` | 传入skus[].remark回写 |
| 修改商品供应商关联 | 修改/新增商品V2 | `erp.item.general.addorupdate` | 传入id+suppliers[].code更换关联供应商，不直接修改supplier_name/code字段 |
| 刷新会话 | 刷新会话(必接) | `session.refresh` | 保持accessToken有效 |

## 技术选型

### 核心技术栈

| 层面 | 选型 | 理由 |
|---|---|---|
| 语言 | **Kotlin** | Android官方推荐，生态成熟 |
| 最低SDK | API 24 (Android 7.0) | 覆盖99%+ PDA设备 |
| UI框架 | **Jetpack Compose** | 声明式UI，开发效率高 |
| 架构 | MVVM + Repository | 关注点分离，可测试 |
| 网络库 | **Retrofit2 + OkHttp3** | 成熟稳定，拦截器支持签名 |
| 本地存储 | Room（离线缓存+操作队列）+ DataStore（配置） | Room作为后端数据的本地缓存和离线操作队列，支持断网操作；DataStore存配置 |
| 依赖注入 | Hilt | Google官方DI方案 |
| 图片加载 | Coil | Kotlin优先，Compose友好 |
| 图片上传 | OkHttp3 Multipart | 上传库区图/装箱图到服务器，上传前压缩 |
| 离线同步 | WorkManager | 网络恢复后自动同步本地操作队列到后端 |
| 摄像头扫码 | ML Kit Barcode Scanning | PDA硬件故障时的降级扫码方案 |
| 导航 | Compose Navigation | 单Activity架构 |

### 后端服务（完整后端，多PDA共享数据）

多台PDA使用同一套数据，需要后端服务承担**取货单+图片**的存储和同步。

| 方案 | 说明 | 推荐 |
|---|---|---|
| **Python FastAPI + SQLite** | 轻量、部署简单，NAS Docker运行，本项目唯一选择 | 确定 |

**后端职责**：
- 取货单 CRUD（创建、查询、更新状态）
- 待办行 CRUD（添加、完成/恢复）
- 12小时超时自动完成逻辑（后端定时任务）
- 图片上传 + 访问（库区图/装箱图严格区分）
- 多PDA数据同步（所有PDA访问同一后端）
- Web管理后台（/admin页面，用户管理+拣货区管理+快麦配置+系统配置+图片查看）
- 扫码配置页面（/setup页面，生成配置二维码供PDA扫码）
- 快麦凭证手动更新API（/api/kuaimai/update-credentials）

**数据流向**：
```
快麦ERP API ←→ 后端服务（代理+缓存） ←→ App（日常业务操作）
                                      ↑         ↑
                               商品信息查询   取货单/图片数据
                               （后端缓存24h）  多PDA共享

管理员浏览器 → /admin（系统管理：用户/拣货区/快麦凭证/服务器配置）
PDA首次配置 → /setup（扫码配置二维码）
```

### PDA扫码方案

**双模扫码**：PDA硬件扫描头（广播模式，主方案）+ 摄像头扫码（ML Kit，降级方案）

```
┌──────────────────────────────────┐
│        ScannerManager            │
├──────────────┬───────────────────┤
│ PDA硬件扫描头 │  摄像头扫码        │
│ (广播模式)    │ (ML Kit降级)      │
├──────────────┴───────────────────┤
│     统一回调 onBarcodeScanned()   │
├──────────────────────────────────┤
│     扫码反馈（振动+声音）          │
│     成功→短振动+提示音            │
│     失败→长振动+错误音            │
└──────────────────────────────────┘
```

**广播模式适配**：
- 封装 `PdaScannerManager`，根据 `Build.MANUFACTURER` 自动匹配广播Action和Key
- 支持用户自定义配置（DataStore存储）
- 300ms防抖处理
- Activity生命周期管理：onResume注册、onPause注销

**扫码反馈**：
- 扫码成功→短振动+提示音
- 扫码失败（商品未找到）→长振动+错误音
- 重复扫码检测→中等振动+提示"已存在"
- 设置页可开关声音和振动

**摄像头降级**：
- 设置页提供"摄像头扫码"开关
- 开启后底部显示扫码按钮，点击打开摄像头扫码界面
- 使用ML Kit Barcode Scanning解码

| 品牌 | 广播Action | 数据Key |
|---|---|---|
| iData | `com.android.server.scannerservice.broadcast` | `data` |
| Urovo | `com.android.server.scannerservice.broadcast` | `barcode_string` |
| 新大陆 | `com.android.server.scannerservice.broadcast` | `data` |
| 通用 | `com.scanner.broadcast` | `data` |

## 项目结构

```
com.kuaimai.pda/
├── App.kt                          # Application
├── MainActivity.kt                 # 单Activity
├── di/                             # 依赖注入
│   ├── NetworkModule.kt
│   ├── DatabaseModule.kt
│   └── RepositoryModule.kt
├── data/                           # 数据层
│   ├── api/                        # 快麦API
│   │   ├── KuaimaiApiService.kt
│   │   ├── UserApiService.kt       # 用户管理API
│   │   ├── AreaApiService.kt       # 拣货区API（含create/delete）
│   │   ├── ImageUploadService.kt   # 图片上传API
│   │   ├── KuaimaiInterceptor.kt   # 签名拦截器
│   │   └── dto/                    # 请求/响应DTO
│   │       ├── ItemListResponse.kt
│   │       ├── SkuListResponse.kt
│   │       ├── SupplierListResponse.kt
│   │       └── ItemUpdateRequest.kt
│   ├── db/                         # 本地数据库
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── PickOrderDao.kt     # 取货单DAO
│   │   │   └── PickItemDao.kt      # 取货商品行DAO
│   │   └── entity/
│   │       ├── PickOrderEntity.kt  # 取货单实体
│   │       └── PickItemEntity.kt   # 取货商品行实体
│   └── repository/                 # Repository
│       ├── ItemRepository.kt       # 商品信息
│       ├── PickOrderRepository.kt  # 取货单
│       ├── ImageRepository.kt      # 图片上传
│       ├── AuthRepository.kt       # 会话管理
│       └── UserRepository.kt       # 用户认证与权限管理
├── scanner/                        # 扫码模块
│   ├── ScannerManager.kt           # 统一扫码管理
│   ├── PdaScannerReceiver.kt       # 硬件扫描头广播接收
│   └── PdaDeviceConfig.kt          # 设备配置
├── ui/                             # UI层
│   ├── navigation/
│   │   └── AppNavigation.kt
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Alignment.kt          # 统一对齐常量，全局唯一对齐方式入口
│   ├── login/                      # 登录页
│   │   ├── LoginScreen.kt
│   │   └── LoginViewModel.kt
│   ├── guide/                      # 首次使用引导页
│   │   └── GuideScreen.kt
│   ├── home/                       # 主页（模块卡片入口，无底部导航）
│   │   └── HomeScreen.kt
│   ├── picklist/                   # 取货列表页
│   │   ├── PickListScreen.kt
│   │   └── PickListViewModel.kt
│   ├── pickdetail/                 # 取货单详情页（待办事项）
│   │   ├── PickDetailScreen.kt
│   │   └── PickDetailViewModel.kt
│   ├── product/                    # 商品详情页
│   │   ├── ProductScreen.kt
│   │   └── ProductViewModel.kt
│   ├── settings/                   # 个人设置页面（扫码方式、反馈开关、退出登录）
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── components/                 # 通用组件
│       ├── PickOrderCard.kt
│       ├── PickItemRow.kt
│       └── ImageUploadSection.kt
└── util/
    ├── TimeUtils.kt
    └── SignUtils.kt                # 快麦API签名工具
```

## 页面设计

### 主页：三个模块入口（非Tab导航）

```
┌─────────────────────────────┐
│                             │
│      快麦取货通              │  ← 居中显示
│                             │
│  ┌─────────────────────────┐│
│  │     📋 取货列表          ││  ← 点击进入取货列表页
│  └─────────────────────────┘│
│                             │
│  ┌─────────────────────────┐│
│  │     🔍 商品详情          ││  ← 点击进入商品详情页
│  └─────────────────────────┘│
│                             │
│  ┌─────────────────────────┐│
│  │     ⚙️ 设置             ││  ← 管理拣货区与系统配置
│  └─────────────────────────┘│
│                             │
└─────────────────────────────┘
```

### 取货列表页（仅显示未完成的取货单）

```
┌─────────────────────────────┐
│  ← 返回    取货列表   [+新建] │  ← 点击新建创建取货单
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
│ │ 📋 20260614-拣货区A          │ │  ← 取货单单号
│ │    拣货区A  3/5已完成        │ │  ← 显示拣货区 + 进度
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ 📋 20260614-拣货区B          │ │
│ │    拣货区B  0/2已完成        │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

> **注意**：已完成的取货单从列表中消失，不再显示。列表仅展示状态为"进行中"的取货单。

### 取货单详情页（点击取货单进入）

```
┌─────────────────────────────┐
│  ← 取货单 20260614-拣货区A     │
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
│ │ [扫码输入框___________] │ │  ← 默认光标在此，PDA扫码自动填入
│ └─────────────────────────┘ │
│ [全部] [深圳市源丰电子有限公司]  │  ← 供应商筛选，自动换行全量显示
│ [广州华强通讯科技]              │  ← 根据已添加商品自动生成
├─────────────────────────────┤
│ ┌─────────────────────────┐ │
 │ │ [规格图] 红色/XL        │ │  ← 规格图（点击放大），底部标注"规格图"
 │ │        供应商：备货① XX公司│  ← 红色加粗，备货①样式，可换行
 │ │         [🏭库区][📦装箱] │ │  ← 图片小方块+文字标注，与规格图一致
 │ │               [✓ 完成]  │ │
 │ └─────────────────────────┘ │
 │ ┌─────────────────────────┐ │
 │ │ [规格图] ~~蓝色/M~~     │ │  ← 已完成（灰色+删除线）
 │ │      供应商：备货② XX公司│  │
 │ │         [🏭库区][📦装箱] │ │
 │ │               [↩ 恢复]  │ │
 │ └─────────────────────────┘ │
│                             │
│  进度: 1/2  [全部完成]       │  ← 一键完成所有待办行
└─────────────────────────────┘
```

> **待办行显示内容**：规格图（方块图片+底部"规格图"文字标注）、规格名称、供应商名称（如"备货①"，红色加粗可换行）、库区图（底部标注"库区"）、箱图（底部标注"箱图"）
> **库区图/装箱图**：与规格图同款式的小方块图片，底部有文字标注，点击弹窗查看对应图片
> **扫码框行为**：扫码后自动添加待办行，输入框内容保持不消失，下次扫码直接替换为新编码
> **供应商筛选**：扫码输入框下方显示供应商筛选Chips，自动换行全量显示所有供应商。选项根据当前取货单已添加商品的供应商自动生成（去重），默认选中"全部"。选择某供应商后仅显示该供应商的商品行。
> **自动排序**：待办行按状态+添加时间自动排序。未完成的按添加时间倒序（最新添加的在最上方），已完成的按添加时间倒序排在未完成组下方。点击完成后该行移到已完成组，点击恢复后回到未完成组顶部。
> **下拉刷新**：支持下拉刷新，同步其他PDA的操作数据。
> **全部完成**：底部"全部完成"按钮一键将所有待办行标记完成。
> **重复扫码检测**：同一取货单内重复扫同一SKU时，中等振动+提示"已存在"并高亮滚动到该行。

### 商品详情页（按规格维度显示，扫码规格编码）

```
┌─────────────────────────────┐
│  ← 返回    商品详情          │
├─────────────────────────────┤
│  ┌──────────────────────┐   │
│  │ [扫码输入框________] │   │  ← 默认光标在此，扫描规格编码
│  └──────────────────────┘   │
│  ┌──────┐                   │
│  │ SKU图 │  规格名称:红色/XL │  ← 按规格维度显示
│  └──────┘  规格编码:SKU001   │
│            供应商: XX公司 ✏️  │  ← 点击可修改供应商（F18）
│            备注: [可编辑__]  │  ← 规格备注编辑+保存
│            [💾保存备注]      │
├─────────────────────────────┤
│  图片上传:                   │
│  ┌──────────┐ ┌──────────┐ │
│  │  库区图   │ │  装箱图   │ │  ← 2个上传位，后端严格区分
│  │  [📷上传] │ │  [📷上传] │ │
│  └──────────┘ └──────────┘ │
└─────────────────────────────┘
```

> **商品详情页按规格维度显示**：扫描的是规格编码（skuOuterId），展示的是单个SKU的信息
> **供应商关联可修改（F18）**：点击供应商名称弹出供应商选择列表，选择后弹窗二次确认（"确认将供应商修改为XXX？"），确认后回传快麦ERP更换关联供应商，成功后立即更新本地supplier_name/code字段+后端缓存失效。
> **备注修改（F4）**：编辑备注后点击保存，弹窗二次确认（"确认修改规格备注？"），确认后回写快麦ERP，成功后立即更新本地备注字段+后端缓存失效。
> **图片上传严格区分**：后端数据库中库区图(areaImage)和装箱图(boxImage)是独立字段，不可混淆
> **扫码框行为**：扫码后查询该SKU信息并替换当前页面内容（快速切换查看其他SKU，无需返回取货单详情页再扫码），输入框内容保持不消失，下次扫码直接替换为新编码

## UI设计规范

### 设计参考来源

| 来源 | 参考项 |
|---|---|
| **Material Design 3 （Google）** | 动态取色、卡片式布局、自适应间距 |
| **SAP Fiori** | "1-1-3"原则（1个任务/1个页面/3步内完成），仓库ERP场景UX |
| **Ant Design Mobile （阿里巴巴）** | 企业级信息密度、列表清晰度、操作反馈 |
| **GreaterWMS（开源仓库系统）** | PDA手持端简洁白底布局 |

### 色彩系统

```
主色    #2563EB（品牌蓝）— 标题、Tab选中态
强调色  #3B82F6（亮蓝）— 链接
成功    #16A34A（绿）— 已完成
警告    #EAB308（黄）— 超时、弱网
错误    #DC2626（红）— 失败、错误提示、供应商名称
背景    #FFFFFF（白）— 主背景
        #F3F4F6（浅灰）— 次要区域/分割

按钮语义色（浅底深字，保证高对比度）：
  #DBEAFE（浅蓝底） #1D4ED8（深蓝字） — 主操作按钮
  #DCFCE7（浅绿底） #15803D（深绿字） — 完成/成功按钮
  #FEE2E2（浅红底） #B91C1C（深红字） — 危险/删除按钮

文字    #111827（深灰）— 主文字
        #6B7280（中灰）— 次要文字
        #9CA3AF（浅灰）— 禁用/占位
```

### 字体系统

| 用途 | 字号 | 字重 | 颜色 |
|---|---|---|---|
| 页面标题 | 20sp | Bold | #111827 |
| 供应商名称 | 20sp | Bold | #DC2626（红色强调） |
| 商品/规格名称 | 18sp | Medium | #111827 |
| 单号/编码 | 16sp | Regular | #6B7280 |
| 按钮文字 | 18sp | Medium | #1D4ED8（深蓝） |
| 辅助信息 | 14sp | Regular | #6B7280 |
| 占位符 | 16sp | Regular | #9CA3AF |

### 间距系统

```
水平间距（屏幕两边）: 16dp
水平间距（组件之间）: 12dp
垂直间距（卡片之间）: 8dp
垂直间距（内部行之间）: 4dp
内边距（卡片内部）: 12dp
圆角（卡片）: 12dp
圆角（按钮）: 8dp
```

### 触摸目标

| 元素 | 最小尺寸 | 说明 |
|---|---|---|
| 按钮 | 56dp × 40dp（视觉） / 56dp × 56dp（触摸热区） | 含"完成""恢复""全部完成""保存"，视觉尺寸可更小但触摸热区≥56dp×56dp |
| 图片上传位 | 64dp × 64dp | 库区图/装箱图的上传和按钮 |
| 待办行 | 72dp 高 | 确保戴手套也能准确点击 |
| 筛选Chip | 40dp 高 | 供应商筛选标签 |
| 供应商名称 | 20sp+Bold+红色 | 视觉焦点，加大加粗加色 |

### 过渡与动效

| 场景 | 动效 | 时长 |
|---|---|---|
| 页面切换 | Fade过渡（无滑动，PDA性能考虑） | 200ms |
| 完成点击 | 行内打勾动画 → 移动到已完成组 | 300ms |
| 扫码成功 | 顶部Snackbar绿色提示 + 短振动 | 1500ms |
| 扫码失败 | 顶部Snackbar红色提示 + 长振动 | 2000ms |
| 网络切换 | 顶部薄条渐变（绿/黄/红） | 300ms |

### 页面状态规范

| 状态 | 表现 |
|---|---|
| **Loading** | 顶部LinearProgressIndicator（Material3标准） |
| **空状态** | 纯文字提示，无插画。如"暂无进行中的取货单，点击右上角+新建" |
| **错误** | 顶部Snackbar红色+文字说明，不阻断操作 |
| **网络离线** | 顶部薄条显示"已离线，操作将在网络恢复后同步" |
| **网络弱网** | 顶部薄条黄色"网络不稳定" |

### 对齐方式统一

所有页面统一使用 `Modifier.align() + Arrangement`，收敛到 `ui/theme/Alignment.kt`：

```kotlin
object AppAlignment {
    val RowBetween = Arrangement.SpaceBetween
    val RowCenter = Alignment.CenterVertically
    val ItemStart = Alignment.TopStart
    val ButtonEnd = Arrangement.End
    val ColumnCenter = Alignment.CenterHorizontally
}
```

## 前端UI原型规范（开发必须遵循）

> **权威来源**：[prototype/index.html](file:///d:/trea项目/快麦取货通/prototype/index.html) 是唯一UI设计参考，前端开发必须按照此原型的视觉规格实现。
> 原型包含4个可交互页面：主页、取货列表、取货单详情、商品详情。

### CSS变量 → Compose Color映射

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

### 组件规格映射（CSS → Compose）

#### 1. 模块卡片（主页）

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 布局 | `display:flex; gap:16px; padding:20px` | `Row(horizontalArrangement=spacedBy(16.dp), modifier=padding(20.dp))` |
| 圆角 | `border-radius:12px` | `RoundedCornerShape(12.dp)` |
| 阴影 | `box-shadow:0 1px 3px rgba(0,0,0,0.08)` | `elevation=2.dp` |
| 图标框 | `52×52px; border-radius:14px` | `Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))` |
| 卡片间距 | `gap:14px` | `verticalArrangement=spacedBy(14.dp)` |

#### 2. 取货单卡片（列表页）

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 布局 | `padding:12px 16px` | `Modifier.padding(horizontal=16.dp, vertical=12.dp)` |
| 圆角 | `border-radius:12px` | `Card(shape=RoundedCornerShape(12.dp))` |
| 单号字号 | `font-size:18px; font-weight:600` | `fontSize=18.sp, fontWeight=FontWeight.SemiBold` |
| 进度文字 | `font-size:14px; font-weight:500` | `fontSize=14.sp, fontWeight=FontWeight.Medium` |
| 状态标签 | `padding:2px 10px; border-radius:20px` | `Modifier.padding(horizontal=10.dp, vertical=2.dp).clip(RoundedCornerShape(20.dp))` |
| 进度点 | `10×10px; border-radius:50%` | `Modifier.size(10.dp).clip(CircleShape)` |

#### 3. 扫码输入框

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 边框 | `border:2px solid #2563EB` | `border=BorderStroke(2.dp, BrandBlue)` |
| 圆角 | `border-radius:8px` | `RoundedCornerShape(8.dp)` |
| 内边距 | `padding:12px 16px` | `Modifier.padding(horizontal=16.dp, vertical=12.dp)` |
| 字号 | `font-size:16px` | `fontSize=16.sp` |
| 聚焦效果 | `box-shadow:0 0 0 3px rgba(37,99,235,0.12)` | `focusIndicator=Box(border=2.dp, BrandBlue, shadow)` |

#### 4. 供应商筛选Chip

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 布局 | `flex-wrap:wrap; gap:8px` | `FlowRow(horizontalArrangement=spacedBy(8.dp), verticalArrangement=spacedBy(8.dp))` |
| 内边距 | `padding:6px 14px` | `Modifier.padding(horizontal=14.dp, vertical=6.dp)` |
| 圆角 | `border-radius:20px` | `RoundedCornerShape(20.dp)` |
| 字号 | `font-size:13px; font-weight:500` | `fontSize=13.sp, fontWeight=FontWeight.Medium` |
| 选中态 | `bg:#2563EB; color:#fff` | `background=BrandBlue, color=Color.White` |
| 未选中态 | `border:1px solid #E5E7EB; color:#6B7280` | `border=BorderStroke(1.dp, BorderGray), color=TextSecondary` |

#### 5. 待办行（取货单详情页）

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 行高 | `min-height:72px` | `Modifier.heightIn(min=72.dp)` |
| 内边距 | `padding:10px 12px` | `Modifier.padding(horizontal=12.dp, vertical=10.dp)` |
| 圆角 | `border-radius:12px` | `Card(shape=RoundedCornerShape(12.dp))` |
| 规格图 | `52×52px; border-radius:8px` | `Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))` |
| 规格图标注 | `position:absolute; bottom:0; bg:rgba(0,0,0,0.55); font-size:10px` | `Box(modifier=align(BottomCenter), background=Black.copy(alpha=0.55f), fontSize=10.sp)` |
| 规格名称 | `font-size:16px; font-weight:500` | `fontSize=16.sp, fontWeight=FontWeight.Medium` |
| 供应商名称 | `font-size:20px; font-weight:700; color:#DC2626` | `fontSize=20.sp, fontWeight=FontWeight.Bold, color=SupplierRed` |
| 库区图/箱图 | `40×40px; border-radius:6px` | `Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))` |
| 完成按钮 | `padding:4px 14px; border-radius:8px; min-width:56px` | `Modifier.padding(horizontal=14.dp, vertical=4.dp).heightIn(min=56.dp)` |
| 已完成态 | `opacity:0.55; text-decoration:line-through` | `alpha=0.55f, TextDecoration.LineThrough` |

#### 6. 商品详情页

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| SKU信息卡 | `padding:14px; gap:14px` | `Card + Row(horizontalArrangement=spacedBy(14.dp), padding=14.dp)` |
| SKU图 | `72×72px; border-radius:10px` | `Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))` |
| 规格名称 | `font-size:18px; font-weight:600` | `fontSize=18.sp, fontWeight=FontWeight.SemiBold` |
| 规格编码 | `font-size:14px; color:#6B7280` | `fontSize=14.sp, color=TextSecondary` |
| 供应商 | `font-size:20px; font-weight:700; color:#DC2626` | `fontSize=20.sp, fontWeight=FontWeight.Bold, color=SupplierRed` |
| 备注输入框 | `padding:10px 14px; border-radius:8px` | `OutlinedTextField(shape=RoundedCornerShape(8.dp))` |
| 保存按钮 | `padding:10px 20px; border-radius:8px` | `Button(shape=RoundedCornerShape(8.dp), padding=10.dp)` |
| 上传位 | `aspect-ratio:1; border:2px dashed; border-radius:12px; min-height:120px` | `Modifier.aspectRatio(1f).border(2.dp, BorderGray, RoundedCornerShape(12.dp)).heightIn(min=120.dp)` |
| 上传位网格 | `grid-template-columns:1fr 1fr; gap:12px` | `Row(horizontalArrangement=spacedBy(12.dp), modifier=fillMaxWidth()) + Modifier.weight(1f)` |

#### 7. 弹窗（图片预览/拣货区选择/设置）

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 遮罩 | `background:rgba(0,0,0,0.6)` | `scrimColor=Color.Black.copy(alpha=0.6f)` |
| 弹窗圆角 | `border-radius:16px` | `RoundedCornerShape(16.dp)` |
| 弹窗内边距 | `padding:20-24px` | `Modifier.padding(24.dp)` |
| 拣货区按钮 | `padding:12px 24px; border-radius:8px; min-width:100px` | `Modifier.padding(horizontal=24.dp, vertical=12.dp).widthIn(min=100.dp)` |
| 设置行 | `padding:8px 0; border-bottom:1px solid #E5E7EB` | `Modifier.padding(vertical=8.dp) + HorizontalDivider(color=BorderGray)` |

#### 8. Toast/Snackbar

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 位置 | `position:fixed; top:20px; left:50%` | `SnackbarHostState + 顶部对齐` |
| 圆角 | `border-radius:10px` | `RoundedCornerShape(10.dp)` |
| 字号 | `font-size:14px; font-weight:500` | `fontSize=14.sp, fontWeight=FontWeight.Medium` |
| 成功色 | `bg:#DCFCE7; color:#15803D` | `background=SuccessBg, color=SuccessText` |
| 错误色 | `bg:#FEE2E2; color:#B91C1C` | `background=DangerBg, color=DangerText` |
| 警告色 | `bg:#FEF9C3; color:#A16207` | `background=WarningBg, color=WarningText` |

#### 9. 网络状态条

| 属性 | 原型CSS | Compose实现 |
|------|---------|------------|
| 高度 | `height:3px` | `Modifier.height(3.dp)` |
| 在线色 | `background:#16A34A` | `Color(0xFF16A34A)` |
| 圆角 | `border-radius:2px` | `RoundedCornerShape(2.dp)` |

### 页面交互规格

| 交互 | 行为 | Compose实现 |
|------|------|------------|
| 页面切换 | Tab切换/点击卡片导航 | `NavController.navigate()` + Fade过渡(200ms) |
| 扫码自动聚焦 | 进入页面光标在扫码框 | `LaunchedEffect { focusRequester.requestFocus() }` |
| 扫码替换 | 新扫码内容替换旧内容 | `textFieldValue = scannedCode` |
| 完成动画 | 行变灰+移到底部 | `animateColorAsState` + `animateContentSize` |
| 供应商筛选 | 点击Chip过滤列表 | `filteredList = if(selected=="全部") all else all.filter{it.supplier==selected}` |
| 图片预览 | 点击弹窗放大 | `Dialog + Image(painter=rememberAsyncImagePainter(url))` |
| 新建取货单 | 弹窗选择拣货区→自动生成单号 | `AlertDialog + 动态拣货区列表` |
| 设置管理 | 增删拣货区 | `AlertDialog + LazyColumn + 添加/删除按钮` |

### 原型与开发对照表

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

## 核心流程

### 流程1：新建取货单

```
点击[+新建] → 弹出"选择拣货区"弹窗（列表来自设置页动态管理）→
  → 选择拣货区 → 自动生成单号（格式：yyyyMMdd-拣货区X，同一拣货区多单后缀（1）（2）...）→
  → 进入取货单详情页 → 扫码添加商品行
```

**单号生成规则**：
- 格式：`yyyyMMdd-拣货区X` 或 `yyyyMMdd-拣货区X（序号）`
- 示例：`20260614-拣货区A`、`20260614-拣货区B`、`20260614-拣货区A（1）`
- 新建取货单时弹出"选择拣货区"弹窗，弹窗中的拣货区列表来自**设置页管理的动态列表**（非硬编码）
- 同一拣货区当天第1单不加序号，第2单起加（1）（2）...递增

### 流程2：扫码添加待办行

```
进入取货单详情页 → 光标默认在扫码输入框 → 按PDA扫码键 →
  → 扫码结果填入输入框（替换之前内容）→ 本地查重（Room，同一取货单内skuOuterId是否已存在）→
    → 已存在：中等振动+高亮提示"已存在" → 结束
    → 不存在：调用后端API添加待办行（后端负责查询快麦SKU信息+创建待办行，1次网络请求）→
  → 显示在列表中 → 输入框内容保持不消失，等待下次扫码替换
```

> **性能优化**：App端只需1次网络请求（`POST /api/orders/{id}/items`），后端负责查询快麦API获取SKU信息并创建待办行。多PDA共享后端缓存，避免重复查询快麦API。App端本地先查重再请求，减少无效网络调用。

### 流程3：完成/恢复待办行

```
点击完成 → 该行标记为已完成 → UI变灰+删除线
  → 检查取货单内所有行是否都已完成 →
    → 是：取货单自动标记为已完成
    → 否：取货单保持进行中
再次点击恢复 → 该行恢复为待办 → UI恢复正常
  → 取货单恢复为进行中（如果之前是已完成状态）
```

### 流程4：12小时自动完成（后台逻辑，前端不显示）

```
取货单创建时记录 createdAt →
  → 后台定时检查（每分钟） →
  → 发现 createdAt + 12小时 < 当前时间 且 取货单未完成 →
  → 自动将取货单及所有未完成行标记为已完成（completionType=TIMEOUT）→
  → 前端刷新后显示为已完成状态（无倒计时提示）
```

### 流程5：商品详情（按规格维度，扫码快速切换）

```
商品详情页 → 光标默认在扫码输入框 → 扫描规格编码(skuOuterId) →
  → 扫码结果填入输入框（替换之前内容）→
  → 调用API查询SKU信息 → 替换当前页面内容（快速切换查看其他SKU，无需返回取货单详情页再扫码）→
  → 输入框内容保持不消失，等待下次扫码替换
```

### 流程6：规格备注修改

```
在商品详情页 → 编辑备注字段 → 点击保存 →
  → 弹出确认弹窗"确认修改规格备注？" →
    → 取消：关闭弹窗，不修改
    → 确认：调用 erp.item.general.addorupdate →
      → 传入 {id, skus: [{sysSkuId, remark}]} →
      → 成功后立即更新本地备注字段 + 后端缓存失效 →
      → 页面显示新备注
```

> **本质**：备注修改后回传快麦ERP，成功后立即更新本地备注字段（否则用户改完还看到旧值），同时使后端sku_cache中该SKU的缓存失效。二次确认防止PDA误触。

### 流程7：图片上传（严格区分库区图和装箱图）

```
商品详情页 → 点击库区图/装箱图上传位 →
  → 选择拍照或从相册选取 →
  → 上传到后端服务器（携带imageType参数：area/box）→
  → 服务器存储到本地磁盘，数据库记录image_type字段 →
  → 返回图片URL → App保存URL到本地数据库
```

### 流程8：查看库区图/装箱图（取货单详情页）

```
取货单详情页 → 点击待办行的[库区图]或[装箱图]按钮 →
  → 弹窗展示对应图片 → 点击关闭弹窗
```

### 流程9：修改商品供应商关联（F18）

```
商品详情页 → 点击供应商名称 →
  → 弹出供应商选择列表（调用supplier.list.query获取）→
  → 选择新供应商 →
  → 弹出确认弹窗"确认将供应商修改为XXX？" →
    → 取消：关闭弹窗，不修改
    → 确认：调用 erp.item.general.addorupdate →
      → 传入 {id, suppliers: [{code: 新供应商编码}]} →
      → 成功后立即更新本地supplier_name/code字段 + 后端缓存失效 →
      → 页面显示新供应商
```

> **本质**：F18修改的是商品与供应商的**关联关系**。回传快麦ERP成功后，立即更新本地supplier_name/code字段（否则用户改完还看到旧值），同时使后端sku_cache中该SKU的缓存失效。

### 流程10：用户登录（F36）

```
打开App → 检查本地是否有有效Token →
  → 有Token：验证Token有效性（调用/api/users/me）→
    → 有效：进入主页
    → 无效/过期：跳转登录页
  → 无Token：显示登录页
登录页 → 输入用户名+密码 → 调用/api/users/login →
  → 成功：保存Token+userId+permissions到EncryptedSharedPreferences → 进入主页
  → 失败：显示错误提示（5次失败锁定5分钟）
```

> **Token过期处理**：App端通过SharedFlow事件流监听token过期事件，收到事件后自动跳转登录页。ImageUploadService等原生OkHttp请求返回401时也触发跳转。

### 流程11：权限检查（F36）

```
用户操作需权限的功能 → 检查本地permissions列表 →
  → 有权限：允许操作
  → 无权限：提示"无权限执行此操作"
```

**权限与功能映射**：

| 权限代码 | 控制的功能 |
|---------|----------|
| settings | 设置页用户管理、拣货区增删 |
| update_supplier | 商品详情页修改供应商关联（F18） |
| update_remark | 商品详情页修改规格备注（F4） |
| manage_area_image | 上传/删除库区图 |
| manage_box_image | 上传/删除装箱图 |

> **前端控制仅为体验优化**：UI层根据权限显示/隐藏功能入口，后端同时做权限校验（`check_permission(perm)`），前端控制不作为安全屏障。

## 数据库设计

### PickOrderEntity（取货单）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| orderNo | String | 取货单单号，如20260614-拣货区A |
| status | Int | 0-进行中 1-已完成 |
| completionType | Int | 0-手动完成 1-超时自动完成 |
| totalCount | Int | 总商品行数 |
| completedCount | Int | 已完成商品行数 |
| createdAt | Long | 创建时间（北京时间） |
| completedAt | Long? | 完成时间 |
| expireAt | Long | 过期时间 = createdAt + 12小时 |

### PickItemEntity（取货商品行 — 按规格维度）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| orderId | Long | 关联取货单ID（外键） |
| skuOuterId | String | 规格编码（扫码获取的编码） |
| sysItemId | Long | 系统主商品ID |
| sysSkuId | Long | 系统SKU ID |
| propertiesName | String | 规格名称（如红色/XL） |
| picPath | String | SKU图片URL（来自快麦） |
| status | Int | 0-待办 1-已完成 |
| supplierName | String | 供应商名称 |
| supplierCode | String | 供应商编码（F18修改供应商时需传入此值） |
| remark | String | 规格备注（来自快麦ERP，默认空字符串，F4修改后回传快麦并立即更新本地） |
| createdAt | Long | 创建时间（北京时间） |
| completedAt | Long? | 完成时间 |

### ProductImageEntity（商品图片 — 与后端窄表一致，一行存一图）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (PK) | 自增主键 |
| skuOuterId | String | 规格编码（关联SKU，非主商品编码） |
| imageType | String | 图片类型：`area`（库区图）/ `box`（装箱图），严格区分 |
| imageUrl | String | 图片访问URL |
| createdAt | Long | 创建时间 |

> **说明**：App端Entity结构与后端 `product_images` 表保持一致（窄表方案），同一规格编码最多2条记录（一条area、一条box）。

### Room索引定义

高频查询字段必须添加索引，避免全表扫描：

```kotlin
@Entity(indices = [
    Index("orderId"),                    // 按取货单查待办行
    Index("skuOuterId"),                 // 按规格编码查重
    Index("orderId", "status")           // 按取货单+状态筛选
])
data class PickItemEntity(...)

@Entity(indices = [
    Index("skuOuterId"),                 // 按规格编码查图片
    Index("skuOuterId", "imageType"),    // 按规格+类型查唯一图片（唯一约束）
], uniqueIndices = [
    Index("skuOuterId", "imageType")     // UNIQUE约束，防止重复
])
data class ProductImageEntity(...)

@Entity(indices = [
    Index("orderId")                     // 按取货单查待同步操作
])
data class PendingOperationEntity(...)
```

## 快麦API签名实现

```kotlin
// 签名算法：HMAC-MD5
// 1. 所有业务参数按key字母升序排列
// 2. 拼接为 key1value1key2value2...
// 3. 在首尾加上appSecret
// 4. 对拼接字符串做HMAC-MD5（key为appSecret）
fun sign(params: Map<String, String>, appSecret: String): String {
    val sorted = params.toSortedMap()
    val sb = StringBuilder(appSecret)
    for ((k, v) in sorted) {
        sb.append(k).append(v)
    }
    sb.append(appSecret)
    return HmacUtils.hmacMd5Hex(appSecret, sb.toString())
}
```

## 后端服务设计（完整后端，多PDA共享数据）

### 后端数据库表设计

#### pick_orders（取货单）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| order_no | VARCHAR(30) | UNIQUE NOT NULL | 取货单单号，如20260614-拣货区A |
| status | INTEGER | NOT NULL DEFAULT 0 | 0-进行中 1-已完成，CHECK(status IN (0,1)) |
| completion_type | INTEGER | NOT NULL DEFAULT 0 | 0-手动完成 1-超时自动完成，CHECK(completion_type IN (0,1)) |
| total_count | INTEGER | NOT NULL DEFAULT 0 | 总商品行数，CHECK(total_count >= 0) |
| completed_count | INTEGER | NOT NULL DEFAULT 0 | 已完成商品行数，CHECK(completed_count >= 0) |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |
| completed_at | DATETIME | | 完成时间（status=1时NOT NULL） |
| expire_at | DATETIME | NOT NULL | 过期时间 = created_at + 12小时 |

#### pick_items（取货商品行）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| order_id | INTEGER | NOT NULL FK→pick_orders(id) ON DELETE CASCADE | 关联取货单ID（取货单删除时级联删除待办行） |
| sku_outer_id | VARCHAR(64) | NOT NULL | 规格编码 |
| sys_item_id | INTEGER | NOT NULL | 快麦系统主商品ID |
| sys_sku_id | INTEGER | NOT NULL | 快麦系统SKU ID |
| properties_name | VARCHAR(128) | NOT NULL DEFAULT '' | 规格名称（如红色/XL） |
| pic_path | VARCHAR(512) | NOT NULL DEFAULT '' | SKU图片URL（来自快麦） |
| status | INTEGER | NOT NULL DEFAULT 0 | 0-待办 1-已完成，CHECK(status IN (0,1)) |
| supplier_name | VARCHAR(128) | NOT NULL DEFAULT '' | 供应商名称 |
| supplier_code | VARCHAR(64) | NOT NULL DEFAULT '' | 供应商编码（F18修改供应商关联时需传入此值） |
| remark | VARCHAR(512) | DEFAULT '' | 规格备注（来自快麦ERP，F4修改后回传快麦并立即更新本地） |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |
| completed_at | DATETIME | | 完成时间（status=1时NOT NULL） |

> **约束说明**：`UNIQUE(order_id, sku_outer_id)` — 同一取货单内不允许重复SKU（防重复扫码，App端查重+后端约束双重保障）。`ON DELETE CASCADE` — 删除取货单时自动删除其所有待办行，避免孤立数据。

#### pick_areas（拣货区）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| name | VARCHAR(32) | UNIQUE NOT NULL | 拣货区名称，如"拣货区A" |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |

#### product_images（商品图片 — 严格区分库区图和装箱图）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| sku_outer_id | VARCHAR(64) | NOT NULL | 规格编码 |
| image_type | VARCHAR(10) | NOT NULL CHECK(image_type IN ('area','box')) | 图片类型：`area`（库区图）/ `box`（装箱图），严格区分 |
| image_url | VARCHAR(512) | NOT NULL | 图片访问URL |
| file_path | VARCHAR(512) | NOT NULL | 服务器本地存储路径 |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |

> **关键**：`image_type` 字段明确区分库区图和装箱图，同一规格编码最多2条记录（一条area、一条box），不可混淆。
> **约束**：`UNIQUE(sku_outer_id, image_type)` — 同一规格同一类型只能有一条记录，上传时使用 `INSERT OR REPLACE` 语义自动覆盖旧图，防止多PDA同时上传产生重复记录。
> **CHECK约束**：`image_type` 只允许 `area` 或 `box`，防止写入非法类型。

#### sku_cache（快麦API缓存）

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

#### crash_logs（崩溃日志）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| app_version | VARCHAR(32) | NOT NULL | App版本号 |
| device_model | VARCHAR(64) | NOT NULL | 设备型号 |
| error_message | TEXT | NOT NULL | 错误消息 |
| stack_trace | TEXT | NOT NULL | 完整堆栈信息 |
| created_at | DATETIME | NOT NULL | 上报时间（北京时间） |

> crash日志保留30天，每天凌晨4:00自动清理。

#### users（用户表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| username | VARCHAR(64) | UNIQUE NOT NULL | 用户名 |
| password_hash | VARCHAR(128) | NOT NULL | bcrypt密码哈希 |
| is_active | INTEGER | NOT NULL DEFAULT 1 | 是否启用：0-禁用 1-启用，CHECK(is_active IN (0,1)) |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |
| updated_at | DATETIME | NOT NULL | 更新时间（北京时间） |

> **默认管理员**：系统初始化时自动创建 `admin/admin123` 用户，分配全部5个权限。

#### user_permissions（用户权限表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| user_id | INTEGER | NOT NULL FK→users(id) ON DELETE CASCADE | 关联用户ID（用户删除时级联删除权限） |
| permission | VARCHAR(32) | NOT NULL | 权限代码 |

> **5种权限代码**：`settings`（设置管理）、`update_supplier`（修改供应商）、`update_remark`（修改备注）、`manage_area_image`（管理库区图）、`manage_box_image`（管理装箱图）
> **约束**：`UNIQUE(user_id, permission)` — 同一用户同一权限不重复

#### user_tokens（用户Token表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | 自增主键 |
| user_id | INTEGER | NOT NULL FK→users(id) ON DELETE CASCADE | 关联用户ID（用户删除时级联删除Token） |
| token | VARCHAR(64) | UNIQUE NOT NULL | UUID Token |
| expires_at | DATETIME | NOT NULL | 过期时间（7天有效期） |
| created_at | DATETIME | NOT NULL | 创建时间（北京时间） |

> **Token策略**：UUID Token，7天有效期。支持多设备同时登录（每个设备一个Token）。禁用用户时清理其所有Token。过期Token由定时任务每小时清理。

### 后端API设计

#### 取货单相关

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/orders` | 创建取货单（自动生成单号） |
| GET | `/api/orders` | 获取取货单列表（支持status筛选） |
| GET | `/api/orders/{id}` | 获取取货单详情（含待办行列表，支持supplierName筛选） |
| POST | `/api/orders/{id}/items` | 扫码添加待办行（后端查询快麦API获取SKU信息+创建待办行，App端1次请求完成） |
| PUT | `/api/orders/{id}/items/{itemId}/complete` | 完成待办行 |
| PUT | `/api/orders/{id}/items/{itemId}/restore` | 恢复待办行 |
| PUT | `/api/orders/{id}/complete-all` | 全部完成（批量SQL一次更新所有待办行，非逐条更新） |
| DELETE | `/api/orders/{id}` | 删除取货单（F19，仅未完成状态） |
| DELETE | `/api/orders/{id}/items/{itemId}` | 删除待办行（F20） |
| GET | `/api/orders/{id}/suppliers` | 获取取货单内的供应商列表（去重，用于筛选） |

#### 拣货区相关

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/areas` | 获取拣货区列表 |
| POST | `/api/areas` | 新增拣货区（传入name） |
| DELETE | `/api/areas/{id}` | 删除拣货区 |

> **说明**：拣货区数据存储在后端数据库，所有PDA共享同一份拣货区配置。App设置页的增删操作通过API同步到后端。

#### 用户管理相关

| 方法 | 路径 | 说明 | 认证要求 |
|---|---|---|---|
| POST | `/api/users/login` | 用户登录（返回token+userId+permissions） | 仅API Key |
| GET | `/api/users/me` | 获取当前用户信息 | User Token |
| GET | `/api/users` | 获取用户列表 | settings权限 |
| POST | `/api/users` | 创建用户 | settings权限 |
| PUT | `/api/users/{id}` | 更新用户（密码/权限/启用状态） | settings权限 |
| DELETE | `/api/users/{id}` | 删除用户（清理其Token） | settings权限 |
| POST | `/api/users/logout` | 退出登录（删除Token） | User Token |

> **自我保护规则**：禁止禁用自己（`isActive=false`）、禁止移除自己的`settings`权限、禁止删除自己。防止管理员误操作导致系统无法管理。
> **登录限流**：5次失败锁定5分钟（内存级字典，服务重启重置）。
> **密码安全**：使用bcrypt哈希存储，禁止明文。

#### 图片相关

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/upload` | 上传图片（携带skuOuterId + imageType） |
| GET | `/api/images/{skuOuterId}` | 获取某规格的库区图+装箱图URL |
| DELETE | `/api/images/{id}` | 删除图片（F22） |

#### 系统相关

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/crash-report` | 接收App端crash日志（ACRA） |
| GET | `/api/app-version` | 获取最新版本号和APK下载URL |
| GET | `/health` | 健康检查端点 |

#### 定时任务

| 任务 | 说明 |
|---|---|
| 12小时超时检查 | 每分钟检查expire_at < 当前时间的未完成取货单，自动标记完成 |
| 过期Token清理 | 每小时清理user_tokens中expires_at < 当前时间的记录 |

### 后端API安全认证（双层认证机制）

后端采用双层认证机制，API Key用于系统级访问控制，User Token用于用户级身份认证和权限控制：

**第一层：API Key认证（X-API-Key Header）**
- 所有请求必须携带 `X-API-Key` Header
- 由 `ApiKeyMiddleware` 中间件统一校验
- 免认证路径：`/images`、`/health`、`/docs`、`/redoc`、`/openapi.json`

**第二层：用户Token认证（X-User-Token Header）**
- 需要用户身份的接口必须携带 `X-User-Token` Header
- 由 `get_current_user()` 依赖注入校验Token有效性（查询user_tokens表）
- 需要特定权限的接口使用 `check_permission(perm)` 进一步校验
- 免用户认证：`/api/users/login`、`/api/images/{skuOuterId}`（GET图片）、`/health`、`/api/app-version`

**认证流程**：
```
请求 → ApiKeyMiddleware校验X-API-Key → 通过 → 路由处理函数
                                          ↓
                              get_current_user()校验X-User-Token
                                          ↓
                              check_permission(perm)校验具体权限
```

**App端实现**：
- `ApiKeyInterceptor`：OkHttp拦截器，自动添加 `X-API-Key` Header
- `UserRepository`：登录后获取Token，Retrofit接口通过 `@Header("X-User-Token")` 参数传递
- `ImageUploadService`：原生OkHttp请求需手动添加 `X-User-Token` Header（从EncryptedSharedPreferences读取）
- Token存储：使用 `EncryptedSharedPreferences` 加密存储（与API Key共用加密存储实例）

### Python FastAPI 实现（核心结构）

```python
# main.py
from fastapi import FastAPI, UploadFile, File, Form, Depends, Request, HTTPException
from fastapi.staticfiles import StaticFiles
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from datetime import datetime, timedelta, timezone
import uuid, os, sqlite3

app = FastAPI()
UPLOAD_DIR = "/data/product_images"
DB_PATH = "/data/kuaimai.db"
os.makedirs(UPLOAD_DIR, exist_ok=True)

BJ_TZ = timezone(timedelta(hours=8))

# 连接池：应用启动时初始化，避免每次请求新建连接
_db_conn: sqlite3.Connection | None = None

def init_db() -> sqlite3.Connection:
    """应用启动时调用，初始化数据库连接+PRAGMA（仅执行一次）"""
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")      # WAL模式只需设置一次
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=5000")      # 写锁等待5秒，避免立即SQLITE_BUSY
    # 建表...
    return conn

@app.on_event("startup")
def startup():
    global _db_conn
    _db_conn = init_db()

def get_db() -> sqlite3.Connection:
    """依赖注入：返回共享连接（FastAPI单进程模式下线程安全由WAL保证）"""
    return _db_conn

# --- 取货单API ---
@app.post("/api/orders")
async def create_order(): ...

@app.get("/api/orders")
async def list_orders(status: int = None): ...

@app.get("/api/orders/{order_id}")
async def get_order(order_id: int): ...

@app.post("/api/orders/{order_id}/items")
async def add_item(order_id: int, skuOuterId: str): ...

@app.put("/api/orders/{order_id}/items/{item_id}/complete")
async def complete_item(order_id: int, item_id: int): ...

@app.put("/api/orders/{order_id}/items/{item_id}/restore")
async def restore_item(order_id: int, item_id: int): ...

# --- 图片API ---
@app.post("/api/upload")
async def upload_image(
    file: UploadFile = File(...),
    skuOuterId: str = Form(...),
    imageType: str = Form(...)  # "area" 或 "box"
): ...

@app.get("/api/images/{sku_outer_id}")
async def get_images(sku_outer_id: str): ...

# --- 12小时超时定时任务 ---
async def check_expired_orders():
    now = datetime.now(BJ_TZ)
    conn = get_db()
    conn.execute(
        """UPDATE pick_orders SET status=1, completion_type=1, completed_at=?
           WHERE status=0 AND expire_at < ?""",
        (now.isoformat(), now.isoformat())
    )
    conn.execute(
        """UPDATE pick_items SET status=1, completed_at=?
           WHERE order_id IN (SELECT id FROM pick_orders WHERE completion_type=1)
           AND status=0""",
        (now.isoformat(),)
    )
    conn.commit()
    # 注意：使用连接池模式，不关闭conn

scheduler = AsyncIOScheduler()
scheduler.add_job(check_expired_orders, "interval", minutes=1)
scheduler.start()

app.mount("/images", StaticFiles(directory=UPLOAD_DIR), name="images")
```

```python
# auth.py — 双层认证核心
API_KEY = os.environ.get("API_KEY", "kuaimai-pda-secret-key")
VALID_PERMISSIONS = {"settings", "update_supplier", "update_remark", "manage_area_image", "manage_box_image"}
SKIP_AUTH_PREFIXES = ("/images", "/health", "/docs", "/redoc", "/openapi.json")

@app.middleware("http")
async def verify_api_key(request: Request, call_next):
    """第一层：API Key认证"""
    for prefix in SKIP_AUTH_PREFIXES:
        if request.url.path.startswith(prefix):
            return await call_next(request)
    api_key = request.headers.get("X-API-Key", "")
    if api_key != API_KEY:
        return JSONResponse(status_code=401, content={"detail": "Invalid API Key"})
    return await call_next(request)

async def get_current_user(request: Request) -> dict:
    """第二层：从X-User-Token Header解析当前用户"""
    token = request.headers.get("X-User-Token", "")
    if not token:
        raise HTTPException(status_code=401, detail="未提供认证Token")
    # 查询user_tokens表验证Token有效性
    conn = get_db()
    row = conn.execute(
        "SELECT ut.user_id, ut.expires_at FROM user_tokens ut WHERE ut.token = ?",
        (token,)
    ).fetchone()
    if not row:
        raise HTTPException(status_code=401, detail="无效的Token")
    expires_at = datetime.fromisoformat(row["expires_at"])
    if datetime.now(BJ_TZ) > expires_at:
        raise HTTPException(status_code=401, detail="Token已过期")
    # 获取用户信息和权限
    user = conn.execute("SELECT * FROM users WHERE id = ?", (row["user_id"],)).fetchone()
    if not user or not user["is_active"]:
        raise HTTPException(status_code=401, detail="用户已被禁用")
    perms = conn.execute(
        "SELECT permission FROM user_permissions WHERE user_id = ?",
        (row["user_id"],)
    ).fetchall()
    return {
        "user_id": user["id"],
        "username": user["username"],
        "permissions": [p["permission"] for p in perms]
    }

def check_permission(perm: str):
    """权限检查依赖注入工厂"""
    async def _check(user: dict = Depends(get_current_user)) -> dict:
        if perm not in user["permissions"]:
            raise HTTPException(status_code=403, detail="无权限执行此操作")
        return user
    return _check
```

### App端数据策略

| 数据 | 来源 | 本地缓存 | 说明 |
|---|---|---|---|
| 取货单/待办行 | 后端API | Room缓存+操作队列 | Room缓存但每次进入页面时从后端刷新（优先展示缓存+后台静默刷新），操作时先写后端再更新缓存；断网时写入本地操作队列，WorkManager网络恢复后自动同步 |
| 商品信息 | 后端API（后端代理快麦API，带缓存层） | Room缓存 | App只请求后端，后端查缓存或调快麦API；App端Room缓存24小时 |
| 商品主图/SKU图 | 快麦API | Coil自动缓存（非Room） | 商品主图和SKU图从快麦API获取，Coil自动做内存+磁盘缓存（无需手动Room缓存），仅展示使用 |
| 规格备注 | 快麦ERP（权威数据源） | Room缓存（存于pick_items.remark字段） | 修改后回传快麦ERP，成功后立即更新本地字段+后端缓存失效；快麦后台修改后下次扫码同步 |
| 库区图/装箱图 | 后端服务器 | 无需缓存 | 每次从后端获取URL，Coil加载 |

### 离线操作队列设计

**PendingOperationEntity（待同步操作）**：

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

### 图片上传策略

1. 拍照/选图后先压缩：最大宽度1024px，JPEG质量80%，约200KB
2. 显示上传进度条
3. 上传失败自动重试（最多3次）
4. 上传期间可继续其他操作（异步上传）

### Docker部署

```yaml
# docker-compose.yml
services:
  kuaimai-server:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        BUILD_VERSION: v1.0  # 与代码版本号同步
    container_name: kuaimai-pda-server
    restart: unless-stopped
    ports:
      - "8900:8000"
    env_file:
      - .env
    environment:
      - DOCKER_BUILDKIT=1
    volumes:
      - ./data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s
```

```dockerfile
# Dockerfile（多阶段构建）
FROM python:3.12-alpine AS base
WORKDIR /app

FROM base AS builder
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt

ARG BUILD_VERSION
COPY . .

FROM base AS runner
ENV TZ=Asia/Shanghai
RUN apk add --no-cache curl tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY --from=builder /app /app

EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 环境变量配置（.env）

在NAS上创建 `.env` 文件（由 `.env.docker.example` 复制并填入真实值）：

```bash
# .env.docker.example — 复制为 .env 后填入真实值

# 后端API认证密钥（PDA App请求时需携带X-API-Key Header）
API_KEY=your-api-key-here

# 快麦ERP开放平台
KUAIMAI_APP_KEY=your-app-key
KUAIMAI_APP_SECRET=your-app-secret
KUAIMAI_SESSION=your-access-token

# 服务器配置
SERVER_PORT=8900
```

> **安全要求**：`.env` 文件禁止提交到Git，`.dockerignore` 已忽略所有 `.env*` 文件。

### 网络部署方案

PDA App需连接后端API，根据NAS与PDA是否在同一网络，分为两种场景：

#### 场景一：同网络（NAS与PDA在同一局域网）

直接通过内网IP访问，无需额外配置：

| 配置项 | 示例值 | 说明 |
|--------|--------|------|
| BASE_URL | `http://192.168.1.100:8900` | NAS内网IP + 映射端口 |
| API_KEY | 与.env中API_KEY一致 | 请求认证 |

#### 场景二：跨网络（NAS与PDA不在同一局域网）— Tailscale组网

P2P组网方案要求**服务端（NAS）和客户端（PDA）都安装Tailscale客户端**，登录同一账号后自动组网，无需公网IP。

**Tailscale免费版限制**：

| 项目 | 免费版限制 | 本项目是否满足 |
|------|-----------|--------------|
| 用户数 | 最多6个用户 | 满足（1个账号即可） |
| 设备数 | **每个用户无限设备** | 满足（多台PDA都登录同一账号） |
| ACL分组 | 最多3组 | 满足（无需复杂分组） |
| 费用 | 永久免费 | 满足 |

> **多PDA方案**：所有PDA安装Tailscale Android App后登录**同一个Tailscale账号**即可。Tailscale免费版允许同一用户无限设备，多台PDA可以同时在线。每台PDA会获得独立的Tailscale IP（100.64.x.x段），但访问NAS时都使用NAS的Tailscale IP。

**Tailscale组网部署步骤**：

##### 第1步：注册Tailscale账号

1. 访问 https://tailscale.com 注册账号
2. 推荐使用**微软账号**登录（国内网络环境下Google/GitHub可能需要特殊方法）
3. 注册完成后进入管理页面 https://login.tailscale.com/admin/machines

##### 第2步：NAS端安装Tailscale（绿联NAS Docker部署）

绿联NAS通过Docker部署Tailscale容器：

1. **准备工作**：
   - 在NAS存储中创建目录：`/docker/tailscale/config`（持久化配置）
   - 登录Tailscale管理页面 → Settings → Keys → Generate auth key → 开启Reusable → 复制auth key

2. **Docker部署**：
   - 打开绿联云客户端 → Docker → 镜像仓库 → 搜索 `tailscale` → 下载官方镜像（latest）
   - 创建容器，配置如下：

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 镜像 | tailscale/tailscale:latest | 官方镜像 |
| 重启策略 | always | 自动重启 |
| 卷映射1 | /docker/tailscale/config → /var/lib | 持久化配置 |
| 卷映射2 | /dev/net/tun → /dev/net/tun | TUN设备（网络隧道） |
| 环境变量 | TS_AUTH_KEY=tskey-auth-xxxxx | 第1步获取的auth key |
| 特权模式 | 开启 | 需要访问TUN设备 |

3. **验证**：容器启动后，在Tailscale管理页面Machines中看到NAS设备及其Tailscale IP（如100.64.0.1）

4. **注意**：Tailscale容器与快麦取货通后端容器在同一NAS上，PDA通过Tailscale IP访问NAS的8900端口即可

##### 第3步：PDA端安装Tailscale

1. **下载APK**：iData PDA无Google Play，需从以下渠道获取Tailscale Android APK：
   - 官方GitHub Release：https://github.com/tailscale/tailscale-android/releases
   - F-Droid：https://f-droid.org/packages/com.tailscale.ipn/
   - APKPure：搜索"Tailscale"下载

2. **安装并登录**：
   - 安装APK后打开Tailscale App
   - 点击登录 → 选择与NAS端相同的账号方式（微软/Google/GitHub）
   - 登录成功后点击"Connect"加入网络

3. **验证**：登录后App左上角显示"Active"，表示已加入Tailscale网络

##### 第4步：配置App连接地址

App设置页配置服务器地址为NAS的Tailscale IP：

| 配置项 | 示例值 | 说明 |
|--------|--------|------|
| BASE_URL | `http://100.64.0.1:8900` | NAS的Tailscale IP + 映射端口 |
| API_KEY | 与.env中API_KEY一致 | 请求认证 |

> **Tailscale安全性**：基于WireGuard协议的端到端加密，即使使用HTTP传输，数据在Tailscale隧道内已加密，无需额外配置HTTPS。

**Tailscale网络拓扑**：

```
┌─────────────────────────────────────────────┐
│              Tailscale 虚拟网络               │
│                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │  NAS     │  │  PDA-1   │  │  PDA-2   │ │
│  │100.64.0.1│  │100.64.0.2│  │100.64.0.3│ │
│  │ :8900    │  │          │  │          │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘ │
│       │              │              │       │
│       └──────────────┴──────────────┘       │
│          P2P直连（WireGuard加密）            │
└─────────────────────────────────────────────┘
```

> **关键**：所有PDA登录同一Tailscale账号，每台PDA获得独立Tailscale IP，但访问后端时统一使用NAS的Tailscale IP（100.64.0.1:8900）。Tailscale自动选择最优路径，同局域网内走局域网直连，跨网络走DERP中继或P2P打洞。

#### App设置页配置

App设置页提供"服务器地址"配置项，支持两种格式：
- 内网IP：`192.168.x.x:8900`（同网络场景）
- Tailscale IP：`100.64.x.x:8900`（跨网络场景）

首次使用时输入服务器地址，DataStore持久化存储。App启动时自动检测连通性，无法连接时提示检查网络或服务器地址。

### 数据备份方案

| 数据 | 位置 | 备份方式 |
|------|------|---------|
| SQLite数据库 | `./data/kuaimai.db` | NAS定时任务每日备份到其他目录 |
| 商品图片 | `./data/product_images/` | NAS快照/RAID保护 |
| .env配置 | `./.env` | 手动备份，勿提交Git |

> **SQLite WAL模式**：启用WAL模式支持并发读写，容器重启后 `-wal` 和 `-shm` 文件自动清理。Docker volume挂载整个 `./data` 目录（非单个文件），确保附属文件可正常写入。

## API契约一致性方案（Contract-First）

### 问题

前后端分别开发时，容易出现函数名（`create_order` vs `createOrder`）、字段名（`sku_outer_id` vs `skuOuterId`）、类型不一致的问题，导致联调时反复修改。

### 方案：OpenAPI 契约优先

核心思路：**先写API规范文件（唯一真实来源），再从规范自动生成前后端代码**，彻底杜绝命名不一致。

### 工作流

```
                 api.yaml（唯一契约）
                /                 \
   openapi-generator              openapi-generator
        ↓                              ↓
   Python FastAPI 代码              Android Retrofit 代码
   （Server Stub）                   （Client SDK）
   模型类自动生成                    数据类自动生成
   路由函数名自动生成                 Retrofit接口自动生成
```

### 工具链

| 工具 | 用途 | 仓库 |
|---|---|---|
| **openapi-generator** | 从OpenAPI规范生成后端/前端代码 | https://github.com/OpenAPITools/openapi-generator |
| **swagger-editor** | 可视化编辑OpenAPI规范 | https://editor.swagger.io |
| **pydantic** | Python模型层（openapi-generator自动生成） | https://github.com/pydantic/pydantic |
| **Retrofit** | Android网络层（openapi-generator生成接口代码） | https://github.com/square/retrofit |

### 规范文件示例（api.yaml）

```yaml
openapi: 3.0.0
info:
  title: 快麦取货通 API
  version: 1.0.0
paths:
  /api/orders:
    post:
      operationId: createOrder
      description: 创建取货单（选择拣货区后自动生成单号）
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                area:
                  type: string
                  description: 拣货区名称
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OrderResponse'

  /api/orders/{orderId}/items:
    post:
      operationId: addItem
      description: 扫码添加待办行
      parameters:
        - name: orderId
          in: path
          required: true
          schema: { type: integer }
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AddItemRequest'
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ItemResponse'

  /api/orders/{orderId}/items/{itemId}/complete:
    put:
      operationId: completeItem
      description: 完成单个待办行
      parameters:
        - name: orderId
          in: path
          schema: { type: integer }
        - name: itemId
          in: path
          schema: { type: integer }
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BaseResponse'

  /api/orders/{orderId}/items/{itemId}/restore:
    put:
      operationId: restoreItem
      description: 恢复单个待办行
      ...

  /api/orders/{orderId}/complete-all:
    put:
      operationId: completeAll
      description: 一键完成所有待办行
      ...

  /api/orders/{orderId}/suppliers:
    get:
      operationId: listSuppliers
      description: 获取取货单内的供应商列表（去重）
      ...

  /api/upload:
    post:
      operationId: uploadImage
      description: 上传库区图或箱图
      requestBody:
        content:
          multipart/form-data:
            schema:
              type: object
              properties:
                file: { type: string, format: binary }
                skuOuterId: { type: string }
                imageType: { type: string, enum: [area, box] }
      ...

components:
  schemas:
    OrderResponse:
      type: object
      properties:
        id: { type: integer }
        orderNo: { type: string }
        status: { type: integer }
        totalCount: { type: integer }
        completedCount: { type: integer }
        createdAt: { type: string, format: date-time }

    AddItemRequest:
      type: object
      required: [skuOuterId]
      properties:
        skuOuterId:
          type: string
          description: 规格编码（通过PDA扫码获取）

    ItemResponse:
      type: object
      properties:
        id: { type: integer }
        skuOuterId: { type: string }
        propertiesName: { type: string }
        supplierName: { type: string }
        status: { type: integer }

    BaseResponse:
      type: object
      properties:
        success: { type: boolean }
        message: { type: string }
```

### 生成命令

```bash
# 生成 Python FastAPI 后端代码
openapi-generator generate \
  -i api.yaml \
  -g python-fastapi \
  -o backend/ \
  --additional-properties=packageName=kuaimai_server

# 生成 Android Kotlin 客户端代码
openapi-generator generate \
  -i api.yaml \
  -g kotlin \
  -o android-client/ \
  --additional-properties=library=jvm-retrofit2,packageName=com.kuaimai.pda.api
```

### 生成结果对比（无工具 vs 有工具）

| 对比项 | 无工具（手动维护） | 有工具（Contract-First） |
|---|---|---|
| 后端函数名 | `def add_pick_item()` | `def addItem()` ← 与规范一致 |
| 前端方法名 | `apiService.addPickItem()` | `apiService.addItem()` ← 自动生成 |
| 字段名 | `sku_outer_id` vs `skuOuterId` | 统一 `skuOuterId` ← 规范定义 |
| 类型 | `int` vs `String` 不匹配 | 自动匹配 ← 规范定义 |
| 修改流程 | 手动改两端，容易遗漏 | 改规范→重新生成两端→自动同步 |
| 联调问题 | 必现10+次字段不一致 | 零次 |

### 对快麦API的处理

快麦ERP的外部API无法使用OpenAPI方案。策略不变：App内通过 **DTO映射层** 隔离快麦API的字段名，映射文件收敛到 `data/api/dto/mapper/` 目录下，快麦字段变化时只改映射文件。

## 开源参考项目

| 项目 | 地址 | 参考价值 |
|---|---|---|
| ZXing | https://github.com/zxing/zxing | 条码解码核心库（备用参考） |
| Odoo | https://github.com/odoo/odoo | ERP+扫码架构参考 |
| FastAPI | https://github.com/fastapi/fastapi | 图片上传后端框架 |
| Coil | https://github.com/coil-kt/coil | Compose图片加载 |

## 开发阶段

### Phase 1：项目搭建（基础骨架）
- 创建Kotlin + Compose项目
- 配置Hilt、Retrofit、Room
- 实现快麦API签名拦截器
- 实现登录/会话刷新
- 搭建FastAPI图片上传服务

### Phase 2：扫码模块
- 实现 `PdaScannerManager`（广播模式）
- 实现设备自动识别 + 手动配置
- 统一扫码回调接口
- 防抖处理与生命周期管理

### Phase 3：取货单功能
- Room数据库设计（PickOrderEntity + PickItemEntity）
- 取货单列表页（新建、展示未完成列表）
- 取货单详情页（扫码添加商品行、完成/恢复）
- 取货单自动完成逻辑（全部完成 + 12小时超时）
- 单号自动生成（日期-拣货区格式）

### Phase 4：商品详情与备注修改
- 商品信息查询API集成
- SKU列表展示（含图片）
- 规格备注编辑与同步
- 图片上传功能（库区图 + 装箱图）

### Phase 5：优化与发布
- 离线缓存
- 错误处理与重试
- PDA设备适配测试
- APK打包 + Docker部署图片服务

## 关键依赖

```gradle
// Compose
implementation("androidx.compose.ui:ui:1.7.0")
implementation("androidx.compose.material3:material3:1.3.0")
implementation("androidx.activity:activity-compose:1.9.0")
implementation("androidx.navigation:navigation-compose:2.8.0")

// 网络
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// 本地存储
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
implementation("androidx.datastore:datastore-preferences:1.1.0")

// 加密存储（F28）
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// DI
implementation("com.google.dagger:hilt-android:2.51.1")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// 图片
implementation("io.coil-kt:coil-compose:2.7.0")

// 离线同步
implementation("androidx.work:work-runtime-ktx:2.9.1")

// 摄像头扫码降级
implementation("com.google.mlkit:barcode-scanning:17.3.0")
implementation("androidx.camera:camera-core:1.3.4")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")

// 签名
implementation("commons-codec:commons-codec:1.17.0")
```

## 假设与决策

1. **主页设计**：不使用底部导航Tab，主页直接显示三个模块入口卡片（取货列表 + 商品详情 + 设置），点击进入对应页面。
2. **取货单概念**：取货单是核心实体，包含多条商品行。单号格式为`yyyyMMdd-拣货区X`（如`20260614-拣货区A`），同一拣货区当天第2单起添加`（1）` `（2）`后缀。新建时弹出区域选择弹窗。
3. **取货单自动完成**：取货单内所有商品行都完成后，取货单自动标记完成；12小时未全部完成则超时自动完成。**12小时超时是后台逻辑，前端不显示倒计时。**
4. **按规格维度**：取货单详情页和商品详情页都按规格（SKU）维度展示，扫码扫描的是规格编码（skuOuterId），不是主商品编码。
5. **待办行显示内容**：规格图片（可点击放大弹窗查看）、规格名称、供应商名称（加粗/大字/高亮着重显示）、库区图按钮、装箱图按钮。不显示规格编码。
6. **图片存储严格区分**：库区图和装箱图在后端数据库中通过`image_type`字段（area/box）严格区分，不可混淆。上传时携带imageType参数。
7. **商品图片**：商品主图和SKU图从快麦API获取，Coil自动做内存+磁盘缓存（无需手动Room缓存），仅展示使用。
8. **多PDA共享**：取货单、待办行、图片数据存储在后端服务器，所有PDA访问同一后端，数据实时同步。App端Room仅作为离线缓存。
9. **12小时超时由后端执行**：后端定时任务（每分钟）检查超时取货单并自动标记完成，App端无需本地定时器。
10. **规格备注修改**：每次只修改单个SKU的备注，调用 `erp.item.general.addorupdate` 接口。备注权威数据源为快麦API，修改后回传快麦ERP成功则立即更新本地字段+缓存失效。保存前弹窗二次确认，防止PDA误触。
11. **扫码框行为**：进入有扫码框的页面时，光标默认在扫码框内可直接扫描；扫码后自动添加/查询，输入框内容不消失，下次扫码直接替换为新编码。
12. **会话管理**：accessToken有效期30天，需定期调用刷新会话接口。过期需联系快麦重新授权。
13. **供应商关联修改**：商品详情页点击供应商名称可修改商品与供应商的关联关系（F18），通过`supplier.list.query`获取列表，`erp.item.general.addorupdate`回写。supplier_name/code权威数据源为快麦API，修改关联关系回传快麦ERP成功后立即更新本地字段+缓存失效。选择后弹窗二次确认，防止PDA误触。供应商主数据（编码/名称等）的创建和修改仍需在快麦ERP后台操作。
14. **PDA设备**：PDA硬件扫描头（广播模式）为主方案，摄像头扫码（ML Kit）为降级方案。设置页可开启摄像头扫码。
15. **统一对齐方式**：全系统所有页面的对齐方式统一使用 **Modifier.align() + Arrangement** 方式，禁止混用 padding偏移模拟对齐、Row/Column的horizontalGravity等不同方式。所有对齐逻辑收敛到 `ui/theme/Alignment.kt` 中定义统一的对齐常量，页面中只引用常量不直接写对齐值。
16. **离线优先**：断网时操作写入本地队列，乐观更新UI，网络恢复后WorkManager自动同步，冲突后端优先。
17. **扫码反馈**：扫码成功/失败/重复均有振动+声音反馈，设置页可开关。
18. **图片压缩**：上传前压缩到1024px宽/80%质量，显示进度条，异步上传不阻塞操作。
19. **后端安全**：所有API请求需携带X-API-Key，防止未授权访问。
20. **屏幕常亮**：取货单详情页和商品详情页保持屏幕常亮，退出恢复。
21. **触摸优化**：所有可点击元素最小触摸热区56dp×56dp（视觉尺寸可以更小如按钮56dp×40dp，但触摸热区必须≥56dp×56dp），供应商等关键信息字体20sp+。
22. **会话预警**：accessToken过期前3-5天提醒刷新，而非过期后才提示。
23. **双层认证机制**：后端API采用API Key + User Token双层认证。API Key用于系统级访问控制（防止未授权系统调用），User Token用于用户级身份认证和权限控制。两层认证独立，缺一不可。
24. **权限粒度**：5种权限代码覆盖核心功能，不采用RBAC角色模型（系统用户少，直接分配权限更灵活）。默认管理员（admin/admin123）拥有全部5个权限。
25. **Token策略**：UUID Token，7天有效期，存储在SQLite的user_tokens表中。支持多设备同时登录（每个设备一个Token）。禁用用户时清理其所有Token。
26. **登录安全**：5次失败锁定5分钟（内存级字典，服务重启重置）。密码使用bcrypt哈希存储。
27. **权限前端控制**：App端登录后获取权限列表缓存在EncryptedSharedPreferences中，UI层根据权限显示/隐藏功能入口。后端同时做权限校验，前端控制仅为体验优化，不作为安全屏障。
28. **敏感信息加密存储**：用户Token、userId、permissions等敏感信息使用EncryptedSharedPreferences加密存储（与API Key共用加密存储实例），防止PDA丢失后数据泄露。

## 技术保障

### 数据库迁移（Room Migration）

App升级时Room数据库结构可能变化，必须提供迁移策略：

- 每次数据库schema变更时，编写 `Migration` 对象（如 `MIGRATION_1_2`）
- 禁止使用 `fallbackToDestructiveMigration()`，避免用户数据丢失
- 迁移逻辑在 `DatabaseModule.kt` 中统一管理
- 发布前在测试设备上验证迁移路径（1→2→3...）

### 并发冲突处理

多台PDA同时操作同一取货单时，可能出现冲突：

| 场景 | 冲突类型 | 处理策略 |
|------|---------|---------|
| 两个PDA同时完成同一待办行 | 幂等操作 | 后端忽略重复完成，返回成功 |
| 一个PDA完成行，另一个PDA删除同一行 | 操作冲突 | 后端先到先得，后到的操作返回409 Conflict，App端刷新数据 |
| 一个PDA删除取货单，另一个PDA在操作 | 数据不存在 | App端检测到404，提示"取货单已被删除"并返回列表 |

> **核心原则**：后端数据为准，App端冲突时重新拉取最新数据。

### 快麦API限流与缓存

快麦ERP开放平台API存在调用频率限制，需本地缓存减少调用：

| 数据 | 缓存策略 | 过期时间 |
|------|---------|---------|
| SKU信息（名称/图片/供应商） | Room缓存，按skuOuterId索引 | 24小时 |
| 供应商列表 | Room缓存 | 24小时 |
| 取货单/待办行 | Room缓存，每次进入页面时从后端刷新（优先展示缓存+后台静默刷新） | 无 |
| accessToken | DataStore存储 | 30天 |

> **缓存失效**：用户下拉刷新时清除对应缓存，强制重新请求。

#### 后端快麦API缓存层

后端维护SKU信息缓存表，App请求添加待办行时后端先查缓存再调快麦API，多PDA共享缓存：

```python
# 后端缓存逻辑（add_item接口内）
def get_sku_info(sku_outer_id: str) -> dict:
    # 1. 先查后端缓存表 sku_cache
    cached = conn.execute(
        "SELECT * FROM sku_cache WHERE sku_outer_id=? AND cached_at > ?",
        (sku_outer_id, (datetime.now(BJ_TZ) - timedelta(hours=24)).isoformat())
    ).fetchone()
    if cached:
        return dict(cached)  # 缓存命中，直接返回

    # 2. 缓存未命中，调用快麦API
    sku_data = call_kuaimai_api(sku_outer_id)

    # 3. 写入缓存表（INSERT OR REPLACE）
    conn.execute(
        "INSERT OR REPLACE INTO sku_cache (sku_outer_id, properties_name, pic_path, "
        "supplier_name, supplier_code, remark, sys_item_id, sys_sku_id, cached_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (sku_outer_id, sku_data['propertiesName'], sku_data['picPath'],
         sku_data['supplierName'], sku_data['supplierCode'], sku_data.get('skuRemark', ''),
         sku_data['sysItemId'], sku_data['sysSkuId'], datetime.now(BJ_TZ).isoformat())
    )
    conn.commit()
    return sku_data
```

**缓存表 sku_cache**：

| 字段 | 类型 | 说明 |
|------|------|------|
| sku_outer_id | VARCHAR(64) PK | 规格编码 |
| properties_name | VARCHAR(128) | 规格名称 |
| pic_path | VARCHAR(512) | SKU图片URL |
| supplier_name | VARCHAR(128) | 供应商名称 |
| supplier_code | VARCHAR(64) | 供应商编码 |
| sys_item_id | INTEGER | 快麦主商品ID |
| sys_sku_id | INTEGER | 快麦SKU ID |
| cached_at | DATETIME | 缓存时间（24小时过期） |

> **优势**：PDA-A扫码查询的SKU信息，PDA-B扫码同一SKU时直接命中缓存，无需再次调用快麦API。

#### 缓存失效机制

F18修改供应商关联或F4修改备注后，必须使后端sku_cache中对应SKU的缓存失效，确保下次扫码获取最新数据：

```python
def invalidate_sku_cache(sku_outer_id: str):
    """F18/F4修改后删除对应SKU的缓存，下次扫码时重新从快麦API获取"""
    conn.execute(
        "DELETE FROM sku_cache WHERE sku_outer_id = ?",
        (sku_outer_id,)
    )
    conn.commit()
```

> **失效策略**：直接删除缓存记录（而非更新cached_at），确保下次扫码时必定重新查询快麦API获取最新数据。

### 错误上报

生产环境需收集crash和ANR信息：

- 集成 **ACRA**（Application Crash Reports for Android），crash日志发送到后端 `/api/crash-report` 接口
- 后端存储crash日志到SQLite，定期清理30天前的记录
- ANR检测：主线程阻塞5秒以上记录到本地日志
- 禁止使用第三方SaaS（Bugly/Crashlytics），数据必须留在自己的服务器

### App版本更新

PDA上分发新版本APK的方式：

- 后端提供 `/api/app-version` 接口，返回最新版本号和APK下载URL
- App启动时检查版本，有新版本时弹窗提示更新
- 下载APK到本地后调用系统安装器（需REQUEST_INSTALL_PACKAGES权限）
- APK文件存储在后端 `/data/apk/` 目录，通过静态文件接口分发

### 性能优化

#### OkHttp连接池与限流

```kotlin
// NetworkModule.kt
@Provides
@Singleton
fun provideOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))  // 5个连接，30秒keep-alive
        .addInterceptor(ApiKeyInterceptor())        // 统一添加X-API-Key
        .addInterceptor(RateLimitInterceptor())      // 令牌桶限流，防止快麦API超频
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

// 令牌桶限流拦截器（快麦API调用频率控制）
class RateLimitInterceptor : Interceptor {
    private val limiter = RateLimiter.create(5)  // 每秒最多5次请求
    override fun intercept(chain: Interceptor.Chain): Response {
        limiter.acquire()
        return chain.proceed(chain.request())
    }
}
```

#### 离线同步按取货单分组并行

不同取货单的离线操作互不依赖，可并行同步；同一取货单内按顺序串行：

```kotlin
// OrderSyncWorker.kt
suspend fun syncPendingOperations() {
    val pendingOps = pendingDao.getAllPending()
    // 按orderId分组，不同组并行，同组内串行
    pendingOps.groupBy { it.orderId }
        .map { group -> coroutineScope { async { syncGroup(group.value) } } }
        .awaitAll()
}
```

#### 下拉刷新并行请求

取货单详情页刷新时，并行请求多个独立接口：

```kotlin
// PickDetailViewModel.kt
fun refresh() {
    viewModelScope.launch {
        coroutineScope {
            val orderDeferred = async { api.getOrder(orderId) }
            val suppliersDeferred = async { api.getOrderSuppliers(orderId) }
            val imagesDeferred = async { api.getImages(skuOuterId) }
            // 并行请求，总耗时 = max(各请求耗时) 而非 sum
            val order = orderDeferred.await()
            val suppliers = suppliersDeferred.await()
            val images = imagesDeferred.await()
            // 更新UI...
        }
    }
}
```

#### Room批量写入事务

```kotlin
// PickItemDao.kt
@Transaction
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAll(items: List<PickItemEntity>)

@Transaction
suspend fun syncOrderWithItems(order: PickOrderEntity, items: List<PickItemEntity>) {
    insertOrder(order)
    insertAll(items)  // 一次性批量写入，避免逐条插入
}
```

#### SQLite并发写入说明

SQLite WAL模式允许读写并行，但**写写仍互斥**。当前3-5台PDA场景下WAL模式足够（实测TPS约50-100）。如果未来超过10台PDA同时操作，需考虑升级PostgreSQL。后端已配置 `PRAGMA busy_timeout=5000`，写锁冲突时等待5秒而非立即返回SQLITE_BUSY错误。

### 数据库排序规范

排序不当会导致读写不一致（如App端显示顺序与后端不同步），所有涉及排序的查询必须明确ORDER BY：

| 场景 | 排序规则 | SQL |
|------|---------|-----|
| 取货单列表 | 创建时间倒序（最新在上） | `ORDER BY created_at DESC` |
| 待办行（未完成组） | 添加时间倒序（最新在上） | `ORDER BY created_at DESC` |
| 待办行（已完成组） | 完成时间倒序 | `ORDER BY completed_at DESC` |
| 待办行（完整排序） | 未完成在前+已完成在后，各自按时间倒序 | `ORDER BY status ASC, CASE WHEN status=0 THEN created_at ELSE completed_at END DESC` |
| 拣货区列表 | 创建时间正序（先添加的在前） | `ORDER BY created_at ASC` |
| 离线操作队列 | 创建时间正序（先入先同步） | `ORDER BY created_at ASC` |

> **关键**：App端Room查询和后端API查询必须使用**完全相同的排序规则**，否则下拉刷新后列表顺序跳变，用户体验差。排序逻辑收敛到各自DAO层，禁止在ViewModel/Service层二次排序。

### 快麦ERP字段同步策略

数据库中存储了来自快麦ERP的字段，需明确这些字段的同步策略：

| 字段 | 来源 | 本地是否可修改 | 快麦ERP更新后是否同步 | 同步方式 |
|------|------|--------------|---------------------|---------|
| properties_name（规格名称） | 快麦API | 否 | 是（下次扫码时通过后端缓存更新） | 被动：扫码时后端查缓存，缓存过期则重新调快麦API |
| pic_path（SKU图片URL） | 快麦API | 否 | 是（同上） | 被动：同上 |
| supplier_name（供应商名称） | 快麦API | 通过F18修改关联关系后同步更新 | 是（下次扫码时获取最新值） | F18：回传快麦ERP成功后立即更新本地字段+缓存失效；快麦后台修改后下次扫码同步 |
| supplier_code（供应商编码） | 快麦API | 通过F18修改关联关系后同步更新 | 是（同上） | 同上 |
| skuRemark（规格备注） | 快麦ERP | 通过F4修改后同步更新 | 是（下次扫码时获取最新值） | F4：回传快麦ERP成功后立即更新本地字段+缓存失效；快麦后台修改后下次扫码同步 |

> **核心原则**：
> - **权威数据源为快麦API**：supplier_name/code、skuRemark等字段的权威数据来自快麦ERP，App端不能绕过快麦ERP直接修改本地字段
> - **F18供应商关联修改**：修改商品与供应商的关联关系，回传快麦ERP成功后**立即更新本地supplier_name/code字段**（否则用户改完还看到旧值），同时使后端缓存失效
> - **F4备注修改**：修改备注后回传快麦ERP，成功后**立即更新本地备注字段**，同时使后端缓存失效
> - **快麦ERP后台修改了供应商/备注**：本系统下次扫码该SKU时，后端缓存失效后拉取最新值更新pick_items表
> - **已完成的取货单中的字段不会更新**：已完成取货单是历史快照，不随快麦ERP数据变化而变化

### 数据清理策略

长期运行后数据库会积累大量已完成取货单和过期缓存，需定期清理：

| 数据 | 保留策略 | 清理方式 | 清理时机 |
|------|---------|---------|---------|
| 已完成取货单+待办行 | 保留30天 | 后端定时任务删除30天前已完成的取货单（CASCADE自动删待办行） | 每天凌晨3:00 |
| sku_cache缓存 | 保留24小时 | 后端定时任务删除cached_at超过24小时的记录 | 每小时 |
| 商品图片文件 | 跟随取货单 | 取货单删除时检查关联SKU是否还有其他取货单引用，无引用则删除图片文件+记录 | 取货单删除时 |
| crash日志 | 保留30天 | 后端定时任务删除30天前的记录 | 每天凌晨4:00 |
| 离线操作队列（App端） | 同步成功即删 | 同步成功后立即从Room删除 | WorkManager同步成功时 |
| App端Room缓存 | 保留24小时 | 超过24小时的缓存数据下次请求时覆盖 | 查询时判断 |

```python
# 后端定时清理任务
async def cleanup_expired_data():
    now = datetime.now(BJ_TZ)

    # 1. 删除30天前已完成的取货单（CASCADE自动删待办行）
    conn.execute(
        "DELETE FROM pick_orders WHERE status=1 AND completed_at < ?",
        ((now - timedelta(days=30)).isoformat(),)
    )

    # 2. 清理过期SKU缓存
    conn.execute(
        "DELETE FROM sku_cache WHERE cached_at < ?",
        ((now - timedelta(hours=24)).isoformat(),)
    )

    # 3. 清理30天前的crash日志
    conn.execute(
        "DELETE FROM crash_logs WHERE created_at < ?",
        ((now - timedelta(days=30)).isoformat(),)
    )

    # 4. 清理孤立图片（无取货单引用的SKU图片）
    conn.execute(
        "DELETE FROM product_images WHERE sku_outer_id NOT IN "
        "(SELECT DISTINCT sku_outer_id FROM pick_items) "
        "AND created_at < ?",
        ((now - timedelta(days=7)).isoformat(),)  # 7天安全期，避免误删正在使用的图片
    )

    conn.commit()

# 每天凌晨3:00执行
scheduler.add_job(cleanup_expired_data, "cron", hour=3, minute=0)
```

> **安全措施**：图片清理有7天安全期，只删除7天前且无任何取货单引用的图片。清理前建议先做数据库备份。

### F27 连续扫码模式

取货单详情页提供"连续扫码"开关（默认关闭），开启后扫码流程变为：

```
扫码 → 自动添加待办行 → 短振动+提示音确认 → 光标自动回到扫码框 → 等待下一个扫码
```

**与普通扫码模式的区别**：

| 对比项 | 普通模式 | 连续扫码模式 |
|--------|---------|------------|
| 扫码后 | 添加待办行，需手动操作下一个 | 自动添加，光标自动回到扫码框 |
| 重复SKU | 中等振动+高亮提示 | 中等振动+高亮提示（不中断连续扫码） |
| 适用场景 | 需要逐个确认的精细操作 | 批量快速扫货 |
| 关闭方式 | — | 再次点击开关或返回上一页 |

> **关键**：连续扫码模式下不自动标记完成，只自动添加待办行。完成操作仍需手动点击。

### F28 API Key加密存储

App端敏感信息（API Key、服务器地址、快麦Session）使用EncryptedSharedPreferences加密存储（非DataStore），防止PDA丢失后数据泄露：

```kotlin
// 使用 EncryptedSharedPreferences 加密存储敏感信息（非DataStore，是SharedPreferences）
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "kuaimai_secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

**加密存储的敏感信息**：

| 信息 | 存储方式 | 说明 |
|------|---------|------|
| API Key（后端认证） | EncryptedSharedPreferences | 防止PDA丢失后泄露 |
| 服务器地址 | EncryptedSharedPreferences | 含Tailscale IP等内网信息 |
| 快麦Session | EncryptedSharedPreferences | 快麦ERP访问凭证 |
| User Token | EncryptedSharedPreferences | 用户认证Token，7天有效期 |
| User ID | EncryptedSharedPreferences | 当前登录用户ID |
| User Permissions | EncryptedSharedPreferences | 当前用户权限列表（Set<String>） |
| 扫码配置 | 普通DataStore | 非敏感信息，无需加密 |

### F31 条码格式兼容

后端对扫码结果做清洗，支持多种条码格式：

```python
def clean_barcode(raw: str) -> str:
    """清洗扫码结果：去除控制字符、前后空格、零宽字符"""
    if not raw:
        return ""
    cleaned = raw.strip()
    # 去除控制字符（0x00-0x1F, 0x7F）和零宽字符
    cleaned = re.sub(r'[\x00-\x1f\x7f\u200b-\u200f\u2028-\u202f\ufeff]', '', cleaned)
    return cleaned

def validate_barcode(code: str) -> tuple[bool, str]:
    """验证条码格式，返回(is_valid, error_msg)"""
    if not code:
        return False, "扫码内容为空"
    if len(code) > 64:
        return False, "条码过长（超过64字符）"
    # 支持的格式：纯数字（EAN-13/UPC等）、字母数字混合（Code128等）、URL（QR码）
    if not re.match(r'^[A-Za-z0-9\-_./:]+$', code):
        return False, f"条码含非法字符：{code}"
    return True, ""
```

**支持的条码格式**：

| 格式 | 示例 | 说明 |
|------|------|------|
| 纯数字 | `6901234567890` | EAN-13/UPC-A，最常见 |
| 字母数字混合 | `SKU-2024-001` | Code128，仓库常用 |
| URL | `https://...` | QR码，可能包含商品链接 |
| 快麦outerId | `12345678` | 快麦系统规格编码 |

> **异常处理**：清洗后为空或含非法字符时，App端显示友好提示（如"条码格式异常，请重新扫描"），短振动+错误音。

### F32 App冷启动优化

目标冷启动时间 < 2秒，关键措施：

| 优化项 | 做法 | 说明 |
|--------|------|------|
| Application.onCreate | 仅初始化Hilt，不做网络/数据库操作 | 延迟初始化非关键组件 |
| 首页数据 | Room缓存秒加载，后台异步刷新 | 用户看到缓存的旧数据，同时静默请求最新数据 |
| 数据库预填充 | 首次安装时预填充空数据库 | 避免首次启动时建表耗时 |
| 布局优化 | 首页使用LazyColumn | 避免一次性加载所有取货单卡片 |
| 图片加载 | Coil内存缓存+磁盘缓存 | 避免每次启动重新下载图片 |

```kotlin
// Application.onCreate 最小化
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 仅初始化Hilt，其他组件延迟初始化
    }
}
```

### F35 Token刷新失败处理

快麦API Token刷新失败时的完整处理流程：

```
快麦API返回session过期错误 →
  → 拦截器检测到错误码 →
  → 尝试自动刷新Token（调用session.refresh）→
    → 刷新成功：重试原请求
    → 刷新失败：
      → 弹窗提示"会话已过期，请重新授权"
      → 提供"一键跳转快麦后台重新授权"按钮
      → 刷新期间暂停所有写操作（离线队列接管）
      → 用户重新授权后恢复操作
```

**实现要点**：

| 要点 | 说明 |
|------|------|
| 自动刷新 | OkHttp Authenticator自动检测401并调用session.refresh |
| 暂停写操作 | Token失效期间所有写操作转入离线队列，避免数据丢失 |
| 用户提示 | 弹窗不可关闭（防止用户忽略），必须处理才能继续 |
| 一键跳转 | 打开快麦ERP授权页面（WebView或外部浏览器） |
| 重试机制 | 新Token获取后自动重试之前失败的请求 |

## 验证步骤

1. API签名正确性：调用 `查询商品列表` 验证签名逻辑
2. PDA扫码：在真实PDA设备上测试广播模式扫码，扫码结果自动填入输入框
3. 取货单CRUD：新建取货单、添加商品行、完成/恢复商品行
4. 取货单自动完成：所有行完成后取货单自动标记完成
5. 12小时超时：创建取货单后模拟时间流逝，验证超时自动完成逻辑
6. 单号生成：同一天多次新建，验证序号递增
7. 备注修改：修改规格备注后，在快麦ERP后台确认同步成功
8. 图片上传：上传库区图和装箱图，验证服务器存储和URL访问
9. 会话刷新：30天内自动刷新，过期后提示用户
10. 用户登录：正确/错误密码登录，5次失败锁定验证
11. 权限控制：不同权限用户访问受限功能，前端隐藏+后端拒绝
12. Token过期：7天后Token失效，自动跳转登录页
13. 用户管理：管理员创建/编辑/禁用/删除用户，自我保护规则验证
14. 图片上传认证：ImageUploadService携带X-User-Token验证
