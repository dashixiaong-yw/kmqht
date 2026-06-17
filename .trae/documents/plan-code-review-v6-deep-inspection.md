# 第六轮代码审查计划 - 残留缺陷深度排查

## 审查范围
全面审查后端（Python FastAPI）和前端（Kotlin/Compose）代码，重点检查：
- Bug与逻辑缺陷
- UTC与北京时间混用
- 硬编码问题
- 前后端字段名/函数名一致性
- 重复代码
- 无用代码

## 当前版本
- build.gradle.kts: versionName = "0.7", versionCode = 7
- gradle.properties: Version: 0.7
- CHANGELOG.md: 最新为0.7

---

## 发现的问题

### BUG-66: App.kt DEFAULT_SERVER_URL硬编码（中等）
- **文件**: `app/.../App.kt` 第30行
- **问题**: `DEFAULT_SERVER_URL = "http://10.0.2.2:8000"` 硬编码，未引用`AppConstants.DEFAULT_SERVER_URL`
- **修复**: 改为引用`AppConstants.DEFAULT_SERVER_URL`

### BUG-67: App.kt logAnr使用SimpleDateFormat而非TimeUtils（中等）
- **文件**: `app/.../App.kt` 第78-82行
- **问题**: `logAnr()`方法中手动创建`SimpleDateFormat`并设置`Asia/Shanghai`时区，但`TimeUtils.formatTimestamp()`已提供相同功能
- **修复**: 使用`TimeUtils.formatTimestamp(System.currentTimeMillis())`替代手动格式化

### BUG-68: App.kt logAnr日志文件名也使用SimpleDateFormat（轻微）
- **文件**: `app/.../App.kt` 第99行
- **问题**: 日志文件名中的日期也使用`SimpleDateFormat`手动格式化，应使用`TimeUtils.formatDate()`
- **修复**: 使用`TimeUtils.formatDate(System.currentTimeMillis())`

### BUG-69: PickOrderRepository updateItemRemark和updateRemarkWithQueue重复（重复代码）
- **文件**: `app/.../data/repository/PickOrderRepository.kt` 第116-128行 vs 第150-162行
- **问题**: `updateItemRemark()`和`updateRemarkWithQueue()`实现完全相同（都是乐观更新本地+写入离线队列），第五轮审查添加了`updateRemarkWithQueue`但未删除旧的`updateItemRemark`
- **修复**: 删除`updateItemRemark()`，统一使用`updateRemarkWithQueue()`

### BUG-70: PickOrderRepository updateItemSupplier和updateSupplierWithQueue重复（重复代码）
- **文件**: `app/.../data/repository/PickOrderRepository.kt` 第131-143行 vs 第165-177行
- **问题**: 同BUG-69，`updateItemSupplier()`和`updateSupplierWithQueue()`实现完全相同
- **修复**: 删除`updateItemSupplier()`，统一使用`updateSupplierWithQueue()`

### BUG-71: PickOrderRepository接口声明了重复方法（重复代码）
- **文件**: `app/.../data/repository/PickOrderRepository.kt` 第31-32行 vs 第37-39行
- **问题**: 接口中同时声明了`updateItemRemark`/`updateItemSupplier`和`updateRemarkWithQueue`/`updateSupplierWithQueue`，功能完全相同
- **修复**: 删除接口中的`updateItemRemark`和`updateItemSupplier`声明

### BUG-72: ImageUploadService parseImageUrlFromResponse手动解析JSON（逻辑缺陷）
- **文件**: `app/.../data/api/ImageUploadService.kt` 第138-155行
- **问题**: `parseImageUrlFromResponse()`使用手动字符串搜索解析JSON，与第五轮修复的OrderSyncWorker `extractPayloadValue()`问题相同。当imageUrl包含转义双引号时会解析错误
- **修复**: 使用`JSONObject`解析响应

### BUG-73: 后端orders.py get_order使用supplierName参数但未转义LIKE通配符（中等）
- **文件**: `backend/app/routers/orders.py` 第118-127行
- **问题**: `get_order()`中`supplierName`参数直接用于`WHERE supplier_name = ?`精确匹配，不是LIKE查询，所以**不存在SQL注入风险**。确认无Bug。

### BUG-74: 后端models.py未使用的datetime导入（无用代码）
- **文件**: `backend/app/models.py` 第3行
- **问题**: `from datetime import datetime` — models.py中没有任何地方使用`datetime`
- **修复**: 删除未使用的导入

### BUG-75: 前端PickOrderDao getByOrderNo/deleteById/deleteAll/insertAll未使用（无用代码）
- **文件**: `app/.../data/db/dao/PickOrderDao.kt` 第39-40行、第57-58行、第87-88行、第93-94行
- **问题**: 第五轮审查决定保留`insertAll()`和`deleteAll()`，但删除`getByOrderNo()`和`deleteById()`。实际执行时只删除了`getActiveOrders()`和Flow版本的`getOrderById()`，`getByOrderNo()`和`deleteById()`仍然存在
- **修复**: 删除`getByOrderNo()`和`deleteById()`

### BUG-76: 前端PickItemDao delete方法未使用（无用代码）
- **文件**: `app/.../data/db/dao/PickItemDao.kt` 第81-82行
- **问题**: 第五轮审查决定删除单个`delete()`方法，但实际未执行
- **修复**: 删除未使用的`delete()`方法

### BUG-77: 前端ProductImageDao delete方法未使用（无用代码）
- **文件**: `app/.../data/db/dao/ProductImageDao.kt` 第44-45行
- **问题**: 第五轮审查决定删除单个`delete()`方法，但实际未执行
- **修复**: 删除未使用的`delete()`方法

### BUG-78: 后端kuaimai_api.py _sign函数注释仍写"HMAC-MD5"（轻微）
- **文件**: `backend/app/services/kuaimai_api.py` 第19-22行
- **问题**: `_sign()`函数的docstring仍写"生成HMAC-MD5签名"，但实际是普通MD5签名。第五轮只修改了文件级注释，函数级注释未更新
- **修复**: 将docstring改为"生成MD5签名"

### BUG-79: 前端PickItemDao getItemsByOrder未被使用（无用代码）
- **文件**: `app/.../data/db/dao/PickItemDao.kt` 第22-27行
- **问题**: `getItemsByOrder()`（按状态排序）当前未被任何代码调用，`PickOrderRepository.getItemsByOrderId()`使用的是`getByOrderId()`
- **修复**: 删除未使用的`getItemsByOrder()`

### BUG-80: 前端PickOrderDao insertAll未使用（无用代码）
- **文件**: `app/.../data/db/dao/PickOrderDao.kt` 第57-58行
- **问题**: `insertAll()`当前未被使用。第五轮决定保留，但考虑到当前无使用场景且违反"不加推测性功能"原则
- **决策**: 保留（批量插入可能在数据同步时使用，属于Room DAO标准方法）

### BUG-81: 前端OrderApiService getSuppliers返回类型与后端不匹配（严重）
- **文件**: `app/.../data/api/OrderApiService.kt` 第87行
- **问题**: `suspend fun getSuppliers(...): List<String>` — 前端期望直接返回`List<String>`，但后端`get_suppliers()`确实返回`List[str]`（FastAPI会将其序列化为JSON数组）。**确认无Bug** — FastAPI返回`["供应商A", "供应商B"]`，Retrofit可以正确解析为`List<String>`

### BUG-82: 前端App.kt sendCrashReport方法未完成实现（技术债务）
- **文件**: `app/.../App.kt` 第112-122行
- **问题**: `sendCrashReport()`方法只有注释和Log，没有实际发送崩溃报告的代码
- **决策**: 保留为技术债务，当前不影响功能

---

## 修复优先级

### P1 - 中等问题（应该修复）
1. **BUG-66**: App.kt硬编码DEFAULT_SERVER_URL
2. **BUG-67/68**: App.kt使用SimpleDateFormat而非TimeUtils
3. **BUG-69/70/71**: PickOrderRepository重复方法（updateItemRemark/updateItemSupplier vs WithQueue版本）
4. **BUG-72**: ImageUploadService手动解析JSON

### P2 - 轻微问题（建议修复）
5. **BUG-74**: models.py未使用的datetime导入
6. **BUG-75**: PickOrderDao getByOrderNo/deleteById未使用
7. **BUG-76**: PickItemDao delete未使用
8. **BUG-77**: ProductImageDao delete未使用
9. **BUG-78**: kuaimai_api.py _sign函数注释仍写HMAC-MD5
10. **BUG-79**: PickItemDao getItemsByOrder未使用

---

## 修复步骤

### Step 1: 修复P1中等问题
1. BUG-66: App.kt引用AppConstants.DEFAULT_SERVER_URL
2. BUG-67/68: App.kt logAnr使用TimeUtils替代SimpleDateFormat
3. BUG-69/70/71: 删除PickOrderRepository中重复的updateItemRemark/updateItemSupplier（接口+实现），统一使用WithQueue版本
4. BUG-72: ImageUploadService使用JSONObject解析响应

### Step 2: 修复P2轻微问题
5. BUG-74: 删除models.py未使用的datetime导入
6. BUG-75: 删除PickOrderDao.getByOrderNo()和deleteById()
7. BUG-76: 删除PickItemDao.delete()
8. BUG-77: 删除ProductImageDao.delete()
9. BUG-78: 修正_sign函数注释
10. BUG-79: 删除PickItemDao.getItemsByOrder()

### Step 3: 验证
- `./gradlew assembleDebug`
- 版本号更新到0.8（3处一致）
- CHANGELOG.md更新
- 知识图谱更新
- Git提交推送

---

## 假设与决策
1. updateItemRemark/updateItemSupplier删除后，统一使用WithQueue版本，接口和实现都要清理
2. DAO中批量操作方法（insertAll/deleteAll）保留
3. App.kt sendCrashReport未实现保留为技术债务
4. ImageUploadService使用JSONObject需要添加import
