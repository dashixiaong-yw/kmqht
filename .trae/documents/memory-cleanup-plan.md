# App 内存泄漏 & 缓存清理修复计划

> 全量审计结论

---

## 关键发现

**后端已全部有清理机制**（main.py），无需额外修复：

| 后端表 | 清理机制 | 周期 | 保留期 |
|:-------|:---------|:-----|:-------|
| `crash_logs` | `_cleanup_crash_logs()` ✅ | 每天04:00 | 30天 |
| `sku_cache` | `_cleanup_sku_cache()` ✅ | 每6小时 | 30天 |
| `pick_orders` | `_cleanup_completed_orders()` ✅ | 每天03:00 | 30天 |
| 孤立图片 | `_cleanup_orphan_images()` ✅ | 每天03:30 | 7天 |
| SKU缓存索引 | `idx_sku_cache_cached_at` ✅ | 已有索引 | — |

**Android 端全部缺失清理机制**，需新增。

---

## Android端修复（5项）

| # | 问题 | 严重度 | 现有 DAO 方法 | 修复方式 |
|:-:|:-----|:------:|:--------------|:---------|
| A1 | `pending_operation`冲突记录永不清除 | **高** | 无批量删除 | PendingOperationDao 新增 + Worker 触发 |
| A2 | 已完成取货单+明细永不清除 | **高** | 无按时间删除 | PickOrderDao+PickItemDao 新增 + Worker 触发 |
| A3 | `pending_images`孤立文件堆积 | **中** | — | 标记冲突前删文件 |
| A4 | ANR日志文件无上限 | **中** | — | App.logAnr 写入前清理 |
| A5 | `product_image`旧数据不清理 | **低** | 无按时间删除 | ProductImageDao 新增 + App启动触发 |

---

## 修改方案

### A1：`pending_operation` 冲突清理

**文件**：[PendingOperationDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/PendingOperationDao.kt)

新增方法：
```kotlin
@Query("DELETE FROM pending_operation WHERE created_at < :before AND retry_count = -1")
suspend fun deleteConflictsOlderThan(before: Long): Int
```

### A2：已完成取货单清理

**文件**：[PickOrderDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/PickOrderDao.kt)

```kotlin
@Query("DELETE FROM pick_order WHERE status = 1 AND completed_at < :before")
suspend fun deleteCompletedOlderThan(before: Long): Int
```

**文件**：[PickItemDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/PickItemDao.kt)

```kotlin
@Query("DELETE FROM pick_item WHERE order_id NOT IN (SELECT id FROM pick_order)")
suspend fun deleteOrphanItems(): Int
```

### A3：`pending_images` 孤立文件

**文件**：[OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L119-L121)

```kotlin
} else if (current.retryCount >= MAX_RETRY) {
    // upload_image 失败时删除对应文件
    if (op.operationType == "upload_image") {
        val filePath = extractPayloadValue(op.payload, "file_path")
        filePath?.let { File(it).delete() }
    }
    Log.e(TAG, "操作同步失败超过${MAX_RETRY}次，标记冲突: ${op.operationType}")
    dao.updateRetryCount(op.id, -1)
}
```

### A4：ANR日志文件上限

**文件**：[App.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/App.kt#L113-L144)

在 `logAnr` 开头添加清理：
```kotlin
val logDir = File(getExternalFilesDir(null), "anr_logs")
if (logDir.exists()) {
    val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    logDir.listFiles()?.forEach { if (it.lastModified() < sevenDaysAgo) it.delete() }
}
```

### A5：`product_image` 旧数据清理

**文件**：[ProductImageDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/ProductImageDao.kt)

```kotlin
@Query("DELETE FROM product_image WHERE created_at < :before")
suspend fun deleteOlderThan(before: Long): Int
```

**触发**：`App.onCreate()` 末尾

### 清理触发：`OrderSyncWorker.doWork()` 末尾

**文件**：[OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L135-L141)

在 `Result.success()` 前增加清理逻辑：
```kotlin
// 清理冲突记录（超过7天）
try {
    val oneWeekAgo = java.lang.System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val cleaned = dao.deleteConflictsOlderThan(oneWeekAgo)
    if (cleaned > 0) appendLog(applicationContext, "清理冲突记录: ${cleaned}条")
} catch (_: Exception) { }

// 清理已完成取货单（超过7天）
try {
    val oneWeekAgo = java.lang.System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val cleaned = dao.deleteCompletedOlderThan(oneWeekAgo)
    if (cleaned > 0) {
        itemDao?.deleteOrphanItems()
        appendLog(applicationContext, "清理已完成取货单: ${cleaned}单")
    }
} catch (_: Exception) { }
```

---

## 修改文件清单

| 文件 | 修改内容 | 行数 |
|:-----|:---------|:----:|
| [PendingOperationDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/PendingOperationDao.kt) | 新增 `deleteConflictsOlderThan` | +2 |
| [PickOrderDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/PickOrderDao.kt) | 新增 `deleteCompletedOlderThan` | +2 |
| [PickItemDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/PickItemDao.kt) | 新增 `deleteOrphanItems` | +2 |
| [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | 3处修改：markConflict删文件 + doWork末尾清理(冲突+订单) | ~15 |
| [App.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/App.kt) | logAnr 清理旧文件 + onCreate 清理旧图片 | ~15 |
| [ProductImageDao.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/dao/ProductImageDao.kt) | 新增 `deleteOlderThan` | +2 |

---

## 回归风险检查

| 修复 | 风险点 | 评估 |
|:-----|:-------|:------|
| A1 | 误删未处理的冲突记录 | ✅ 条件 `retry_count = -1` 仅标记为冲突的记录，不会影响正在重试的记录 |
| A2 | 误删7天内的已完成订单 | ✅ `completed_at < 7天前` 条件确保不会 |
| A2 | `deleteOrphanItems` 子查询性能 | ✅ Room/后端 SQLite 均有索引 |
| A3 | 文件仍在上传时被删除 | ✅ 仅标记冲突（3次重试后）才删，此时已放弃该操作 |
| A4 | `logDir.listFiles()` NPE | ✅ `?` 安全调用 |
| A5 | `onCreate` 中调用 DAO 依赖注入 | ✅ `@Inject lateinit var` 在 `onCreate` 前已初始化 |

---

## 验证

1. 修改备注/供应商 → Worker 同步成功后 → 查看日志应有 `清理冲突记录: N条` `清理已完成取货单: N单`
2. 检查 ANR 日志目录：只保留最近 7 天文件
3. 数据库大小：持续使用一周后不再增长
