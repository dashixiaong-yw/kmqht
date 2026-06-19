# 扫码添加取货单 HTTP 409 修复方案

## 一、问题

扫码添加取货单明细时，后端返回 HTTP 409，客户端显示"添加明细失败: HTTP 409 ..."。

## 二、根因

后端 `POST /api/orders/{orderId}/items` 中检查该 SKU 是否已存在于该取货单：

```python
cursor.execute("SELECT id FROM pick_items WHERE order_id = ? AND sku_outer_id = ?", (order_id, sku_outer_id))
if cursor.fetchone():
    raise HTTPException(status_code=409, detail="该SKU已存在于取货单中")
```

当 SKU 已在后端数据库中时返回 409。客户端收到后走通用 catch 块，没有针对 409 做特殊处理。

**客户端当前行为极其不友好**：

```kotlin
} catch (e: Exception) {
    _errorMessage.value = "添加明细失败: ${e.message}"  // e.message = "HTTP 409 该SKU已存在于取货单中"
    _scanFailureEvent.emit("添加明细失败: ${e.message}")
}
```

点击"确定"关闭 Snackbar 后 **没有后续处理**，本地 Room 没有入库，下次扫码同一条码会再次触发 409，陷入死循环。

## 三、修复方案

**改 1 个文件：[PickDetailViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt)**

### 改动1：onBarcodeScanned catch 块增加 409 判断

```kotlin
// 改前
} catch (e: Exception) {
    _errorMessage.value = "添加明细失败: ${e.message}"
    _scanFailureEvent.emit("添加明细失败: ${e.message}")
}

// 改后
} catch (e: Exception) {
    if (e is retrofit2.HttpException && e.code() == 409) {
        // 服务端已有该SKU，从后端同步最新明细到本地，然后触发重复反馈
        _errorMessage.value = null
        syncItemsFromBackend()
        _duplicateScan.value = true
    } else {
        _errorMessage.value = "添加明细失败: ${e.message}"
        _scanFailureEvent.emit("添加明细失败: ${e.message}")
    }
}
```

### 改动2：新增 syncItemsFromBackend() 轻量同步方法

```kotlin
/**
 * 从后端同步最新明细到本地（不含UI加载状态）
 */
private suspend fun syncItemsFromBackend() {
    try {
        val token = userRepository.getToken()
        val detail = orderApiService.getOrderDetail(token, orderId)
        detail.items.forEach { itemResponse ->
            val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, itemResponse.skuOuterId)
            if (existing == null) {
                pickOrderRepository.insertItem(
                    PickItemEntity(
                        id = itemResponse.id,
                        orderId = orderId,
                        skuOuterId = itemResponse.skuOuterId,
                        sysItemId = itemResponse.sysItemId,
                        sysSkuId = itemResponse.sysSkuId,
                        propertiesName = itemResponse.propertiesName,
                        picPath = itemResponse.picPath,
                        status = itemResponse.status,
                        supplierName = itemResponse.supplierName,
                        supplierCode = itemResponse.supplierCode,
                        remark = itemResponse.remark,
                        createdAt = TimeUtils.parseBeijingTime(itemResponse.createdAt).let { if (it > 0) it else TimeUtils.now() }
                    )
                )
            } else {
                val completedAt = TimeUtils.parseBeijingTimeOrNull(itemResponse.completedAt)
                if (existing.status != itemResponse.status) {
                    pickOrderRepository.updateItemStatusDirect(existing.id, itemResponse.status, completedAt)
                }
            }
        }
        loadSuppliers()
    } catch (_: Exception) { }
}
```

### 改动3：新增 import

```kotlin
import retrofit2.HttpException
```

### 修复后效果

| 场景 | 旧行为 | 新行为 |
|:----|:-------|:-------|
| 扫码 → 本地重复 | 振动+声音+Snackbar"重复扫码！该SKU已在当前取货单中" | ✅ 不变 |
| 扫码 → API 返回 409 | Snackbar"添加明细失败: HTTP 409 ..." → 用户反复扫码死循环 | ✅ 触发重复扫码反馈（振动+声音+Snackbar），同时本地同步该条码数据 |

## 四、安全性验证

| 检查项 | 结论 |
|--------|:----:|
| 409 判断范围 | 仅 HTTP 409 走重复分支，其他 4xx/5xx/网络异常仍走原失败路径 |
| syncItemsFromBackend 中的异常 | 被 `catch (_: Exception) { }` 静默捕获，不影响主流程 |
| PickItemEntity 依赖 | 已 import |
| TimeUtils 依赖 | 已 import |
| loadSuppliers 访问 | ViewModel 私有方法，可调用 |
| `_duplicateScan` 效果 | UI 层已处理：振动+声音+Snackbar+滚动到该行+自动清除 |

## 五、修改清单

| # | 文件 | 改动 |
|:--:|------|------|
| 1 | [PickDetailViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt) | `onBarcodeScanned` catch 块增加 409 判断 |
| 2 | [PickDetailViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt) | 新增 `syncItemsFromBackend()` 方法 |
| 3 | [PickDetailViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt) | 新增 import: `retrofit2.HttpException` |
