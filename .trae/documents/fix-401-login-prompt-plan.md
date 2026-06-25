# 取货列表/商品详情模块增加401登录提示

## 问题

新安装/登录过期后，点击「取货单列表」只显示 "获取取货单列表失败"，**没有弹出重新登录弹窗**。只有在「新建取货单」时才触发登录提示。

**根因**：ViewModels 的 catch 块只设置了通用错误消息，没有主动检测 401 并触发 `SessionExpiredEvent`。虽然 OkHttp `TokenAuthenticator` 理论上会在 OkHttp 线程触发 `SessionExpiredEvent`，但由于以下原因叠加，用户看不到登录弹窗：

1. **渲染时序竞争**：`TokenAuthenticator` 触发 `SessionExpiredEvent` 后，AppNavigation 弹出登录弹窗。同时 ViewModel catch 块设置错误消息，PickListScreen 显示 Snackbar。Snackbar（底部）和弹窗（居中）同时出现，用户注意力被 Snackbar 吸引
2. **401 后错误 Snackbar 覆盖了登录弹窗的视觉效果**：用户看到 Snackbar 后要么等它消失要么点击关闭，此时登录弹窗可能已被忽略
3. **`createOrder` 场景不同的原因**：新建取货单弹窗 → API 调用 → 弹窗关闭 → 401 触发登录弹窗（屏幕上无其他 UI 元素干扰），用户清楚看到

---

## 脑暴 — 12 项关联点

| # | 检查项 | 结论 | 说明 |
|:-:|:-------|:----:|:------|
| 1 | **SessionExpiredEvent 触发两次问题** | ⚠️ 需防护 | ViewModel catch 触发 + Authenticator 可能也触发 → 两次 `notifyExpired()` 设 `true`，StateFlow 第二个 `true` 不会重发。`bool` 已是 `true`，collector 已设 `showDialog = true`，没问题 |
| 2 | **TokenAuthenticator 和 ViewModel catch 谁先触发** | ✅ 确定 | OkHttp Authenticator 在 Retrofit 抛出异常之前执行。`notifyExpired()` 先执行，然后 ViewModel catch 才运行 |
| 3 | **401 时是否要抑制 Snackbar** | ✅ 建议抑制 | 401 时错误 Snackbar 与登录弹窗竞争用户注意力。改为不显示 Snackbar，仅让登录弹窗独立展示 |
| 4 | **`loginRequired` 事件是否需要一并触发** | ✅ 不建议 | `loginRequired` 会直接导航到登录页（无弹窗），与 `SessionExpiredEvent` 弹窗不符。用户预期看到弹窗再选择操作 |
| 5 | **多个 API 同时返回 401** | ✅ 无问题 | `SessionExpiredEvent._isExpired` 是 `bool`，多个 `notifyExpired()` 不会重复触发。collector 的 `if (isExpired)` 只执行一次 |
| 6 | **HomeScreen/Scanner 是否需要同样处理** | ⚠️ 暂不需要 | HomeScreen 有单独的 session 有效期监控。本次修复限定在用户反馈的 3 个模块 |
| 7 | **SessionExpiredEvent.reset() 时机** | ✅ 已验证 | 用户在登录弹窗点「重新登录」后调用 `reset()`，不影响后续再次触发 |
| 8 | **401 错误消息内容** | ✅ 改进 | 401 时不显示通用错误，改为 `"登录已过期，请重新登录"`，提示更明确 |
| 9 | **网络错误（非 401）** | ✅ 不影响 | 非 401 错误不触发 `SessionExpiredEvent`，保持现有 Snackbar 行为 |
| 10 | **ApkInstallReceiver** | ✅ 无关 | OTA 安装不涉及后端 API |
| 11 | **OrderSyncWorker** | ✅ 无关 | 后台 Worker 同步失败已有其他处理 |
| 12 | **本次跳过构建** | ✅ 按用户要求 | 只改代码，不更新版本号和构建，等通知再构建 |

---

## 改动

### 文件：[PickListViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListViewModel.kt)

**新增 import**：

```kotlin
import com.kuaimai.pda.util.SessionExpiredEvent
import retrofit2.HttpException
```

**`loadActiveOrders()` catch 块**：

```kotlin
} catch (e: Exception) {
    Log.e("PickListViewModel", "加载取货单列表失败: ${e.message}", e)
    if (e is HttpException && e.code() == 401) {
        SessionExpiredEvent.notifyExpired()
        _errorMessage.value = "登录已过期，请重新登录"
    } else {
        _errorMessage.value = "加载取货单列表失败: ${e.message?.take(80) ?: "未知错误"}"
    }
    _activeOrders.value = emptyList()
}
```

**注意**：
- 401 时不显示通用错误，改为 `"登录已过期，请重新登录"`，避免 Snackbar 与登录弹窗争抢用户注意力
- 不 401 时保留现有 Snackbar 行为

**同样方式处理 `loadAreas()` 和 `loadCompletedOrders()`** 的 catch 块。

---

### 文件：[PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt)

**新增 import**：

```kotlin
import com.kuaimai.pda.util.SessionExpiredEvent
import retrofit2.HttpException
```

**在所有 catch API 调用异常的块中增加 401 检测**。

---

### 文件：[ProductViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt)

**新增 import**：

```kotlin
import com.kuaimai.pda.util.SessionExpiredEvent
import retrofit2.HttpException
```

**在所有 catch API 调用异常的块中增加 401 检测**。

---

## 改动清单

| 文件 | 改动 |
|:-----|:------|
| PickListViewModel.kt | 新增 import + 3 个 catch 块增加 401 检测（401 时设专用消息，不 401 时保留通用消息） |
| PickDetailViewModel.kt | 新增 import + 6 个 catch 块增加 401 检测 |
| ProductViewModel.kt | 新增 import + 4 个 catch 块增加 401 检测 |

## 版本号

2.23 → 2.24（等用户通知后再构建）

## 完整 7 步流程

| Step | 内容 | 状态 |
|:----:|:-----|:----:|
| 1 | 查阅知识图谱 | 待执行 |
| 2 | 修改 3 个 ViewModel | 待执行 |
| 3 | 版本号 2.23→2.24 | **等用户通知** |
| 4 | 构建 APK | **等用户通知** |
| 5 | 更新知识图谱 | 待执行 |
| 6 | 同步 docker-deploy | 待执行 |
| 7 | Git 提交 `v2.24: 取货列表/商品详情增加401登录提示` | 待执行 |
