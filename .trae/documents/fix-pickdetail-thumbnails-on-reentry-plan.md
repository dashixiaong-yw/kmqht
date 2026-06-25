# 修复：退出再进入取货单详情时库区缩略图不显示

## 问题

取货单详情页中，首次进入时库区缩略图和点击查看大图功能正常。**退出后重新进入**，缩略图不显示，点击无反应。

## 根本原因

[PickDetailViewModel.kt L131-L143](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L131-L143) 中图片URL加载逻辑存在**协程竞态条件 (Race Condition)**：

```kotlin
.collectLatest { skuSet ->    // ① collectLatest 会取消前一次处理
    ...
    val map = newSkus.associateWith { sku -> getImageUrls(sku) }  // ② 网络请求，耗时
    _imageUrlsMap.value = current + map
}
```

### 触发时序

重新进入时，`init` 块中并发了两个操作：
1. `items` 流从 **Room 缓存** 发射数据（已有取货明细）
2. `syncItemsFromBackend()` 从**后端API**同步最新的取货明细，并写入Room

| 时间 | 事件 | 影响 |
|:----:|:-----|:-----|
| t1 | | ViewModel 重建，`items` 流发射 Room 缓存的明细数据 |
| t2 | ① | `collectLatest` 收到 SKU 集合 {A, B, C}，开始`getImageUrls(A)`（网络请求） |
| t3 | ② | `syncItemsFromBackend()` 完成，写入 Room → Room 表变化 |
| t4 | | Room 表变化触发`items`流重新发射 |
| t5 | | `items` 流的 `distinctUntilChanged` → 数据相同但对象引用不同 → **放行** |
| t6 | | 映射为 SKU 集 {A, B, C} → `distinctUntilChanged` → 集合相同 → **跳过** |
| t7 | | **t4 的发射被 t6 跳过，collectLatest 收不到新值** |
| t8 | | **t2 正在进行的 getImageUrls 被 t4 的发射取消了 (collectLatest特性)** |
| t9 | | `_imageUrlsMap` 永远为空 → 图片不显示，点击无反应 |

### 本质

`collectLatest` 收到新发射（t4）时**取消**了当前正在进行的图片加载处理（t2），但新发射又被后续的 `distinctUntilChanged` 过滤掉（t6）——**处理被取消但新值从不生效，导致 `_imageUrlsMap` 永远无法被填充**。

### 为什么首次进入没问题？

首次进入时，Room 中没有缓存数据，`items` 流只发射一次（来自 `syncItemsFromBackend` 写入 Room 后的那次），没有并发竞态。

---

## 修复方案

### 涉及文件

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| `app/.../PickDetailViewModel.kt` | L135: `collectLatest` → `collect` | **1 行** |

### 具体 diff

```diff
-                .collectLatest { skuSet ->
+                .collect { skuSet ->
```

`collect` 与 `collectLatest` 的关键区别：

| 特性 | `collectLatest` | `collect` |
|:-----|:---------------:|:---------:|
| 新发射到达时 | **取消**当前处理 | 等待当前处理完成 |
| 竞态安全性 | ❌ 此场景有 bug | ✅ 无此问题 |
| 其他行为 | 相同 | 相同 |

### 为什么改为 `collect` 就修复了？

- t2 开始的 `getImageUrls` 会**正常完成** → `_imageUrlsMap` 被填充
- t4 的发射到来时，t2 的处理不受影响（`collect` 不取消）
- t4 的 SKU 集与 t2 相同 → `distinctUntilChanged` 跳过 → 无额外处理
- 即使 t4 通过了 SKU 集的 `distinctUntilChanged`，`_imageUrlsMap` 中的 `newSkus` 过滤也会跳过已加载的 SKU

### 前置条件

- [x] `getImageUrls` 内部有 try-catch (L493-511)，异常不会冒泡到 `collect` 块 ✅
- [x] `imageUrlsMap` 已在 Compose 侧用 `collectAsState()` 收集 ✅
- [x] 图片URL缓存机制不变，`syncImagesFromBackend` 仍从Room降级 ✅

---

## 验证

| # | 验证项 | 方法 |
|:--|:-------|:-----|
| 1 | 首次进入显示缩略图 | 进入取货单详情 → 缩略图正常显示 → 点击可看大图 |
| 2 | 退出后重进显示缩略图 | 返回取货单列表 → 再次进入 → 缩略图正常显示 → 点击可看大图 |
| 3 | 多次进出 | 重复步骤2 3-5次，每次缩略图都应显示 |
| 4 | 扫码添加新商品 | 进入详情 → 扫码添加新SKU → 新商品的缩略图也能正常显示 |
