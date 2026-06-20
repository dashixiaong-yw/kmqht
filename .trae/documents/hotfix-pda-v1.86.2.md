# 修复计划：日志改为 App 内显示

## 改动

### 1. SettingsScreen.kt：日志内容弹窗

在设置页新增状态 + 弹窗，点击按钮显示日志内容：

```kotlin
var showLogDialog by remember { mutableStateOf(false) }
val appContext = LocalContext.current
```

弹窗（放在 Scaffold 外部、函数末尾）：

```kotlin
if (showLogDialog) {
    val logContent = try {
        java.io.File(appContext.cacheDir, "sync_log.txt").readText()
    } catch (_: Exception) { "暂无同步日志" }

    AlertDialog(
        onDismissRequest = { showLogDialog = false },
        shape = RoundedCornerShape(16.dp),
        title = { Text("同步日志") },
        text = {
            Column {
                Text(
                    text = logContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "长按上方文字可复制",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { showLogDialog = false }) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // 复制到剪贴板
                val clipboard = appContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("sync_log", logContent))
                android.widget.Toast.makeText(appContext, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                showLogDialog = false
            }) {
                Text("复制")
            }
        }
    )
}
```

按钮改为：

```kotlin
TextButton(
    onClick = { showLogDialog = true },
    modifier = Modifier.fillMaxWidth()
) {
    Text("查看同步日志", color = TextSecondary, fontSize = 12.sp)
}
```

**变更**：按钮文本「导出同步日志」→「查看同步日志」，点击弹窗显示内容，可复制。

### 2. PickDetailViewModel.kt：删除 loadSuppliersFromLocal() 调用

```kotlin
// 删除 L191 的 loadSuppliersFromLocal() 调用
```

## 验证

1. 供应商筛选：第一个商品添加后立即出现供应商标签 ✅
2. 查看日志：点击「查看同步日志」→ 弹窗显示日志内容 → 可复制发送 ✅
