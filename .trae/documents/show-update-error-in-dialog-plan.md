# 更新弹窗显示下载错误详情

## 目标

当前下载失败时仅弹 Toast（一闪而过）和通知（Android 6.0 不显示），用户无法知道具体错误。

改为在弹窗内直接显示红色错误信息，用户看到后可以告诉我们，「下载失败: SSL handshake failed」之类的具体原因。

## 改动

### 文件：[MainActivity.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/MainActivity.kt)

**新增 1 个状态变量**（第 82 行附近）：

```kotlin
var downloadErrorMsg by remember { mutableStateOf<String?>(null) }
```

**弹窗文本部分**（第 107-120 行）新增错误展示：

```kotlin
text = {
    Column {
        Text("最新版本: v${info.latestVersion}")
        if (info.updateNotes.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(info.updateNotes)
        }
        if (isDownloading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text("正在下载更新...", style = MaterialTheme.typography.bodySmall)
        }
        // 【新增】下载错误信息
        if (downloadErrorMsg != null) {
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = downloadErrorMsg!!,
                color = androidx.compose.ui.graphics.Color(0xFFDC2626),
                fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        }
    }
}
```

**collect Failed 部分**（第 140-147 行）：

```kotlin
is DownloadState.Failed -> {
    isDownloading = false
    downloadErrorMsg = "下载失败: ${state.message}"
}
```

**点击「立即更新」时重置错误信息**（第 126 行）：

```kotlin
if (!isDownloading) {
    downloadErrorMsg = null  // 【新增】
    isDownloading = true
    ...
}
```

**onDismissRequest 时也重置**（第 100 行）：

```kotlin
onDismissRequest = {
    if (!info.forceUpdate && !isDownloading) {
        showUpdateDialog = false
        updateInfo = null
        downloadErrorMsg = null  // 【新增】
    }
}
```

**「稍后再说」按钮**（第 162-164 行）：

```kotlin
onClick = {
    showUpdateDialog = false
    updateInfo = null
    downloadErrorMsg = null  // 【新增】
}
```

## 效果

```
┌─────────────────────────────┐
│    发现新版本               │
│                             │
│ 最新版本: v2.17             │
│                             │
│ 下载失败: SSL handshake     │
│ failed                     │  ← 红色文字，用户可见
│                             │
│      [稍后再说]  [立即更新] │
└─────────────────────────────┘
```

## 改动范围

1 个文件，新增约 10 行，改了 5 处。
