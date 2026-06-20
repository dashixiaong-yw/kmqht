# 综合修复计划（最终版）— 30 项

> **审查状态**：最终轮 3 路子代理并行审计，发现 **5 项新问题**，全部已纳入修复方案。

---

## Bug A：B08-24 备注/供应商回写失败 — P0（16 处）

### 修改清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `backend/app/database.py` | pick_items ALTER TABLE 追加 `item_outer_id`（migrations 列表） |
| 2 | `backend/app/models.py` ItemResponse | 增加 `itemOuterId: str = ""` |
| 3 | `backend/app/routers/orders.py` add_item INSERT | INSERT 增加 `item_outer_id` 列 |
| 4 | `backend/app/routers/orders.py` _row_to_item_response | 返回增加 `"itemOuterId"` |
| 5 | `PickItemEntity.kt` | 增加 `itemOuterId` 字段 + ColumnInfo |
| 6 | `AppDatabase.kt` | version 3 → 4，修正注释 |
| 7 | `DatabaseModule.kt` | 新增 MIGRATION_3_4 + `.addMigrations()` 追加 |
| 8 | `PickItemDao.kt` updateItemFields | 增加 `itemOuterId` 参数和 SQL 列 |
| 9 | `OrderDto.kt` OrderItemResponse | 增加 `itemOuterId: String = ""` |
| 10 | `PickDetailViewModel.kt` refresh() | 新明细 INSERT + updateItemFieldsDirect 同步 itemOuterId |
| 11 | `PickOrderRepository.kt` payload 4处 | 增加 `"item_outer_id"` 字段 |
| 12 | `PickOrderRepository.kt` triggerSyncWorker | `KEEP` → `APPEND_OR_REPLACE` |
| 13 | `MainActivity.kt` | Worker 触发也用 `APPEND_OR_REPLACE` |
| 14 | `OrderSyncWorker.kt` fetchLatestSkuData | 增加 `itemOuterIdFallback`；getSkuInfo 失败时用 fallback 调 getItemDetail |
| 15 | `OrderSyncWorker.kt` syncRemarkUpdate/syncSupplierUpdate | 从 payload 取 `item_outer_id` 传给 fetchLatestSkuData |
| 16 | `ItemUpdateRequest.kt` SkuUpdateDto | `skuSuppliers` 默认值改为 `null` |

---

## Bug B：添加明细后供应商筛选短暂消失 + 进度延迟 — P0（3 处）

| # | 文件 | 改动 |
|---|------|------|
| 17 | `PickDetailViewModel.kt` `loadSuppliersFromLocal()` | 改为 `val items = this.items.value`（复用类级 StateFlow） |
| 18 | `PickDetailViewModel.kt` `onBarcodeScanned()` | 成功后追加 `loadOrder()` |
| 19 | `PickDetailViewModel.kt` `onBarcodeScanned()` | addItem 成功后同步更新 `_order.value.totalCount` |

---

## Bug C：add_item 后端延迟 — 无需修改（已解决）

v1.82 的 `modified` 时间戳比对方案已足够高效。移除前的短 TTL 方案不纳入。

---

## Bug D：取货单列表页进度不更新 — P0（9 处）

### 审查发现的新增问题

| 发现问题 | 来源 | 影响 |
|---------|------|------|
| `PickItemDao` **缺少** `getCompletedCount` 方法 | 最终审计 | 所有 completeItem/restoreItem/completeAllItems 无法编译 |
| `syncItemsFromBackend()` 不更新 `pick_order` 表 | 最终审计 | 409 重复扫码后列表页进度错误 |
| Worker 中 5 个后端 API sync 方法忽略 `response.success` | 最终审计 | 后端如果 200 返回 `success=false`，Worker 静默当成功处理 |
| `completeItem`/`restoreItem`/`completeAllItems` catch 分支也应补 `completedCount` | 最终审计 | 确保离线乐观更新路径一致性 |
| `fetchLatestSkuData` 内部 3 处静默 `return null` 无日志 | 最终审计 | 无法区分"API 失败"还是"数据为空" |

### 修改清单

| # | 文件 | 改动 |
|---|------|------|
| 20 | `PickItemDao.kt` | **新增** `getCompletedCount(orderId: Long, status: Int): Int` — `SELECT COUNT(*) FROM pick_item WHERE order_id=:orderId AND status=:status` |
| 21 | `PickOrderRepository.kt` 接口+实现 | 新增 `getCompletedCount()` 转发给 DAO |
| 22 | `PickOrderRepository.kt` `completeAllItemsDirect()` | 内部补 `updateCompletedCount(orderId, totalCount)` — 从 `pickOrderDao.getById(orderId)` 读 |
| 23 | `PickOrderRepository.kt` `enqueueCompleteAll()` | 乐观更新时补 `updateCompletedCount(orderId, totalCount)` |
| 24 | `PickDetailViewModel.kt` `completeItem()` try+catch 分支 | 成功后用 DAO `getCompletedCount` 计算并调 `updateCompletedCount` |
| 25 | `PickDetailViewModel.kt` `restoreItem()` try+catch 分支 | 同上 |
| 26 | `PickDetailViewModel.kt` `completeAllItems()` try+catch 分支 | 同上（try 分支已有 completeAllItemsDirect 内部处理，catch 分支需额外补） |
| 27 | `PickDetailViewModel.kt` `syncItemsFromBackend()` | 补充 `pickOrderRepository.updateOrder(orderEntity)` 同步 order 的 completedCount/status |
| 28 | `OrderSyncWorker.kt` 5 个 sync 方法 | 检查 `BaseResponse.success`，为 false 时返回 false 而非无条件 true |
| 29 | `OrderSyncWorker.kt` `fetchLatestSkuData()` | 内部 3 处 `return null` 前增加 `Log.w()` 日志 |
| 30 | `PickListScreen.kt` | 新增 `DisposableEffect + LifecycleEventObserver` 监听 `ON_RESUME` 时调 `loadActiveOrders()` |
| 31 | `PickListViewModel.kt` | 新增 `refreshActiveOrders()`（委托给 `loadActiveOrders()`） |

---

## 修改汇总

| 优先级 | 文件 | # 修改 | 涉及 |
|:------:|------|:----:|------|
| 🔴P0 | backend ×4 | 1-4 | pick_items 加 item_outer_id |
| 🔴P0 | Android Room ×6 | 5-9, 20 | Entity + Migration v3→4 + DAO + DTO + getCompletedCount |
| 🔴P0 | Android ViewModel ×6 | 10, 17-19, 24-27 | refresh/loadSuppliersFromLocal/onBarcodeScanned/completeItem/restoreItem/completeAllItems/syncItemsFromBackend |
| 🔴P0 | Android Repository ×5 | 11-12, 21-23 | payload 预存 + APPEND_OR_REPLACE + getCompletedCount + completeAllItemsDirect + enqueueCompleteAll |
| 🔴P0 | Android Worker ×4 | 14-16, 28-29 | fetchLatestSkuData fallback + sync 取 payload + skuSuppliers null + 5 个 sync 检查 response.success + fetchLatestSkuData 日志 |
| 🔴P0 | Android 列表页 ×2 | 30-31 | PickListScreen Lifecycle ON_RESUME + refreshActiveOrders |

**总计**：31 处修改，5 个文件后端 + 14 个文件 Android。覆盖 4 个 Bug 区域。

## 安全审查确认（最终轮新增）

| 审查发现项 | 是否已修复 | 关联 # |
|-----------|:--------:|:------:|
| `PickItemDao` 缺少 `getCompletedCount` 方法 | ✅ 纳入 | #20 |
| `syncItemsFromBackend` 不更新 pick_order | ✅ 纳入 | #27 |
| Worker 5 个 sync 方法忽略 response.success | ✅ 纳入 | #28 |
| `fetchLatestSkuData` 3 处静默 return null 无日志 | ✅ 纳入 | #29 |
| completeItem/restoreItem catch 分支补 completedCount | ✅ 纳入 | #24-#25 |
| `OrderDto` itemOuterId 无需 `@SerializedName` | ✅ 确认 | N/A |
| Gson 未启用 serializeNulls，null 字段会跳过 | ✅ 确认 | N/A |
| `completeItem` 响应格式：后端当前只 200 或抛异常 | ✅ 确认 | N/A |
| `completeAllItems` 后端无延迟 | ✅ 确认 | N/A |
| `syncImageUpload` 不受策略影响 | ✅ 确认 | N/A |
| `deleteOrder` 整单删除无 completedCount 问题 | ✅ 确认 | N/A |

## 验证

1. 修改 B08-24 供应商/备注 → 快麦 ERP 确认更新
2. 弱网下 Worker 同步失败 → 用 payload item_outer_id fallback 继续
3. 添加第一个明细后供应商筛选**立即显示**
4. 添加明细后进度**立刻更新**
5. 完成/恢复/批量完成明细后列表页进度**同步更新**
6. 409 重复扫码后 `syncItemsFromBackend` 同步 order 数据
7. Worker 所有 sync 方法不静默忽略 `response.success`
8. `fetchLatestSkuData` 失败时有明确 W/TAG 日志
9. `./gradlew lint` + `./gradlew assembleRelease` 通过
