# v1.86 Bug 修复完整性审计

> 逐行检查所有已修改文件的当前状态

---

## Bug 1/2：Worker 备注/供应商同步 ⚠️ DTO+降级链 正确，但还有1处需要确认

### ✅ 已正确

**KuaimaiQueryDto.kt L31**: `val title: String = ""` — 已添加，API 返回此字段，Gson 将正确反序列化

**OrderSyncWorker.kt L382-426** `fetchLatestSkuData`:
- 主路径：`getItemDetail.title` → `sku?.title` → 跳过 ✅
- fallback 路径：`fallback.title` → `sku?.title` → 跳过 ✅
- `sku?.title` 使用安全调用，`sku` 可能为 null ✅
- appendLog 记录成功/失败 ✅

**OrderSyncWorker.kt L270-373** `syncRemarkUpdate`/`syncSupplierUpdate`:
- 所有 `?: return false` 改为 `?: run { Log.e; appendLog; return false }` ✅
- 成功/失败均有 appendLog ✅

### ✅ 回归安全
- `appendLog` 内部 `catch (_: Exception)` 静默忽略，不影响主要流程 ✅

---

## Bug 3：httpx 连接池 ✅ 已正确修复

### ✅ 已正确

**kuaimai_api.py L27-34** `_get_client`:
```python
limits=httpx.Limits(max_keepalive_connections=5, max_connections=10, keepalive_expiry=30.0)
```
- 控制连接池复用，30秒主动释放 ✅

**kuaimai_api.py L95-144** `_call_api`:
- 内层 try 捕获 `TransportError` 最多重试1次（500ms间隔）✅
- 耗尽重试后 raise，外层 `except httpx.RequestError` 兜底（因 TransportError 继承自 RequestError）✅
- `if last_exception: raise` 是冗余安全代码，路径实际不会触发 ✅

### ✅ 回归安全
- 连接池参数不会影响已有功能 ✅
- 异常传播路径正确 ✅

---

## Bug 4：供应商筛选器 ❌ 仍有问题（需修复）

### 当前位置

**PickDetailViewModel.kt L186-192** `onBarcodeScanned()`:

```kotlin
pickOrderRepository.insertItem(item)                    // 写入Room（未传播）
val newSupplier = response.supplierName
if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
    _suppliers.value = _suppliers.value + newSupplier   // ["全部","新供应商A"] ✅
}
loadSuppliersFromLocal()                                // ❌ 覆盖!!! → ["全部"]
loadOrder()
```

**`loadSuppliersFromLocal()` L135-146** 现在是同步的，所以：
1. 手动追加 → `["全部", "A"]`
2. `loadSuppliersFromLocal()` 立即执行 → 读 `items.value`（Room未传播，只有旧数据）→ `["全部"]`
3. 步骤1的结果被覆盖

### 🔧 需要修复

**删除 L191 的 `loadSuppliersFromLocal()` 调用**。删除后：
1. 手动追加 → `["全部", "A"]`
2. 后续刷新/重新进入时 `loadSuppliers()`（网络）或 Room Flow 自行传播后补充

---

## 导出日志 ❌ 需改为弹窗显示

### 当前问题

**SettingsScreen.kt L380-400** 使用 `ACTION_SEND + FileProvider`：
- PDA 上无微信/QQ → "没有应用可执行操作"
- 现在 file_paths.xml 中多了一个未使用的 `cache-path` 配置（无害）

### 🔧 需要改为

**「导出同步日志」→「查看同步日志」**，点击弹窗显示日志文件内容并提供「复制」按钮。

---

## 总结：还有 2 处需要改动

| # | 文件 | 改动 | 行数 |
|:-:|:-----|:-----|:----:|
| 1 | PickDetailViewModel.kt L191 | **删除** `loadSuppliersFromLocal()` 调用 | -1 |
| 2 | SettingsScreen.kt L380-400 | 替换为弹窗显示日志内容 | ~30 |

其他文件（KuaimaiQueryDto.kt、OrderSyncWorker.kt、kuaimai_api.py、file_paths.xml）**已正确，无需修改。**
