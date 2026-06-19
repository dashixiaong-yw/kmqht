# v1.61: 修复拣货区加载错误被硬编码假数据掩盖

## 现象

后台管理页面已配置拣货区，PDA 新建取货单时提示"暂无拣货区，请先在设置中配置"。

## 根因

[PickListViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListViewModel.kt) L81-L84 的 `catch` 用硬编码假数据**掩盖所有真实错误**：

```kotlin
catch (e: Exception) {
    Log.w("PickListViewModel", "加载拣货区失败: ${e.message}")
    _areas.value = listOf("A区", "B区", "C区", "D区")  // ← 无论网络/SSL/认证错误都显示假数据
}
```

真实问题可能是网络不通、SSL 证书、API 认证或数据库为空，但都被假数据遮蔽，用户永远看不到真实错误。

## 修复

### 仅改 1 处：PickListViewModel.kt L81-L84

将 `catch` 中的硬编码假数据替换为设置 `_errorMessage`（复用已有的错误提示机制）：

```kotlin
catch (e: Exception) {
    Log.e("PickListViewModel", "加载拣货区失败: ${e.message}", e)
    _errorMessage.value = "加载拣货区失败: ${e.message?.take(80) ?: "未知错误"}"
    _areas.value = emptyList()
}
```

已有基础设施：`PickListScreen.kt` 已监听 `errorMessage` 并通过 Snackbar 显示，无需额外 UI 改动。

### 效果

| 场景 | 修复前 | 修复后 |
|:-----|:-----|:-----|
| 网络不通 | 显示"A区 B区 C区 D区" | Snackbar 显示 "加载拣货区失败: ConnectException..." |
| SSL证书错误 | 显示假数据 | Snackbar 显示 "加载拣货区失败: CertPathValidatorException..." |
| 数据库真的没配区域 | 显示"暂无拣货区" ✅ | 显示"暂无拣货区" ✅ 不变 |

## 步骤

| Step | 操作 |
|:----:|------|
| 1 | PickListViewModel.kt 移除硬编码兜底 |
| 2 | 构建 APK v1.61 |
| 3 | 版本号 1.60→1.61 + sync + Git |

## 验证

清除数据 → 首次打开 → 登录 → 取货列表 → 如果后台有配拣货区 → 正常显示；如果后台数据库空 → 显示"暂无拣货区"；如果网络/SSL错误 → Snackbar 显示具体错误信息。
