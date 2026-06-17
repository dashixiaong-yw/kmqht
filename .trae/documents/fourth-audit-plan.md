# 第四次全面代码审计计划 - 最终验证

> 生成日期：2026-06-17
> 背景：在三次审计（v1.13=12修复, v1.14=18修复, v1.15=16修复）基础上，对仍未被覆盖的模块做最终检查

---

## 一、已完成的审计覆盖

| 版本 | 修复数 | 覆盖范围 |
|:----:|:------:|----------|
| v1.13 | 12 | 图片上传/删除、供应商、刷新入队、401检测、死代码 |
| v1.14 | 18 | SP匹配、Dockerfile、同步脚本、启动白屏、asyncio、认证、配置 |
| v1.15 | 16 | 扫码音效、广播崩溃、签名、4xx丢失、死代码 |

---

## 二、本次新增发现的缺陷

| 级别 | 数量 | 说明 |
|:----:|:----:|------|
| **P0** | 2 | 严重安全漏洞：管理后台XSS |
| **P1** | 6 | 功能不可用或安全缺失：离线同步失效、PDA扫码失效、权限缺失、图片不显示 |
| **P2** | 4 | 逻辑缺陷：记录重复、触摸热区、对齐常量 |

---

## 三、P0 安全漏洞

### P0-1：管理后台XSS - 用户名嵌入onclick属性

- **文件**：backend/app/routers/admin.py:L389
- **问题**：`onclick='editUser(${{JSON.stringify(u)}})'` 用户数据直接嵌入HTML onclick属性，用户名含单引号时可突破属性边界执行任意JS
- **修复**：使用`encodeURIComponent(JSON.stringify(u))` 或改用事件监听器

### P0-2：管理后台XSS - 用户名直接渲染

- **文件**：backend/app/routers/admin.py:L383
- **问题**：`<td>${{u.username}}</td>` 直接渲染，用户名含`<script>`时触发存储型XSS
- **修复**：前端使用`textContent`而非`innerHTML`或添加HTML转义

---

## 四、P1 功能缺陷

### P1-1：WorkManager触发机制缺失（离线同步永不可用）

- **文件**：全局 - `OrderSyncWorker`已定义但`WorkManager.enqueue()`从未被调用
- **影响**：任何离线操作（completeItem/addItem/delete/update）写入pending_operations后，永远不会被自动同步到后端。网络恢复后数据永久留在本地
- **修复**：在NetworkMonitor检测到网络恢复时或App启动时调用WorkManager.enqueue()

### P1-2：ScannerManager.register() 从未被调用（PDA硬件扫码失效）

- **文件**：MainActivity.kt / PickDetailScreen.kt
- **问题**：ScannerManager被注入但register()从未被调用，BroadcastReceiver未注册。PDA硬件扫码广播无法接收，vibrator/soundPool未初始化导致反馈失效
- **修复**：在PickDetailScreen的LaunchedEffect中调用register()，DisposableEffect中unregister()

### P1-3：kuaimai/* 三个路由权限缺失

- **文件**：backend/app/routers/system.py:L86,L100,L126
- **问题**：`update_kuaimai_credentials`（修改快麦凭证）、`refresh_kuaimai_session`（刷新session）只要求登录，应要求settings权限
- **修复**：改为`Depends(check_permission("settings"))`

### P1-4：PickItemRow图片URL未拼接服务器地址

- **文件**：app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt:L100,L169-L176,L211-L218
- **问题**：areaImageUrl/boxImageUrl从后端返回相对路径（如`/images/xxx.jpg`），直接传给AsyncImage不预拼接服务器BaseURL。Coil无自定义配置，相对路径无法解析为完整HTTP URL
- **修复**：在PickDetailViewModel.getImageUrls()中拼接完整URL，或在PickItemRow内部拼接

### P1-5：PDA硬件扫码注册无生命周期管理

- **文件**：MainActivity.kt/Multiple files
- **问题**：ScannerManager.register()和unregister()没有在合适的Activity/Fragment生命周期中调用。不仅是缺失，即使补上也无正确的生命周期绑定（应在onResume注册、onPause注销）
- **修复**：在MainActivity中注入ScannerManager，在onResume/onPause中注册/注销，或在每个使用扫码的Screen中管理

### P1-6：401处理覆盖范围不一致

- **文件**：UserRepository.kt:L246-L253 vs OrderApiService/AreaApiService/KuaimaiApiService
- **问题**：handleAuthError 仅覆盖UserRepositoryImpl中5个方法。OrderApiService等API的401只能走TokenAuthenticator，而TokenAuthenticator侧重快麦session刷新。token过期时用户可能收到混乱的异常信息而非明确的重新登录提示
- **修复**：在各Repository中统一401处理，或扩展TokenAuthenticator的能力

---

## 五、P2 逻辑缺陷

### P2-1：syncImagesFromBackend 未清理旧记录

- **文件**：ImageRepository.kt:L74-L93
- **问题**：每次同步调用insert而非upsert，同skuOuterId+imageType的旧记录累积

### P2-2：触摸热区多处 < 56dp

- **文件**：HomeScreen.kt:L176/L209、PickItemRow.kt:L92/L162/L204/L252、SettingsScreen.kt:L112
- **总计**：7处

### P2-3：未使用 Alignment.kt 常量

- **文件**：HomeScreen.kt(3处)、PickOrderCard.kt(3处)、NetworkStatusIndicator.kt(2处)、SettingsScreen.kt(4处)
- **总计**：12处

### P2-4：HomeScreen.kt引导条prefs=null异常

- **文件**：HomeScreen.kt:L77
- **问题**：`prefs?.getBoolean(KEY_GUIDE_SHOWN, false) == false`，prefs=null时`null==false`返回false，引导提示不显示

---

## 六、验证标准

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 成功
3. 后端 `uvicorn main:app --port 8000` 正常
