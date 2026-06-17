# 代码审查计划 - 第二轮 Bug/缺陷/逻辑问题修复

## 审查范围

App端（Kotlin/Compose）+ 后端（Python FastAPI），重点关注：
1. 上一轮遗留的 BUG-16~19
2. 新发现的严重bug（async未await、状态恢复缺失等）
3. 状态定义不一致问题
4. UTC与北京时间混用问题

---

## 已修复确认（上一轮 BUG-01~12, 15, 20）

| Bug | 状态 | 说明 |
|-----|------|------|
| BUG-01 | ✅ | TimeUtils.parseBeijingTime 已添加 |
| BUG-02 | ✅ | PickDetailViewModel.refresh() 使用后端时间 |
| BUG-03 | ✅ | PickListViewModel.createOrder() 使用后端时间 |
| BUG-04 | ✅ | database.py threading.local 连接池 |
| BUG-05 | ✅ | complete_all_items 更新取货单状态 |
| BUG-06 | ✅ | complete_item 检查全部完成 |
| BUG-08 | ✅ | status CHECK(0,1,2) |
| BUG-09 | ✅ | pick_items status CHECK(0,1,2) |
| BUG-10 | ✅ | SignUtils 改为 MD5 |
| BUG-11 | ✅ | cache.py get_sku_info 改为 async |
| BUG-12 | ✅ | kuaimai_api.py 使用 beijing_now() |
| BUG-15 | ✅ | auth.py + ApiKeyMiddleware 已存在 |
| BUG-20 | ✅ | delete_order 先收集 SKU 再级联删除 |

---

## 待修复问题（按严重程度排序）

### P0 - 严重Bug（会导致功能故障）

#### BUG-21: 后端 add_item 调用 async 函数缺少 await（致命！）
- **位置**: `orders.py` L164
- **代码**: `sku_info = get_sku_info(sku_outer_id)`
- **问题**: `get_sku_info` 是 `async def`，但调用时没有 `await`。返回的是 coroutine 对象而非实际结果。coroutine 对象是 truthy，所以 `if not sku_info:` 永远为 False，即使 SKU 不存在也会继续插入，导致数据错误。
- **修复**: 改为 `sku_info = await get_sku_info(sku_outer_id)`

#### BUG-22: 后端 restore_item 不恢复取货单状态
- **位置**: `orders.py` L247-275
- **问题**: 恢复一条已完成的明细后，只递减 `completed_count`，但不检查是否需要将取货单状态从 1（已完成）改回 0（进行中）。如果用户对一个已完成的取货单恢复了一条明细，取货单仍然显示为"已完成"，与实际状态不符。
- **修复**: 恢复明细后检查 `completed_count < total_count`，如果是则 `UPDATE pick_orders SET status = 0, completed_at = NULL WHERE id = ?`

### P1 - 逻辑缺陷

#### BUG-18: App端重复扫码检查逻辑有缺陷
- **位置**: `PickDetailViewModel.kt` L110, `PickItemDao.kt` L56-57
- **代码**:
  ```kotlin
  val existing = pickOrderRepository.getItemBySkuOuterId(barcode)
  if (existing != null && existing.orderId == orderId) { ... }
  ```
- **问题**: `getItemBySkuOuterId` 查询 `SELECT * FROM pick_item WHERE sku_outer_id = :skuOuterId LIMIT 1`，不限定 `order_id`。如果同一 SKU 存在于多个订单中，`LIMIT 1` 可能返回其他订单的记录，导致 `existing.orderId != orderId`，当前订单中的重复扫码不会被检测到。
- **修复**: 在 DAO 中添加 `getByOrderIdAndSkuOuterId(orderId, skuOuterId)` 方法，查询当前订单下的 SKU

#### BUG-19: 后端单号生成有并发问题
- **位置**: `orders.py` L42-53
- **问题**: 先查询当日该区域已有单号数量，再生成新单号。两个并发请求可能读到相同数量，生成相同单号。虽然 `UNIQUE` 约束会阻止重复插入，但会导致其中一个请求返回 500 错误。
- **修复**: 添加重试机制，捕获 `IntegrityError` 后重新查询并生成

#### BUG-16: 后端 images.py 上传无文件大小限制
- **位置**: `images.py` L23-100
- **问题**: 项目规范要求"图片上传前需压缩至200KB左右"，但后端没有限制上传文件大小。恶意用户可上传超大文件。
- **修复**: 读取文件内容后检查大小，超过 2MB 拒绝上传

#### BUG-17: App端 ImageUploadService 硬编码 MediaType
- **位置**: `ImageUploadService.kt` L89
- **代码**: `imageFile.asRequestBody("image/jpeg".toMediaType())`
- **问题**: 上传 PNG 或 WebP 图片时，Content-Type 仍然是 `image/jpeg`，后端可能无法正确处理。
- **修复**: 根据文件扩展名动态设置 MediaType

### P2 - 状态定义不一致

#### BUG-23: PickOrderEntity status 注释与后端语义不一致
- **位置**: `PickOrderEntity.kt` L18
- **App注释**: `0-待取货 1-取货中 2-已完成`
- **后端实际**: `0=进行中 1=已完成`（代码中 complete_item/complete_all_items 设置 status=1 表示已完成）
- **问题**: App 端注释说 status=1 是"取货中"，但后端 status=1 是"已完成"。开发者可能误用。
- **修复**: 统一注释为 `0-进行中 1-已完成`，删除未使用的 status=2

#### BUG-24: completion_type CHECK(0,1) vs App端定义 0/1/2
- **位置**: `database.py` L77, `PickOrderEntity.kt` L22
- **App注释**: `0-未完成 1-全部完成 2-部分完成`
- **后端约束**: `CHECK(completion_type IN (0,1))`
- **问题**: App 端定义了 3 种完成类型，但后端只允许 2 种。如果 App 发送 completion_type=2，后端会报错。
- **修复**: 统一为 0/1/2，后端 CHECK 约束改为 `CHECK(completion_type IN (0,1,2))`

#### BUG-25: create_order 单号格式与规范不一致
- **位置**: `orders.py` L42-53
- **当前实现**:
  - 第一单: `yyyyMMdd-拣货区`（无序号）
  - 第二单: `yyyyMMdd-拣货区(1)`
- **项目规范**: `yyyyMMdd-拣货区X`（X 是序号）
- **问题**: 第一单没有序号，格式不统一
- **修复**: 第一单也加序号，如 `yyyyMMdd-拣货区1`，第二单 `yyyyMMdd-拣货区2`

### P3 - 小问题

#### BUG-26: PickDetailViewModel.completeItem 使用本地时间
- **位置**: `PickDetailViewModel.kt` L153
- **代码**: `pickOrderRepository.updateItemStatus(itemId, 1, TimeUtils.now())`
- **问题**: 使用本地当前时间而非后端返回的 completedAt。虽然差异很小，但与 BUG-01/02 的修复思路不一致。
- **修复**: 由于 completeItem API 只返回 BaseResponse（无 completedAt），保持使用 TimeUtils.now() 是可接受的。但应在注释中说明原因。

#### BUG-27: PickOrderDao.getCompletedOrders 查询 status=1
- **位置**: `PickOrderDao.kt` L33
- **代码**: `WHERE status = 1`
- **问题**: 如果按 App 注释理解 status=1 是"取货中"，则查询逻辑错误。但实际后端 status=1 就是已完成，查询正确。注释修正后此问题自然解决。
- **修复**: 随 BUG-23 一起修正注释

---

## 修复步骤

### 第1步：修复致命Bug（BUG-21/22）
1. `orders.py` L164: `sku_info = get_sku_info(sku_outer_id)` → `sku_info = await get_sku_info(sku_outer_id)`
2. `orders.py` restore_item: 添加取货单状态恢复逻辑

### 第2步：修复逻辑缺陷（BUG-18/19/16/17）
1. `PickItemDao.kt`: 添加 `getByOrderIdAndSkuOuterId` 方法
2. `PickOrderRepository.kt`: 添加 `getItemByOrderIdAndSkuOuterId` 接口和实现
3. `PickDetailViewModel.kt`: 修改重复扫码检查使用新方法
4. `orders.py` create_order: 添加 IntegrityError 重试机制
5. `images.py`: 添加文件大小限制（2MB）
6. `ImageUploadService.kt`: 根据文件扩展名动态设置 MediaType

### 第3步：修复状态定义不一致（BUG-23/24/25）
1. `PickOrderEntity.kt`: 修正 status 注释为 `0-进行中 1-已完成`
2. `PickItemEntity.kt`: 修正 status 注释（确认 0/1/2 含义）
3. `database.py`: completion_type CHECK 约束改为 `(0,1,2)`
4. `orders.py`: create_order 单号格式统一加序号

### 第4步：构建验证
1. 运行 `gradlew assembleDebug` 验证 App 端修改
2. 检查后端 Python 语法

### 第5步：版本号更新 + 知识图谱 + Git
1. 更新 3 处版本号
2. 更新知识图谱
3. 同步 docker-deploy
4. Git 提交推送

---

## 涉及文件清单

| 文件 | 修改内容 |
|------|----------|
| `backend/app/routers/orders.py` | BUG-21 await, BUG-22 状态恢复, BUG-19 重试, BUG-25 单号格式 |
| `backend/app/routers/images.py` | BUG-16 文件大小限制 |
| `backend/app/database.py` | BUG-24 completion_type CHECK |
| `app/.../data/db/dao/PickItemDao.kt` | BUG-18 新增查询方法 |
| `app/.../data/repository/PickOrderRepository.kt` | BUG-18 新增接口方法 |
| `app/.../ui/pickdetail/PickDetailViewModel.kt` | BUG-18 修改重复检查 |
| `app/.../data/api/ImageUploadService.kt` | BUG-17 动态 MediaType |
| `app/.../data/db/entity/PickOrderEntity.kt` | BUG-23 修正注释 |
| `app/.../data/db/entity/PickItemEntity.kt` | BUG-23 修正注释 |
