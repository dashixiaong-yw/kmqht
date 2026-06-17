# 计划：近期8个版本回归检查（v1.12 ~ v1.19）

## 摘要

对 v1.12 到 v1.19 共 8 个版本的修复内容进行全面回归检查，确保无回归 bug。

## 发现的问题清单

### P0 - 必须修复（2项）

| # | 版本 | 文件 | 行号 | 问题 |
|:-:|:----:|:-----|:----:|------|
| 1 | v1.16 | `backend/app/routers/admin.py` | L502-503 | **拣货区名称 XSS 漏洞**：`a.name` 在 `<td>` 和 `onclick` 中未转义，可注入任意 HTML/JS。用户通过页面创建的拣货区名称包含恶意内容时，可突破 `onclick=` 属性注入 JS |
| 2 | v1.15 | `app/.../scanner/ScannerManager.kt` | L95 | **Android 14+ RECEIVER_EXPORTED 缺失**：`registerReceiver(receiver, filter)` 未指定导出标志，在 Android 14+ PDA 设备上厂商自定义广播可能触发 `SecurityException` |

### P1 - 建议修复（5项）

| # | 版本 | 文件 | 行号 | 问题 |
|:-:|:----:|:-----|:----:|------|
| 3 | v1.17 | `app/.../ui/components/PickItemRow.kt` | L91-94 | **规格图触摸热区 52dp < 56dp**：用户点击规格图预览时可能点不到 |
| 4 | v1.17 | `app/.../ui/components/PickItemRow.kt` | L162-165 | **库区图触摸热区 40dp < 56dp**：用户点击库区图预览时可能点不到 |
| 5 | v1.17 | `app/.../ui/home/HomeScreen.kt` | L77-79 | **引导条 prefs=null 永远无法关闭**：当 `prefs` 参数为 null 时，`showGuide` 始终为 true 且关闭按钮无效 |
| 6 | v1.18 | `app/.../ui/pickdetail/PickDetailViewModel.kt` | L379-381 | **getImageUrls catch 块缺少日志**：捕获异常后直接返回 null，未记录任何日志，排查问题困难 |
| 7 | v1.15 | `app/.../data/OrderSyncWorker.kt` | L78-86 | **4xx retryCount 被 doWork 覆盖**：`syncOperation()` 将 4xx 标记为 retryCount=-1，但 `doWork()` 的 else 分支用快照值重新计算覆盖了 -1，导致 4xx 被重试最多3次后才最终标记冲突 |

### P2 - 改进建议（4项）

| # | 版本 | 文件 | 行号 | 问题 |
|:-:|:----:|:-----|:----:|------|
| 8 | v1.16 | `app/.../MainActivity.kt` | L83 | **enqueueSyncWorker 未去重**：使用 `enqueue()` 而非 `beginUniqueWork()`，Activity 重建时会重复入队多个 worker |
| 9 | v1.17 | `app/.../ui/home/HomeScreen.kt` | L198 | **会话警告条未使用 AppAlignment 常量**：使用原始 `Alignment.CenterVertically` 而非 `AppAlignment.RowCenter` |
| 10 | v1.16 | `app/.../data/repository/ImageRepository.kt` | L76-97 | **syncImagesFromBackend 无事务保护**：delete 和 insert 之间无事务，中途失败会导致数据丢失 |
| 11 | v1.15 | `app/.../scanner/ScannerManager.kt` | L76-78 | **SoundPool 注册时重建**：每次 register() 都重建 SoundPool 和加载音效，建议移到 init 块 |

### 状态良好的修复（无问题，共 20+ 项）

- v1.18: OkHttp 日志级别正确为 HEADERS ✓
- v1.18: ImageRepository 所有 catch 块均有日志 ✓
- v1.18: AuthRepository session 刷新日志使用 Log.e ✓
- v1.17: PickOrderCard/SettingsScreen/NetworkStatusIndicator 正确使用 AppAlignment 常量 ✓
- v1.17: 装箱图触摸热区 56dp ✓
- v1.16: admin.py 用户名已转义（escapeHtml）✓
- v1.16: MainActivity onResume/onPause 正确配对 ScannerManager ✓
- v1.16: system.py 所有4个快麦路由正确设置 settings 权限 ✓
- v1.16: syncImagesFromBackend 先删后插顺序正确 ✓
- v1.15: SoundPool.load() 使用 AssetFileDescriptor 重载 ✓
- v1.15: JSONObject.NULL 处理正确 ✓
- v1.15: OrderSyncWorker 4xx 保留记录不删除 ✓
- v1.15: GuideScreen 使用 EncryptedSharedPreferences 存储 ✓
- v1.15: NetworkModule 从加密存储读取 ✓
- v1.14: /admin 正确在 SKIP_AUTH_PREFIXES 中 ✓
- v1.14: Dockerfile 构建正确 ✓
- v1.13: uploadImage 返回 Pair<Long,String> ✓
- v1.13: deleteImage 先远程后本地 ✓
- v1.13: deleteOrderWithQueue 存在且正确 ✓
- v1.12: GuideScreen API Key 存入 encryptedPrefs ✓
- v1.12: SessionExpiredEvent 监听正确 ✓
- v1.12: confirmDelete 在线/离线策略正确 ✓

## 影响范围与风险

| 问题 | 触发条件 | 影响 |
|:----:|----------|------|
| P0-1 XSS | 用户创建含恶意内容的拣货区名称 | 管理员访问管理后台时 JS 被执行 |
| P0-2 RECEIVER_EXPORTED | Android 14+ PDA 设备上扫码 | 扫码广播注册时崩溃，扫码完全失效 |
| P1-3 规格图热区 | 用户点击规格图 | 点击不到，但可通过"完成"按钮间接操作 |
| P1-4 库区图热区 | 用户点击库区图 | 点击不到，但可通过"完成"按钮间接操作 |
| P1-5 prefs=null | 启动时未正确注入 prefs 参数 | 引导条一直显示且无法关闭 |
| P1-6 catch无日志 | getImageUrls 异常 | 出现问题后无法排查 |
| P1-7 4xx被覆盖 | 离线队列中的 4xx 请求 | 最多产生 3 次无用重试 |

## 修复方案

### P0-1: admin.py 拣货区名称 XSS

在 L502-503 中，将 `a.name` 改为 `escapeHtml(a.name)`：

```javascript
// 修改前
<td>${a.name}</td>
onclick="confirmDeleteArea(${a.id},'${a.name}')"

// 修改后
<td>${escapeHtml(a.name)}</td>
onclick="confirmDeleteArea(${a.id},'${escapeHtml(a.name)}')"
```

### P0-2: ScannerManager RECEIVER_EXPORTED

在 L93 添加 API 版本判断：

```kotlin
val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Context.RECEIVER_EXPORTED
} else {
    0
}
context.registerReceiver(receiver, filter, flag)
```

需要新增 import: `import android.os.Build`（如已存在则不需要）

### P1-3/P1-4: PickItemRow 触摸热区

规格图和库区图 Box 尺寸从 52dp/40dp 扩大到 56dp：
- 规格图：`Modifier.size(52.dp)` → `Modifier.size(56.dp)`
- 库区图：`Modifier.size(40.dp)` → `Modifier.size(56.dp)`

### P1-5: HomeScreen prefs=null 处理

```kotlin
// 修改前
var showGuide by remember {
    mutableStateOf(prefs?.getBoolean(KEY_GUIDE_SHOWN, false) != true)
}

// 改为
var showGuide by remember {
    mutableStateOf(prefs?.getBoolean(KEY_GUIDE_SHOWN, false) == false)
}
```

注意 `!= true` 改为 `== false`，当 prefs=null 时返回 false（不显示引导条）。

### P1-6: PickDetailViewModel catch 加日志

```kotlin
} catch (e: Exception) {
    Log.w("PickDetailViewModel", "获取图片URL失败: ${e.message}")
    Pair(null, null)
}
```

### P1-7: OrderSyncWorker retryCount 覆盖

在 doWork else 分支中，先重新查询当前 retryCount，若已是 -1 则不覆盖：

```kotlin
if (success) {
    pendingOperationDao.deleteById(op.id)
} else {
    // 重新查询当前retryCount，防止syncOperation已设置-1被覆盖
    val current = pendingOperationDao.getById(op.id)
    if (current?.retryCount == -1) {
        // 已标记为冲突，不覆盖
    } else {
        val newRetryCount = op.retryCount + 1
        if (newRetryCount >= MAX_RETRY) {
            pendingOperationDao.updateRetryCount(op.id, -1)
        } else {
            pendingOperationDao.updateRetryCount(op.id, newRetryCount)
        }
    }
}
```

## 涉及文件清单

| 文件 | 修改项 |
|:-----|--------|
| `backend/app/routers/admin.py` | P0-1: 拣货区名称 escapeHtml |
| `app/.../scanner/ScannerManager.kt` | P0-2: RECEIVER_EXPORTED + P2-11: SoundPool移到init |
| `app/.../ui/components/PickItemRow.kt` | P1-3/P1-4: 规格图/库区图 56dp |
| `app/.../ui/home/HomeScreen.kt` | P1-5: prefs=null处理 + P2-9: AppAlignment |
| `app/.../ui/pickdetail/PickDetailViewModel.kt` | P1-6: catch加日志 |
| `app/.../data/OrderSyncWorker.kt` | P1-7: retryCount覆盖修复 |

## 验证步骤

1. **admin.py XSS**：创建包含 `');alert(1);//` 的拣货区名称，确认管理后台不弹窗
2. **ScannerManager**：确认 registerReceiver 包含 RECEIVER_EXPORTED 标志
3. **PickItemRow**：确认规格图和库区图尺寸 ≥ 56dp
4. **HomeScreen**：确认 prefs=null 时引导条不显示
5. **PickDetailViewModel**：确认 catch 块有 Log.w 调用
6. **OrderSyncWorker**：单元测试验证 4xx → retryCount=-1 不被覆盖
7. `./gradlew lint` 通过
8. `./gradlew assembleDebug` 构建成功
