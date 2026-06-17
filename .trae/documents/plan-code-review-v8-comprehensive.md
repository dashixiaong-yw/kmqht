# 第八轮代码审查计划 - 全面检查

## 审查范围
- Bug与缺陷、逻辑问题
- UTC与北京时间混用
- 硬编码问题
- 前后端函数名称一致性
- 重复代码、无用代码
- 流程规范变更确认

## 流程规范确认

项目开发流程已更新为8步（确认无误）：
1. 查阅知识图谱 → 2. 修改代码 → 3. lint验证 → 4. 构建APK → 5. 版本号(3处一致+验证) → 6. 知识图谱 → 7. docker-deploy → 8. Git提交

---

## 发现问题清单

### P0 严重（1个）

#### BUG-87: OrderSyncWorker 仅处理3种操作类型，6种操作被静默丢弃
- **文件**: `app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt` L91-98
- **问题**: `syncOperation()` 的 when 分支只处理 `update_remark`/`update_supplier`/`upload_image`，其余6种操作类型（ADD_ITEM/COMPLETE_ITEM/RESTORE_ITEM/COMPLETE_ALL/DELETE_ITEM/DELETE_ORDER）落入 else 分支，被当作"未知类型"直接删除（返回true）
- **影响**: 用户离线时完成/恢复/删除操作只更新本地数据库，**永远不会同步到后端**。网络恢复后这些操作被静默丢弃，导致多PDA间数据不一致
- **修复**: 在 OrderSyncWorker 中添加对 COMPLETE_ITEM/RESTORE_ITEM/DELETE_ITEM/DELETE_ORDER/COMPLETE_ALL/ADD_ITEM 的同步逻辑，调用后端对应API

### P1 中等（4个）

#### BUG-88: orders.py 未使用的 datetime 导入（第七轮遗留BUG-83未修复）
- **文件**: `backend/app/routers/orders.py` L6
- **问题**: `from datetime import datetime, timedelta` 中 `datetime` 未使用
- **修复**: 改为 `from datetime import timedelta`

#### BUG-89: KuaimaiInterceptor 缺少公共参数 format/v/sign_method
- **文件**: `app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt` L66-73
- **问题**: 前端拦截器只添加 `app_key`/`timestamp`/`session`，缺少 `format=json`/`v=2.0`/`sign_method=md5`，与后端 `_build_common_params()` 不一致
- **影响**: 快麦API请求可能因缺少必要参数而失败或返回非预期结果
- **修复**: 在拦截器中添加缺失的公共参数

#### BUG-90: ProductViewModel.loadImages() Flow收集泄漏
- **文件**: `app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt` L142-157
- **问题**: `productImageDao.getBySkuOuterId(skuOuterId).collect { }` 是持续收集的挂起函数，每次调用 `loadSkuInfo()` 启动新协程但不取消旧的 collect，导致多个 Flow 同时收集，可能造成UI状态被旧数据覆盖
- **修复**: 使用 `collectLatest` 替代 `collect`，或在 loadSkuInfo 开头取消之前的图片加载协程

#### BUG-91: AuthRepository.refreshSession() 异常静默吞掉
- **文件**: `app/src/main/java/com/kuaimai/pda/data/repository/AuthRepository.kt` L40-47
- **问题**: `catch (e: Exception) { false }` 不记录日志，违反"捕获异常后必须记录日志或向上传播"规则
- **修复**: 添加 `Log.w` 记录异常信息

### P2 轻微（5个）

#### BUG-92: commons-codec 依赖未使用 + 注释错误
- **文件**: `app/build.gradle.kts` L128
- **问题**: `implementation("commons-codec:commons-codec:1.17.0")` 注释为"HMAC-MD5"，但 SignUtils 使用 `java.security.MessageDigest`（MD5），commons-codec 完全未使用
- **修复**: 删除 commons-codec 依赖，修正注释为"MD5"

#### BUG-93: ItemUpdateRequest.kt 和 ItemListResponse.kt 未使用的 SerializedName 导入
- **文件**: `app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateRequest.kt` L3, `ItemListResponse.kt` L3
- **问题**: `import com.google.gson.annotations.SerializedName` 未使用
- **修复**: 删除未使用的导入

#### BUG-94: 后端辅助函数缺少参数类型注解
- **文件**: `backend/app/routers/images.py` L153, `backend/app/routers/orders.py` L473, `backend/app/services/cache.py` L99
- **问题**: `_row_to_image_response(row)` / `_cleanup_sku_images(cursor, ...)` / `_cache_row_to_dict(row)` 的参数缺少类型注解
- **修复**: 添加 `row: sqlite3.Row` / `cursor: sqlite3.Cursor` 类型注解

#### BUG-95: NetworkMonitor.unregister() 空catch块
- **文件**: `app/src/main/java/com/kuaimai/pda/util/NetworkMonitor.kt` L78-80
- **问题**: `catch (_: Exception)` 静默吞掉异常，违反规则
- **修复**: 添加 `Log.d` 记录

#### BUG-96: PendingOperationEntity 操作类型命名风格不一致
- **文件**: `app/src/main/java/com/kuaimai/pda/data/db/entity/PendingOperationEntity.kt` L24-32
- **问题**: 操作类型混用大写下划线（ADD_ITEM/COMPLETE_ITEM）和小写下划线（update_remark/update_supplier），应统一风格
- **修复**: 统一为小写下划线风格（与 OrderSyncWorker 已处理的3种类型保持一致），同时更新 PickOrderRepository 中的操作类型字符串

---

## 修复步骤

### Step 1: 修复P0 - OrderSyncWorker 补充6种操作类型的同步逻辑
1. 在 OrderSyncWorker 中添加 `syncCompleteItem()` / `syncRestoreItem()` / `syncDeleteItem()` / `syncDeleteOrder()` / `syncCompleteAllItems()` / `syncAddItem()` 方法
2. 这些方法调用后端 API（OrderApiService）进行同步
3. 需要注入 OrderApiService 依赖到 OrderSyncWorker
4. 更新 when 分支处理所有9种操作类型

### Step 2: 修复P1问题
1. BUG-88: 删除 orders.py 未使用的 datetime 导入
2. BUG-89: KuaimaiInterceptor 添加 format/v/sign_method 参数
3. BUG-90: ProductViewModel.loadImages() 改用 collectLatest
4. BUG-91: AuthRepository.refreshSession() 添加日志

### Step 3: 修复P2问题
1. BUG-92: 删除 commons-codec 依赖
2. BUG-93: 删除未使用的 SerializedName 导入
3. BUG-94: 后端辅助函数添加类型注解
4. BUG-95: NetworkMonitor.unregister() 添加日志
5. BUG-96: 统一操作类型命名风格为小写下划线

### Step 4: 按新流程规范验证
1. `./gradlew lint` (Step 3)
2. `./gradlew assembleDebug` (Step 4)
3. 版本号更新到0.9（3处一致+验证）(Step 5)
4. CHANGELOG.md更新
5. 知识图谱更新 (Step 6)
6. 同步到docker-deploy (Step 7)
7. Git提交推送 (Step 8)

---

## UTC/北京时间混用检查结果
- 前端 TimeUtils 统一使用 `Asia/Shanghai` 时区，`now()` 返回 epoch 毫秒（无时区概念），格式化时通过 SimpleDateFormat 设置北京时区 ✅
- 后端 time_utils.py 统一使用 `BEIJING_TZ = timezone(timedelta(hours=8))` ✅
- 后端所有时间存储使用 `format_beijing()` 格式化 ✅
- 前端所有时间戳使用 Long 类型 ✅
- **未发现UTC/北京时间混用问题**

## 硬编码检查结果
- AppConstants 集中管理服务器地址 ✅
- TimeUtils 命名常量管理魔法数字 ✅
- Color.kt 统一色彩常量 ✅
- 后端配置通过环境变量读取 ✅
- **未发现新的硬编码问题**（commons-codec注释问题已在P2中列出）

## 前后端函数名称一致性检查结果
- OrderApiService 端点与后端路由完全匹配 ✅
- AreaApiService 端点与后端路由匹配 ✅
- DTO字段名 camelCase 与后端 Pydantic 模型一致 ✅
- **未发现前后端函数名称不一致问题**
