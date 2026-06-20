# 修复计划：供应商筛选仍不刷新（v1.86 hotfix）

## 问题

v1.86 修复了「供应商筛选项不即时刷新」（Bug 4），但仍有问题：
**第一个商品添加后，其供应商不会出现在筛选器中，需要添加第二个商品时才出现。**

## 根因

修复代码（v1.86 新增）在 `onBarcodeScanned()` 中：

```kotlin
pickOrderRepository.insertItem(item)
val newSupplier = response.supplierName
if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
    _suppliers.value = _suppliers.value + newSupplier     // ← 手动追加 ✅
}
loadSuppliersFromLocal()   // ← 触发异步覆盖 ❌
loadOrder()
```

`loadSuppliersFromLocal()` 是 **异步函数**（`viewModelScope.launch { ... }`）：

```kotlin
private fun loadSuppliersFromLocal() {
    viewModelScope.launch {          // ← 异步！
        val itemList = items.value   // ← 读的是 Room Flow 未传播的旧数据
        // _suppliers.value = ["全部"]  ❌ 覆盖了手动追加的结果
    }
}
```

执行时序：
```
1. 手动追加 _suppliers = ["全部", "新供应商"]  ✅
2. loadSuppliersFromLocal() 被调用（launch 调度协程）
3. loadOrder() 执行
4. (稍后) loadSuppliersFromLocal 的协程体执行
   → items.value 仍是旧数据（Room Flow 未传播）
   → _suppliers = ["全部"]  ❌ 覆盖！
5. 第二个商品添加时，Room Flow 已完成前一次传播
   → items.value 包含第一个商品
   → loadSuppliersFromLocal 读到正确数据
   → _suppliers 包含第一个供应商 ✅
```

## 修复方案

**将 `loadSuppliersFromLocal` 改为同步函数**，不再 launch 协程。因为调用方 `onBarcodeScanned` 已经在 `viewModelScope.launch` 中，内部直接读 `items.value` 快照即可。

| 文件 | 修改 | 行数 |
|:-----|:-----|:-----|
| [PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt) | `loadSuppliersFromLocal()` 去掉 `viewModelScope.launch` 包装 | -2 |

```kotlin
// 修改前
private fun loadSuppliersFromLocal() {
    viewModelScope.launch {
        try {
            val itemList = this@PickDetailViewModel.items.value
            ...
        } catch ...
    }
}

// 修改后
private fun loadSuppliersFromLocal() {
    try {
        val itemList = items.value
        val suppliers = itemList.map { it.supplierName }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        _suppliers.value = listOf(AppConstants.SUPPLIER_ALL_LABEL) + suppliers
    } catch (e: Exception) {
        Log.w(TAG, "从本地提取供应商列表失败: ${e.message}")
    }
}
```

执行时序变为同步：
```
1. loadSuppliersFromLocal() → _suppliers = ["全部"]（Room未传播，旧数据）
2. 手动追加 → _suppliers = ["全部", "新供应商"]  ✅
3. loadOrder()
```

## 回归风险

| 风险 | 评估 |
|:-----|:------|
| `loadSuppliersFromLocal` 是 suspend 函数吗？ | ❌ 不是。它不调用任何 suspend 函数，仅读 StateFlow 快照 |
| 其他调用方？ | ✅ 只有 `onBarcodeScanned()` 一处调用 |
| Room Flow 同步问题？ | ✅ `loadSuppliersFromLocal` 本身读的是 StateFlow 快照，不在 UI 线程阻塞 |

## 验证

1. 新建一个空的取货单
2. 添加第一个商品（该商品有供应商A）
3. 立即查看供应商筛选器 → FilterChip 应出现「供应商A」
4. 添加第二个不同供应商的商品 → 筛选器应同时显示两个供应商
