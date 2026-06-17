# 第三次全面代码审计计划

> 生成日期：2026-06-17
> 背景：在 v1.13（12个修复）+ v1.14（18个修复）基础上进行第三次全面检查

---

## 一、检查摘要

覆盖之前未深入审查的模块：扫码模块、网络层底层、API/DTO后端对照、后端核心服务。

| 级别 | 数量 | 说明 |
|:----:|:----:|------|
| **P1** | 7 | 功能异常/崩溃：扫码音效不可用、Android 14+崩溃、签名不匹配、数据丢失 |
| **P2** | 9 | 逻辑缺陷/结构问题：缓存过期、包结构、并发安全、死代码 |
| **P3** | 5 | 代码质量：命名风格、导入优化 |

---

## 二、P1 高级别缺陷

### P1-1：ScannerManager SoundPool.load() 参数错误

- **问题**：`SoundPool.load(String, int)` 传入了 content:// URI 字符串，应使用 `load(Context, Uri, int)`
- **影响**：扫码反馈提示音永远无法播放
- **文件**：[ScannerManager.kt:L77-L82](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/scanner/ScannerManager.kt#L77-L82)
- **修复**：改为 `soundPool?.load(context, ...Uri, 1)`

### P1-2：Android 14+ 广播注册缺少导出标记

- **问题**：`registerReceiver(receiver, filter)` 在 API 34+ 抛 SecurityException
- **影响**：Android 14 设备上崩溃
- **文件**：[ScannerManager.kt:L88](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/scanner/ScannerManager.kt#L88)
- **修复**：加 `Context.RECEIVER_EXPORTED`

### P1-3：CameraPreview 空 catch 块

- **问题**：`catch (e: Exception) { }` 空实现
- **文件**：[CameraScanScreen.kt:L160-L162](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/scanner/CameraScanScreen.kt#L160-L162)
- **修复**：加 `Log.e` 日志

### P1-4：PdaScannerReceiver 死代码

- **问题**：与 ScannerManager 内联 BroadcastReceiver 功能完全重复，且未被引用
- **文件**：PdaScannerReceiver.kt
- **修复**：删除文件

### P1-5：KuaimaiInterceptor JSON null 签名错误

- **问题**：`JSONObject.get(key)` 返回 `JSONObject.NULL`，`toString()` 得 `"null"` 字符串，导致签名计算与服务端不一致
- **文件**：[KuaimaiInterceptor.kt:L57](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/KuaimaiInterceptor.kt#L57)
- **修复**：判断 `value === JSONObject.NULL` 时取空字符串

### P1-6：OrderSyncWorker 4xx 数据永久丢失

- **问题**：4xx 标记冲突后 `return true` 删除 pending 记录
- **文件**：OrderSyncWorker.kt:L125-L131
- **修复**：冲突记录不删除，保留给用户查看

### P1-7：TokenAuthenticator runBlocking

- **问题**：OkHttp 认证器中使用 `runBlocking` 阻塞分发线程
- **文件**：NetworkModule.kt:L293
- **修复**：标注风险注释

---

## 三、P2 中级别

| # | 描述 | 文件 |
|:-:|------|------|
| P2-1 | ImageCompressor parentFile 可能 null | ImageCompressor.kt |
| P2-2 | calculateSampleSize 缺尺寸校验 | ImageCompressor.kt |
| P2-3 | session 刷新未持久化到 SP | NetworkModule.kt |
| P2-4 | Content-Type 被强制替换 | KuaimaiInterceptor.kt |
| P2-5 | AreaCreateRequest 包路径不一致 | AreaApiService.kt |
| P2-6 | get_item_detail 未调用 | kuaimai_api.py |
| P2-7 | SKU 缓存永不过期 | cache.py |
| P2-8 | isScanned 防重复不重置 | CameraScanScreen.kt |
| P2-9 | lastScanTime 非线程安全 | ScannerManager.kt |

---

## 四、验证标准

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 成功
3. 后端 `uvicorn main:app --port 8000` 正常
