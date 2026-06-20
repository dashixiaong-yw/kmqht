# 确认弹窗 UI 改进计划（简化版）

## 修改范围

**仅修改 1 个文件**：[ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt#L660-L693)

**不改任何功能**，仅重写 `ConfirmDialog` composable 的外观。

---

## 现状

```kotlin
// 当前 34 行代码 - 直板、无圆角、无层次
androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
        TextButton(onClick = onConfirm) { Text("确认", color = BrandBlue) }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text("取消") }
    }
)
```

## 改进点

| 改进 | 当前 | 改为 |
|:-----|:-----|:-----|
| 圆角 | 直角 | 16dp（与 SupplierSelectDialog 一致） |
| 阴影 | 无 | `tonalElevation = 6.dp` 产生层次 |
| 标题字号 | 默认（跟随主题） | 18sp Bold |
| 内容字号 | 默认 | 说明 14sp + 高亮值 20sp Bold |
| 按钮样式 | TextButton | 确认用品牌 `PrimaryLightBg` 按钮，取消灰色 TextButton |
| 触控区 | TextButton 默认 | 确认按钮 48dp 高度 |

## 统一颜色方案

全部使用一个色调 —— **品牌蓝**：

- 高亮值背景：`PrimaryLightBg`（浅蓝底）
- 高亮值文字：`PrimaryLightText`（深蓝字）
- 确认按钮：`PrimaryLightBg` 底 + `PrimaryLightText` 字
- 取消按钮：`TextSecondary` 灰色

## 布局

```
┌──────────────────────────────┐
│                              │
│    确认保存备注               │  18sp Bold
│    确认切换供应商             │
│                              │
│    是否保存备注修改？         │  14sp 灰色
│                              │
│  ┌────────────────────────┐  │
│  │    备注内容/备货②       │  │  20sp Bold 浅蓝底深蓝字
│  └────────────────────────┘  │
│                              │
│        [取消]    [确认修改]   │  并排，取消左确认右
│                              │
└──────────────────────────────┘
```

备注和供应商弹窗仅文本内容不同，颜色完全一致。

## 关键代码

```kotlin
@Composable
private fun ConfirmDialog(
    confirmType: ConfirmType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (confirmType) {
        is ConfirmType.Remark -> "确认保存备注"
        is ConfirmType.Supplier -> "确认切换供应商"
    }
    val message = when (confirmType) {
        is ConfirmType.Remark -> "是否保存备注修改？"
        is ConfirmType.Supplier -> "是否将供应商切换为「${confirmType.name}」？"
    }
    val highlightedText = when (confirmType) {
        is ConfirmType.Remark -> confirmType.remark
        is ConfirmType.Supplier -> confirmType.name
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = SurfaceWhite,
        tonalElevation = 6.dp,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = PrimaryLightBg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = highlightedText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLightText,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryLightBg,
                    contentColor = PrimaryLightText
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("确认修改", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}
```

## 做了什么、没做什么

| 做了 | 没做 |
|:----|:-----|
| ✅ 加 16dp 圆角 + 6dp 阴影 | ❌ 不改功能逻辑 |
| ✅ 标题 18sp Bold | ❌ 不改 ViewModel |
| ✅ 高亮值用 20sp Bold 浅蓝卡片展示 | ❌ 不改其他文件 |
| ✅ 确认按钮用品牌色 FilledButton | ❌ 不新增颜色常量 |
| ✅ 统一蓝色调（不区分操作类型） | ❌ 不动其他弹窗 |

## 验证

1. `./gradlew lint` 通过
2. 点击确认触发对应回调（功能不变）
3. 点击取消关闭弹窗
4. 16dp 圆角 + 阴影可见
