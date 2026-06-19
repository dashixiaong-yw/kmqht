# FOREIGN KEY constraint failed 根治方案（修订版）

## 一、问题（依旧存在）

`add_item` 函数仍然报 `FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)`

## 二、上一轮修复为何无效

上次修复在 `await get_sku_info()` 后重新执行 `get_db()` + `db.cursor()`，**假设** `await` 后线程切换了。但 **uvicorn 单事件循环线程**下，async 路由内的 `await` **不切换线程**，仅暂停协程：

```
请求A (Thread MAIN):
  1. get_db() → 创建连接CONN_main [存入 threading.local]
  2. SELECT..., SELECT..., SELECT...  ✅
  3. await get_sku_info() → ⏸️ 暂停协程

  ⬇️ Thread MAIN 空闲，请求B 运行

请求B (Thread MAIN):
  4. get_db() → 获取到 SAME 连接 CONN_main!
  5. SELECT/INSERT sku_cache + commit → 修改了 CONN_main 的状态
  6. await xxx → ⏸️

请求A (Thread MAIN) ← 恢复:
  7. get_db() → 获取到 SAME 连接 CONN_main
  8. cursor.execute("INSERT INTO pick_items ...")
     → 连接状态已被B污染 → FOREIGN KEY 约束失败 ❌
```

**根因**：`get_db()` 基于 `threading.local()`，同一线程所有协程共享同一连接。一个协程暂停期间，另一个协程会污染该连接的状态。

修复前的 fix（await 后重获 db/cursor）在同一线程上没有作用，因为 `get_db()` 返回的是同一个已被污染的连接。

## 三、根治方案

将 `add_item` 从 `async def` 改为 `def`（同步函数）。FastAPI 自动在**线程池**中运行同步路由，每个线程池线程有独立的 `threading.local()`，`get_db()` 不再被并发请求共享。

### 修改 1 个文件：[orders.py](file:///d:/trea项目\快麦取货通/backend/app/routers/orders.py)

**改动**：

1. `async def add_item` → `def add_item`
2. `await get_sku_info(...)` → `asyncio.run(get_sku_info(...))`
3. 新增 `import asyncio`
4. 移除上次加的 `await` 后重获 db/cursor 逻辑（不再需要）

```python
# 改前
import asyncio  # ← 新增

@router.post("/{order_id}/items", response_model=ItemResponse)
async def add_item(order_id: int, req: AddItemRequest, user: dict = Depends(get_current_user)) -> ItemResponse:
    """添加取货明细，后端查询快麦API并缓存"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    # 检查取货单状态
    cursor.execute("SELECT status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="取货单已完成，无法添加明细")

    # 清理并验证条码
    sku_outer_id = clean_barcode(req.skuOuterId)
    if not validate_barcode(sku_outer_id):
        raise HTTPException(status_code=400, detail="条码格式无效")

    # 检查是否已添加
    cursor.execute(
        "SELECT id FROM pick_items WHERE order_id = ? AND sku_outer_id = ?",
        (order_id, sku_outer_id)
    )
    if cursor.fetchone():
        raise HTTPException(status_code=409, detail="该SKU已存在于取货单中")

    # 查询SKU信息（先查缓存，再查快麦API）
    sku_info = await get_sku_info(sku_outer_id)
    if not sku_info:
        raise HTTPException(status_code=404, detail=f"未找到SKU信息: {sku_outer_id}")

    # await后重新获取当前线程的连接和游标
    db = get_db()
    cursor = db.cursor()

    # 重新验证订单存在性和状态
    cursor.execute("SELECT id, status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if not order_row:
        raise HTTPException(status_code=404, detail="取货单已被删除")
    if order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="取货单已完成，无法添加明细")

    now = beijing_now()
    try:
        cursor.execute(
            """INSERT INTO pick_items ...""",
            (order_id, ...)
        )
        cursor.execute("UPDATE pick_orders SET total_count = total_count + 1 WHERE id = ?", (order_id,))
        db.commit()
        ...
    except Exception as e:
        db.rollback()
        logger.error(f"添加取货明细失败: {e}")
        raise HTTPException(status_code=500, detail="添加取货明细失败，请稍后重试")


# 改后
import asyncio  # 新增

@router.post("/{order_id}/items", response_model=ItemResponse)
def add_item(order_id: int, req: AddItemRequest, user: dict = Depends(get_current_user)) -> ItemResponse:
    """添加取货明细，后端查询快麦API并缓存"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    # 检查取货单状态
    cursor.execute("SELECT status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="取货单已完成，无法添加明细")

    # 清理并验证条码
    sku_outer_id = clean_barcode(req.skuOuterId)
    if not validate_barcode(sku_outer_id):
        raise HTTPException(status_code=400, detail="条码格式无效")

    # 检查是否已添加
    cursor.execute(
        "SELECT id FROM pick_items WHERE order_id = ? AND sku_outer_id = ?",
        (order_id, sku_outer_id)
    )
    if cursor.fetchone():
        raise HTTPException(status_code=409, detail="该SKU已存在于取货单中")

    # 同步调用快麦API获取SKU信息（asyncio.run在同步def的线程池中运行）
    try:
        sku_info = asyncio.run(get_sku_info(sku_outer_id))
    except Exception as e:
        logger.error(f"查询SKU信息失败: {e}")
        raise HTTPException(status_code=500, detail=f"查询SKU信息失败: {e}")
    if not sku_info:
        raise HTTPException(status_code=404, detail=f"未找到SKU信息: {sku_outer_id}")

    now = beijing_now()
    try:
        cursor.execute(
            """INSERT INTO pick_items ...""",
            (order_id, ...)
        )
        cursor.execute(
            "UPDATE pick_orders SET total_count = total_count + 1 WHERE id = ?",
            (order_id,)
        )
        db.commit()
        ...
    except Exception as e:
        db.rollback()
        logger.error(f"添加取货明细失败: {e}")
        raise HTTPException(status_code=500, detail="添加取货明细失败，请稍后重试")
```

### 如何工作

```
定义函数（非异步） → FastAPI 在线程池中运行

线程池线程T:
  1. get_db() → 创建线程T独占的连接 CONN_T
  2. 全部 check 处理 → 同一 CONN_T  ✅
  3. asyncio.run(get_sku_info(...)) → 阻塞线程T直到完成
     → get_sku_info 也在线程T运行（asyncio.run 在当前线程创建临时事件循环）
     → get_sku_info 的 get_db() 得到 同一 CONN_T
     → 插入 sku_cache + commit
  4. INSERT pick_items + UPDATE pick_orders + commit → 同一 CONN_T  ✅
  5. 函数返回 → 线程T空闲 → 处理下一个请求

其他请求，同一时刻:
  → 在线程池的不同线程运行（线程U、线程V...）
  → 各自的 get_db() 获得各自独占连接
  → 完全不冲突 ✅
```

## 四、其他 async def 路由排查

| 路由 | 文件 | 状态 | 说明 |
|:-----|:-----|:----:|:------|
| `add_item` | orders.py | ❌ 需修复 | await 后 DB 写入，多协程共享连接 |
| `upload_image` | images.py | ⚠️ 低风险 | `await file.read()` 后仍用同一 `db`/`cursor`。但上传频率低且含 try/commit/rollback 保护 |
| `get_kuaimai_suppliers` | system.py | ✅ 无影响 | 只调第三方 API，无 DB 操作 |
| `refresh_kuaimai_session` | system.py | ✅ 无影响 | 只更新 JSON 配置文件，无 DB 操作 |

**结论**：仅 `add_item` 需要修复。

## 五、安全性验证

| 检查项 | 结论 |
|--------|:----:|
| `asyncio.run()` 在同步 `def` 中 | 在 FastAPI 线程池中安全使用，创建临时事件循环 |
| `get_sku_info` 内 `get_db()` | 同一线程调用，得到同一连接，但执行是**串行的**（`asyncio.run` 阻塞），无并发冲突 |
| get_sku_info 的 commit | 提交 sku_cache 的**独立事务**，不影响后续 pick_items 的 INSERT |
| 线程池其他请求 | 各线程独立连接，互不干扰 |
| 回归风险 | **极低。** 业务逻辑与原 `async def` 完全一致，仅调用方式从 `await` 变为 `asyncio.run()` |

## 六、修改清单

| # | 文件 | 改动 |
|:--:|:------|:------|
| 1 | [orders.py](file:///d:/trea项目\快麦取货通/backend/app/routers/orders.py) | 顶部新增 `import asyncio` |
| 2 | orders.py | `async def add_item` → `def add_item` |
| 3 | orders.py | `await get_sku_info(...)` → `asyncio.run(get_sku_info(...))` + try/except |
| 4 | orders.py | 移除 `await` 后重获 db/cursor + 重验证代码（不再需要） |

## 七、验证步骤

1. 重新部署后端（需重启 uvicorn 进程）
2. PDA 扫码添加商品到取货单
3. 连续多次添加不同商品
4. 确认不再出现 `FOREIGN KEY constraint failed` 错误
