# 项目代码审计报告 & 优化计划

## 审计范围

两个搜索代理并行检查了 **9 个维度**：
1. 重复文件 ✅
2. 孤立/废弃文件 ✅
3. 重复代码 ✅
4. 重复逻辑链 ✅
5. 重复验证 ✅
6. 孤立代码 ✅
7. DTO 字段映射冗余 ✅
8. 后端路由路径冲突 ✅
9. Android ViewModel 常见错误处理模式 ✅

---

## 一、审计结果总表

| # | 类型 | 严重性 | 位置 | 问题 | 建议 |
|:-:|:----:|:-----:|:-----|:-----|:-----|
| 1 | 重复代码 | **P0** | PickDetailViewModel.kt | `refresh()` 和 `syncItemsFromBackend()` 中 14 字段映射 + upsert 判断完全重复 | 提取为统一扩展函数 |
| 2 | 重复代码 | **P0** | 3 个 ViewModel 共 14 处 | `if (e is HttpException && e.code() == 401)` 重复 | 在 OkHttp Interceptor 层统一处理 |
| 3 | 重复逻辑 | **P1** | UserRepository + ViewModel | `SessionExpiredEvent` 和 `loginRequired SharedFlow` 两套 401 机制共存 | 收敛为一处 |
| 4 | 孤立代码 | **P1** | `test_*.py` 根目录 7 个 | 历史调试脚本，无任何引用 | 清理或归入 `backend/tests/` |
| 5 | 重复封装 | **P1** | ImageRepository.kt | 3 个方法是对 DAO 的纯透传 | 移除透传，统一注入 |
| 6 | 重复封装 | **P1** | ProductViewModel.kt | 同时注入了 DAO 和 Repository | 统一为一种方式 |
| 7 | 不一致 | **P1** | ImageUploadService.kt | 使用 OkHttp 而非 Retrofit，手动加 token | 迁移到 Retrofit Multipart |
| 8 | 冗余查询 | **P2** | orders.py | `_check_order_access()` 返回值在 2 个路由中未复用 | 已修复（v2.32 已完成） |
| 9 | 样式不一致 | **P2** | areas.py | 行转响应与 orders/images 模式不一致 | 统一为 `_row_to_*` 函数 |
| 10 | 声明重复 | **P2** | 后端全部路由 | `Depends(get_current_user)` 出现 38 次 | 可考虑 APIRouter 级别统一 |
| 11 | 无效引用 | ⚠️ | 项目规则 README.md | 记录 `mcp-server/` 目录不存在 | 更新文档 |

---

## 二、P0 — 必须修复

### 修复 1：PickDetailViewModel 重复映射 + upsert 逻辑

**现状**：`refresh()` 和 `syncItemsFromBackend()` 中各有完全独立的 `OrderItemResponse → PickItemEntity` 映射和 upsert 判断（14 个字段逐一手写，查 existing → insert/update 判断也完全一样）。

**改动**：[PickDetailViewModel.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt)

新增扩展函数（在文件末尾或其他合适位置）：
```kotlin
/**
 * 将 OrderItemResponse 转换为 PickItemEntity
 */
private fun OrderItemResponse.toPickItemEntity(orderId: Long): PickItemEntity = PickItemEntity(
    id = id, orderId = orderId,
    skuOuterId = skuOuterId, sysItemId = sysItemId, sysSkuId = sysSkuId,
    propertiesName = propertiesName, picPath = picPath,
    status = status, supplierName = supplierName, supplierCode = supplierCode,
    remark = remark, itemOuterId = itemOuterId,
    createdAt = TimeUtils.parseBeijingTime(createdAt).let { if (it > 0) it else TimeUtils.now() },
    completedAt = TimeUtils.parseBeijingTimeOrNull(completedAt)
)
```

新增统一 upsert 方法：
```kotlin
/**
 * 统一 upsert：从响应中插入或更新明细
 */
private suspend fun upsertItemFromResponse(itemResponse: OrderItemResponse) {
    val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, itemResponse.skuOuterId)
    if (existing == null) {
        pickOrderRepository.insertItem(itemResponse.toPickItemEntity(orderId))
    } else {
        pickOrderRepository.updateItemFieldsDirect(existing.copy(
            id = existing.id, status = itemResponse.status,
            supplierName = itemResponse.supplierName,
            supplierCode = itemResponse.supplierCode,
            propertiesName = itemResponse.propertiesName,
            picPath = itemResponse.picPath, remark = itemResponse.remark,
            itemOuterId = itemResponse.itemOuterId,
            completedAt = TimeUtils.parseBeijingTimeOrNull(itemResponse.completedAt)
        ))
        // 状态变化时同步
        if (existing.status != itemResponse.status) {
            if (itemResponse.status == 1) {
                loadOrder()
            }
        }
    }
}
```

然后在 `refresh()` 和 `syncItemsFromBackend()` 中替换为：
```kotlin
// 改前（11 行重复代码）
val existing = pickOrderRepository.getItemByOrderIdAndSkuOuterId(orderId, itemResponse.skuOuterId)
if (existing == null) {
    pickOrderRepository.insertItem(PickItemEntity(...14 fields...))
} else {
    pickOrderRepository.updateItemFieldsDirect(...)
    if (existing.status != itemResponse.status) { ... }
}

// 改后（1 行）
upsertItemFromResponse(itemResponse)
```

**约减少 30 行重复代码。**

### 修复 2：401 错误处理统一到 OkHttp Interceptor 层

**现状**：14 处 `if (e is HttpException && e.code() == 401) { SessionExpiredEvent.notifyExpired() }` 零散分布在 ViewModel 中。

**改动**：[NetworkModule.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt)

在 `KuaimaiInterceptor`（或其他 OkHttp Interceptor）中统一处理 401：

```kotlin
// 在 KuaimaiInterceptor 或新增的 AuthInterceptor 中
class AuthInterceptor(
    private val encryptedPrefs: SharedPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            SessionExpiredEvent.notifyExpired()
        }
        return response
    }
}
```

然后从所有 ViewModel 中移除重复的 `if (e is HttpException && e.code() == 401)` 判断，只在需要特殊处理的地方保留（如 UserRepository 的 `handleAuthError` 收敛到同一个机制）。

**但需要注意**：Interceptor 在后台线程运行，`SessionExpiredEvent.notifyExpired()` 需要确保线程安全。当前 `SessionExpiredEvent` 使用 `StateFlow`，是线程安全的。

**约减少 42 行重复代码。**

---

## 三、P1 — 建议修复

### 修复 3：清理 7 个孤立测试脚本

**现状**：`test_find_title.py`、`test_item_title.py`、`test_item_title2.py`、`test_kuaimai_full.py`、`test_remark_update.py`、`test_sku_fields.py`、`test_supplier_permission.py` 共 7 个脚本在根目录，无任何引用。

**改动**：移动到 `backend/tests/` 目录（如果仍需要）或直接删除。

### 修复 4：清理 ImageRepository 透传 + 统一 ViewModel 注入

**现状**：
- ImageRepository 有 3 个方法完全透传 DAO，毫无封装价值
- ProductViewModel 同时注入了 `ProductImageDao` 和 `ImageRepository`，关系混乱

**改动**：
- 移除 ImageRepository 中的 `getImagesBySkuOuterId()`、`getImageBySkuAndType()`、`saveImage()` 三个透传方法
- ProductViewModel 移除 `ProductImageDao` 注入，改走 ImageRepository
- PickDetailViewModel 中 `getImageUrls()` 调 `imageRepository.getImageBySkuAndType()` 不变

### 修复 5：ImageUploadService 迁移到 Retrofit

**现状**：图片上传使用 OkHttp 手动构造请求，手动从 SharedPreferences 读取 token，没有统一拦截器。

**改动**：迁移到 Retrofit `@Multipart` 方式

```kotlin
interface ImageApiService {
    @Multipart
    @POST("api/upload")
    suspend fun uploadImage(
        @Part("skuOuterId") skuOuterId: RequestBody,
        @Part("imageType") imageType: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ImageResponse>
}
```

---

## 四、P2 — 可优化（但不紧急）

| # | 问题 | 原因不紧急 |
|:--|:-----|:-----------|
| 6 | areas.py `_row_to_*` 不一致 | 功能正确，纯风格问题 |
| 7 | `Depends(get_current_user)` 38 次 | FastAPI Depends 在依赖链中会缓存，不造成实际性能问题 |
| 8 | `mcp-server/` 目录缺失 | 仅文档引用，不影响运行 |

---

## 五、已处理的优化（v2.32）

以下是在审计前 v2.32 已完成修复的：
- ✅ `orders.py` 冗余查询：`complete_item`/`restore_item` 已复用 `_check_order_access` 返回值

---

## 六、改动清单

| # | 文件 | 改动 | 行数变化 | 优先级 |
|:-:|:-----|:-----|:--------:|:------:|
| 1 | PickDetailViewModel.kt | 新增 `toPickItemEntity()` 扩展函数 + `upsertItemFromResponse()` 统一方法 | **-30** | **P0** |
| 2 | NetworkModule.kt | 新增 `AuthInterceptor` 统一处理 401 | +20 | **P0** |
| 2 | 所有 3 个 ViewModel | 移除 14 处重复 401 判断 | **-42** | **P0** |
| 3 | ImageRepository.kt | 移除 3 个透传方法 | **-8** | P1 |
| 3 | ProductViewModel.kt | 移除 ProductImageDao 注入，改走 ImageRepository | -2 | P1 |
| 4 | ImageUploadService.kt | 迁移到 Retrofit Multipart | ~+10 | P1 |
| 4 | backend/tests/ | 根目录 7 个 test_*.py 移入 | 0 | P1 |
| 5 | backend/app/routers/areas.py | 统一为 `_row_to_*` 函数 | +5 | P2 |

**总计**：约 **-47 行重复代码**（P0: -72 行 + P1: 0 行代码变化）

## 版本号

2.32 → 2.33（不构建 APK，等通知）
