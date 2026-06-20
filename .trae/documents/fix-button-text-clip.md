# 取货单详情修复计划（5 项 — 安全审查通过）

> **审查状态**：4 子代理并行审查完成，所有风险点已标注并纳入修复方案。

---

## Bug 1：完成按钮文字不显示

### 根因

v1.81 将按钮 `height(24dp)` 后，Material3 `TextButton` 内置 `contentPadding` 与 24dp 冲突，13.sp 文字被 clip 不可见。

### 修复

去掉 `Modifier.height()`，改用 `contentPadding` 控制紧凑度：

| # | 文件 | 行 | 改动 |
|---|------|:--:|------|
| 1 | `PickItemRow.kt` | L212 | `height(24dp)` → `contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)` |
| 2 | `PickItemRow.kt` | L224 | 同上 |

**前置条件**：追加 `import androidx.compose.foundation.layout.PaddingValues`

> ✅ 安全审查：`TextButton(contentPadding = ...)` 是 Material3 标准参数，编译期可捕获缺失。

---

## Bug 2：单选货单完成/恢复 400 错误

### 根因

`backend/app/routers/orders.py` 检查 `order.status == 1` 时拒绝操作。单选货单唯一明细完成后，后端 auto-complete 将 order.status 设为 1，但前端 `PickItemRow` 仅判断 `item.status`，按钮仍可点击 → 400。

**审查发现的关键缺陷**：`completeItem()` / `restoreItem()` 成功后**从不调用 `loadOrder()`**，导致 `_order` StateFlow 不会更新为 status=1，即使 UI 加了 `order.status == 1` 检查也不会触发。

### 修复

| # | 位置 | 改动 |
|---|------|------|
| 3 | `PickDetailViewModel.kt` `completeItem()` | 成功后追加 `loadOrder()`（刷新 _order StateFlow） |
| 4 | `PickDetailViewModel.kt` `restoreItem()` | 同上 |
| 5 | `PickDetailScreen.kt` `PickItemRow` 回调 | `order?.status == 1` 时传空 `{}` |
| 6 | `PickDetailScreen.kt` "全部完成"按钮 | `enabled` 加 `order?.status != 1` |
| 7 | `PickDetailScreen.kt` | `order?.status == 1` 时显示"取货单已完成，不可操作明细" |

---

## Bug 3：SKU 信息缓存陈旧 — 比对式缓存策略

### 根因

三路子代理全面审计发现系统存在 **3 层陈旧数据节点**：

| 层 | 位置 | 刷新机制 | 最长过期 |
|---|------|------|:--:|
| 🔴 后端 `sku_cache` | `cache.py` `get_sku_info()` | 有缓存直接返回，无 TTL 检查 | **24 小时** |
| 🔴 后端 `pick_items` | `orders.py` `add_item` 写入 | INSERT 时写入一次，永不再更新属性字段 | **永久** |
| 🔴 Android Room | `PickItemEntity` + `refresh()` 仅更新 status | Flow 自动反应但字段值不变 | **永久** |

### 对比式缓存策略

```
每次查询 SKU:
  1. 调快麦 API 获取最新数据（必然调用）
  2. 提取 modified 时间戳
  3. 对比缓存中 cached_modified：
     = 未变 → 返回缓存（不写 DB）
     ≠ 已变 → 写入新缓存 + 返回新数据
     (缓存不存在 → 写入新缓存 + 返回新数据)
  API 失败 → 降级返回缓存（即使可能过时，保证可用性）
```

### 审查发现的 3 个关键实现细节

| # | 风险 | 严重度 | 说明 |
|---|------|:--:|------|
| 1 | `INSERT OR REPLACE` 遗漏 `cached_modified` | **严重** | 不追加会导致每次覆盖缓存时 `cached_modified` 重置为 0 → 下次比对判定已变更 → 无限循环刷新 |
| 2 | API 失败必须降级返回缓存 | **严重** | 不实现则弱网下 `add_item` 全部 404，网络隔离韧性完全丧失 |
| 3 | 比对用 API 应使用短超时 | 中 | 5 秒超时 + 回退缓存，避免 `add_item` 阻塞 61 秒 |

### 修复（后端部分）

| # | 文件 | 改动 |
|---|------|------|
| 8 | `backend/app/database.py` | `sku_cache` 加 `cached_modified BIGINT DEFAULT 0` 列（migrations 列表追加） |
| 9 | `backend/app/database.py` | 迁移脚本：现有行 `cached_modified = 0`（首次查询必然刷新） |
| 10 | `backend/app/services/cache.py` | `get_sku_info()` 改为比对式：先调 API → 对比 → 决定是否更新缓存；API 失败降级返回缓存 |
| 11 | `backend/app/services/cache.py` | `INSERT OR REPLACE` SQL 追加 `cached_modified` 列及绑定值 |
| 12 | `backend/app/services/cache.py` | `_cleanup_sku_cache()` 改为 30 天清理（不删除，防表膨胀） |
| 13 | `backend/app/services/kuaimai_api.py` | `get_sku_by_outer_id()` 返回增加 `modified` 字段 |
| 14 | `backend/main.py` | `_cleanup_sku_cache` 定时任务保留（间隔放宽到每 6 小时清理 >30 天记录） |

---

## Bug 4：`refresh()` 不更新已有明细的 SKU 元数据

### 根因

[PickDetailViewModel.refresh() L329-L333](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L329-L333) 对已有明细只更新 status，其他字段**永不更新**。

### 审查发现的关键回归风险

**覆盖率问题**：如果 `updateItemFields()` 包含 `remark`/`supplierName`/`supplierCode`，refresh() 会用后端旧快照覆盖用户的乐观更新。后端 `pick_items` 表是 `add_item` 时的快照，Worker 同步到快麦成功后不会回写到后端表。

**修复原则**：`updateItemFields()` **仅更新用户不可修改的快麦字段**：

| 字段 | 是否更新 | 原因 |
|------|:--:|------|
| `propertiesName` | ✅ | 用户不可修改，快麦端可能变更 |
| `picPath` | ✅ | 用户不可修改 |
| `sysItemId` / `sysSkuId` | ❌ | 固定不变，更新反而有风险 |
| `remark` | ❌ | 用户可修改，已乐观更新到 Room |
| `supplierName` / `supplierCode` | ❌ | 用户可修改，已乐观更新到 Room |

### 修复

| # | 文件 | 改动 |
|---|------|------|
| 15 | `PickItemDao.kt` | 新增 `updateItemFields(id, propertiesName, picPath)` — **只更新不可变字段** |
| 16 | `PickOrderRepository.kt` | 接口 + 实现：`updateItemFieldsDirect()` |
| 17 | `PickDetailViewModel.kt` | refresh() 已有明细调 `updateItemFieldsDirect()`（与 status 更新分开，各自独立判断） |

---

## Bug 5：备注/供应商回写失败 — 两处缺陷（推测 + 无验证）🔴 P0

### 根因 1：`outerId` 用 `skuOuterId.substringBefore("-")` 推导

```kotlin
val outerId = skuOuterId.substringBefore("-")  // "B08-24" → "B08" ❌ 快麦实际 = "B-08"
```

### 根因 2：`getLatestTitle()` 丢弃了已拿到的 `itemOuterId` 和 `propertiesName`

### 审查发现的前置依赖

**`SkuItemInfo` DTO 缺少 `propertiesName` 字段**（[KuaimaiQueryDto.kt:L25](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/KuaimaiQueryDto.kt#L25-L29)），Gson 会静默丢弃快麦 API 返回的 `propertiesName`。必须扩展此 DTO。

### 修复

| # | 文件 | 改动 |
|---|------|------|
| 18 | `KuaimaiQueryDto.kt` `SkuItemInfo` | 增加 `propertiesName: String = ""`、`skuOuterId: String = ""` |
| 19 | `OrderSyncWorker.kt` | 新增 `private data class SkuSyncData(title, itemOuterId, propertiesName)` |
| 20 | `OrderSyncWorker.kt` | `getLatestTitle()` → `fetchLatestSkuData()`，返回 `SkuSyncData?` |
| 21 | `OrderSyncWorker.kt` `syncRemarkUpdate()` | 使用 `skuData.itemOuterId` 替代 `substringBefore("-")`，`skuData.propertiesName` 替代 payload 快照 |
| 22 | `OrderSyncWorker.kt` `syncSupplierUpdate()` | 同上 |

---

## 修改清单汇总

| # | 优先级 | 文件 | 改动 |
|---|:--:|------|------|
| 1 | 🟡 | `PickItemRow.kt` L212 | `height(24dp)` → `contentPadding(vertical=4dp)` + 追加 import PaddingValues |
| 2 | 🟡 | `PickItemRow.kt` L224 | 同上 |
| 3 | 🟡 | `PickDetailViewModel.kt` `completeItem()` | 成功后追加 `loadOrder()` |
| 4 | 🟡 | `PickDetailViewModel.kt` `restoreItem()` | 同上 |
| 5 | 🟡 | `PickDetailScreen.kt` | `order?.status == 1` 时禁用完成/恢复按钮 |
| 6 | 🟡 | `PickDetailScreen.kt` | "全部完成"按钮加 `order?.status != 1` |
| 7 | 🟡 | `PickDetailScreen.kt` | 已完成时显示提示文字 |
| 8 | 🔴 | `backend/app/database.py` | sku_cache 加 `cached_modified` 列 |
| 9 | 🔴 | `backend/app/database.py` | 迁移：现有行 `cached_modified = 0` |
| 10 | 🔴 | `backend/app/services/cache.py` | `get_sku_info()` 比对式缓存 + API 失败降级 |
| 11 | 🔴 | `backend/app/services/cache.py` | `INSERT OR REPLACE` 追加 `cached_modified` |
| 12 | 🔴 | `backend/app/services/cache.py` | `_cleanup_sku_cache()` 改为 30 天 |
| 13 | 🔴 | `backend/app/services/kuaimai_api.py` | 返回增加 `modified` 字段 |
| 14 | 🔴 | `backend/main.py` | 保留定时清理，放宽间隔 |
| 15 | 🟡 | `PickItemDao.kt` | 新增 `updateItemFields(id, propertiesName, picPath)` |
| 16 | 🟡 | `PickOrderRepository.kt` | 接口 + 实现 `updateItemFieldsDirect()` |
| 17 | 🟡 | `PickDetailViewModel.kt` | refresh() 已有明细同步不可变快麦字段 |
| 18 | 🔴 | `KuaimaiQueryDto.kt` `SkuItemInfo` | 增加 `propertiesName`、`skuOuterId` 字段 |
| 19 | 🔴 | `OrderSyncWorker.kt` | 新增 `SkuSyncData` data class |
| 20 | 🔴 | `OrderSyncWorker.kt` | `getLatestTitle()`→`fetchLatestSkuData()` |
| 21 | 🔴 | `OrderSyncWorker.kt` `syncRemarkUpdate()` | 使用 API 真值替代推导 + 快照 |
| 22 | 🔴 | `OrderSyncWorker.kt` `syncSupplierUpdate()` | 同上 |

---

## 审查确认的安全事项

| 项 | 结论 |
|------|------|
| Bug 3 与 Bug 5 无执行顺序依赖 | ✅ 可同版本部署 |
| `_cleanup_sku_cache` 保留（30天）防表膨胀 | ✅ 已调整 |
| Bug 4 `updateItemFields()` 排除用户可修改字段 | ✅ 防回归覆盖 |
| Bug 5 `SkuItemInfo` DTO 前置扩展 | ✅ 已纳入 |
| `sysItemId`/`sysSkuId` 不在 refresh 中更新 | ✅ 防误覆盖 |
| `fetchLatestSkuData()` 仅 2 处调用方 | ✅ 变更范围明确 |
| API 失败降级返回缓存 | ✅ 保证弱网可用性 |
| INSERT OR REPLACE 追加 `cached_modified` | ✅ 防无限循环刷新 |
| `completeItem()`/`restoreItem()` 成功后 `loadOrder()` | ✅ UI 守卫可触发 |

---

## 验证

1. 完成/恢复按钮文字清晰可见，高度紧凑
2. 单选货单 → 完成后恢复按钮禁用（不再 400）
3. 多选货单全部完成 → 按钮禁用，提示文字显示
4. 快麦修改 B08-24 备注 → PDA 扫码立即显示新备注
5. 快麦修改 B08-24 供应商 → PDA 扫码立即显示新供应商
6. 数据未变更时缓存命中、无 DB 写入
7. 下拉刷新后取货单明细的 propertiesName/picPath 已更新（remark/supplierName 保留用户修改）
8. PDA 改备注 → Worker 同步成功 → 快麦 ERP 确认已更新
9. PDA 改供应商 → Worker 同步成功 → 快麦 ERP 确认已更新
10. 弱网下 SKU 查询失败 → 降级返回缓存（不 404）
11. `./gradlew lint` + `./gradlew assembleRelease` 通过
