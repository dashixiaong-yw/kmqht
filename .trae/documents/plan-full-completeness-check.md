# 全面功能完整性检查计划

## 摘要

对比设计文档（kuaimai-pda-app-plan.md）F1-F35功能需求、HTML原型（prototype/index.html）、后端API设计，与前后端实际实现进行全面对比，发现差距并修复。

## 当前状态分析

### 后端API实现状态（19/19 已实现）

| # | API端点 | 状态 | 备注 |
|---|---------|------|------|
| 1 | POST /api/orders | ✅ | 创建取货单 |
| 2 | GET /api/orders | ✅ | 列表（支持status筛选） |
| 3 | GET /api/orders/{id} | ✅ | 详情（支持supplierName筛选） |
| 4 | POST /api/orders/{id}/items | ✅ | 扫码添加待办行 |
| 5 | PUT /api/orders/{id}/items/{itemId}/complete | ✅ | 完成待办行 |
| 6 | PUT /api/orders/{id}/items/{itemId}/restore | ✅ | 恢复待办行 |
| 7 | PUT /api/orders/{id}/complete-all | ✅ | 全部完成 |
| 8 | DELETE /api/orders/{id} | ✅ | 删除取货单 |
| 9 | DELETE /api/orders/{id}/items/{itemId} | ✅ | 删除待办行 |
| 10 | GET /api/orders/{id}/suppliers | ✅ | 供应商列表 |
| 11 | GET /api/areas | ✅ | 拣货区列表 |
| 12 | POST /api/areas | ✅ | 新增拣货区 |
| 13 | DELETE /api/areas/{id} | ✅ | 删除拣货区 |
| 14 | POST /api/upload | ✅ | 上传图片 |
| 15 | GET /api/images/{skuOuterId} | ✅ | 获取图片 |
| 16 | DELETE /api/images/{id} | ✅ | 删除图片 |
| 17 | POST /api/crash-report | ✅ | 崩溃日志 |
| 18 | GET /api/app-version | ✅ | 版本信息 |
| 19 | GET /health | ✅ | 健康检查 |

### 后端定时任务实现状态（6/6 已实现）

| 任务 | 状态 |
|------|------|
| 12小时超时检查 | ✅ 每分钟 |
| 已完成取货单清理 | ✅ 每天3:00 |
| SKU缓存清理 | ✅ 每小时 |
| 崩溃日志清理 | ✅ 每天4:00 |
| 孤立图片清理 | ✅ 每天3:30 |
| Session过期警告 | ✅ 每24小时 |

### 后端关键问题

**P0-BUG**: `_check_order_timeout()` 仅记录日志，未实际更新数据库将超时订单标记为已完成。设计文档要求"自动将取货单及所有未完成行标记为已完成（completionType=TIMEOUT）"。

### 前端功能实现状态（F1-F35）

| # | 功能 | 状态 | 差距说明 |
|---|------|------|---------|
| F1 | 取货单管理 | ✅ | 新建/查看/自动生成单号均已实现 |
| F2 | 扫码待办 | ✅ | 扫码添加+完成/恢复 |
| F3 | 自动完成 | ⚠️ | 后端超时检查仅记录日志未更新DB；前端完成所有行后自动标记完成已实现 |
| F4 | 商品详情+备注修改 | ✅ | 二次确认弹窗已实现 |
| F5 | 图片上传 | ✅ | 库区图/装箱图上传 |
| F6 | PDA扫码适配 | ✅ | ScannerManager+PdaScannerReceiver+CameraScanScreen |
| F7 | 离线操作队列 | ✅ | PendingOperationEntity+OrderSyncWorker(9种操作类型) |
| F8 | 扫码反馈 | ⚠️ | ScannerManager有振动+声音，但PickDetailScreen仅手动振动，未调用ScannerManager.provideFeedback() |
| F9 | 重复扫码检测 | ✅ | 中等振动+Snackbar提示 |
| F10 | 图片压缩上传 | ❌ | 无compressImage实现，上传前未压缩 |
| F11 | 后端API安全认证 | ✅ | ApiKeyMiddleware |
| F12 | 网络状态指示 | ✅ | NetworkMonitor+NetworkStatusIndicator |
| F13 | 屏幕常亮 | ✅ | FLAG_KEEP_SCREEN_ON + DisposableEffect |
| F14 | 全部完成按钮 | ✅ | 底部"全部完成"按钮 |
| F15 | 下拉刷新 | ⚠️ | PickDetailViewModel有refresh()方法，但PickDetailScreen未使用PullToRefreshBox包裹列表 |
| F16 | 会话过期预警 | ⚠️ | SessionExpiredEvent存在但未在UI层实现过期前3-5天提醒 |
| F17 | PDA触摸优化 | ⚠️ | 部分按钮触摸热区未达56dp×56dp |
| F18 | 供应商关联修改 | ✅ | SupplierSelectDialog+二次确认 |
| F19 | 取货单删除 | ✅ | 长按+确认弹窗 |
| F20 | 待办行删除 | ✅ | 长按+确认弹窗 |
| F21 | 已完成取货单查看 | ✅ | "查看已完成"入口+7天内列表 |
| F22 | 图片删除/替换 | ⚠️ | ImageUploadSlot仅有onClick上传，缺少长按删除和替换逻辑 |
| F23 | 首次使用引导 | ⚠️ | GuideScreen存在但未在AppNavigation中集成（无路由注册） |
| F24 | 取货单排序 | ✅ | 按创建时间倒序 |
| F25 | 长按操作 | ✅ | combinedClickable长按 |
| F27 | 连续扫码模式 | ✅ | Switch开关+连续扫码逻辑 |
| F28 | API Key加密存储 | ⚠️ | 使用SharedPreferences而非EncryptedSharedPreferences |
| F31 | 条码格式兼容 | ✅ | 后端barcode.py清洗 |
| F32 | App冷启动优化 | ✅ | Room缓存秒加载 |
| F35 | Token刷新失败处理 | ⚠️ | AuthRepository有refreshSession()但无弹窗提示"会话已过期"+跳转入口 |

### 前端UI与HTML原型不一致

| # | 差距 | 严重度 | 说明 |
|---|------|--------|------|
| UI-01 | 待办行缺少库区图/装箱图小方块 | P1 | 原型要求40×40px库区图+装箱图按钮，当前仅有完成/恢复按钮 |
| UI-02 | 取货单卡片进度点尺寸8dp vs 原型10dp | P2 | 应为10dp |
| UI-03 | 设置页版本号硬编码"v0.10" | P2 | 应动态读取BuildConfig |
| UI-04 | PickDetailScreen未使用PullToRefreshBox | P1 | 设计文档要求下拉刷新 |
| UI-05 | HomeScreen ModuleCard背景色PrimaryLightBg | P2 | 原型中卡片为白色背景+图标框蓝色背景 |
| UI-06 | 扫码框placeholder不一致 | P2 | 已改为"按PDA扫码键扫描规格编码" |
| UI-07 | 供应商筛选Chip字号12sp vs 原型13sp | P2 | 应为13sp |
| UI-08 | 待办行规格图标注字号8sp过小 | P2 | 原型为10sp |

## 差距清单与修复方案

### P0 - 严重（必须修复）

#### GAP-P0-01: 后端超时检查未实际更新数据库
- **文件**: `backend/main.py` L163-182
- **问题**: `_check_order_timeout()` 仅SELECT+记录日志，未UPDATE将超时订单标记为已完成
- **修复**: 添加UPDATE语句，将status=1, completion_type=1, completed_at=now，同时更新pick_items

#### GAP-P0-02: 图片上传前未压缩
- **文件**: `app/src/main/java/com/kuaimai/pda/data/api/ImageUploadService.kt` 或新建工具类
- **问题**: F10要求上传前压缩到1024px宽/质量80%（约200KB），当前无压缩逻辑
- **修复**: 新增`ImageCompressor`工具类，在uploadImage前调用压缩

### P1 - 重要（应该修复）

#### GAP-P1-01: PickDetailScreen未实现下拉刷新
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt`
- **问题**: F15要求下拉刷新同步其他PDA操作，ViewModel有refresh()但Screen未使用PullToRefreshBox
- **修复**: 用PullToRefreshBox包裹LazyColumn，调用viewModel.refresh()

#### GAP-P1-02: 待办行缺少库区图/装箱图小方块按钮
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt`
- **问题**: 原型要求待办行右侧显示40×40px库区图+装箱图小方块（带文字标注），当前仅有完成/恢复按钮
- **修复**: 在完成/恢复按钮左侧添加库区图/装箱图小方块（AsyncImage+底部标注）

#### GAP-P1-03: 扫码反馈未统一使用ScannerManager
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt`
- **问题**: F8要求扫码成功/失败/重复使用ScannerManager.provideFeedback()，当前PickDetailScreen手动调用Vibrator
- **修复**: 注入ScannerManager，在onBarcodeScanned成功/失败/重复时调用provideFeedback()

#### GAP-P1-04: 图片删除/替换功能未实现
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt`
- **问题**: F22要求已上传图片可长按删除或点击替换，当前ImageUploadSlot仅有onClick上传
- **修复**: 添加长按删除（确认弹窗）+ 点击已有图片时替换（先删旧再传新）

#### GAP-P1-05: 首次使用引导页未集成到导航
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt`
- **问题**: F23要求首次启动显示引导页，GuideScreen已存在但未注册路由
- **修复**: 添加GUIDE路由，启动时检查prefs[KEY_GUIDE_SHOWN]，未引导则跳转GuideScreen

#### GAP-P1-06: API Key未使用EncryptedSharedPreferences加密存储
- **文件**: `app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt` 或 `SettingsViewModel.kt`
- **问题**: F28要求使用EncryptedSharedPreferences存储API Key等敏感信息
- **修复**: 在Hilt Module中提供EncryptedSharedPreferences实例，SettingsViewModel使用加密存储

#### GAP-P1-07: 会话过期预警未在UI层实现
- **文件**: 新增或修改HomeScreen/PickDetailScreen
- **问题**: F16要求accessToken过期前3-5天提醒用户刷新
- **修复**: 在HomeScreen添加SessionExpiryWarning检查，距过期<5天时显示警告条

#### GAP-P1-08: Token刷新失败处理不完整
- **文件**: `app/src/main/java/com/kuaimai/pda/data/repository/AuthRepository.kt` + UI层
- **问题**: F35要求刷新失败→弹窗提示"会话已过期"+跳转快麦后台入口
- **修复**: AuthRepository刷新失败时发送事件，UI层监听并弹出Dialog

### P2 - 次要（建议修复）

#### GAP-P2-01: 取货单卡片进度点尺寸8dp→10dp
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/components/PickOrderCard.kt` L130
- **修复**: `Modifier.size(8.dp)` → `Modifier.size(10.dp)`

#### GAP-P2-02: 设置页版本号硬编码
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt` L267
- **修复**: 使用`BuildConfig.VERSION_NAME`动态读取

#### GAP-P2-03: 供应商筛选Chip字号12sp→13sp
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt` L258
- **修复**: `fontSize = 12.sp` → `fontSize = 13.sp`

#### GAP-P2-04: 待办行规格图标注字号8sp→10sp
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt` L119
- **修复**: `fontSize = 8.sp` → `fontSize = 10.sp`

#### GAP-P2-05: HomeScreen ModuleCard背景色
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt`
- **问题**: 原型中卡片白色背景+左侧52dp蓝色图标框，当前整张卡片PrimaryLightBg
- **修复**: 卡片背景改为SurfaceWhite，左侧添加52dp蓝色图标框

#### GAP-P2-06: 按钮触摸热区未统一56dp×56dp
- **文件**: 多个Screen文件
- **问题**: F17要求所有可点击元素最小触摸热区56dp×56dp
- **修复**: 关键按钮添加`Modifier.minimumInteractiveComponentSize()`或自定义padding

## 修复步骤

### 第1步：P0修复（2项）
1. 修复后端超时检查_update数据库逻辑
2. 新增图片压缩工具类+集成到上传流程

### 第2步：P1修复（8项）
1. PickDetailScreen添加PullToRefreshBox下拉刷新
2. PickItemRow添加库区图/装箱图小方块
3. PickDetailScreen扫码反馈统一使用ScannerManager
4. ProductScreen图片删除/替换功能
5. AppNavigation集成GuideScreen引导页
6. API Key改用EncryptedSharedPreferences
7. 会话过期预警UI实现
8. Token刷新失败弹窗处理

### 第3步：P2修复（6项）
1. 进度点8dp→10dp
2. 版本号动态读取
3. Chip字号12sp→13sp
4. 规格图标注8sp→10sp
5. ModuleCard背景色调整
6. 按钮触摸热区优化

### 第4步：验证
1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 更新版本号（3处一致）
4. 更新知识图谱
5. 同步docker-deploy
6. Git提交推送

## 假设与决策

1. **图片压缩**：使用Android原生Bitmap.compress()，无需额外依赖
2. **EncryptedSharedPreferences**：使用AndroidX Security Crypto库，需添加依赖
3. **会话过期预警**：在HomeScreen检查，距过期<5天显示黄色警告条
4. **Token刷新失败**：使用EventBus或SharedFlow在AuthRepository和UI之间通信
5. **下拉刷新**：使用Material3 PullToRefreshBox（已导入但未使用）
6. **库区图/装箱图小方块**：从ProductImageDao查询图片URL，Coil异步加载
