# 代码审查计划 - 第三轮 Bug/缺陷/逻辑问题修复

## 审查范围

App端（Kotlin/Compose）+ 后端（Python FastAPI），重点关注：
1. 前两轮修复是否引入新问题
2. 深层逻辑缺陷
3. 数据一致性问题
4. 安全/健壮性问题
5. UTC与北京时间混用问题

---

## 已修复确认（第一轮 BUG-01~12,15,20 + 第二轮 BUG-16~25）

全部已修复并验证通过，此处不再重复。

---

## 新发现问题（按严重程度排序）

### P0 - 严重Bug

#### BUG-28: OrderSyncWorker 离线同步操作全部为空实现（致命！）
- **位置**: `OrderSyncWorker.kt` L99-127
- **代码**:
  ```kotlin
  private suspend fun syncRemarkUpdate(op: PendingOperationEntity): Boolean {
      val remark = extractPayloadValue(op.payload, "remark") ?: return false
      return true  // 没有实际调用API！
  }
  private suspend fun syncSupplierUpdate(op: PendingOperationEntity): Boolean {
      val supplierName = extractPayloadValue(op.payload, "supplier_name") ?: return false
      return true  // 没有实际调用API！
  }
  private suspend fun syncImageUpload(op: PendingOperationEntity): Boolean {
      return true  // 没有实际调用API！
  }
  ```
- **问题**: 三个同步方法都没有实际调用后端API或快麦API，直接返回 `true`。这意味着：
  1. 离线修改备注后，同步时不会真正更新后端
  2. 离线修改供应商后，同步时不会真正更新后端
  3. 离线上传图片后，同步时不会真正上传
  4. 操作从队列中删除后，数据永远丢失
- **修复**: 实现实际的API调用逻辑。备注和供应商更新应调用后端API（而非快麦API），图片上传应调用ImageUploadService

#### BUG-29: KuaimaiInterceptor 签名拦截器完全未实现
- **位置**: `KuaimaiInterceptor.kt` L26-39
- **代码**:
  ```kotlin
  val originalBody = originalRequest.body
  // TODO: 解析请求体参数，计算签名，追加sign参数后重新构建请求
  val newRequest = originalRequest.newBuilder()
      .addHeader("app_key", appKey)
      .build()
  ```
- **问题**: 拦截器只添加了 `app_key` header，没有解析请求体、计算签名、追加 `sign` 参数。所有快麦API请求都不会携带签名，会被快麦服务器拒绝。
- **修复**: 实现完整的签名流程：解析请求体 → 排序参数 → 计算签名 → 追加sign → 重建请求

#### BUG-30: DatabaseModule 注册了 MIGRATION_1_2 但 AppDatabase version=1
- **位置**: `DatabaseModule.kt` L37, `AppDatabase.kt` L25
- **问题**: `AppDatabase` version=1，但 `DatabaseModule` 注册了 `MIGRATION_1_2`（1→2迁移）。Room 在 version=1 时不会触发此迁移。如果后续升级 version 到 2，空的 migrate() 方法会导致数据丢失（什么都不做）。
- **修复**: 删除 `MIGRATION_1_2` 注册（当前 version=1 不需要迁移），保留迁移框架以备后续使用

### P1 - 逻辑缺陷

#### BUG-31: ProductViewModel.loadSkuInfo 使用全局查询而非当前订单
- **位置**: `ProductViewModel.kt` L99
- **代码**: `val item = pickItemDao.getBySkuOuterId(skuOuterId)`
- **问题**: 与 BUG-18 相同的问题。`getBySkuOuterId` 查询全局，`LIMIT 1` 可能返回其他订单的记录。如果同一 SKU 在不同订单中存在，可能显示错误的备注/供应商信息。
- **修复**: ProductViewModel 需要知道当前 orderId，使用 `getByOrderIdAndSkuOuterId` 查询

#### BUG-32: ProductViewModel.confirmSaveRemark 中 payload 的 JSON 注入风险
- **位置**: `ProductViewModel.kt` L211
- **代码**: `payload = """{"remark":"${confirmType.remark}"}"""`
- **问题**: 如果备注内容包含双引号 `"`，JSON 格式会被破坏。例如备注为 `He said "hello"`，生成的 payload 为 `{"remark":"He said "hello""}`，解析时出错。
- **修复**: 对 remark 值进行 JSON 转义（替换 `"` 为 `\"`）

#### BUG-33: ProductViewModel.confirmChangeSupplier 同样的 JSON 注入风险
- **位置**: `ProductViewModel.kt` L284
- **代码**: `payload = """{"supplier_name":"${confirmType.name}","supplier_code":"${confirmType.code}"}"""`
- **问题**: 供应商名称或编码包含双引号时 JSON 格式被破坏。
- **修复**: 对值进行 JSON 转义

#### BUG-34: 后端 delete_item 不恢复取货单状态
- **位置**: `orders.py` L372-400
- **问题**: 删除一条已完成的明细时，递减了 `completed_count` 和 `total_count`，但没有检查是否需要将取货单状态从已完成恢复为进行中。与 BUG-22（restore_item）相同的问题。
- **修复**: 删除已完成明细后，检查 `completed_count < total_count`，如果是则恢复取货单状态为进行中

#### BUG-35: 后端 complete_all_items 不检查取货单是否有明细
- **位置**: `orders.py` L300-330
- **问题**: 对一个 `total_count=0` 的空取货单调用 `complete_all_items`，会将 `completed_count=0, status=1`，即空取货单被标记为"已完成"。
- **修复**: 添加检查 `total_count > 0`

#### BUG-36: 后端 create_order 单号 LIKE 查询可能匹配到意外结果
- **位置**: `orders.py` L44-48
- **代码**: `WHERE order_no LIKE ?` with `(f"{prefix}%",)`
- **问题**: 如果拣货区名称包含 `%` 或 `_` 等 SQL LIKE 通配符，查询结果会不准确。例如 `areaName="A%区"` 会匹配到所有以 `20260617-A` 开头的单号。
- **修复**: 对 areaName 中的 `%` 和 `_` 进行转义

#### BUG-37: 后端 images.py 上传图片时先删除旧文件再保存新文件，中间失败会丢失图片
- **位置**: `images.py` L43-60, L71-77
- **问题**: 如果已有旧图片，先删除旧文件和旧记录，然后保存新文件。如果新文件保存失败（如磁盘满），旧图片已删除且记录已删除，数据丢失。
- **修复**: 先保存新文件，成功后再删除旧文件和旧记录

### P2 - 小问题

#### BUG-38: App端 ProductImageEntity 缺少 filePath 字段
- **位置**: `ProductImageEntity.kt`
- **问题**: 后端 `product_images` 表有 `file_path` 字段，但 App 端 `ProductImageEntity` 没有 `filePath` 字段。虽然 App 端不需要文件路径（通过 URL 访问），但字段不对称可能导致后续同步问题。
- **修复**: 暂不处理，App 端不需要 filePath

#### BUG-39: 后端 orders.py 中 `_BEIJING_TZ` 重复定义
- **位置**: `orders.py` L30, `main.py` L33, `config.py` L18
- **问题**: `_BEIJING_TZ = timezone(timedelta(hours=8))` 在三个文件中重复定义。`orders.py` 中的 `_BEIJING_TZ` 未被使用（已使用 `beijing_now()`），属于冗余代码。
- **修复**: 删除 `orders.py` 中未使用的 `_BEIJING_TZ` 和 `datetime/timedelta/timezone` 导入

#### BUG-40: 后端 orders.py 中 `os` 和 `sqlite3` 导入冗余
- **位置**: `orders.py` L4-5
- **问题**: `os` 仅在 `_cleanup_sku_images` 中使用（合理），`sqlite3` 仅在类型注解中使用（合理）。但 `datetime`、`timedelta`、`timezone` 中 `timezone` 未使用。
- **修复**: 删除未使用的 `timezone` 导入

---

## 修复步骤

### 第1步：修复致命Bug（BUG-28/29/30）
1. `OrderSyncWorker.kt`: 实现三个同步方法的实际API调用逻辑
2. `KuaimaiInterceptor.kt`: 实现完整的签名流程
3. `DatabaseModule.kt`: 删除 MIGRATION_1_2 注册

### 第2步：修复逻辑缺陷（BUG-31/32/33/34/35/36/37）
1. `ProductViewModel.kt`: 使用当前订单查询SKU
2. `ProductViewModel.kt`: JSON 转义备注和供应商值
3. `orders.py` delete_item: 添加取货单状态恢复
4. `orders.py` complete_all_items: 添加空取货单检查
5. `orders.py` create_order: LIKE 通配符转义
6. `images.py`: 调整上传顺序（先保存新文件再删除旧文件）

### 第3步：清理冗余代码（BUG-39/40）
1. `orders.py`: 删除未使用的 `_BEIJING_TZ` 和 `timezone` 导入

### 第4步：构建验证
1. 运行 `gradlew assembleDebug` 验证 App 端修改

### 第5步：版本号更新 + 知识图谱 + Git
1. 更新 3 处版本号
2. 更新知识图谱
3. Git 提交推送

---

## 涉及文件清单

| 文件 | 修改内容 |
|------|----------|
| `app/.../data/OrderSyncWorker.kt` | BUG-28 实现同步API调用 |
| `app/.../data/api/KuaimaiInterceptor.kt` | BUG-29 实现签名流程 |
| `app/.../di/DatabaseModule.kt` | BUG-30 删除空迁移 |
| `app/.../ui/product/ProductViewModel.kt` | BUG-31/32/33 修复查询和JSON转义 |
| `backend/app/routers/orders.py` | BUG-34/35/36/39/40 修复逻辑+清理 |
| `backend/app/routers/images.py` | BUG-37 调整上传顺序 |
