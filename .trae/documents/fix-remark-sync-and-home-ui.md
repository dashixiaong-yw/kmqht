# 全面修复计划：备注/供应商回传 + 供应商列表 + 死循环 + UI 修复

## 一、P0-1：备注回传失败（独立扫码也可修改）

### 根因

[ProductViewModel.kt:L127-L137](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L127-L137)

API 成功分支只有 UI 更新，`currentItem` 和 API 数据均未缓存，保存时无可用的 `sys_sku_id` / `sys_item_id` 等字段。

### 修复

**(1) ProductViewModel — 缓存 API 响应 + 新增独立保存路径**

- 新增 `private var currentSkuDetail: SkuDetailResponse? = null`
- `loadSkuInfo()` API 成功分支：`currentSkuDetail = detail` + 尝试从 Room 加载 `currentItem`
- `confirmSaveRemark()`：先尝试 `currentItem`（走原路径），否则走 `currentSkuDetail` 直接入队。⚠️ payload 中 `propertiesName` 为空时使用 `ifBlank { "-" }` 保护
- `confirmChangeSupplier()`：同上

**(2) PickOrderRepository — 新增两个直接入队方法**

```kotlin
suspend fun enqueueRemarkUpdateDirect(
    skuOuterId: String, sysSkuId: Long, sysItemId: Long,
    propertiesName: String, remark: String
)
suspend fun enqueueSupplierUpdateDirect(
    skuOuterId: String, sysSkuId: Long, sysItemId: Long,
    propertiesName: String, supplierName: String, supplierCode: String
)
```

---

## 二、P0-2：供应商选择对话框显示"暂无供应商数据"

### 根因

[kuaimai_api.py:L194](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py#L194)

```python
return result.get("supplier_list_query_response", {}).get("list", [])
```

`get_supplier_list` 使用 multipart 编码 (`files=`)，快麦 API 返回扁平结构无包装键。搜索 `supplier_list_query_response` 不到 → 退化为 `{}` → 永远返回 `[]`。

### 修复

```python
return result.get("supplier_list_query_response", result).get("list", [])
```

---

## 三、P1：Worker 冲突操作死循环

### 根因

[OrderSyncWorker.kt:L95-L106](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L95-L106)

冲突操作（retryCount=-1）只打日志不删除 → `getAllPending()` 永不空 → `hasWork` 永为 true → while 死循环。

### 修复

```kotlin
if (current.retryCount == -1) {
    Log.w(TAG, "操作已标记冲突，删除: ${op.operationType} orderId=$orderId")
    dao.deleteById(op.id)
}
```

---

## 四、P2-1：首页设置模块 icon 与底色无法分辨

### 根因

[HomeScreen.kt:L273-L278](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L273-L278)

`SurfaceGray` (#F3F4F6) 与 `SurfaceWhite` (#FFFFFF) 色差仅 2.2%，白色 icon 完全不可见。

### 修复

```kotlin
iconBgColor = BorderGray,       // #E5E7EB 色差 ~10%
icon = { Icon(Settings, tint = TextSecondary, ...) }  // #6B7280 深灰
```

---

## 五、P2-2：取货单进度圆点靠右对齐

### 根因

[PickOrderCard.kt:L165](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickOrderCard.kt#L165)

`Spacer(8.dp)` 圆点紧贴文字，卡片宽时局促。

### 修复

```kotlin
Spacer(modifier = Modifier.weight(1f))
```

---

## 六、P2-3：取货单详情完成按钮高度降低

### 根因

[PickItemRow.kt:L213+L226](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt#L213-L227)

`contentPadding = PaddingValues(vertical = 4.dp)` 按钮高度 ~26dp，偏大。

### 修复

参照"取货列表 → + 新建"按钮方式：

```kotlin
// 完成/恢复按钮
modifier = Modifier.height(36.dp).defaultMinSize(minWidth = 56.dp)
// 移除 contentPadding，用 TextButton 默认内边距
```

---

## 七、P2-4：新建取货单选择拣货区后闪烁

### 根因

[PickListViewModel.kt:L146](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListViewModel.kt#L146)

`_isLoading.value = true` 触发 NewOrderDialog 全量重组——所有按钮 `enabled = !isLoading` 同时切换→肉眼可见闪烁。

### 修复

保持 `_isLoading` 的防重复提交作用，但优化 NewOrderDialog 的 UI 呈现——loading 时隐藏按钮列表，显示加载指示器，消除 enabled=false 动画引起的闪烁：

```kotlin
// NewOrderDialog 中：
if (isLoading) {
    Box(modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
} else {
    areas.forEach { area ->
        Button(onClick = { onConfirm(area) }, ...) {
            Text(area)
        }
    }
}
```

---

## ⚠️ 前置依赖（执行前必须完成）

以下 3 项是第1项（备注/供应商回传）能编译通过的前提，缺少任意一个会导致编译失败：

| # | 依赖项 | 说明 |
|---|--------|------|
| D1 | `ProductViewModel.kt` 新增 import | `import com.kuaimai.pda.data.api.dto.SkuDetailResponse` — 当前未导入 |
| D2 | `PickOrderRepository.kt` 接口新增签名 | `enqueueRemarkUpdateDirect` + `enqueueSupplierUpdateDirect` — 当前接口无此二方法 |
| D3 | `PickOrderRepositoryImpl.kt` 新增实现 | 两个方法实现（复用 `enqueueOperation`，`orderId = 0L`，`targetId = 0L`） |

---

## 修改清单

| # | 优先级 | 文件 | 改动 |
|---|:--:|------|------|
| 1 | 🔴P0 | `ProductViewModel.kt` | API 分支缓存 `currentSkuDetail` + 独立扫码入队路径 + new import |
| 2 | 🔴P0 | `PickOrderRepository.kt` 接口 | 新增 `enqueueRemarkUpdateDirect` / `enqueueSupplierUpdateDirect` |
| 3 | 🔴P0 | `PickOrderRepository.kt` 实现 | 两个方法的实现 |
| 4 | 🔴P0 | `kuaimai_api.py` L194 | `{}` → `result` |
| 5 | 🟠P1 | `OrderSyncWorker.kt` L98-L99 | 冲突操作 `deleteById` |
| 6 | 🟡P2 | `HomeScreen.kt` L273+L276 | `SurfaceGray`→`BorderGray` + `White`→`TextSecondary` |
| 7 | 🟡P2 | `PickOrderCard.kt` L165 | `Spacer(8.dp)`→`Spacer(weight(1f))` |
| 8 | 🟡P2 | `PickItemRow.kt` L213+L226 | 移除 `contentPadding` 加 `Modifier.height(36.dp)` |
| 9 | 🟡P2 | `PickListScreen.kt` NewOrderDialog | loading时隐藏按钮显示加载中，消除闪烁 |

---

## 验证步骤

1. 扫码取货单中 SKU → 修改备注/供应商 → 快麦 ERP 验证已更新
2. 独立扫码（非取货单 SKU）→ 备注/供应商也能正常保存
3. 点击"切换供应商" → 弹出供应商列表
4. 新建取货单→选择拣货区→无闪烁
5. 首页设置模块图标清晰可见
6. 取货单进度圆点靠右
7. 完成/恢复按钮高度约 36dp
8. `./gradlew lint` + `./gradlew assembleRelease` 通过
