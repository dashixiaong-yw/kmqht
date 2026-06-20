# Bug 修复计划 v1.86

## 总览

| Bug | 描述 | 严重程度 | 涉及端 |
|:---:|:-----|:--------:|:------:|
| 1/2 | 供应商/备注修改后快麦ERP未同步生效 | **紧急** | Android Worker |
| 3 | 首次添加商品返回 HTTP 404，第二次成功 | **紧急** | 后端 httpx |
| 4 | 供应商筛选项在添加商品后不立即刷新 | 中 | Android ViewModel |

---

## Bug 1/2：供应商/备注未同步到快麦ERP

### 前置确认
- ✅ 快麦 Session 有效
- ✅ 网络正常
- ✅ 无 API 限流

### 根因分析

Worker 同步链路（`syncRemarkUpdate` / `syncSupplierUpdate`）执行 `fetchLatestSkuData()` 时失败，返回 null，导致整个同步被**静默跳过**（无用户提示）。

`fetchLatestSkuData()` 的核心逻辑：

```
fetchLatestSkuData(kmApi, skuOuterId, itemOuterIdFallback)
  → kmApi.getSkuInfo(skuOuterId)
    → 快麦API erp.item.single.sku.get 响应中真实包含 title 字段
    → BUT: SkuItemInfo DTO 未定义 title 字段 → title 被 Gson 丢弃 ❌
  → 拿到 itemOuterId
  → kmApi.getItemDetail(outerId)
    → 快麦API item.single.get 返回 title
    → 如果 title 为空（对部分商品可能返回空） → return null ❌
    → 同步被跳过
```

**两处问题**：

**问题 A**：`SkuItemInfo` DTO 缺少 `title` 字段
- 快麦 `erp.item.single.sku.get` 响应中包含 `title` 字段
- 但 [KuaimaiQueryDto.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/KuaimaiQueryDto.kt#L25-L31) 的 `SkuItemInfo` 没有定义 `title`
- Gson 反序列化时丢弃了 title 数据，本可用的 fallback 源丢失

**问题 B**：`getItemDetail` 返回空 title 时没有降级
- 有些商品的 `item.single.get` 响应可能 `title` 为空
- 当前代码直接 `return null`，没有尝试使用 `getSkuInfo` 响应中的 title

### 修复方案

#### 修复 1.1：补齐 `SkuItemInfo` 的 `title` 字段

**文件**：[KuaimaiQueryDto.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/KuaimaiQueryDto.kt)

```kotlin
data class SkuItemInfo(
    val itemOuterId: String = "",
    val sysItemId: Long = 0,
    val sysSkuId: Long = 0,
    val propertiesName: String = "",
    val skuOuterId: String = "",
    val title: String = ""  // ← 新增，快麦API实际返回此字段
)
```

#### 修复 1.2：`fetchLatestSkuData` 增加 title 降级链

**文件**：[OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt#L326-L369)

```
title获取降级链:
  1. getItemDetail(outerId).response.item.title   ← 主要来源
  2. sku.title (从getSkuInfo响应中获取)            ← 新增fallback
  3. 两者都为空 → 返回 null（跳过同步）
```

代码修改（主路径）：

```kotlin
// 原代码 (L354-361):
val itemResp = kmApi.getItemDetail(ItemGetRequest(outerId = itemOuterId))
val title = itemResp.response?.item?.title ?: ""
if (title.isBlank()) {
    Log.w(TAG, "fetchLatestSkuData: title为空: $skuOuterId")
    return null
}
return SkuSyncData(title = title, itemOuterId = itemOuterId, propertiesName = sku.propertiesName)

// 新代码:
val itemResp = kmApi.getItemDetail(ItemGetRequest(outerId = itemOuterId))
val title = itemResp.response?.item?.title ?: ""
val effectiveTitle = if (title.isNotBlank()) title else sku?.title ?: ""
if (effectiveTitle.isBlank()) {
    Log.w(TAG, "fetchLatestSkuData: getItemDetail和getSkuInfo均无title: $skuOuterId")
    return null
}
Log.d(TAG, "fetchLatestSkuData成功: sku=$skuOuterId, title来源=${if (title.isNotBlank()) "getItemDetail" else "getSkuInfo"}")
return SkuSyncData(title = effectiveTitle, itemOuterId = itemOuterId, propertiesName = sku.propertiesName)
```

同样的降级逻辑应用到 fallback 路径（L337-L348）。

#### 修复 1.3：Worker 关键路径增加日志

**文件**：[OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt)

`syncRemarkUpdate` 和 `syncSupplierUpdate` 中所有 `?: return false` 处增加日志：

```kotlin
// 原代码:
val kmApi = apiService ?: return false

// 新代码:
val kmApi = apiService ?: run {
    Log.e(TAG, "apiService为null，跳过同步")
    return false
}
```

同理在 payload 字段提取失败处也加日志。这样如果以后再出现问题，通过 logcat 即可准确定位。

---

## Bug 3：首次添加商品返回 HTTP 404，第二次成功

### 后端日志证据

```
00:00:46 [ERROR] 查询SKU失败 sku_outer_id=DSGHQC: 
        unable to perform operation on <TCPTransport closed=True reading=False 0x7fb110f670>; the handler is closed
                ↑↑↑ 根因确认: httpx 复用已被服务端关闭的 TCP 连接
00:00:46 [WARNING] 快麦API未找到SKU: DSJQBF  (无缓存) → 404
00:01:50 [INFO]  快麦SKU数据 sku=DSJQBF: ...  ← 重试成功后正常!

← 同样的模式重复 4 次: DSJQBF、DSJQBE、DSJBB、DSJBF
```

### 根因

**httpx 连接池默认 keep-alive 超时与服务端不匹配。**

- `_get_client()` 使用 `httpx.AsyncClient(timeout=30.0)` 无连接池参数
- 快麦服务端 (gw.superboss.cc) 的 keep-alive 超时约为 30-60 秒
- 服务端关闭连接后，httpx 不知道，下次复用已关闭的连接 → `TCPTransport closed`
- 异常被 `_call_api` 的 except 捕获，但没有重试 → `get_sku_by_outer_id` 返回 None
- 无缓存 → `get_sku_info` 返回 None → `orders.py` 抛出 404

### 修复方案

#### 修复 3.1：配置 httpx 连接池（根因修复）

**文件**：[kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py)

```python
def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        with _client_lock:
            if _client is None:
                _client = httpx.AsyncClient(
                    timeout=_TIMEOUT,
                    limits=httpx.Limits(
                        max_keepalive_connections=5,
                        max_connections=10,
                        keepalive_expiry=30.0
                    )
                )
    return _client
```

#### 修复 3.2：`_call_api` 增加 TransportError 重试（兜底）

**文件**：[kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py#L62-L122)

```python
async def _call_api(method, extra_params=None):
    # ...构建params(不变)...
    max_retries = 1
    for attempt in range(max_retries + 1):
        try:
            client = _get_client()
            response = await client.post(KUAIMAI_API_BASE, data=params)
            response.raise_for_status()
            # ...结果解析(不变)...
        except httpx.TransportError as e:
            logger.warning(f"快麦API传输错误(第{attempt+1}次): {e}")
            if attempt < max_retries:
                await asyncio.sleep(0.5)
                continue
            raise
        # ...原有异常处理(不变)...
```

> 修复 3.1 解决根因，修复 3.2 兜底防止任何传输异常。

---

## Bug 4：供应商筛选项不即时刷新

### 根因

`loadSuppliersFromLocal()` 读取 `_items.value` 快照，但 Room Flow 异步传播未完成，读到的是**插入前的数据**。

### 修复

**文件**：[PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt)

`onBarcodeScanned()` 成功后从 API 响应直接追加供应商：

```kotlin
pickOrderRepository.insertItem(item)
// 即时更新供应商筛选列表（直接从API响应获取，不依赖Room Flow传播）
val newSupplier = response.supplierName
if (newSupplier.isNotEmpty() && !_suppliers.value.contains(newSupplier)) {
    _suppliers.value = _suppliers.value + newSupplier
}
loadSuppliersFromLocal()  // 后续同步保证
```

---

## 功能 5：导出同步日志（辅助排查）

**目的**：无需 USB 连接电脑，在 PDA 上即可查看 Worker 日志。

### 实现方式

#### 5.1 Worker 端：写入日志文件

**文件**：[OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt)

新增 `appendLog` 工具函数，所有关键节点同时写 logcat + 写文件：

```kotlin
companion object {
    private const val TAG = "OrderSyncWorker"
    private const val MAX_RETRY = 3
    private const val LOG_FILE = "sync_log.txt"

    /** 写入同步日志文件（最大保留 500 行） */
    fun appendLog(context: Context, message: String) {
        try {
            val file = File(context.cacheDir, LOG_FILE)
            val now = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            val line = "[$now] $message\n"
            // 读取现有行数，超过 500 行时截断前半
            val existing = if (file.exists()) file.readLines() else emptyList()
            val lines = if (existing.size >= 500) existing.drop(existing.size - 250) else existing
            file.writeText(lines.joinToString("\n") + "\n" + line)
        } catch (_: Exception) { /* 日志写入失败不影响主流程 */ }
    }
}
```

#### 5.2 设置页：导出按钮

**文件**：[SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt)

在版本号下方加一个"导出同步日志"按钮：

```kotlin
// 导出同步日志
TextButton(
    onClick = {
        val logFile = File(appContext.cacheDir, "sync_log.txt")
        if (!logFile.exists()) {
            // 没有日志时提示
            scope.launch { /* 显示 Snackbar "暂无同步日志" */ }
            return@TextButton
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            appContext, "${appContext.packageName}.fileprovider", logFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(Intent.createChooser(intent, "分享同步日志"))
    },
    modifier = Modifier.fillMaxWidth()
) {
    Text("导出同步日志", color = TextSecondary, fontSize = 12.sp)
}
```

> 注意：`FileProvider` 需要在 AndroidManifest.xml 中配置，或在 `res/xml/file_paths.xml` 中声明 `cache-path`。如果项目已有 FileProvider（用于图片上传等），直接复用即可。

#### 5.3 Worker 中调用

所有关键节点调用 `appendLog`：

```kotlin
// doWork 开始
appendLog(applicationContext, "Worker启动，共 ${operations.size} 个待处理操作")

// fetchLatestSkuData 成功
appendLog(applicationContext, "获取SKU数据成功: sku=$skuOuterId, title来源=$source")

// fetchLatestSkuData 失败
appendLog(applicationContext, "获取SKU数据失败: sku=$skuOuterId")

// API 调用成功
appendLog(applicationContext, "快麦API同步成功: type=$type, sku=$skuOuterId, response.success=$success")

// API 调用失败
appendLog(applicationContext, "快麦API同步失败: type=$type, sku=$skuOuterId, code=$code, msg=$msg")
```

---

## 修改文件清单

| 文件 | 修改内容 | 行数 | 优先级 |
|:-----|:---------|:----:|:------:|
| [KuaimaiQueryDto.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/KuaimaiQueryDto.kt) | `SkuItemInfo` 增加 `val title: String = ""` | +1 | **P0** |
| [OrderSyncWorker.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/OrderSyncWorker.kt) | 1. `fetchLatestSkuData` title 降级链 2. 所有 `?: return false` 加日志 3. 日志文件写入 | ~40 | **P0** |
| [PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt) | API 响应直接追加供应商 | ~5 | **P0** |
| [SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt) | 增加"导出同步日志"按钮 | ~20 | **P0** |
| [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | 连接池 `keepalive_expiry=30` + TransportError 重试 | ~20 | **P0** |
| `res/xml/file_paths.xml` | 如项目尚无 FileProvider 配置则新增 | ~5 | P1 |

---

## 验证标准

| Bug | 验证方法 |
|:----|:---------|
| 1/2 | 修改备注/供应商后，去设置页点"导出同步日志"，查看日志文件确认每一步的状态。在快麦后台验证数据已更新。 |
| 3 | 连续添加 5 个新 SKU，全部第一次成功。后端日志无 `TCPTransport closed`。 |
| 4 | 添加的商品具有首次出现的供应商时，FilterChip 立即显示该供应商。 |
| 日志导出 | 点击"导出同步日志"，弹出系统分享选择器，选择微信/文件管理器可成功分享 txt 文件。 |

---

## 回归风险检查

| 风险点 | Bug | 检查 |
|:-------|:---:|:-----|
| `SkuItemInfo.title` 空默认值不影响已有反序列化 | 1/2 | ✅ 默认 `""`，向下兼容 |
| `sku.title` 是真实快麦数据，非占位符 | 1/2 | ✅ 是 API 实际返回字段 |
| 连接池 `keepalive_expiry=30` 不影响正常请求 | 3 | ✅ 空闲连接 30 秒后自动关闭重建，无副作用 |
| TransportError 重试 1 次不超过限流阈值 | 3 | ✅ 仅 1 次额外调用 |
| `_suppliers.contains()` 防重生效 | 4 | ✅ 不会出现重复供应商标签 |
| 日志文件写入异常不影响主流程 | 5 | ✅ `catch (_: Exception)` 静默忽略 |
