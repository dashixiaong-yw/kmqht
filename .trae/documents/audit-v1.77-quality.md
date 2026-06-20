# v1.77 修复质量审计报告

4 路并行审计，覆盖全部 10 个修改文件。

---

## 一、验证通过项（6 项，无缺陷）

| # | 文件 | 修改 | 结论 |
|---|------|------|:--:|
| 1 | `kuaimai_api.py` L194 | `{}`→`result` | ✅ 完美。扁平/嵌套两种响应结构均正确兼容，空dict退化为空列表无异常 |
| 2 | `OrderSyncWorker.kt` L98-L99 | 冲突操作加 `deleteById` | ✅ 正确。解决死循环根因。删除后下轮 `getAllPending()` 不再包含此操作，队列可清空 |
| 3 | `HomeScreen.kt` L273+L276 | icon背景/着色 | ✅ 有效。`BorderGray`(#E5E7EB)+`TextSecondary`(#6B7280) 对比度合理，唯一残留 `SurfaceGray` import（无影响） |
| 4 | `PickOrderCard.kt` L165 | `Spacer(8.dp)`→`weight(1f)` | ✅ 正确。Row 中唯一 weight，圆点推至右端 |
| 5 | `PickItemRow.kt` L213+L226 | 移除 `contentPadding` + `height(36.dp)` | ✅ 安全。13sp 文字在 36dp 高度中正常居中，`PaddingValues` import 已移除无其他引用 |
| 6 | `PickListScreen.kt` NewOrderDialog | loading 时 spinner 替代 disabled 按钮 | ✅ 完美。组件树完全不同不触发 disabled 动画，loading 期间按钮不存在无法点击（防重复提交） |

---

## 二、发现缺陷（3 项）

### 缺陷 1（P1）：`currentSkuDetail` 旧值污染

**位置**：[ProductViewModel.kt:L142](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L142)

**攻击路径**：
```
1. 扫码 SKU-A → API 成功 → currentSkuDetail = SKU-A 详情
2. 扫码 SKU-B → API 失败 → currentSkuDetail 仍为 SKU-A（未被清除）
3. 修改 SKU-B 的备注 → currentItem=null, currentSkuDetail=SKU-A
   → 入队操作使用 SKU-A 的 sysSkuId/sysItemId/skuOuterId
   → Worker 同步时更新了 SKU-A 而不是 SKU-B ← 严重错误
```

**触发条件**：SKU-A 不在 Room、SKU-B 不在 Room、API 对 SKU-B 失败，三条件同时满足。

**修复**：`loadSkuInfo()` 开头加一行：
```kotlin
currentSkuDetail = null
```

---

### 缺陷 2（P1）：`propertiesName.ifBlank { "-" }` 降级 title 污染

**位置**：[PickOrderRepository.kt:L230,L242](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/PickOrderRepository.kt#L230)

当 `propertiesName` 为空字符串时，`safeProperties = "-"`。Worker 的 `getLatestTitle` 在降级时将此 `"-"` 作为 `title` 回传给快麦 API，可能将商品标题覆盖为 `-`。原路径 `updateRemarkWithQueue` 使用 `item.propertiesName`（可能为空串 `""`），空串通常被快麦 API 忽略（不更新 title 字段）。

**修复**：`ifBlank { "-" }` → `ifBlank { "" }`，与原路径保持一致。

---

### 缺陷 3（P3）：`currentItem` 旧值污染（已有 bug）

**位置**：[ProductViewModel.kt L141-L148 降级路径](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L141-L148)

当 API 失败 + Room 查询返回 `null` 时，`currentItem` 保持旧值不变。这是已有 bug（非 v1.77 引入），但应在同版本一并修复。

**修复**：降级路径 `item == null` 时加 `currentItem = null`。

---

## 三、修改清单

| # | 优先级 | 文件 | 行 | 改动 |
|---|:--:|------|:--:|------|
| 1 | 🟠P1 | `ProductViewModel.kt` | L120（loadSkuInfo 开头） | `currentSkuDetail = null` |
| 2 | 🟠P1 | `PickOrderRepository.kt` | L230+L242 | `ifBlank { "-" }` → `ifBlank { "" }` |
| 3 | 🟢P3 | `ProductViewModel.kt` | L148 附近（降级路径） | `item == null` 时 `currentItem = null` |

---

## 四、验证结论

- **6/9 项修改**完全正确，无任何缺陷
- **2 项**存在 P1 级缺陷（旧值污染 + 降级 title）
- **1 项**已有 bug 顺带修复（P3）
- **无编译错误**，无回归 bug
- **P0 级缺陷数**：0

修复这 3 项后，v1.77 可以认定为"无缺陷"。
