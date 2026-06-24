# 修复：所有商品完成后隐藏恢复按钮

## 问题

当前恢复按钮的隐藏条件：

```
恢复按钮隐藏条件 = order?.status == 1（取货单整体状态为已完成）
```

但用户逐一点击每个商品的「完成」后，`order.status` 仍然是 **0**（进行中），只有点击底部「全部完成」按钮或后端同步后才会变成 **1**。所以会出现「所有商品都显示已完成，但已完成商品仍然有恢复按钮」的矛盾状态。

## 正确行为

**当所有商品都被标记为已完成时（completedCount == totalCount），已完成商品的恢复按钮应自动隐藏，显示「已完成」文字。**

## 改动

### 文件：[PickDetailScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt)

**新增 1 个变量**（第 202 行之后）：

```kotlin
val allCompleted = completedCount >= totalCount && totalCount > 0
```

**改第 357-358 行**：

```kotlin
// 改前
onLongPress = { if (order?.status != 1) showDeleteConfirm = item },
orderCompleted = order?.status == 1,

// 改后
onLongPress = { if (order?.status != 1 && !allCompleted) showDeleteConfirm = item },
orderCompleted = order?.status == 1 || allCompleted,
```

**逻辑**：`orderCompleted` 为 true 的条件扩展为：

| 条件 | 说明 |
|:-----|:-----|
| `order?.status == 1` | 取货单整体已完成（已有逻辑） |
| `allCompleted` | 所有商品已逐一点完完成（新增逻辑） |

**影响**：

| 影响点 | 条件变化 | 原行号 |
|:-------|:---------|:------:|
| 已完成商品的「恢复按钮」隐藏 | `order?.status == 1` → `order?.status == 1 \|\| allCompleted` | 358 |
| 已完成商品的长按删除禁用 | `order?.status != 1` → `order?.status != 1 && !allCompleted` | 357 |
| 「全部完成」按钮 | 不变（已有 `completedCount < totalCount`） | 417 |
| 底部提示文字 | 不变（保持 `order?.status == 1`） | 428 |

## 验证

| 场景 | 预期 |
|:-----|:-----|
| 全部商品未完成 | 已完成的商品显示恢复按钮 |
| 点完最后一个商品的完成（completedCount == totalCount） | 所有已完成商品立即变为「已完成」文字，恢复按钮消失 |
| 恢复某个商品（completedCount < totalCount） | 该商品恢复为「完成」按钮，其他已完成商品恢复按钮重新出现 |
| order.status == 1 | 恢复按钮隐藏（原有逻辑正常） |

## 改动范围

仅 1 个文件 1 行代码。
