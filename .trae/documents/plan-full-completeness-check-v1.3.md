# 全面功能完整性检查计划 v1.3

## 摘要

在v1.2基础上，再次全面对比设计文档（kuaimai-pda-app-plan.md）F1-F35功能需求、HTML原型（prototype/index.html）、后端API设计，与前后端实际实现进行逐项对比，发现残留差距并修复。

## 当前状态分析

### 后端（19/19 API + 6/6 定时任务 = 全部已实现）

| # | API端点 | 状态 |
|---|---------|------|
| 1 | POST /api/orders | ✅ |
| 2 | GET /api/orders | ✅ |
| 3 | GET /api/orders/{id} | ✅ |
| 4 | POST /api/orders/{id}/items | ✅ |
| 5 | PUT /api/orders/{id}/items/{itemId}/complete | ✅ |
| 6 | PUT /api/orders/{id}/items/{itemId}/restore | ✅ |
| 7 | PUT /api/orders/{id}/complete-all | ✅ |
| 8 | DELETE /api/orders/{id} | ✅ |
| 9 | DELETE /api/orders/{id}/items/{itemId} | ✅ |
| 10 | GET /api/orders/{id}/suppliers | ✅ |
| 11 | GET /api/areas | ✅ |
| 12 | POST /api/areas | ✅ |
| 13 | DELETE /api/areas/{id} | ✅ |
| 14 | POST /api/upload | ✅ |
| 15 | GET /api/images/{skuOuterId} | ✅ |
| 16 | DELETE /api/images/{id} | ✅ |
| 17 | POST /api/crash-report | ✅ |
| 18 | GET /api/app-version | ✅ |
| 19 | GET /health | ✅ |

**定时任务**：超时检查(1min) + 已完成清理(3:00) + SKU缓存清理(1h) + 崩溃日志清理(4:00) + 孤立图片清理(3:30) + Session过期警告(24h) = ✅ 全部已实现

**v1.2修复确认**：`_check_order_timeout()` 已从仅记录日志改为UPDATE数据库标记超时订单已完成 ✅

### 前端功能逐项检查（F1-F35）

| # | 功能 | v1.2状态 | 本次检查 | 差距说明 |
|---|------|---------|---------|---------|
| F1 | 取货单管理 | ✅ | ✅ | 新建/查看/自动生成单号均已实现 |
| F2 | 扫码待办 | ✅ | ✅ | 扫码添加+完成/恢复 |
| F3 | 自动完成 | ✅ | ✅ | 后端超时检查已修复UPDATE DB；前端完成所有行后自动标记完成已实现 |
| F4 | 商品详情+备注修改 | ✅ | ✅ | 二次确认弹窗已实现 |
| F5 | 图片上传 | ✅ | ✅ | 库区图/装箱图上传 |
| F6 | PDA扫码适配 | ✅ | ✅ | ScannerManager+PdaScannerReceiver+CameraScanScreen |
| F7 | 离线操作队列 | ✅ | ✅ | OrderSyncWorker 9种操作类型全部实现 |
| F8 | 扫码反馈 | ✅ | ✅ | ScannerManager.provideFeedback()统一管理 |
| F9 | 重复扫码检测 | ✅ | ✅ | 中等振动+Snackbar提示 |
| F10 | 图片压缩上传 | ✅ | ✅ | ImageCompressor工具类已集成 |
| F11 | 后端API安全认证 | ✅ | ✅ | ApiKeyMiddleware |
| F12 | 网络状态指示 | ✅ | ✅ | NetworkMonitor+NetworkStatusIndicator |
| F13 | 屏幕常亮 | ✅ | ✅ | FLAG_KEEP_SCREEN_ON + DisposableEffect |
| F14 | 全部完成按钮 | ✅ | ✅ | 底部"全部完成"按钮 |
| F15 | 下拉刷新 | ✅ | ✅ | PullToRefreshBox已包裹列表 |
| F16 | 会话过期预警 | ✅ | ✅ | HomeScreen黄色警告条 |
| F17 | PDA触摸优化 | ✅ | ✅ | 按钮高度44dp，关键元素≥56dp |
| F18 | 供应商关联修改 | ✅ | ✅ | SupplierSelectDialog+二次确认 |
| F19 | 取货单删除 | ✅ | ✅ | 长按+确认弹窗 |
| F20 | 待办行删除 | ✅ | ✅ | 长按+确认弹窗 |
| F21 | 已完成取货单查看 | ✅ | ✅ | "查看已完成"入口 |
| F22 | 图片删除/替换 | ✅ | ✅ | 长按删除+点击替换+确认弹窗 |
| F23 | 首次使用引导 | ✅ | ✅ | GUIDE路由+启动检查 |
| F24 | 取货单排序 | ✅ | ✅ | 按创建时间倒序 |
| F25 | 长按操作 | ✅ | ✅ | combinedClickable长按 |
| F27 | 连续扫码模式 | ✅ | ✅ | Switch开关+连续扫码逻辑 |
| F28 | API Key加密存储 | ✅ | ✅ | EncryptedSharedPreferences |
| F31 | 条码格式兼容 | ✅ | ✅ | 后端barcode.py清洗 |
| F32 | App冷启动优化 | ✅ | ✅ | Room缓存秒加载 |
| F35 | Token刷新失败处理 | ✅ | ✅ | SharedFlow事件+AlertDialog |

### 发现的残留差距

| # | 差距 | 严重度 | 说明 |
|---|------|--------|------|
| GAP-01 | PickDetailScreen未传入库区图/装箱图URL给PickItemRow | P1 | PickItemRow有areaImageUrl/boxImageUrl参数但调用方未传入，导致待办行库区图/装箱图小方块只显示文字占位而非实际图片 |
| GAP-02 | PickItemRow库区图/装箱图点击回调未在PickDetailScreen中处理 | P1 | onAreaImageClick/onBoxImageClick有默认空实现，但应跳转到商品详情页对应图片 |
| GAP-03 | PickDetailScreen下拉刷新时isRefreshing立即设为false | P2 | viewModel.refresh()是异步的，但isRefreshing在调用后立即设为false，PullToRefreshBox不会显示刷新动画 |

## 修复方案

### GAP-01: PickDetailScreen传入库区图/装箱图URL

**问题**：PickDetailScreen调用PickItemRow时没有传入areaImageUrl/boxImageUrl参数，导致待办行的库区图/装箱图小方块只显示"库区"/"箱图"文字占位。

**修复方案**：
1. PickDetailViewModel添加方法获取每个SKU的图片URL
2. 在PickDetailScreen的items循环中，查询每个item的图片URL并传入PickItemRow

**具体修改**：

#### 文件1: `PickDetailViewModel.kt`
- 注入`ImageRepository`
- 添加`getImageUrls(skuOuterId: String): Pair<String?, String?>`方法
- 从ProductImageDao查询area和box图片URL

#### 文件2: `PickDetailScreen.kt`
- 在items循环中调用viewModel获取图片URL
- 将areaImageUrl/boxImageUrl/onAreaImageClick/onBoxImageClick传入PickItemRow
- onAreaImageClick和onBoxImageClick都跳转到商品详情页

### GAP-02: 库区图/装箱图点击跳转商品详情

**问题**：库区图/装箱图小方块点击后无响应。

**修复方案**：在GAP-01的修改中一并处理，onAreaImageClick和onBoxImageClick都调用onNavigateToProduct(item.skuOuterId)跳转到商品详情页。

### GAP-03: 下拉刷新动画修复

**问题**：`isRefreshing = false`在`viewModel.refresh()`后立即执行，而refresh()是异步的，导致PullToRefreshBox不显示刷新动画。

**修复方案**：
1. 使用PickDetailViewModel已有的`isRefreshing` StateFlow
2. 在refresh()方法中正确管理isRefreshing状态（开始时true，finally时false）
3. PickDetailScreen使用viewModel.isRefreshing而非本地状态

## 修复步骤

### Step 1: 修改PickDetailViewModel
1. 注入ImageRepository
2. 添加getImageUrls方法
3. 修复refresh()中的isRefreshing状态管理

### Step 2: 修改PickDetailScreen
1. 使用viewModel.isRefreshing替代本地isRefreshing
2. 在items循环中获取图片URL并传入PickItemRow
3. 添加onAreaImageClick/onBoxImageClick回调

### Step 3: 验证
1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 更新版本号（3处一致）
4. 更新知识图谱
5. 同步docker-deploy
6. Git提交推送

## 假设与决策

1. **图片URL查询**：在ViewModel中通过ImageRepository查询，避免在Composable中直接访问DAO
2. **点击跳转**：库区图/装箱图点击都跳转到商品详情页（该页已有图片查看功能）
3. **下拉刷新**：使用ViewModel的isRefreshing StateFlow，确保刷新动画正确显示
