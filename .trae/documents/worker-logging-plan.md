# Worker 日志全覆盖计划

## 目标

所有 Worker 同步操作的**每个入口、每个退出点（成功/失败/异常）**都有 `appendLog` 输出，
确保一次日志导出就能定位任何阻塞点或 Bug。

## 现状审计

### ✅ 已有完善日志的方法
- `syncRemarkUpdate()` — 每个 exit 点都有 ✅
- `syncSupplierUpdate()` — 每个 exit 点都有 ✅
- `fetchLatestSkuDataViaBackend()` — 成功/失败都有 ✅

### ❌ 完全缺失日志的方法（需要添加）

以下方法没有任何 `appendLog` 调用：

| 方法 | 缺少的关键日志点 |
|:-----|:----------------|
| `syncCompleteItem()` | ①失败(userRepo/api为null) ②API调用开始 ③业务拒绝 ④异常 |
| `syncRestoreItem()` | 同上 ①②③④ |
| `syncAddItem()` | ①提取payload失败 ②API调用开始 ③API异常 ④userRepo/api为null |
| `syncCompleteAll()` | ①userRepo/api为null ②业务拒绝 ③异常 |
| `syncDeleteItem()` | 同上 ①②③ |
| `syncDeleteOrder()` | 同上 ①②③ |
| `syncImageUpload()` | ①提取payload失败 ②文件不存在 ③uploader/imageDao为null ④上传异常 |
| `syncOperation()` | ①未知操作类型 ②HTTP 4xx标记冲突 ③HTTP 5xx重试 ④异常 |
| `extractPayloadValue()` | ①key不存在返回null时（当前仅记录JSON解析异常） |
| `doWork()` | ①Worker失败退出 ②Worker重试 ③Worker完成 |

## 修改方案

### 修改1：`syncCompleteItem()` — 添加 +7 行

```kotlin
private suspend fun syncCompleteItem(op: PendingOperationEntity): Boolean {
    val userRepo = userRepository ?: run {
        appendLog(applicationContext, "completeItem同步失败: userRepository为null")
        return false
    }
    val api = orderApiService ?: run {
        appendLog(applicationContext, "completeItem同步失败: orderApiService为null")
        return false
    }
    val token = userRepo.getToken()
    appendLog(applicationContext, "completeItem开始同步: orderId=${op.orderId}, itemId=${op.targetId}")
    return try {
        val resp = api.completeItem(token, op.orderId, op.targetId)
        if (!resp.success) {
            Log.w(TAG, "completeItem业务拒绝: ${resp.message}")
            appendLog(applicationContext, "completeItem业务拒绝: ${resp.message}")
            return false
        }
        appendLog(applicationContext, "completeItem同步成功: orderId=${op.orderId}, itemId=${op.targetId}")
        true
    } catch (e: Exception) {
        Log.w(TAG, "completeItem同步失败: ${e.message}")
        appendLog(applicationContext, "completeItem同步异常: ${e.message}")
        false
    }
}
```

### 修改2：`syncRestoreItem()` — 同样 +7 行

```kotlin
private suspend fun syncRestoreItem(op: PendingOperationEntity): Boolean {
    val userRepo = userRepository ?: run {
        appendLog(applicationContext, "restoreItem同步失败: userRepository为null")
        return false
    }
    val api = orderApiService ?: run {
        appendLog(applicationContext, "restoreItem同步失败: orderApiService为null")
        return false
    }
    val token = userRepo.getToken()
    appendLog(applicationContext, "restoreItem开始同步: orderId=${op.orderId}, itemId=${op.targetId}")
    return try {
        val resp = api.restoreItem(token, op.orderId, op.targetId)
        if (!resp.success) {
            Log.w(TAG, "restoreItem业务拒绝: ${resp.message}")
            appendLog(applicationContext, "restoreItem业务拒绝: ${resp.message}")
            return false
        }
        appendLog(applicationContext, "restoreItem同步成功: orderId=${op.orderId}, itemId=${op.targetId}")
        true
    } catch (e: Exception) {
        Log.w(TAG, "restoreItem同步失败: ${e.message}")
        appendLog(applicationContext, "restoreItem同步异常: ${e.message}")
        false
    }
}
```

### 修改3：`syncAddItem()` — +6 行

```kotlin
private suspend fun syncAddItem(op: PendingOperationEntity): Boolean {
    val userRepo = userRepository ?: run {
        appendLog(applicationContext, "addItem同步失败: userRepository为null")
        return false
    }
    val api = orderApiService ?: run {
        appendLog(applicationContext, "addItem同步失败: orderApiService为null")
        return false
    }
    val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: run {
        appendLog(applicationContext, "addItem同步失败: payload缺少sku_outer_id")
        return false
    }
    val token = userRepo.getToken()
    appendLog(applicationContext, "addItem开始同步: orderId=${op.orderId}, sku=$skuOuterId")
    return try {
        api.addItem(token, op.orderId, AddOrderItemRequest(skuOuterId = skuOuterId))
        appendLog(applicationContext, "addItem同步成功: orderId=${op.orderId}, sku=$skuOuterId")
        true
    } catch (e: Exception) {
        Log.w(TAG, "addItem同步失败: ${e.message}")
        appendLog(applicationContext, "addItem同步异常: orderId=${op.orderId}, sku=$skuOuterId, error=${e.message}")
        false
    }
}
```

### 修改4：`syncCompleteAll()` — +7 行

```kotlin
private suspend fun syncCompleteAll(op: PendingOperationEntity): Boolean {
    val userRepo = userRepository ?: run {
        appendLog(applicationContext, "completeAll同步失败: userRepository为null")
        return false
    }
    val api = orderApiService ?: run {
        appendLog(applicationContext, "completeAll同步失败: orderApiService为null")
        return false
    }
    val token = userRepo.getToken()
    appendLog(applicationContext, "completeAll开始同步: orderId=${op.orderId}")
    return try {
        val resp = api.completeAllItems(token, op.orderId)
        if (!resp.success) {
            Log.w(TAG, "completeAll业务拒绝: ${resp.message}")
            appendLog(applicationContext, "completeAll业务拒绝: ${resp.message}")
            return false
        }
        appendLog(applicationContext, "completeAll同步成功: orderId=${op.orderId}")
        true
    } catch (e: Exception) {
        Log.w(TAG, "completeAll同步失败: ${e.message}")
        appendLog(applicationContext, "completeAll同步异常: ${e.message}")
        false
    }
}
```

### 修改5：`syncDeleteItem()` — +7 行

```kotlin
private suspend fun syncDeleteItem(op: PendingOperationEntity): Boolean {
    val userRepo = userRepository ?: run {
        appendLog(applicationContext, "deleteItem同步失败: userRepository为null")
        return false
    }
    val api = orderApiService ?: run {
        appendLog(applicationContext, "deleteItem同步失败: orderApiService为null")
        return false
    }
    val token = userRepo.getToken()
    appendLog(applicationContext, "deleteItem开始同步: orderId=${op.orderId}, itemId=${op.targetId}")
    return try {
        val resp = api.deleteItem(token, op.orderId, op.targetId)
        if (!resp.success) {
            Log.w(TAG, "deleteItem业务拒绝: ${resp.message}")
            appendLog(applicationContext, "deleteItem业务拒绝: ${resp.message}")
            return false
        }
        appendLog(applicationContext, "deleteItem同步成功: orderId=${op.orderId}, itemId=${op.targetId}")
        true
    } catch (e: Exception) {
        Log.w(TAG, "deleteItem同步失败: ${e.message}")
        appendLog(applicationContext, "deleteItem同步异常: ${e.message}")
        false
    }
}
```

### 修改6：`syncDeleteOrder()` — +7 行

```kotlin
private suspend fun syncDeleteOrder(op: PendingOperationEntity): Boolean {
    val userRepo = userRepository ?: run {
        appendLog(applicationContext, "deleteOrder同步失败: userRepository为null")
        return false
    }
    val api = orderApiService ?: run {
        appendLog(applicationContext, "deleteOrder同步失败: orderApiService为null")
        return false
    }
    val token = userRepo.getToken()
    appendLog(applicationContext, "deleteOrder开始同步: orderId=${op.orderId}")
    return try {
        val resp = api.deleteOrder(token, op.orderId)
        if (!resp.success) {
            Log.w(TAG, "deleteOrder业务拒绝: ${resp.message}")
            appendLog(applicationContext, "deleteOrder业务拒绝: ${resp.message}")
            return false
        }
        appendLog(applicationContext, "deleteOrder同步成功: orderId=${op.orderId}")
        true
    } catch (e: Exception) {
        Log.w(TAG, "deleteOrder同步失败: ${e.message}")
        appendLog(applicationContext, "deleteOrder同步异常: ${e.message}")
        false
    }
}
```

### 修改7：`syncOperation()` — +4 行

```kotlin
} catch (e: retrofit2.HttpException) {
    if (e.code() in 400..499) {
        Log.w(TAG, "客户端错误${e.code()}，标记冲突: ${op.operationType}")
        appendLog(applicationContext, "操作${op.operationType}客户端错误${e.code()}，标记冲突")
        dao.updateRetryCount(op.id, -1)
        false
    } else {
        Log.e(TAG, "服务端错误${e.code()}，将重试: ${op.operationType}")
        appendLog(applicationContext, "操作${op.operationType}服务端错误${e.code()}，将重试")
        false
    }
} catch (e: Exception) {
    Log.e(TAG, "同步操作失败: ${op.operationType}, error=${e.message}")
    appendLog(applicationContext, "操作${op.operationType}异常: ${e.message}")
    false
}
```

### 修改8：`syncImageUpload()` — +8 行

```kotlin
private suspend fun syncImageUpload(op: PendingOperationEntity): Boolean {
    val skuOuterId = extractPayloadValue(op.payload, "sku_outer_id") ?: run {
        appendLog(applicationContext, "imageUpload同步失败: payload缺少sku_outer_id")
        return false
    }
    val imageType = extractPayloadValue(op.payload, "image_type") ?: run {
        appendLog(applicationContext, "imageUpload同步失败: payload缺少image_type")
        return false
    }
    val filePath = extractPayloadValue(op.payload, "file_path") ?: run {
        appendLog(applicationContext, "imageUpload同步失败: payload缺少file_path")
        return false
    }
    val imageFile = File(filePath)
    if (!imageFile.exists()) {
        Log.w(TAG, "图片文件不存在，放弃同步: $filePath")
        appendLog(applicationContext, "imageUpload放弃同步: 图片文件不存在, path=$filePath")
        return true
    }
    val uploader = imageUploadService ?: run {
        appendLog(applicationContext, "imageUpload同步失败: imageUploadService为null")
        return false
    }
    val imageDao = productImageDao ?: run {
        appendLog(applicationContext, "imageUpload同步失败: productImageDao为null")
        return false
    }
    appendLog(applicationContext, "imageUpload开始上传: sku=$skuOuterId, type=$imageType")
    return try {
        val (remoteId, imageUrl) = uploader.uploadImage(imageFile, imageType, skuOuterId)
        imageDao.insert(com.kuaimai.pda.data.db.entity.ProductImageEntity(
            skuOuterId = skuOuterId,
            imageType = imageType,
            imageUrl = imageUrl,
            remoteId = remoteId,
            createdAt = com.kuaimai.pda.util.TimeUtils.now()
        ))
        imageFile.delete()
        appendLog(applicationContext, "imageUpload上传成功: sku=$skuOuterId, remoteId=$remoteId")
        true
    } catch (e: Exception) {
        Log.w(TAG, "上传图片失败，保留文件以重试: ${e.message}")
        appendLog(applicationContext, "imageUpload上传异常: sku=$skuOuterId, error=${e.message}")
        false
    }
}
```

### 修改9：`doWork()` — +3 行

```kotlin
// 到达结尾时追加结果日志
if (hasFailure) {
    appendLog(applicationContext, "Worker本轮结束: 有失败操作，返回retry")
    Result.retry()
} else {
    appendLog(applicationContext, "Worker本轮结束: 全部成功")
    Result.success()
}
```

### 修改10：`extractPayloadValue()` — +1 行

```kotlin
// 在返回null前加日志
if (!json.has(key)) {
    Log.w(TAG, "解析payload: key=$key 不存在")
    return null
}
```

## 允许先实施

改完以上内容后，运行 `./gradlew compileDebugKotlin` 验证编译，然后构建 APK。

## 验证

查看同步日志时，应该能看到每个操作的完整链路：
```
[time] Worker启动，共 N 个待处理操作
[time] completeItem开始同步: orderId=XX, itemId=XX
[time] completeItem同步成功: orderId=XX, itemId=XX
[time] 操作同步成功: type=complete_item, orderId=XX
[time] Worker本轮结束: 全部成功
```

失败时：
```
[time] completeItem同步失败: userRepository为null
[time] 操作complete_item客户端错误400，标记冲突
```
