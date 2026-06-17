# 第五轮代码审查计划 - 残留缺陷与一致性问题

## 审查范围
全面审查后端（Python FastAPI）和前端（Kotlin/Compose）代码，检查：
- Bug与逻辑缺陷
- UTC与北京时间混用
- 硬编码问题
- 前后端字段名/函数名一致性
- 重复代码
- 无用代码

## 当前版本
- build.gradle.kts: versionName = "0.6", versionCode = 6
- gradle.properties: Version: 0.6
- CHANGELOG.md: 最新为0.5（**缺少0.6条目**）

---

## 发现的问题

### BUG-41: 后端main.py重复定义_BEIJING_TZ（严重）
- **文件**: `backend/main.py` 第33行
- **问题**: `main.py`中定义了`_BEIJING_TZ = timezone(timedelta(hours=8))`，但`time_utils.py`已有`BEIJING_TZ`。第四轮审查已从`config.py`和`orders.py`中删除了重复定义，但`main.py`中漏掉了。
- **修复**: 删除`main.py`中的`_BEIJING_TZ`定义，改用`from app.utils.time_utils import beijing_now`，所有定时任务函数中的`datetime.now(_BEIJING_TZ)`改为`beijing_now()`
- **影响**: 5处引用（第168/190/210/229/258行）

### BUG-42: 后端main.py未使用的import（轻微）
- **文件**: `backend/main.py` 第5行
- **问题**: `from datetime import datetime, timedelta, timezone` — 修复BUG-41后，`datetime`和`timedelta`仍被定时任务函数使用，但`timezone`将不再需要
- **修复**: 删除`timezone`导入

### BUG-43: 前端KuaimaiInterceptor时间戳格式错误（严重）
- **文件**: `app/.../data/api/KuaimaiInterceptor.kt` 第61行
- **问题**: `params["timestamp"] = TimeUtils.formatTimestamp(TimeUtils.now())` — `formatTimestamp`输出格式为`"yyyy-MM-dd HH:mm:ss"`，但快麦API要求的timestamp格式也是`"yyyy-MM-dd HH:mm:ss"`。后端`kuaimai_api.py`第39行使用`beijing_now().strftime("%Y-%m-%d %H:%M:%S")`，两者一致，**此处无问题**。
- **但**: `TimeUtils.formatTimestamp`内部使用`SimpleDateFormat`格式化毫秒时间戳，输出格式确实是`"yyyy-MM-dd HH:mm:ss"`，与快麦API要求一致。**确认无Bug**。

### BUG-44: 前端AreaListResponse与后端API返回格式不匹配（严重）
- **文件**: `app/.../data/api/dto/AreaDto.kt` 第15-18行
- **问题**: 前端`AreaListResponse`定义为`{ success, message, data: List<AreaResponse> }`，但后端`areas.py`第17行`list_areas()`直接返回`List[AreaResponse]`（不是包裹在`data`字段中的对象）。Retrofit解析时会因结构不匹配导致`data`字段为空列表。
- **修复**: 修改前端`AreaListResponse`为直接列表，或修改后端返回包裹格式。根据后端`OrderListResponse`的模式（有`data`字段包裹），应修改后端`areas.py`返回包裹格式以保持一致性。

### BUG-45: 前端AreaApiService返回类型与后端不匹配（严重）
- **文件**: `app/.../data/api/AreaApiService.kt` 第13行
- **问题**: `suspend fun getAreas(): AreaListResponse` — 后端`list_areas()`返回`List[AreaResponse]`，前端期望`AreaListResponse`对象。这是BUG-44的关联问题。
- **修复**: 与BUG-44一起修复

### BUG-46: 前端PickListViewModel loadAreas()解析逻辑错误（严重）
- **文件**: `app/.../ui/picklist/PickListViewModel.kt` 第75-76行
- **问题**: `val response = areaApiService.getAreas(); _areas.value = response.data.map { it.name }` — 由于BUG-44/45，`response.data`将为空列表，导致拣货区列表始终为空，回退到硬编码默认值。
- **修复**: 与BUG-44/45一起修复

### BUG-47: 后端database.py PRAGMA初始化逻辑缺陷（中等）
- **文件**: `backend/app/database.py` 第30-43行
- **问题**: `get_db()`中，`_pragma_initialized`标志在第一个连接时设为True后，后续连接只设置`foreign_keys`和`busy_timeout`，不设置`journal_mode=WAL`。这是正确的（WAL只需设置一次），但`init_db()`第54行又重复设置了`PRAGMA journal_mode=WAL`，与`get_db()`中的逻辑重复。
- **修复**: `init_db()`中删除重复的PRAGMA设置，因为`get_db()`已经处理

### BUG-48: 前端escapeJson重复实现（重复代码）
- **文件**: `app/.../data/repository/PickOrderRepository.kt` 第173-180行 和 `app/.../ui/product/ProductViewModel.kt` 第387-394行
- **问题**: `escapeJson()`方法在两个文件中完全相同地实现
- **修复**: 将`escapeJson()`移到`AppConstants`或新建`JsonUtils`工具类中

### BUG-49: 前端enqueuePendingOperation重复实现（重复代码）
- **文件**: `app/.../data/repository/PickOrderRepository.kt` 第153-168行 和 `app/.../ui/product/ProductViewModel.kt` 第360-375行
- **问题**: `enqueuePendingOperation()`方法在两个文件中几乎相同地实现
- **修复**: `ProductViewModel`应通过`PickOrderRepository`来操作离线队列，而不是直接操作`PendingOperationDao`

### BUG-50: 前端ProductImageEntity缺少filePath字段（中等）
- **文件**: `app/.../data/db/entity/ProductImageEntity.kt`
- **问题**: 后端`ImageResponse`包含`filePath`字段，但前端`ProductImageEntity`没有`filePath`列。虽然当前功能可能不需要本地存储filePath，但如果后续需要删除图片或同步，会缺少关键数据。
- **决策**: 当前不影响功能，暂不修复，记录为已知差异

### BUG-51: 后端kuaimai_api.py注释与实现不一致（轻微）
- **文件**: `backend/app/services/kuaimai_api.py` 第1行
- **问题**: 文件注释写"HMAC-MD5签名"，但实际实现是普通MD5签名（前后加app_secret）。第四轮审查已修复SignUtils，但后端注释未更新。
- **修复**: 将注释改为"MD5签名"

### BUG-52: 前端AuthRepository DEFAULT_BASE_URL硬编码且与AppConstants不一致（中等）
- **文件**: `app/.../data/repository/AuthRepository.kt` 第36行
- **问题**: `DEFAULT_BASE_URL = "https://api.kuaimai.com/router"` — 这个URL与`AppConstants.KUAIMAI_API_URL`（`https://openapi.kuaimai.com/router`）不同，且域名不同（`api.kuaimai.com` vs `openapi.kuaimai.com`）
- **修复**: 改为引用`AppConstants.KUAIMAI_API_URL`

### BUG-53: 前端ProductImageDao getImagesBySku与getBySkuOuterId重复（无用代码）
- **文件**: `app/.../data/db/dao/ProductImageDao.kt` 第21-27行
- **问题**: `getImagesBySku()`和`getBySkuOuterId()`是两个完全相同的查询方法（注释说"旧方法名兼容"）
- **修复**: 删除`getImagesBySku()`，统一使用`getBySkuOuterId()`

### BUG-54: 前端ItemRepository getSupplierList与querySupplierList重复（重复代码）
- **文件**: `app/.../data/repository/ItemRepository.kt` 第15-16行
- **问题**: `getSupplierList()`和`querySupplierList()`是两个不同的API端点（`erp.item.supplier.list.get` vs `supplier.list.query`），但返回类型相同。`ProductViewModel`只使用了`querySupplierList`。
- **决策**: 保留两个方法（对应不同快麦API端点），但添加注释说明区别

### BUG-55: 前端PickItemDao getItemsByOrder与getByOrderId功能重叠（重复代码）
- **文件**: `app/.../data/db/dao/PickItemDao.kt` 第22-27行 vs 第44-45行
- **问题**: `getItemsByOrder()`（按状态排序）和`getByOrderId()`（按ID排序）功能类似但排序不同。`PickOrderRepository.getItemsByOrderId()`使用了`getByOrderId()`。
- **决策**: 保留两个方法（排序逻辑不同），但`getItemsByOrder()`当前未被使用，标记为潜在无用代码

### BUG-56: 前端PickOrderDao getActiveOrders与getByStatus(0)重复（重复代码）
- **文件**: `app/.../data/db/dao/PickOrderDao.kt` 第27-28行 vs 第57-58行
- **问题**: `getActiveOrders()`等同于`getByStatus(0)`
- **修复**: 删除`getActiveOrders()`，统一使用`getByStatus(0)`

### BUG-57: 前端PickOrderDao getOrderById(Flow)未被使用（无用代码）
- **文件**: `app/.../data/db/dao/PickOrderDao.kt` 第39-40行
- **问题**: `getOrderById()`返回Flow，但`PickOrderRepository.getOrderById()`使用的是挂起版本`getById()`
- **修复**: 删除未使用的Flow版本

### BUG-58: 前端PickItemDao getItemById(Flow)未被使用（无用代码）
- **文件**: `app/.../data/db/dao/PickItemDao.kt` 第32-33行
- **问题**: `getItemById()`返回Flow，但所有调用方使用挂起版本`getById()`
- **修复**: 删除未使用的Flow版本

### BUG-59: 前端PickItemDao insertAll/deleteAll/delete未被使用（无用代码）
- **文件**: `app/.../data/db/dao/PickItemDao.kt` 第74-88行
- **问题**: `insertAll()`、`delete()`、`deleteAll()`当前未被任何代码调用
- **决策**: 保留`insertAll()`和`deleteAll()`（批量操作可能后续使用），删除单个`delete()`（使用`deleteByOrderId()`替代）

### BUG-60: 前端PickOrderDao insertAll/deleteById/deleteAll/getByOrderNo部分未使用（无用代码）
- **文件**: `app/.../data/db/dao/PickOrderDao.kt`
- **问题**: `insertAll()`、`getByOrderNo()`、`deleteById()`、`deleteAll()`未被使用
- **决策**: 保留`insertAll()`和`deleteAll()`（批量操作可能后续使用），删除`getByOrderNo()`和`deleteById()`（当前无使用场景）

### BUG-61: 前端ProductImageDao insertAll/delete/deleteAll部分未使用（无用代码）
- **文件**: `app/.../data/db/dao/ProductImageDao.kt`
- **问题**: `insertAll()`、`delete()`、`deleteAll()`未被使用
- **决策**: 保留`insertAll()`和`deleteAll()`，删除单个`delete()`

### BUG-62: 前端SettingsViewModel完全空实现（无用代码）
- **文件**: `app/.../ui/settings/SettingsViewModel.kt`
- **问题**: 整个类只有TODO注释，无任何实现
- **决策**: 保留（后续需要实现），但属于技术债务

### BUG-63: 后端orders.py delete_item查询status字段时使用了错误的字段引用（严重）
- **文件**: `backend/app/routers/orders.py` 第407行
- **问题**: `if order_info and order_info["status"] == 1:` — 但`SELECT total_count, completed_count FROM pick_orders`查询中没有包含`status`字段！这会导致`KeyError`或始终为False。
- **修复**: 在SELECT中添加`status`字段：`SELECT total_count, completed_count, status FROM pick_orders WHERE id = ?`

### BUG-64: 前端OrderSyncWorker extractPayloadValue转义字符处理缺陷（中等）
- **文件**: `app/.../data/OrderSyncWorker.kt` 第164-172行
- **问题**: `extractPayloadValue()`使用简单的字符串搜索解析JSON，无法处理值中包含转义双引号（`\"`）的情况。当备注或供应商名称包含双引号时，`escapeJson()`会转义为`\"`，但`extractPayloadValue()`会错误地在转义的引号处截断。
- **修复**: 使用`JSONObject`解析payload而非手动字符串搜索

### BUG-65: 前端KuaimaiInterceptor对所有请求都添加签名参数（逻辑缺陷）
- **文件**: `app/.../data/api/KuaimaiInterceptor.kt`
- **问题**: 该拦截器被添加到全局OkHttpClient中，会对所有请求（包括后端API请求）添加快麦签名参数。但后端API请求（如`OrderApiService`、`AreaApiService`）不需要快麦签名参数，添加后可能导致后端解析请求体出错。
- **修复**: KuaimaiInterceptor应只对快麦API请求生效，通过URL判断是否需要签名

---

## 修复优先级

### P0 - 严重Bug（必须修复）
1. **BUG-63**: delete_item查询缺少status字段 → 运行时KeyError
2. **BUG-44/45/46**: AreaListResponse与后端返回格式不匹配 → 拣货区列表始终为空
3. **BUG-65**: KuaimaiInterceptor对后端请求也添加签名 → 后端请求可能失败
4. **BUG-41**: main.py重复定义_BEIJING_TZ → 时间处理不一致风险

### P1 - 中等问题（应该修复）
5. **BUG-52**: AuthRepository硬编码URL与AppConstants不一致
6. **BUG-64**: extractPayloadValue无法处理转义字符
7. **BUG-48/49**: escapeJson和enqueuePendingOperation重复代码
8. **BUG-47**: init_db()重复PRAGMA设置

### P2 - 轻微问题（建议修复）
9. **BUG-51**: kuaimai_api.py注释错误
10. **BUG-42**: main.py未使用的timezone导入
11. **BUG-53**: ProductImageDao重复方法
12. **BUG-56/57/58**: DAO中未使用的方法
13. **BUG-55**: PickItemDao getItemsByOrder未使用

---

## 修复步骤

### Step 1: 修复P0严重Bug
1. BUG-63: orders.py delete_item SELECT添加status字段
2. BUG-44/45/46: 后端areas.py返回包裹格式 + 前端AreaListResponse保持不变（已匹配）
3. BUG-65: KuaimaiInterceptor添加URL判断，只对快麦API请求签名
4. BUG-41: main.py删除_BEIJING_TZ，改用beijing_now()

### Step 2: 修复P1中等问题
5. BUG-52: AuthRepository引用AppConstants.KUAIMAI_API_URL
6. BUG-64: OrderSyncWorker使用JSONObject解析payload
7. BUG-48/49: 提取escapeJson到工具类，ProductViewModel通过Repository操作离线队列
8. BUG-47: init_db()删除重复PRAGMA

### Step 3: 修复P2轻微问题
9. BUG-51: kuaimai_api.py注释修正
10. BUG-42: main.py删除timezone导入
11. BUG-53: 删除ProductImageDao.getImagesBySku()
12. BUG-56: 删除PickOrderDao.getActiveOrders()
13. BUG-57/58: 删除未使用的Flow版本DAO方法

### Step 4: 验证
- `./gradlew assembleDebug`
- 版本号更新到0.7（3处一致）
- CHANGELOG.md更新
- 知识图谱更新
- Git提交推送

---

## 假设与决策
1. 后端areas.py应返回包裹格式（与OrderListResponse保持一致），而非修改前端
2. ProductViewModel的离线队列操作应收敛到PickOrderRepository（单一职责）
3. DAO中批量操作方法（insertAll/deleteAll）保留以备后用
4. SettingsViewModel空实现保留为技术债务
5. ProductImageEntity缺少filePath暂不修复
