# 取货单详情视口滚动 — 头脑风暴

## 根因确认

`PickOrderDao.insert()` 使用了 `OnConflictStrategy.REPLACE`：

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insert(order: PickOrderEntity): Long
```

当订单已存在时，REPLACE = DELETE + INSERT。由于 `pick_item` 外键声明了 `onDelete = ForeignKey.CASCADE`，DELETE 级联删除所有明细 → LazyColumn 经历 N→0→1→2→...→N 的数据剧烈波动 → `firstVisibleItemIndex` 漂移 → `snapshotFlow` 检测到 → `scrollToItem(0)` → **从下往上的可见滚动**。

## 5 个备选方案

### 方案A（⭐推荐）— 修改 syncItemsFromBackend，不触发 CASCADE

`syncItemsFromBackend` 中已经有 `_order.value` 和 `upsertItemFromResponse`，不需要 REPLACE 来重建数据：

```kotlin
// 改前
pickOrderRepository.insertOrder(orderEntity) // REPLACE → CASCADE 删除 → 数据波动

// 改后
val existing = pickOrderRepository.getOrderById(orderId)
if (existing != null) {
    pickOrderRepository.updateOrder(orderEntity) // UPDATE → 不触发 CASCADE
} else {
    pickOrderRepository.insertOrder(orderEntity)
}
```

同时清理所有滚动相关代码（删除 snapshotFlow + LaunchedEffect），因为 LazyColumn 默认 index=0 已经满足需求。

| 优点 | 缺点 |
|:-----|:-----|
| 从根源解决，无需滚动 | 需确认 `updateOrder` 行为正确 |
| 零可见滚动 | - |
| 删除全部滚动代码，-10 行 | - |

---

### 方案B — 改 Dao，REPLACE → INSERT_OR_ABORT

```kotlin
@Insert(onConflict = OnConflictStrategy.ABORT)
suspend fun insert(order: PickOrderEntity): Long
```

首次插入时使用 ABORT（不存在则插入，存在则忽略）。再配一个 `update` 用于已有订单。

| 优点 | 缺点 |
|:-----|:-----|
| 简单 | 改动范围不确定（其他调用 insertOrder 的地方是否有依赖 REPLACE 行为） |

---

### 方案C — scroll 位置持久化（你提出的保存/恢复）

进入时保存 `firstVisibleItemIndex` → 退出 → 再进入 → 直接恢复到该位置。

| 优点 | 缺点 |
|:-----|:-----|
| 直观，用户回到上次位置 | 需维护 Map，状态多 |
| | CASCADE 数据波动仍然发生，只是视觉上跳过去 |

---

### 方案D — snapshotFlow 加 debounce

在现有 snapshotFlow 上加 `debounce(200ms)`，滤掉 CASCADE 波动期间的短暂漂移：

```kotlin
snapshotFlow { listState.firstVisibleItemIndex }
    .debounce(200)
    .collect { ... }
```

| 优点 | 缺点 |
|:-----|:-----|
| 改动最小，2 行 | 治标不治本，200ms 在这个时间窗口够不够？ |
| | 仍然有微弱的可见闪烁 |

---

### 方案E — 跳过 init 中的 sync

```kotlin
init {
    loadOrder()
    loadSuppliers()
    // 不调用 syncItemsFromBackend()，Room 已有数据
}
```

| 优点 | 缺点 |
|:-----|:-----|
| 根本不需要 CASCADE | OTA 更新的数据无法及时获取 |
| | 需要另外的 OTA 同步机制 |

---

## 建议

**方案A** 从根源解决问题。`syncItemsFromBackend` 的职责是"从后端同步最新数据到本地"，不应该用 REPLACE 重建已有数据。已存在则 update，不存在则 insert，这是最自然的语义。

如果选方案A，推荐一并删除 v2.43/v2.44 加的所有滚动代码（snapshotFlow + LaunchedEffect），因为 CASCADE 不再发生，LazyColumn 默认 index=0 已完全满足需求。净减 ≈20 行。

## 版本号

2.44 → 2.45
