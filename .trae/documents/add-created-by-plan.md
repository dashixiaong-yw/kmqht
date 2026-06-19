# v1.66: 取货单私人化 + 发布/领取 — 头脑风暴遗漏项

## ✅ 已覆盖 — 原方案完整部分

- 三态模型（私人→公开→被领取）
- database.py ALTER TABLE + models.py 新字段
- orders.py: publish/claim route、list_orders 过滤、操作路由访问校验
- PDA: DTO、Entity、ApiService、ViewModel、UI

## ⚠️ 遗漏项

### 1. `activeOrders` 是 Room 本地查询 — 需要改为 API 驱动

```kotlin
// PickListViewModel.kt L35-37
val activeOrders: StateFlow<List<PickOrderEntity>> =
    pickOrderRepository.getOrdersByStatus(0)  // ← Room本地查询，不从API拉取
```

**问题**：当前 PDA 只显示**本地创建**的订单。UserA 发布后，UserB 的本地 Room 没有这个订单 → UserB 看不到。UserA 发布后 → 自己 Room 还有旧数据 → 本地没删除。

**修复**：把 `activeOrders` 从 Room 查询改为 API 查询 + StateFlow：

```kotlin
// PickListViewModel.kt
private val _activeOrders = MutableStateFlow<List<PickOrderEntity>>(emptyList())
val activeOrders: StateFlow<List<PickOrderEntity>> = _activeOrders.asStateFlow()

fun loadActiveOrders() {
    viewModelScope.launch {
        val token = userRepository.getToken()
        val response = orderApiService.listOrders(token)  // GET /api/orders
        _activeOrders.value = response.data.map { it.toOrderEntity() }
    }
}
```

`OrderApiService` 新增：
```kotlin
@GET("api/orders")
suspend fun listOrders(@Header("X-User-Token") token: String): OrderListResponse
```

在 `init`、`createOrder`、`publishOrder`、`claimOrder` 完成后都调用 `loadActiveOrders()` 刷新列表。

### 2. 领取竞态条件 — 需校验 affected rows

两个用户同时领取同一取货单：

```python
cursor.execute("""
    UPDATE pick_orders SET visibility = 'private', assigned_to = ?
    WHERE id = ? AND visibility = 'public' AND assigned_to = ''
""", (username, order_id))
if cursor.rowcount == 0:
    raise HTTPException(409, "此取货单已被其他用户领取")
db.commit()
```

**必须**检查 `cursor.rowcount`，否则第二个用户的后端 UPDATE 成功（条件不匹配但不会报错），用户以为领取成功但实际无变化。

### 3. 不能自领 — 创建者不能领取自己的订单

PDA 端限制：当 `order.createdBy == currentUsername` 时，即使已发布也不显示【领取】按钮。

后端不需要加此校验（`created_by != username` 在 SQL 条件中已隐含）。

### 4. 已完成取货单访问过滤

`GET /api/orders?status=1` 也需要按相同权限过滤：

```python
cursor.execute("""
    SELECT * FROM pick_orders
    WHERE (assigned_to = ?) AND status = 1
    ORDER BY created_at DESC
""", (username,))
```

无论 `status` 是 `0` 还是 `1`，都加上 `WHERE (assigned_to = ?) OR (visibility = 'public' AND assigned_to = '')`。目前代码传递 `status: Optional[int]`，需要用动态 SQL 拼接。

**新增 `OrderApiService` 接口** — PDA 端 `getCompletedOrders` 也需要传递当前用户名完成过滤。但后端 API 已经有 `get_current_user`，username 从 token 获取即可，前端不需要传。

### 5. `get_order` 返回 `OrderDetailResponse` 需传递新字段

```python
return OrderDetailResponse(
    ...
    expireAt=order.expireAt,
    createdBy=order.createdBy,     # 新增
    assignedTo=order.assignedTo,   # 新增
    visibility=order.visibility,   # 新增
    items=items,
)
```

### 6. delete_order 仅创建者可删除

```python
@router.delete("/{order_id}", response_model=BaseResponse)
def delete_order(order_id: int, user: dict = Depends(get_current_user)):
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    row = cursor.fetchone()
    if not row: raise HTTPException(404)
    if row["created_by"] != user["username"]:
        raise HTTPException(403, "仅创建者可删除此取货单")
    # ... 原有删除逻辑
```

### 7. ALTER TABLE 每个列独立 try/except

```python
migrations = [
    "ALTER TABLE pick_orders ADD COLUMN created_by VARCHAR(32) NOT NULL DEFAULT ''",
    "ALTER TABLE pick_orders ADD COLUMN assigned_to VARCHAR(32) NOT NULL DEFAULT ''",
    "ALTER TABLE pick_orders ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'private'",
]
for sql in migrations:
    try:
        cursor.execute(sql)
        logger.info(f"数据库迁移: {sql.split('ADD COLUMN')[1].strip()}")
    except sqlite3.OperationalError as e:
        if "duplicate column" in str(e).lower():
            pass
        else:
            raise
```

### 8. PDA 端 OrderResponse → PickOrderEntity 转换函数

`OrderDto.kt` 中需要新增转换函数：

```kotlin
fun OrderResponse.toOrderEntity(): PickOrderEntity = PickOrderEntity(
    id = id,
    orderNo = orderNo,
    status = status,
    completionType = completionType,
    totalCount = totalCount,
    completedCount = completedCount,
    createdAt = TimeUtils.parseBeijingTime(createdAt).let { if (it > 0) it else TimeUtils.now() },
    completedAt = completedAt?.let { TimeUtils.parseBeijingTime(it) },
    expireAt = TimeUtils.parseBeijingTime(expireAt).let { if (it > 0) it else TimeUtils.now() + TimeUtils.DEFAULT_EXPIRE_MS },
    createdBy = createdBy,
    assignedTo = assignedTo,
    visibility = visibility
)
```

同时 `PickOrderEntity` 中 `completedAt` 改为 `Long? = null` 以支持详情页赋值（当前是 `Long?` ✅）。

### 9. 离线队列影响

`deleteOrderWithQueue` / `enqueueOperation` 等离线操作不涉及 `assigned_to`/`visibility` 字段，不受影响。

## 修订后改动清单（17 个文件）

| # | 文件 | 新增漏项 |
|:-:|------|---------|
| 1 | database.py | 3列独立try/except |
| 2 | models.py | (无漏项) |
| 3 | orders.py | claim rowcount检查 + get_order传递新字段 + delete_order创建者校验 + 已完成按权限过滤 |
| 4 | OrderApiService.kt | +`listOrders(status)` 接口 |
| 5 | OrderDto.kt | +`toOrderEntity()` 转换函数 |
| 6 | PickListViewModel.kt | `activeOrders`→API+StateFlow + `loadActiveOrders()` + 创建/发布/领取后刷新 |
| 7 | PickOrderCard.kt | 添加`onPublish`/`onClaim`回调 + 显示创建者/可见性标记 |
| 8 | 其余9个文件 | (原方案不变) |
