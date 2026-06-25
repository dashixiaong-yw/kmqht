# 取货列表/商品详情模块增加401登录提示 — 脑暴审查

## 已改动范围

| 文件 | 状态 |
|:-----|:----:|
| PickListViewModel.kt | ✅ **已改完**（3 个 catch 块） |
| PickDetailViewModel.kt | ✅ **已改完**（8 个 catch 块） |
| ProductViewModel.kt | ❌ **未改** |

## 脑暴 — 审查发现 5 个遗漏

### 遗漏 1：PickDetailViewModel 扫码添加明细（内层 catch）—— 401 被内层吃掉了

**文件**：[PickDetailViewModel.kt#L227-L236](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L227-L236)

```
结构：
外层 try {
    // ...
    if (!alreadyExists) {
        try {  ← 内层 try
            orderApiService.addItem(...)  ← API调用
        } catch (e: Exception) {  ← 内层 catch（先处理）
            if (e is HttpException && e.code() == 409) {
                // duplicate → OK
            } else {
                // 401/其他错误 → 只设错误消息，不触发登录弹窗
            }
        }
    }
    loadOrder()
} catch (e: Exception) {  ← 外层 catch（我已加401检测 ✅）
    // 只能捕获到 loadOrder() 等后续代码的异常
}
```

**问题**：`orderApiService.addItem()` 返回 401 → 被内层 catch 捕获 → 进入 `else` 分支 → **没有 `SessionExpiredEvent.notifyExpired()`** → 外层 catch 无法到达。用户得不到登录弹窗。

**修复**：内层 catch 的 `else` 分支也要加 401 检测。

### 遗漏 2：PickDetailViewModel.syncItemsFromBackend() —— 静默失败

**文件**：[PickDetailViewModel.kt#L506](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L506)

```
catch (e: Exception) {
    Log.w(TAG, "syncItemsFromBackend失败: ${e.message}")  ← 只打了log
}
```

被以下路径调用：
- `init {}` 启动时
- `onBarcodeScanned` 检测到重复扫码时（409 后调用）

如果 401，用户无任何反馈。**至少init场景影响小，但409同步场景是用户操作后的结果**。

**修复**：catch 块增加 401 检测。

### 遗漏 3：ProductViewModel 完全未改 —— 4 处需加 401

| 函数 | API 调用 | 位置 | 紧急度 |
|:-----|:---------|:----:|:------:|
| `loadSkuInfo()` 内层 catch | `systemApiService.getSkuDetail()` | L150-152 | **高** — 用户进商品详情的第一屏 |
| `loadSuppliers()` | `systemApiService.getKuaimaiSuppliers()` | L332 | **高** — 打开供应商列表 |
| `uploadImage()` | `imageRepository.uploadImage()` | L418 | **中** — 上传失败后用户可见 |
| `deleteImage()` | `imageRepository.deleteImage()` | L469 | **低** — 主动操作时才有 |

> 注意：`loadSkuInfo()` 内层 catch 当前的降级逻辑是：API 失败后 fallback 到本地 Room。如果 401 时降级到 Room，用户可能不会发现问题（旧数据能用）。但 Room 也可能为空（新安装），此时显示错误却无登录弹窗。

**修复思路**：与 PickDetailViewModel 同样模式——捕获到 `HttpException && code() == 401` 时调用 `SessionExpiredEvent.notifyExpired()`，同时保留现有降级逻辑（不影响本地缓存使用）。

### 遗漏 4：`loadSkuInfo` 的多层 try-catch 结构 —— 外层 catch 不会被触发

**文件**：[ProductViewModel.kt#L128-L186](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L128-L186)

```
try {  ← 外层
    try {  ← 内层（API调用）
        systemApiService.getSkuDetail(token, skuOuterId)
    } catch (apiError: Exception) {
        // 捕获所有API异常，降级到Room
    }
    if (!loaded) {
        // Room降级逻辑
    }
    loadImages(skuOuterId)
} catch (e: Exception) {  ← 外层catch
    // ... 目前只能捕获loadImages等非API异常
}
```

**问题**：与遗漏 1 同样的陷阱——内层 catch 吃掉所有 API 异常，外层 catch 根本不会触发。如果我仅在外层加 401 检测，对 `getSkuDetail` 的 401 无效。

**修复**：必须在内层 catch 加 401 检测。

### 遗漏 5：ProductViewModel 使用 `prefs.getString()` 而非 `userRepository.getToken()`

**文件**：[ProductViewModel.kt#L132](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L132) 和 [L319](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt#L319)

```kotlin
// ProductViewModel 所有 API 调用都用：
val token = prefs.getString(PrefsKeys.KEY_USER_TOKEN, "") ?: ""

// 其他 ViewModel 用：
val token = userRepository.getToken()
```

`getToken()` 会校验本地有效期（7天规则），过期返回空字符串。而 `prefs.getString()` 直接返回存储值，**即使 token 已过期 7 天也会返回旧值**。但这不是本次修复的问题——401 返回后不管 token 值是否有效，后端返回 401 就说明过期了。

**本次不改**，仅记录。`SessionExpiredEvent.notifyExpired()` 的触发逻辑不依赖 token 来源。

---

## 修复后完整改动清单

| 文件 | 改动点 | 已改？ |
|:-----|:-------|:------:|
| PickListViewModel.kt | `loadActiveOrders()` catch | ✅ |
| PickListViewModel.kt | `loadAreas()` catch | ✅ |
| PickListViewModel.kt | `loadCompletedOrders()` catch | ✅ |
| PickDetailViewModel.kt | `loadSuppliers()` catch | ✅ |
| PickDetailViewModel.kt | `onBarcodeScanned` **外层** catch | ✅ |
| PickDetailViewModel.kt | `onBarcodeScanned` **内层** catch else 分支 | **❌ 需补** |
| PickDetailViewModel.kt | `completeItem()` catch | ✅ |
| PickDetailViewModel.kt | `restoreItem()` catch | ✅ |
| PickDetailViewModel.kt | `completeAllItems()` catch | ✅ |
| PickDetailViewModel.kt | `refresh()` catch | ✅ |
| PickDetailViewModel.kt | `deleteItem()` catch | ✅ |
| PickDetailViewModel.kt | `syncItemsFromBackend()` catch | **❌ 需补** |
| ProductViewModel.kt | `loadSkuInfo()` 内层 catch | **❌ 需改** |
| ProductViewModel.kt | `loadSuppliers()` catch | **❌ 需改** |
| ProductViewModel.kt | `uploadImage()` catch | **❌ 需改** |
| ProductViewModel.kt | `deleteImage()` catch | **❌ 需改** |

**总数**：4 个漏 + 3 个文件全未改 = 需补 **7 处**

---

## 关联项审查（无遗漏）

| # | 检查项 | 结论 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | 重复触发两次 SessionExpiredEvent | ✅ 安全 | `bool = true` 两次 notify 不会重复弹窗 |
| 2 | Authenticator 比 ViewModel catch 先跑 | ✅ 确定 | OkHttp 先执行，Retrofit 才抛异常 |
| 3 | 401 时是否抑制 Snackbar | ✅ 已处理 | 401 时用 `"登录已过期，请重新登录"` 替代通用错误 |
| 4 | `loginRequired` 是否需要 | ✅ 不需要 | 用户预期弹窗，不直接跳转 |
| 5 | 多个 API 同时 401 | ✅ 安全 | 多个 `notifyExpired()` 不重复弹窗 |
| 6 | HomeScreen/Scanner 是否需要 | ✅ 本次不处理 | 有单独 session 监控 |
| 7 | `SessionExpiredEvent.reset()` 时机 | ✅ 正常 | 点"重新登录"时由 AppNavigation 调用 |
| 8 | 非 401 网络错误 | ✅ 不影响 | 保持现有 Snackbar 行为 |
| 9 | OTA 安装链路 | ✅ 无关 | 不涉及后端 API |
| 10 | OrderSyncWorker | ✅ 无关 | 后台 Worker 同步失败已有自己的处理 |
| 11 | ProductViewModel 使用 `prefs` 非 `userRepository` | ✅ 仅记录 | 不影响本次修复 |
| 12 | **本次跳过构建** | ✅ 等用户通知 | |

---

## 结论

**已改 8 处，遗漏 7 处。** 全部完成后覆盖 PickList、PickDetail、Product 三个模块的所有 API 调用 catch 块。
