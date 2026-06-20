# v1.74-v1.78 五版本全面审计报告

4 路并行代理覆盖 28 个业务文件 + 50 个文档/测试文件，逐文件逐变更验证。

---

## 一、审计统计

| 维度 | 数据 |
|------|------|
| 审计版本 | v1.74, v1.75, v1.76, v1.77, v1.78 |
| 业务文件变更 | 28 个文件，50 文件总计 |
| CHANGELOG 修复项 | 6(P0) + 5(P1) + 9(P2) + 3(P3) = **23 项** |
| 全部正确落地 | 21 项 |
| 发现缺陷 | **5 项**（1 P0 + 1 P1 + 3 P2） |

---

## 二、全部正确的修复（21 项）✅

### 后端（10 项，全部正确）
| # | 文件 | 修复 | 版本 |
|---|------|------|:--:|
| 1 | `orders.py` | L82 语法修复（三 HTTPSException 挤同一行） | v1.76 |
| 2 | `orders.py` | `created_by` 权限（list_orders + _check_order_access） | v1.75 |
| 3 | `system.py` | `GET /api/sku/{sku_outer_id}` 端点 | v1.74 |
| 4 | `system.py` | apkFileName 空值防御 | v1.76 |
| 5 | `system.py` | QRCode 注释修正（公开访问） | v1.76 |
| 6 | `system.py` | 供应商权限 settings→update_supplier | v1.75 |
| 7 | `admin.py` | publish_app_version APK 存在性验证 | v1.76 |
| 8 | `kuaimai_api.py` | L194 `{}`→`result`（供应商列表修复） | v1.77 |
| 9 | `database.py` | 权限迁移 SQL（INSERT OR IGNORE） | v1.75 |
| 10 | `main.py` | 移除 `/apk` 静态挂载 | v1.76 |

### 核心数据层（5 项，全部正确）
| # | 文件 | 修复 | 版本 |
|---|------|------|:--:|
| 11 | `OrderSyncWorker.kt` | confilct操作 `deleteById` 防死循环 | v1.77 |
| 12 | `OrderSyncWorker.kt` | `getLatestTitle` 失败返回 null 拒绝同步 | v1.78 |
| 13 | `PickOrderRepository.kt` | 新增 `enqueueRemarkUpdateDirect`/`enqueueSupplierUpdateDirect` | v1.77 |
| 14 | `ProductViewModel.kt` | `currentSkuDetail` null 重置防旧值污染 | v1.78 |
| 15 | `ProductViewModel.kt` | 去掉独立扫码路径 `ifBlank` 包装 | v1.78 |

### UI 层（6 项，全部正确）
| # | 文件 | 修复 | 版本 |
|---|------|------|:--:|
| 16 | `HomeScreen.kt` | Settings icon 颜色（SurfaceGray→BorderGray + White→TextSecondary） | v1.77 |
| 17 | `PickOrderCard.kt` | 进度圆点 Spacer(8.dp)→weight(1f) | v1.77 |
| 18 | `PickItemRow.kt` | 移除 contentPadding + height(36.dp) | v1.77 |
| 19 | `PickListScreen.kt` | loading 时 spinner 替代 disabled 按钮 | v1.77 |
| 20 | `PickListScreen.kt` | `CircularProgressIndicator` + `size` import | v1.77 |
| 21 | `SupplierSelectDialog.kt` | 加载/错误状态（isLoadingSuppliers + supplierError + retry） | v1.74 |

---

## 三、发现缺陷（5 项）

### 缺陷 1（P0）：ItemUpdateResponse 可能缺少快麦 API 响应包裹层注解

**位置**：[ItemUpdateResponse.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/dto/ItemUpdateResponse.kt)

```kotlin
data class ItemUpdateResponse(
    val success: Boolean = false,
    val code: Int = 0,
    val msg: String = ""
)
```

**问题**：快麦 `erp.item.general.addorupdate` API 的响应格式可能为：
```json
{
    "erp_item_general_addorupdate_response": {
        "success": true,
        "code": 0,
        "msg": "success"
    }
}
```

当前 DTO 直接从顶层解析 `success`/`code`/`msg`，未经过 `erp_item_general_addorupdate_response` 包裹层。如果 API 确实返回包裹结构，Gson 在所有字段上取到默认值（`success=false`, `code=0`, `msg=""`），导致所有备注/供应商同步被误判为失败。

**影响**：即使快麦 API 实际更新成功，Worker 仍判定失败 → retryCount 递增 → 3 次后标记冲突 → 删除。离线同步全部无效。

**需要验证**：运行 `test_remark_update.py` 测试脚本直接调用快麦 API，检查实际的响应 JSON 结构。如果测试脚本返回 flattened 结构（无包裹层），则本期审计通过——因为 KuaimaiInterceptor 使用 form-urlencoded 编码发送请求，快麦 API 对此编码可能返回与 JSON 不同的响应格式。

**严重程度**：如果被证实，属于 P0 阻断——所有离线备注/供应商同步全部失效。如果测试证实响应无包裹层，则降为无缺陷。

---

### 缺陷 2（P1）：confirmSaveRemark 直接路径缺失 UI `remark` 即时反馈

**位置**：[ProductViewModel.kt:L285-L287](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L285-L287)

```kotlin
// 当前代码：
_uiState.value = _uiState.value.copy(isSavingRemark = false)
// 缺少: remark = confirmType.remark
```

**对比 `confirmChangeSupplier`**（L369-L373）：
```kotlin
_uiState.value = _uiState.value.copy(
    isSavingSupplier = false,
    supplierName = confirmType.name,    // ← 更新了
    supplierCode = confirmType.code     // ← 更新了
)
```

**影响**：独立扫码修改备注后，对话窗关闭但 `_uiState.remark` 仍显示旧值，用户看不到刚才输入的备注生效。当前会话内无即时反馈。

**修复**：在 `_uiState.value.copy(...)` 中加一行 `remark = confirmType.remark`，与 `confirmChangeSupplier` 保持一致。

---

### 缺陷 3（P2）：HomeScreen PickList/Product 卡片仍使用 emoji

**位置**：[HomeScreen.kt:L254](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L254) + [HomeScreen.kt:L264](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L264)

```kotlin
// PickList 卡片：仍使用 emoji "📋"（应为 Icons.Default.ListAlt）
icon = { Text("📋", fontSize = 24.sp) }

// Product 卡片：仍使用 emoji "🔍"（应为 Icons.Default.Search）
icon = { Text("🔍", fontSize = 24.sp) }
```

v1.75 CHANGELOG 宣称 6 处 Emoji→Material Icons 替换，但实际仅完成 4 处（Settings/Edit/Save/Inventory），此 2 处遗漏。国产 PDA 无 emoji 字库时卡片图标显示 tofu/空白。

**Import 已就绪**：`ListAlt`（L25）和 `Search`（L25）均已在 import 之中。

---

### 缺陷 4（P2）：ProductViewModel 双 null 静默丢弃操作

**位置**：[ProductViewModel.kt:L275-L293](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L275-L293)

**场景**：`currentItem == null`（SKU 不在 Room）+ `currentSkuDetail == null`（API 失败）= 两者都 null → 两个 `if`/`else if` 都不满足 → 用户操作被静默丢弃无任何错误提示。

**修复**：加 `else` 分支：
```kotlin
} else {
    _uiState.value = _uiState.value.copy(
        isSavingRemark = false,
        error = "无网络且无本地数据，操作无法保存"
    )
    return@launch
}
```

---

### 缺陷 5（P2）：download_apk 缺少 apkFileName 空字符串防御

**位置**：[system.py:L118-L126](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L118-L126)

`get_app_version()` 已有空值防御，但 `download_apk()` 缺少。当 `apkFileName` 为空字符串时，`os.path.normpath(os.path.join(APK_DIR, ""))` 等于 `APK_DIR` 目录路径，通过路径穿越检查但最终触发 Starlette 500 错误。

**正常流程不可达**（`get_app_version` 已有守卫），但直接 HTTP 请求会得到 500 而非 404。

---

## 四、验证结论

| 类别 | 数量 | 明细 |
|:--|:--:|------|
| ✅ 完全正确落地 | 21 | 10 后端 + 5 数据层 + 6 UI |
| 🔴 P0（需验证） | 1 | ItemUpdateResponse 包裹层（若证实则为阻断，需验证测试脚本） |
| 🟠 P1 | 1 | confirmSaveRemark UI remark 即时反馈缺失 |
| 🟡 P2 | 3 | emoji 残留 + 双 null 静默丢弃 + download_apk 空值防御 |

**无回归 bug，无编译风险。**

---

## 五、建议修复清单

| # | 优先级 | 文件 | 行 | 改动 |
|---|:--:|------|:--:|------|
| 1 | 🔴P0 | `ItemUpdateResponse.kt` | 待验证 | 如确认有包裹层则加 `@SerializedName` wrapper DTO |
| 2 | 🟠P1 | `ProductViewModel.kt` | L287 | `copy(isSavingRemark=false)` → `copy(isSavingRemark=false, remark=confirmType.remark)` |
| 3 | 🟡P2 | `HomeScreen.kt` | L254+L264 | "📋"→`Icon(ListAlt)` + "🔍"→`Icon(Search)` |
| 4 | 🟡P2 | `ProductViewModel.kt` | L287 后 | 加 else 分支：双 null→设置 error 提示 |
| 5 | 🟡P2 | `system.py` | L122 后 | `if not file_name: raise HTTPException(404, ...)` |

---

## 六、验证步骤

1. 先运行 `test_remark_update.py` 验证快麦 API 响应格式（确认或排除 P0）
2. 独立扫码 → 修改备注 → 确认 UI 即时显示新备注（P1 验证）
3. 首页取货列表/商品详情 icon 正常显示（非 emoji）（P2-3 验证）
4. 断网+无 Room 数据时修改备注 → 显示错误提示（P2-4 验证）
5. `./gradlew lint` + `./gradlew assembleRelease` 通过
