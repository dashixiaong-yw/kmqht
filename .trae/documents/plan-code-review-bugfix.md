# 代码审查计划 - Bug/缺陷/逻辑问题/时间混用

## 审查范围

App端（Kotlin/Compose）+ 后端（Python FastAPI），重点关注：
1. UTC与北京时间混用问题
2. 业务逻辑缺陷
3. 数据一致性问题
4. 安全/健壮性问题

---

## 发现的问题（按严重程度排序）

### P0 - 严重Bug（数据错误/丢失）

#### BUG-01: 后端时间存储为字符串，App端时间存储为Long，两端无法互通
- **位置**:
  - 后端 `database.py` L44: `created_at DATETIME NOT NULL`（存储为字符串如 `"2026-06-15 14:30:00"`）
  - 后端 `orders.py` L62: `format_beijing(now)` 写入字符串
  - App端 `PickOrderEntity.kt` L32: `val createdAt: Long`（存储为毫秒时间戳）
  - App端 `PickItemEntity.kt` L64: `val createdAt: Long`
- **问题**: 后端API返回的 `createdAt` 是字符串 `"2026-06-15 14:30:00"`，App端 DTO `OrderResponse.createdAt: String` 接收后，在 `PickDetailViewModel.kt` L133 直接用 `TimeUtils.now()` 覆盖，丢失了后端的真实创建时间。更严重的是，如果后端返回的时间字符串需要解析为 Long 存入 Room，目前没有任何转换逻辑。
- **修复**:
  1. 在 `TimeUtils.kt` 添加 `parseBeijingTime(str: String): Long` 方法
  2. 在 ViewModel 中，将后端返回的时间字符串解析为 Long 再存入 Room
  3. 或者统一后端也返回毫秒时间戳

#### BUG-02: PickDetailViewModel.refresh() 用当前时间覆盖了后端的真实时间
- **位置**: `PickDetailViewModel.kt` L220-230
- **代码**:
  ```kotlin
  createdAt = TimeUtils.now(),          // 应该用后端返回的 detail.createdAt
  completedAt = detail.completedAt?.let { TimeUtils.now() },  // 应该用后端返回的完成时间
  expireAt = TimeUtils.now() + 12 * 60 * 60 * 1000L  // 硬编码12小时，应该用后端返回的
  ```
- **问题**: 刷新时把所有时间都替换为当前时间，导致历史创建时间丢失、过期时间计算错误。
- **修复**: 解析后端返回的时间字符串为 Long 时间戳

#### BUG-03: PickListViewModel.createOrder() 同样用当前时间覆盖后端时间
- **位置**: `PickListViewModel.kt` L121-130
- **代码**:
  ```kotlin
  createdAt = TimeUtils.now(),          // 应该用 response.createdAt
  expireAt = TimeUtils.now() + 12 * 60 * 60 * 1000L  // 硬编码12小时
  ```
- **问题**: 同 BUG-02，创建取货单后同步到本地时，覆盖了后端的真实创建时间和过期时间。
- **修复**: 解析后端返回的时间字符串

#### BUG-04: 后端数据库单连接无连接池，多线程不安全
- **位置**: `database.py` L15-22
- **代码**: `_db_connection` 全局单例 + `check_same_thread=False`
- **问题**: FastAPI 的同步路由在多线程中共享同一个 SQLite 连接，可能导致并发写入冲突和数据损坏。项目规范要求"后端SQLite连接需使用连接池+上下文管理"。
- **修复**: 使用连接池模式（如 `sqlite3` 的 `connect` 每次创建，或使用 `threading.local`）

### P1 - 逻辑缺陷

#### BUG-05: 后端 `complete_all_items` 不更新取货单状态为"已完成"
- **位置**: `orders.py` L267-297
- **问题**: 批量完成所有明细后，只更新了 `completed_count = total_count`，但没有将取货单 `status` 更新为 1（已完成）。取货单会一直显示为"进行中"。
- **修复**: 添加 `UPDATE pick_orders SET status = 1, completed_at = ? WHERE id = ?`

#### BUG-06: 后端 `complete_item` 同样不检查是否全部完成
- **位置**: `orders.py` L203-233
- **问题**: 完成单条明细后，只递增 `completed_count`，不检查是否 `completed_count == total_count`，不会自动标记取货单为已完成。
- **修复**: 完成明细后检查是否全部完成，如果是则更新取货单状态

#### BUG-07: 后端 `delete_item` 先删明细再更新计数，但明细已删除无法回退
- **位置**: `orders.py` L338-366
- **问题**: 代码先更新 `completed_count` 和 `total_count`，再删除明细。但顺序是先更新后删除，如果删除失败会导致计数不一致。应该在一个事务中先删后更新（当前已在事务中，但顺序可以更清晰）。
- **修复**: 调整为先删除明细再更新计数（当前逻辑实际没问题因为事务保护，但顺序可优化）

#### BUG-08: App端 PickOrderEntity.status 与后端 status 含义不一致
- **位置**:
  - `PickOrderEntity.kt` L19: `/** 状态：0-待取货 1-取货中 2-已完成 */`
  - `database.py` L40: `status INTEGER NOT NULL DEFAULT 0 CHECK(status IN (0,1))`
- **问题**: App端定义了3种状态（0/1/2），后端数据库只允许2种（0/1），CHECK约束会阻止status=2的插入。App端注释说"1-取货中"，后端注释说"0=进行中, 1=已完成"，语义完全不同。
- **修复**: 统一状态定义。建议后端也支持0/1/2，或者App端改为0/1

#### BUG-09: App端 PickItemEntity.status 与后端不一致
- **位置**:
  - `PickItemEntity.kt` L52: `/** 状态：0-待取货 1-已取货 2-跳过 */`
  - `database.py` L60: `status INTEGER NOT NULL DEFAULT 0 CHECK(status IN (0,1))`
- **问题**: App端定义了3种状态（含"跳过"），后端CHECK约束只允许0/1。如果App端发送status=2，后端会报错。
- **修复**: 统一状态定义

#### BUG-10: 快麦API签名使用MD5而非HMAC-MD5
- **位置**: `kuaimai_api.py` L18-30
- **问题**: 函数注释说"HMAC-MD5签名"，但实际实现是普通MD5（拼接app_secret + 参数 + app_secret）。App端 `SignUtils.kt` 使用的是 `HmacUtils.hmacMd5Hex`（真正的HMAC-MD5）。两端签名算法不一致，会导致API调用签名验证失败。
- **修复**: 确认快麦API文档要求的签名算法，统一两端实现

#### BUG-11: 后端 `get_sku_info` 在同步路由中调用异步API
- **位置**: `cache.py` L36-46
- **问题**: `get_sku_info` 是同步函数，但内部调用 `get_sku_by_outer_id` 是异步函数。使用 `asyncio.run()` 或线程池来桥接，这在 FastAPI 的同步路由中可能导致事件循环冲突。
- **修复**: 将 `add_item` 路由改为 `async def`，直接 `await get_sku_by_outer_id()`

### P2 - 时间相关问题

#### BUG-12: 后端 `kuaimai_api.py` timestamp 使用本地时间而非北京时间
- **位置**: `kuaimai_api.py` L39
- **代码**: `"timestamp": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())`
- **问题**: `time.localtime()` 返回系统本地时间。如果服务器时区不是 UTC+8，快麦API收到的timestamp就不是北京时间。项目规范要求所有时间使用北京时间。
- **修复**: 改为 `beijing_now().strftime("%Y-%m-%d %H:%M:%S")`

#### BUG-13: 后端 `time_utils.py` 旧版残留代码
- **位置**: `time_utils.py`（旧版搜索结果中发现的）
- **问题**: 旧版 `time_utils.py` 包含 `get_current_utc_datetime()`、`convert_to_utc()` 等UTC相关函数，与项目规范"统一使用北京时间"矛盾。当前新版已修正，但需确认旧版函数是否被引用。
- **修复**: 确认旧函数无引用后删除

#### BUG-14: App端 `TimeUtils.now()` 返回的是 UTC 时间戳
- **位置**: `TimeUtils.kt` L66
- **代码**: `fun now(): Long = System.currentTimeMillis()`
- **问题**: `System.currentTimeMillis()` 返回的是 UTC 毫秒时间戳，这是正确的（时间戳本身无时区概念）。但 `formatTimestamp()` 使用 `beijingZone` 格式化，所以存储和显示是一致的。**这不是bug**，但注释应该更明确说明存储的是epoch毫秒，显示时转为北京时间。

### P3 - 健壮性问题

#### BUG-15: 后端无API Key认证
- **位置**: 所有后端路由
- **问题**: 项目规范要求"后端API必须添加API Key安全认证"，但当前所有接口均无认证。
- **修复**: 添加 FastAPI 中间件或依赖项进行 API Key 校验

#### BUG-16: 后端 `images.py` 上传接口无文件大小限制
- **位置**: `images.py` L23-100
- **问题**: 项目规范要求"图片上传前需压缩至200KB左右"，但后端没有限制上传文件大小，恶意用户可上传超大文件。
- **修复**: 添加文件大小限制（如 1MB）

#### BUG-17: App端 `ImageUploadService` 硬编码 `image/jpeg` MediaType
- **位置**: `ImageUploadService.kt` L89
- **代码**: `imageFile.asRequestBody("image/jpeg".toMediaType())`
- **问题**: 上传PNG或WebP图片时，Content-Type 仍然是 `image/jpeg`，后端可能无法正确处理。
- **修复**: 根据文件扩展名动态设置 MediaType

#### BUG-18: App端 `PickDetailViewModel.onBarcodeScanned` 重复检查逻辑有缺陷
- **位置**: `PickDetailViewModel.kt` L110-113
- **问题**: `getItemBySkuOuterId(barcode)` 查询的是全局所有订单中的SKU，不是当前订单。如果同一SKU在不同订单中存在，会误报重复。应该检查 `existing.orderId == orderId`（当前已有此判断，但 `getItemBySkuOuterId` 只返回一条记录，可能返回其他订单的记录）。
- **修复**: 改为查询当前订单下的明细

#### BUG-19: 后端 `orders.py` 创建取货单的单号生成有并发问题
- **位置**: `orders.py` L43-53
- **问题**: 先查询当日该区域已有单号数量，再生成新单号。两个并发请求可能读到相同的已有数量，生成相同的单号。虽然 `UNIQUE` 约束会阻止重复插入，但会导致其中一个请求失败。
- **修复**: 使用数据库事务 + 重试机制

#### BUG-20: 后端 `orders.py` 删除取货单时先删订单再删图片，顺序错误
- **位置**: `orders.py` L310-335
- **问题**: 代码先 `DELETE FROM pick_orders`，由于 `ON DELETE CASCADE`，pick_items 也会被级联删除。然后遍历 `deleted_skus` 检查是否被其他订单引用，但此时 pick_items 中该订单的记录已经被级联删除了，查询结果不完整。
- **修复**: 先收集SKU列表，再删除订单

---

## 修复优先级

| 优先级 | Bug编号 | 说明 |
|--------|---------|------|
| P0 | BUG-01~04 | 时间格式不互通/数据覆盖/连接池 |
| P1 | BUG-05~11 | 逻辑缺陷/状态不一致/签名算法 |
| P2 | BUG-12~14 | 时间规范问题 |
| P3 | BUG-15~20 | 健壮性/安全问题 |

## 修复步骤

### 第1步：修复时间格式互通（BUG-01/02/03）
1. 在 `TimeUtils.kt` 添加 `parseBeijingTime(str: String): Long` 方法
2. 修改 `PickDetailViewModel.refresh()` 使用后端返回的时间
3. 修改 `PickListViewModel.createOrder()` 使用后端返回的时间
4. 修改 `PickDetailViewModel.onBarcodeScanned()` 使用后端返回的时间

### 第2步：修复后端逻辑缺陷（BUG-05/06/08/09/20）
1. `complete_all_items` 添加更新取货单状态
2. `complete_item` 检查是否全部完成
3. 统一 App/后端 status 定义
4. 修复删除取货单的级联顺序

### 第3步：修复后端时间/连接池（BUG-04/12）
1. 修改 `kuaimai_api.py` timestamp 使用北京时间
2. 修改 `database.py` 使用连接池

### 第4步：修复签名算法（BUG-10/11）
1. 确认快麦API签名算法
2. 统一前后端签名实现
3. 将 `add_item` 路由改为 async

### 第5步：修复健壮性问题（BUG-15~19）
1. 添加 API Key 认证
2. 添加文件大小限制
3. 修复 MediaType 动态设置
4. 修复重复扫码检查逻辑
5. 修复单号生成并发问题
