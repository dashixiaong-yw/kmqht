# 近期改动全面回归审查报告

## 审查范围

近期版本 v2.29 → v2.33 全量改动审查，3 个并行搜索代理覆盖 **30 项检查**。

---

## 审查结果

### v2.29 — 取货单详情扫码添加视口固定重构（8 项检查）

| # | 检查项 | 结果 |
|:-:|:-------|:----:|
| 1 | `_scanSuccessEvent.emit(Unit)` 在 Mutex 外，占位符不被阻塞 | ✅ |
| 2 | `_scrollToTopEvent.emit(Unit)` 在 `_executeAddItem` 的 `finally` 中，数据就绪后才滚动 | ✅ |
| 3 | 404 重试逻辑：retries 从 0 开始，首次 + 重试 1 次 = 2 次 | ✅ |
| 4 | `OrderItemResponse` import/id 类型/可空性正确 | ✅ |

### v2.30 — 缩略图 404 修复（2 项检查）

| # | 检查项 | 结果 |
|:-:|:-------|:----:|
| 5 | `_generate_thumbnail` 中有 `Pillow 支持格式` 诊断日志 | ✅ |
| 6 | `POST /api/images/regenerate-thumbnails` 路由 + admin 权限检查 | ✅ |

### v2.31 — `remember(orderId)` 视口修复（2 项检查）

| # | 检查项 | 结果 |
|:-:|:-------|:----:|
| 7 | `remember(viewModel.orderId) { LazyListState() }` — `viewModel.orderId` 是 public val | ✅ |
| 8 | `LazyListState` import 已存在，`rememberLazyListState` 已移除 | ✅ |

### v2.32 — 6 项性能优化（10 项检查）

| # | 检查项 | 结果 |
|:-:|:-------|:----:|
| 1 | `items Flow + distinctUntilChanged` 在 `stateIn` 之前 | ✅ |
| 2 | `distinctUntilChanged` import 存在 | ✅ |
| 3 | `imageUrlsMap` collect 改为 `map{toSet()}.distinctUntilChanged()` 模式 | ✅ |
| 4 | `map` import 存在 | ✅ |
| 5 | `filteredItems` 用 `derivedStateOf` + `by` delegation | ✅ |
| 6 | `derivedStateOf` import 存在 | ✅ |
| 7 | 3 个 PRAGMA（cache_size/temp_store/mmap_size）在 database.py 中 | ✅ |
| 8 | `complete_item`/`restore_item` 复用 `_check_order_access` 返回值 | ✅ |
| 9 | `_CachedStaticFiles` 类存在，app.mount 使用它 | ✅ |
| 10 | `_CachedStaticFiles` 正确继承 `StaticFiles`，给图片加缓存头 | ✅ |

### v2.33 — 代码审计修复（12 项检查）

| # | 检查项 | 结果 |
|:-:|:-------|:----:|
| 1 | `upsertItemFromResponse()` 函数存在且语法正确 | ✅ |
| 2 | `toPickItemEntity()` 扩展函数字段映射完整 | ✅ |
| 3 | `refresh()`/`syncItemsFromBackend()` 中 `forEach` 替换为 `upsertItemFromResponse()` | ✅ |
| 4 | `updateItemFieldsDirect` 参数匹配（4 参） | ✅ |
| 5 | `completedAt` 解析不破坏老代码（`Long?` 默认 null） | ✅ |
| 6 | PickDetailViewModel — 无 `notifyExpired()` 残留调用 | ✅ |
| 7 | ProductViewModel — 无 `notifyExpired()` 残留调用 | ✅ |
| 8 | PickListViewModel — 无 `notifyExpired()` 残留调用 | ✅ |
| 9 | TokenAuthenticator 保留唯一正确的 `notifyExpired()` 调用 | ✅ |
| 10 | ImageRepository 接口已清理（仅剩 4 个标准方法） | ✅ |
| 11 | PickDetailViewModel `getImageUrls()` 使用 `imageRepository` 可编译 | ✅ |
| 12 | ProductViewModel 的 `productImageDao` 已移除，全部委托 `imageRepository` | ✅ |

---

## 未检查到任何回归

**30 项全部通过。** 无编译错误、无逻辑缺陷、无功能回归。

---

## 轻微发现（不影响运行）

3 个 ViewModel 中残留了未使用的 `import com.kuaimai.pda.util.SessionExpiredEvent`：

| 文件 | import 行 | 状态 |
|:-----|:---------|:----:|
| PickDetailViewModel.kt | L20 | 未使用（已移除所有调用） |
| ProductViewModel.kt | L21 | 未使用（已移除所有调用） |
| PickListViewModel.kt | L12 | 未使用（已移除所有调用） |

**改动**：删除这 3 行未使用的 import。

---

## 版本号

无版本号变更（2.33 不构建 APK，等通知）
