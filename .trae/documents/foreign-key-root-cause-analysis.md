# FOREIGN KEY constraint 787 根因分析 + 最佳解决方案

## 一、根因确认方法

3+1 路并行搜索代理 + 深度验证，结果高度一致：

---

## 二、唯一根因：TOCTOU 竞态条件（Time-of-Check to Time-of-Use）

### 完整触发链

```
线程池 T1 (add_item)                   线程池 T2 (delete_order)
│                                        │
├─ _check_order_access(order_id=5)       │
│   → pick_orders.id=5 存在 ✅            │
├─ SELECT status = 0 ✅                   │
├─ SELECT sku 未重复 ✅                   │
│                                        │
├─ ⚠️ asyncio.run(get_sku_info(...))      │
│     → HTTP请求到快麦API (1~3秒)        │
│     → 线程T1被阻塞,无法处理该连接      │
│                                        ├─ DELETE FROM pick_orders WHERE id=5
│                                        │   → 级联删除 pick_items
│                                        ├─ db.commit()
│                                        │   (WAL模式: T1的下次读立即可见)
│                                        │
├─ 🚨 INSERT INTO pick_items(order_id=5) │
│     → FOREIGN KEY constraint failed ❌│
│     → pick_orders.id=5 已不存在       │
│     → db.rollback()                    │
```

### 三个必要条件（缺一不可）

| 条件 | 说明 | 是否满足 |
|:-----|:------|:--------:|
| **① 存在性检查与使用之间的时间窗口** | `_check_order_access`(SELECT) 与 `INSERT` 之间存在网络 IO 调用 `asyncio.run(get_sku_info(...))`，耗时 1~3 秒 | ✅ **是** |
| **② 另一个写操作可以在此期间执行** | `delete_order` 是同步 `def`，在**线程池的另一线程**上并发运行，互不阻塞 | ✅ **是** |
| **③ WAL 模式使得跨连接变更立即可见** | `PRAGMA journal_mode=WAL` 下，T2 的 DELETE+COMMIT 提交后，T1 的下一条 SQL 立即可见 | ✅ **是** |

### 排除的其他可能性

| 曾怀疑的原因 | 验证结论 |
|:-------------|:---------|
| `threading.local` 连接共享（async def 问题） | ❌ `add_item` 当前是同步 `def`，在线程池运行，各线程连接独立 |
| `get_sku_info` 内部 commit 污染外层事务 | ❌ `get_sku_info` 的 INSERT+commit 是独立事务，在 add_item 的 try 块前已完成 |
| Android 端 `orderId` 传错为 0 | ❌ `orderId` 是 `val`，从导航参数稳定传入，正常流程 >= 1 |
| `PRAGMA foreign_keys` 未设置 | ❌ 每个新连接都设置 `PRAGMA foreign_keys=ON` |
| 订单被 `complete_all_items` 删除 | ❌ `complete_all_items` 只改 status，不改 id，不触发 FK 检查 |

---

## 三、方案对比评估

### 候选方案

| 方案 | 描述 | 优点 | 缺点 | 推荐 |
|:----:|:-----|:-----|:-----|:----:|
| **A** | **INSERT前重验证 + 捕获 `sqlite3.IntegrityError`** | 简单、低风险、双重保护 | 极小窗口仍存在，但被 catch 兜底 | **✅ 推荐** |
| **B** | `BEGIN IMMEDIATE` 排他锁包裹整个函数 | 彻底消除 TOCTOU | `asyncio.run` 期间（1~3秒）阻塞所有写操作，并发性能严重下降 | ❌ |
| **C** | 先 INSERT 占位记录 → 查询 SKU 信息 → 再 UPDATE 补全 | 无 TOCTOU | 改变数据模型，需处理中间状态，复杂度高 | ❌ |
| **D** | 仅捕获 `IntegrityError`，不做重验证 | 最简单 | 发生后返回 410，不够友好 | ⚠️ 次选 |
| **E** | 方案A + 自动重试 | 用户无感知 | 复杂度略高 | ⚠️ 可接受 |

### 推荐方案：方案A（INSERT前重验证 + 捕获IntegrityError）

有两道防线：
1. **第一道**：INSERT 前重新 SELECT 验证 order 存在性（将窗口缩小到 SELECT 与 INSERT 之间的一瞬间）
2. **第二道**：即使第一道防线未命中，捕获 `sqlite3.IntegrityError` 返回 410（取货单已被删除）

---

## 四、具体改动

### 改动1：orders.py add_item 添加重验证 + IntegrityError 捕获

```python
# 改前（当前代码 L212-L253）
    sku_info = asyncio.run(get_sku_info(sku_outer_id))
    if not sku_info:
        raise HTTPException(status_code=404, detail=f"未找到SKU信息: {sku_outer_id}")

    now = beijing_now()
    try:
        cursor.execute(
            """INSERT INTO pick_items (order_id, sku_outer_id, sys_item_id, sys_sku_id, ...)""",
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
import sqlite3  # ← 新增 import（顶部已有 sqlite3）

    sku_info = asyncio.run(get_sku_info(sku_outer_id))
    if not sku_info:
        raise HTTPException(status_code=404, detail=f"未找到SKU信息: {sku_outer_id}")

    now = beijing_now()
    try:
        # 第一道防线：重新验证订单存在性（缩小TOCTOU窗口）
        cursor.execute("SELECT id FROM pick_orders WHERE id = ?", (order_id,))
        if not cursor.fetchone():
            raise HTTPException(status_code=410, detail="取货单已被删除")

        cursor.execute(
            """INSERT INTO pick_items (order_id, sku_outer_id, sys_item_id, sys_sku_id, ...)""",
            (order_id, ...)
        )
        cursor.execute("UPDATE pick_orders SET total_count = total_count + 1 WHERE id = ?", (order_id,))
        db.commit()
        ...
    except sqlite3.IntegrityError as e:
        db.rollback()
        # 第二道防线：捕获 FOREIGN KEY 约束失败
        if "FOREIGN KEY constraint failed" in str(e):
            logger.warning(f"取货单 {order_id} 已被删除，添加明细失败")
            raise HTTPException(status_code=410, detail="取货单已被删除，请刷新列表")
        raise
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        logger.error(f"添加取货明细失败: {e}")
        raise HTTPException(status_code=500, detail="添加取货明细失败，请稍后重试")
```

### 改动2（可选）：orders.py delete_order 添加 status 保护

```python
# 改前 L420
cursor.execute("DELETE FROM pick_orders WHERE id = ?", (order_id,))

# 改后
cursor.execute("DELETE FROM pick_orders WHERE id = ? AND status != 1", (order_id,))
```

### 改动3（建议）：images.py upload_image 改为同步 def

`upload_image` 是 `async def` + `await file.read()` + `get_db()` 三要素齐全，有 threading.local 连接共享风险。建议改为同步 `def` 或使用 `asyncio.run()` 封装。

---

## 五、安全性验证

| 检查项 | 结论 |
|:-------|:------|
| 第一道防线（重验证 SELECT）是否100%消除窗口？ | 否，但将窗口从1~3秒缩小到1微秒 |
| 第二道防线（捕获 IntegrityError）是否兜底？ | 是，任何未预见的并发窗口都被捕获 |
| 返回 410 是否比 500 更友好？ | 是，"取货单已被删除" 说明清楚原因，用户刷新列表即可 |
| 对正常流程有无影响？ | 无。额外一次 SELECT 查询，毫秒级 |
| `sqlite3.IntegrityError` 是否只匹配 FK 错误？ | 否，还需判断 `"FOREIGN KEY constraint failed" in str(e)` 区分 UNIQUE 约束 |
| 回归风险 | **极低**。仅在异常路径增加判断，正常路径不变 |

---

## 六、修改清单

| # | 文件 | 改动 | 类型 |
|:--:|:-----|:------|:----:|
| 1 | [orders.py](file:///d:/trea项目\快麦取货通/backend/app/routers/orders.py) | INSERT 前重验证 order 存在性 | **必要** |
| 2 | [orders.py](file:///d:/trea项目\快麦取货通/backend/app/routers/orders.py) | catch `sqlite3.IntegrityError` 区分 FK 异常 | **必要** |
| 3 | [orders.py](file:///d:/trea项目\快麦取货通/backend/app/routers/orders.py) | `delete_order` 加 `WHERE status != 1` | 建议 |
| 4 | [images.py](file:///d:/trea项目\快麦取货通/backend/app/routers/images.py) | `upload_image` 改为同步 `def` | 建议 |
