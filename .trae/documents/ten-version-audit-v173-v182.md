# 十版本审计结果：安全性与前置条件完整性报告

## 审计范围

- **版本**：v1.73 至 v1.82（2026-06-17~20），共 10 次更新
- **代理**：4 路子代理并行审查（UI布局/缓存Worker/ItemUpdateWrapper/穿透前置条件）
- **检查项**：27 项，覆盖 16 个文件

---

## 审计结论：✅ 安全，零回归风险

**前置条件完整性**：全部 8 项穿透调用链检查通过

| # | 检查项 | 文件 | v1.82 当前状态 | 结论 |
|:-:|:-------|:----|:--------------|:----:|
| 1 | `_cache_row_to_dict` / `_api_data_to_dict` 不含 `modified` | cache.py | 比对逻辑直接操作原始 Row 对象，无需转换函数返回 | ✅ |
| 2 | `_cleanup_sku_cache` 30d + ALTER TABLE 迁移 | main.py/database.py | 清理条件 `cached_at`(30d) 与`cached_modified` 正交；迁移存在且正确 | ✅ |
| 3 | `updateItemFieldsDirect` + Room Flow 自动 emit | PickDetailViewModel/Screen | LazyColumn `key={it.id}` 存在；Flow 自动反应 | ✅ |
| 4 | `loadOrder()` 异步一致性 | PickDetailViewModel | 所有成功路径有 loadOrder()；失败路径乐观更新保证 UI 一致性 | ✅ |
| 5 | `loadSkuInfo` null 重置 | ProductViewModel | L127-128 `currentSkuDetail=null, currentItem=null` | ✅ |
| 6 | `confirmSaveRemark` UI 反馈 | ProductViewModel L293 | `_uiState.copy(remark = confirmType.remark)` 存在 | ✅ |
| 7 | Emoji→Material Icons 残留 | 全项目 .kt | 零残留 | ✅ |
| 8 | database.py WAL 模式 | database.py | 首次连接设 WAL；迁移在 WAL 连接中执行，安全 | ✅ |

**ItemUpdateWrapper 变更链路**：全部 7 项检查通过

| 检查 | 结果 |
|:-----|:----:|
| ItemUpdateWrapper 类已删除 | ✅ 零残留 |
| KuaimaiApiService 返回类型扁平 `ItemUpdateResponse` | ✅ |
| OrderSyncWorker 无 wrapper 解包代码 | ✅ |
| 全项目 Grep `ItemUpdateWrapper` — 仅 CHANGELOG 历史 | ✅ |
| 全项目 Grep `erp_item_general_addorupdate_response` — 仅 Python 测试 | ✅ |
| ProductViewModel 不解析 API 响应 | ✅ |
| PickOrderRepository 不调 KuaimaiApiService | ✅ |

**Worker/缓存/API 变更链**：6/7 项通过

| 检查 | 状态 | 风险 |
|:-----|:----:|:----:|
| Worker 冲突处理 (retryCount=-1 释放队列) | ✅ | 无 |
| Supplier 列表解析 (扁平/包裹层兼容) | ✅ | 无 |
| title 覆盖保护 (fetchLatestSkuData 4个return null出口) | ✅ | 无 |
| currentSkuDetail 旧值污染重置 | ✅ | 无 |
| Worker doWork while 循环 | ✅ | 无 |
| getLatestTitle → fetchLatestSkuData 零残留 | ✅ | 无 |
| **propertiesName 空值 `"-"` 填充** | **❌ 未实现** | **MEDIUM** |

**UI 布局回归**：5/6 项通过

| 检查 | 状态 | 风险 |
|:-----|:----:|:----:|
| HomeScreen ModuleCard (spacedBy 16dp/图标52×52/padding 20dp) | ✅ | 无 |
| PickItemRow 按钮 (v1.82 contentPadding 最终版) | ✅ | 无 |
| PickItemRow 规格图底部标签 | ✅ 已彻底移除 | 无 |
| ProductScreen 备注/保存按钮 Material Icon (非 emoji) | ✅ | 无 |
| Emoji 全量扫描零残留 | ✅ | 无 |
| **ProductScreen ImageUploadSlot heightIn 未使用** | **❌ 注释/import 残差** | **LOW** |

---

## 需修复的 2 项残差

### 残差 1：`propertiesName` 空值 `"-"` 填充未实现

**根因**：v1.77 CHANGELOG 记载了 `propertiesName` 为空时自动填充 `"-"` 保护快麦 API，但代码从未落地。

**修复**：`PickOrderRepository.kt` 4 个入队方法的 propertiesName 入参处补充 `.ifBlank { "-" }`：

| 方法 | 行 | 修复 |
|------|:--:|------|
| `updateRemarkWithQueue(id, remark)` | L176 | `enqueueOperation()` payload 中的 `propertiesName` 用 `item.propertiesName.ifBlank { "-" }` |
| `updateSupplierWithQueue(id, supplierName, supplierCode)` | L191 | 同上 |
| `enqueueRemarkUpdateDirect(...)` | L234 | payload 中的 `propertiesName` 用 `propertiesName.ifBlank { "-" }` |
| `enqueueSupplierUpdateDirect(...)` | L246 | 同上 |

### 残差 2：`ImageUploadSlot` 未用的 `heightIn` import 和误导注释

**根因**：v1.81 加了 `heightIn(max=120.dp)`，v1.82 移除了修饰符残留了 import 和过时注释。`heightIn` 不再使用，注释仍声称"最小高度 120dp"。

**修复**：

| 文件 | 行 | 修复 |
|------|:--:|------|
| `ProductScreen.kt` | L23 | 移除未使用的 `import heightIn` |
| `ProductScreen.kt` | L583-L586 | 更新注释为 `"宽高比 1:1，aspectRatio(1f) 自适应屏幕"` |

---

## 最终结论

| 维度 | 检查项数 | 通过 | 残差 |
|:----|:--------:|:----:|:----:|
| 前置条件完整性 | 8 | 8 | 0 |
| ItemUpdateWrapper 变更链 | 7 | 7 | 0 |
| Worker/缓存/API 变更链 | 7 | 6 | 1 (MEDIUM) |
| UI 布局回归 | 6 | 5 | 1 (LOW) |
| **总计** | **28** | **26** | **2** |

28 项检查中 26 项通过，2 项残差（1 个 MEDIUM 级别 `propertiesName` 空值保护未落地，1 个 LOW 级别 import/注释残差）。无阻塞性回归 bug，无遗漏前置条件。
