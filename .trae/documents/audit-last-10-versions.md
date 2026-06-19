# 最近10版本并行审计报告（v1.65 - v1.74）

## 审计概况

| 审计轮次 | 版本 | 缺陷数 | 风险点数 |
|:--|:--|:--:|:--:|
| 组1 | v1.73 + v1.74 | 4 | 4 |
| 组2 | v1.71 + v1.72 | 0 | 5 |
| 组3 | v1.67 + v1.68 | 3 | 5 |
| 组4 | v1.69 + v1.70 | 2 | 3 |
| 组5 | v1.65 + v1.66 | 1 | 2 |
| **合计** | **10个版本** | **10** | **19** |

---

## 缺陷清单（按严重度排序）

### 🔴 Critical（1 项）

| # | 版本 | 描述 | 位置 |
|---|:--:|------|------|
| **C1** | v1.66 | **创建者在取货单被领取后失去访问权**。`list_orders` 和 `_check_order_access` 只检查 `assigned_to`，未检查 `created_by`。A创建→发布→B领取后，A的列表不再显示该订单、无法查看详情、无法删除 | [orders.py:L96](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L96) + [L551-L553](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L551-L553) |

### 🟠 高危（F1）（1 项）

| # | 版本 | 描述 | 位置 |
|---|:--:|------|------|
| **F1-1** | v1.68 | **SSRF 漏洞**：`/api/images/proxy?url=` 无白名单校验，认证用户可探测内网服务或代理访问任意 URL | [images.py:L212-L221](file:///d:/trea项目/快麦取货通/backend/app/routers/images.py#L212-L221) |

### 🟡 中等（F2）（6 项）

| # | 版本 | 描述 | 位置 |
|---|:--:|------|------|
| **F2-1** | v1.74 | **存量用户权限不兼容**：v1.74 将后端权限 `settings`→`update_supplier`，但已有用户的 `settings` 权限不自带 `update_supplier`，升级后无法获取供应商列表、看不到切换按钮 | [system.py:L175](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L175) |
| **F2-2** | v1.74 | **Worker 漏同步**：`ExistingWorkPolicy.KEEP` + Worker 一次性读取队列 = 用户连续保存多个备注/供应商时，后续操作可能永不执行 | [PickOrderRepository.kt:L259](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/PickOrderRepository.kt#L259) + [OrderSyncWorker.kt:L77](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L77) |
| **F2-3** | v1.70 | **delete_order 缺 rowcount 检查**：竞态导致假成功——用户看到"已删除"但实际未删，后续创建同名单号触发 UNIQUE 冲突 | [orders.py:L434](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L434) |
| **F2-4** | v1.68 | **图片代理无响应体大小限制**：攻击者构造大文件 URL 可导致服务器 OOM | [images.py:L218](file:///d:/trea项目/快麦取货通/backend/app/routers/images.py#L218) |
| **F2-5** | v1.71 | **离线图片文件被清除后死重试**：`syncImageUpload` 文件不存在时返回 false → 重试3次后标记冲突(-1)，记录永远留在冲突表 | [OrderSyncWorker.kt:L300-L303](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L300-L303) |
| **F2-6** | v1.74 | **getLatestTitle() 每个操作额外2次快麦API**：N个待同步操作=2N次额外调用，批量场景下显著增加延迟 | [OrderSyncWorker.kt:L280-L293](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L280-L293) |

### 🟢 低优先级（F3）（6 项）

| # | 版本 | 描述 | 位置 |
|---|:--:|------|------|
| **F3-1** | v1.68 | DropdownMenu 无锚点定位，PDA 小屏可能显示异常 | [PickOrderCard.kt:L188](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickOrderCard.kt#L188) |
| **F3-2** | v1.70 | add_item 非FK IntegrityError 裸 raise → 无信息500 | [orders.py:L260](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L260) |
| **F3-3** | v1.69 | syncItemsFromBackend 异常静默吞掉 `catch (_: Exception) { }` | [PickDetailViewModel.kt:L410](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L410) |
| **F3-4** | v1.74 | SupplierSelectDialog 搜索无结果时缺少清除按钮 | [SupplierSelectDialog.kt:L63-L82](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/SupplierSelectDialog.kt#L63-L82) |
| **F3-5** | v1.68 | 图片代理后端已实现但前端从未调用，代理路由形同虚设 | Android 全局 |
| **F3-6** | v1.67 | orders.py docstring 残留旧单号格式 `yyyyMMdd-拣货区X` | [orders.py:L35](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L35) |

### ⚪ 其他风险点（3 项，无需代码修复）

| # | 版本 | 描述 |
|---|:--:|------|
| R1 | v1.68 | `combinedClickable` 为 ExperimentalFoundationApi，PDA设备长按行为未验证 |
| R2 | v1.68 | 长按操作无振动/视觉反馈，用户可能不知道长按存在 |
| R3 | v1.66 | PickOrderCard 可见性标签"进行中"语义不准确（被领取后仍显示"进行中"） |

---

## 修复方案

### C1：创建者失去访问权修复

**涉及**：[orders.py:L96](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L96) + [L551-L553](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L551-L553)

```python
# list_orders L96:
base_sql = "SELECT * FROM pick_orders WHERE (assigned_to = ? OR created_by = ? OR (visibility = 'public' AND assigned_to = ''))"
params: list = [username, username]

# _check_order_access L551:
if row["assigned_to"] != username and row["created_by"] != username and not (
    row["visibility"] == "public" and not row["assigned_to"]
):
    raise HTTPException(status_code=403, detail="无权操作此取货单")
```

同时 `delete_order` L419 的 `created_by` 检查也应保留，确保创建者可以删除自己的订单。

---

### F1-1：图片代理 SSRF 修复

**涉及**：[images.py:L212-L221](file:///d:/trea项目/快麦取货通/backend/app/routers/images.py#L212-L221)

```python
ALLOWED_IMAGE_DOMAINS = ["img.alicdn.com", "aliyuncs.com"]
def is_allowed_image_url(url: str) -> bool:
    from urllib.parse import urlparse
    parsed = urlparse(url)
    return any(parsed.netloc.endswith(d) for d in ALLOWED_IMAGE_DOMAINS)
# 在 proxy_image 中添加校验
if not is_allowed_image_url(url):
    raise HTTPException(status_code=400, detail="不支持的图片域名")
# 添加大小限制
if len(content) > 10 * 1024 * 1024:
    raise HTTPException(status_code=413, detail="图片过大")
```

---

### F2-1：存量用户权限迁移

**涉及**：[database.py](file:///d:/trea项目/快麦取货通/backend/app/database.py) 或单独迁移脚本

```python
# 为所有拥有 settings 权限的用户追加 update_supplier 权限
cursor.execute("""
    INSERT OR IGNORE INTO user_permissions (user_id, permission)
    SELECT DISTINCT up.user_id, 'update_supplier'
    FROM user_permissions up
    WHERE up.permission = 'settings'
""")
db.commit()
```

---

### F2-2：Worker 漏同步修复

**方案A（简单）**：Worker `doWork()` 改为循环处理直到队列为空

```kotlin
override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    var hasFailure = false
    var hasWork = true
    while (hasWork) {
        val operations = dao.getAllPending()
        if (operations.isEmpty()) { hasWork = false; break }
        // ... 处理操作 ...
    }
    if (hasFailure) Result.retry() else Result.success()
}
```

**方案B（彻底）**：改为 PeriodicWorkRequest + 每15分钟一次

```kotlin
// MainActivity.kt
val workRequest = PeriodicWorkRequestBuilder<OrderSyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(constraints)
    .build()
```

推荐 **方案A**，改动最小且及时。

---

### F2-3：delete_order rowcount 修复

**涉及**：[orders.py:L434](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L434)

```python
cursor.execute("DELETE FROM pick_orders WHERE id = ? AND status != 1", (order_id,))
if cursor.rowcount == 0:
    raise HTTPException(status_code=409, detail="取货单状态已变更，请刷新后重试")
```

---

### F2-4-B：Emoji → Material Icons 替换

**涉及**：[HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L150-L267) + [ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt#L435-L499)

**原因**：Android 7.0-8.1 国产 PDA 的 emoji 字体不完整，📦📋🔍⚙️✏️💾 可能渲染为空白/tofu。

**映射表**（全部使用已有的 Material Icons 依赖）：

| 位置 | 行 | 原 emoji | 替换为 |
|------|:--:|----------|--------|
| HomeScreen Logo | L150 | `"📦"` | `Icons.Default.Inventory2` |
| 取货列表模块 | L245 | `"📋"` | `Icons.Default.ListAlt` |
| 商品详情模块 | L255 | `"🔍"` | `Icons.Default.Search` |
| 设置模块 | L265 | `"⚙️"` | `Icons.Default.Settings` |
| 切换供应商 | L435 | `"✏️ 切换"` | `Icon(Edit) + "切换"` |
| 保存备注 | L499 | `"💾 保存"` | `Icon(Save) + "保存"` |

```kotlin
// HomeScreen Logo: Text("📦") → Icon
Icon(Icons.Default.Inventory2, contentDescription = "Logo",
    tint = BrandBlue, modifier = Modifier.size(28.dp))

// 模块图标: Text("📋") → Icon
Icon(Icons.Default.ListAlt, contentDescription = null,
    tint = Color.White, modifier = Modifier.size(24.dp))

// ProductScreen 按钮: Text("💾 保存") → Row
Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text("保存")
}
```

---

### F2-5：图片死重试修复

**涉及**：[OrderSyncWorker.kt:L300-L303](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L300-L303)

```kotlin
if (!imageFile.exists()) {
    Log.w(TAG, "图片文件不存在，放弃同步: $filePath")
    return true  // 视为不可恢复
}
```

---

## 修改清单

| # | 优先级 | 文件 | 改动 | 版本 |
|---|:--:|------|------|:--:|
| 1 | 🔴C1 | `backend/app/routers/orders.py` L96+L551 | list_orders + _check_order_access 追加 `created_by` 检查 | v1.66 |
| 2 | 🟠F1 | `backend/app/routers/images.py` L212 | URL 白名单 + 大小限制 | v1.68 |
| 3 | 🟡F2 | `backend/app/database.py` | 存量用户权限迁移 SQL | v1.74 |
| 4 | 🟡F2 | `app/.../OrderSyncWorker.kt` L68-111 | doWork 循环处理直到队列空 | v1.74 |
| 5 | 🟡F2 | `backend/app/routers/orders.py` L434 | delete_order rowcount 检查 | v1.70 |
| 6 | 🟡F2 | `app/.../OrderSyncWorker.kt` L300 | 图片不存在返回true | v1.71 |
| 7 | 🟡F2 | `app/.../HomeScreen.kt` + `ProductScreen.kt` | 6处 emoji 替换为 Material Icons（📦→Inventory/📋→ListAlt/🔍→Search/⚙️→Settings/✏️→Edit/💾→Save） | v1.73 |
| 8 | 🟢F3 | `backend/app/routers/orders.py` L260 | 非FK IntegrityError 包装 HTTPException(409) | v1.70 |
| 9 | 🟢F3 | `app/.../PickDetailViewModel.kt` L410 | 静默catch 加Log.w | v1.69 |
| 10 | 🟢F3 | `backend/app/routers/orders.py` L35 | docstring 修正新格式 | v1.67 |

---

## 验证步骤

1. `./gradlew lint` 通过
2. 创建→发布→另一个账号领取→验证原创建者是否仍能看到/操作订单
3. 图片代理：`/api/images/proxy?url=http://192.168.1.1/` 应返回400
4. Worker 连续保存3次备注后检查 pending_operation 表是否全部被处理
5. `./gradlew lint` 通过
6. `./gradlew assembleRelease` 构建成功
