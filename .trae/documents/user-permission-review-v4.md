# 用户权限管理功能全面检查报告

## 检查范围

后端：auth.py、users.py、images.py、orders.py、areas.py、system.py、database.py、models.py、main.py、config.py
前端：UserRepository.kt、NetworkModule.kt、AppNavigation.kt、SettingsScreen.kt、SettingsViewModel.kt、HomeScreen.kt、ProductScreen.kt、ProductViewModel.kt、LoginScreen.kt、ImageUploadService.kt、OrderSyncWorker.kt、AreaApiService.kt、OrderApiService.kt、UserApiService.kt

---

## 发现的问题

### P0-1: ImageUploadService 不传递 X-User-Token（图片功能完全不可用）

**文件**: `app/src/main/java/com/kuaimai/pda/data/api/ImageUploadService.kt`

**问题**: `ImageUploadService` 使用原生 OkHttp 发起请求（非Retrofit），仅通过注入的 OkHttpClient 获得了 ApiKeyInterceptor（自动添加 X-API-Key），但 **没有手动添加 X-User-Token 请求头**。后端 `images.py` 的 `upload_image` 和 `delete_image` 均要求 `Depends(get_current_user)`，即必须携带有效的 X-User-Token。

**影响**: 图片上传和删除请求必定返回 401 Unauthorized，**图片管理功能完全不可用**。

**修复方案**:
- `ImageUploadService` 注入 `UserRepository`（或直接注入 SharedPreferences 读取 token）
- `doUpload()` 和 `deleteImage()` 方法在构建 Request 时添加 `X-User-Token` header

```kotlin
// doUpload() 中：
val token = prefs.getString("user_token", "") ?: ""
val request = Request.Builder()
    .url(uploadUrl)
    .post(progressBody)
    .addHeader("X-User-Token", token)
    .build()

// deleteImage() 中：
val token = prefs.getString("user_token", "") ?: ""
val request = Request.Builder()
    .url(deleteUrl)
    .delete()
    .addHeader("X-User-Token", token)
    .build()
```

---

### P1-1: AreaApiService 缺少 createArea/deleteArea 方法

**文件**: `app/src/main/java/com/kuaimai/pda/data/api/AreaApiService.kt`

**问题**: 前端 `AreaApiService` 只定义了 `getAreas()`，缺少 `createArea()` 和 `deleteArea()` 方法。后端已有 `POST /api/areas`（需 settings 权限）和 `DELETE /api/areas/{area_id}`（需 settings 权限）接口。

**影响**: `SettingsViewModel.addArea()` 和 `deleteArea()` 只在本地操作，不调用后端 API，数据不会持久化到服务器，其他设备看不到变更。

**修复方案**:
1. 在 `AreaApiService` 添加 `createArea()` 和 `deleteArea()` 方法
2. 修改 `SettingsViewModel` 调用后端 API 而非本地操作

---

### P1-2: 后端允许用户禁用自己

**文件**: `backend/app/routers/users.py` L234-241

**问题**: `update_user` 接口只检查了"不能删除自己"（L273），但**没有检查"不能禁用自己"**。用户可以设置 `isActive=false` 禁用自己，后端会立即清理其 token（L241），导致该用户被永久锁定，无法再登录。

**影响**: 管理员可能误操作禁用自己，导致系统无法管理。

**修复方案**: 在 `update_user` 中添加检查：
```python
if user_id == user["user_id"] and req.isActive is False:
    raise HTTPException(status_code=400, detail="不能禁用当前登录用户")
```

---

### P1-3: 后端允许管理员剥夺自己的 settings 权限

**文件**: `backend/app/routers/users.py` L244-255

**问题**: `update_user` 接口没有检查用户是否在移除自己的 `settings` 权限。如果管理员移除自己的 settings 权限，将立即失去用户管理能力，且无法恢复（除非数据库直接操作）。

**影响**: 管理员可能误操作导致无法管理用户。

**修复方案**: 在 `update_user` 中添加检查：
```python
if user_id == user["user_id"] and req.permissions is not None:
    if "settings" in user["permissions"] and "settings" not in req.permissions:
        raise HTTPException(status_code=400, detail="不能移除当前登录用户的设置管理权限")
```

---

### P1-4: UserEditDialog 不支持启用/禁用用户

**文件**: `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt` L145-148

**问题**: 编辑用户弹窗只允许修改密码和权限，**没有提供切换 isActive 状态的功能**。调用 `updateUser()` 时 `isActive` 参数始终传 `null`。

**影响**: 管理员无法通过 UI 禁用/启用用户账户，只能删除用户。

**修复方案**: 在 `UserEditDialog` 中添加 Switch 控件用于切换用户启用/禁用状态，并在 `onConfirm` 回调中传递 `isActive` 参数。

---

### P1-5: UserRepository 使用普通 SharedPreferences 存储敏感数据

**文件**: `app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt` L67-69

**问题**: `UserRepositoryImpl` 注入的是普通 `SharedPreferences`（非加密），用户 token、权限等敏感信息以明文存储。设备 root 后或其他应用可通过备份读取。

**影响**: Token 泄露风险，攻击者可伪造请求。

**修复方案**: 将 `UserRepositoryImpl` 的 `prefs` 参数改为 `@Named("encrypted") encryptedPrefs: SharedPreferences`，使用 EncryptedSharedPreferences 存储敏感数据。注意：`KEY_USER_TOKEN` 等常量需要与 `ImageUploadService` 中读取 token 的 key 保持一致。

---

### P2-1: updateUser() 本地缓存更新不完整

**文件**: `app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt` L169-191

**问题**: `updateUser()` 在修改当前用户时，只更新了 `permissions` 缓存，未处理 `isActive` 变更。如果管理员禁用了当前用户，本地缓存不会反映此变化。

**修复方案**: 在 `updateUser()` 中，当修改的是当前用户时，也检查并更新 `isActive` 状态（如果 `isActive` 参数不为 null）。

---

### P2-2: 登录限流使用内存字典

**文件**: `backend/app/routers/users.py` L32-36

**问题**: `_LOGIN_FAIL_COUNTS` 和 `_LOGIN_LOCK_UNTIL` 使用内存字典存储，服务重启后限流状态丢失。此外，字典会无限增长，没有清理机制。

**影响**: 低风险，服务重启后限流重置；长期运行可能内存缓慢增长。

**修复方案**: 可选优化——添加定期清理过期条目的逻辑，或使用 Redis 等外部存储。当前规模下影响极小，可暂不处理。

---

## 修复优先级

| 编号 | 优先级 | 问题 | 影响范围 |
|------|--------|------|----------|
| P0-1 | **紧急** | ImageUploadService 不传 X-User-Token | 图片上传/删除完全不可用 |
| P1-1 | 高 | AreaApiService 缺少 createArea/deleteArea | 拣货区管理不持久化 |
| P1-2 | 高 | 后端允许用户禁用自己 | 管理员可能锁定自己 |
| P1-3 | 高 | 后端允许剥夺自己的settings权限 | 管理员可能失去管理能力 |
| P1-4 | 中 | UserEditDialog 不支持启用/禁用用户 | 无法通过UI管理用户状态 |
| P1-5 | 中 | UserRepository 敏感数据未加密存储 | Token泄露风险 |
| P2-1 | 低 | updateUser() 缓存更新不完整 | 边界场景状态不一致 |
| P2-2 | 低 | 登录限流内存字典无清理 | 长期运行内存缓慢增长 |

---

## 修复步骤

### Step 1: 修复 P0-1（ImageUploadService 添加 X-User-Token）
- 修改 `ImageUploadService.kt`：注入 SharedPreferences，在 doUpload() 和 deleteImage() 中添加 X-User-Token header
- 注意：需要使用与 UserRepository 相同的 key（`user_token`）读取 token

### Step 2: 修复 P1-1（AreaApiService 添加 createArea/deleteArea）
- 在 `AreaApiService.kt` 添加 createArea() 和 deleteArea() 方法
- 修改 `SettingsViewModel.kt` 的 addArea() 和 deleteArea() 调用后端 API

### Step 3: 修复 P1-2 + P1-3（后端用户自我保护检查）
- 修改 `backend/app/routers/users.py` 的 update_user 函数，添加自我保护检查

### Step 4: 修复 P1-4（UserEditDialog 添加启用/禁用开关）
- 修改 `SettingsScreen.kt` 的 UserEditDialog，添加 isActive Switch

### Step 5: 修复 P1-5（UserRepository 改用加密存储）
- 修改 `UserRepositoryImpl` 使用 @Named("encrypted") SharedPreferences
- 同步修改 `ImageUploadService` 读取 token 的方式（因为 key 可能不同）

### Step 6: 修复 P2-1（updateUser 缓存更新）
- 修改 `UserRepository.kt` 的 updateUser() 方法，完善本地缓存更新逻辑

### Step 7: 验证
- `./gradlew lint` 通过
- `./gradlew assembleDebug` 构建成功

### Step 8: 收尾
- 更新版本号（3处一致）
- 更新知识图谱
- 同步 docker-deploy
- Git 提交推送
