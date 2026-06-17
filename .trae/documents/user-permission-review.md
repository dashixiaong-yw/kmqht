# 用户权限功能全面检查报告

## 检查范围

16个文件逐一审查：后端6个 + App端10个

## 发现的缺陷

### P0 - 严重BUG（必须修复）

#### 1. 后端图片上传/删除接口缺少权限校验
- **文件**: `backend/app/routers/images.py`
- **问题**: `POST /images/upload` 和 `DELETE /images/{image_id}` 接口没有调用 `check_permission()`，任何通过API Key认证的用户都能上传/删除图片
- **影响**: App端隐藏了按钮，但后端接口完全暴露，可被直接调用绕过权限
- **修复**: 上传图片时根据`imageType`校验`manage_area_image`或`manage_box_image`权限；删除图片时先查`image_type`再校验对应权限

#### 2. 后端供应商修改/备注修改接口缺少权限校验
- **文件**: `backend/app/routers/orders.py`
- **问题**: 供应商关联修改和备注修改的接口（如果有）没有调用 `check_permission()`
- **影响**: 同上，后端接口暴露
- **修复**: 需要检查orders.py中是否有供应商/备注修改接口，如有则添加权限校验

#### 3. bcrypt降级为SHA256时密码校验不一致
- **文件**: `backend/app/routers/users.py` 第277-294行
- **问题**: `_hash_password()`和`_verify_password()`在bcrypt不可用时降级为SHA256。但`_init_default_admin()`（database.py第181-206行）也独立实现了同样的降级逻辑。如果admin用bcrypt创建，后来bcrypt被卸载，则SHA256校验会失败
- **影响**: 生产环境不太可能卸载bcrypt（已在requirements.txt），但逻辑上不一致
- **修复**: `_init_default_admin()`应调用`_hash_password()`而非重复实现

### P1 - 中等问题（应该修复）

#### 4. App端token过期后无自动跳转登录页
- **文件**: `app/.../data/repository/UserRepository.kt`
- **问题**: `validateToken()`在token失效时清除本地数据，但AppNavigation只在启动时调用一次。用户使用过程中token过期（7天），后续API调用会返回401，但不会自动跳转到登录页
- **影响**: 用户7天后操作会静默失败，不知道需要重新登录
- **修复**: 在UserApiService的所有请求中检测401响应，触发自动跳转登录页

#### 5. 后端登录接口缺少暴力破解防护
- **文件**: `backend/app/routers/users.py` 第30-85行
- **问题**: 登录接口没有限流，可被无限次尝试密码
- **影响**: admin账户密码可被暴力破解
- **修复**: 添加简单限流（如同一用户名5次失败后锁定5分钟）

#### 6. UserResponse.id默认值为0，与后端不匹配
- **文件**: `app/.../data/api/dto/UserDto.kt` 第38行
- **问题**: `UserResponse.id`默认值为`0`（Long），但后端返回的`id`是自增整数（从1开始）。`restoreFromCache()`中恢复的用户没有`id`，导致`currentUser.value?.id`为0，SettingsScreen中`currentUserId`为0，用户可能误删id=0的用户（虽然不存在）
- **影响**: 低风险，但逻辑不严谨
- **修复**: `restoreFromCache()`时也缓存`id`，或在`validateToken()`成功后更新包含id的完整UserResponse

#### 7. 后端禁用用户后不清理其活跃token
- **文件**: `backend/app/routers/users.py` 第205-210行
- **问题**: `update_user`设置`isActive=False`时，没有删除该用户的活跃token。被禁用的用户如果持有未过期的token，仍可继续使用（`get_current_user`会检查`is_active`并拒绝，但token仍存在于数据库中）
- **影响**: token残留占用空间，且如果后续代码修改忘记检查is_active，会留下安全漏洞
- **修复**: 禁用用户时同时删除其所有token

### P2 - 轻微问题（建议修复）

#### 8. SKIP_USER_TOKEN_PREFIXES定义但未使用
- **文件**: `backend/app/auth.py` 第19行
- **问题**: `SKIP_USER_TOKEN_PREFIXES = ("/api/users/login",)` 定义了但从未在任何地方使用
- **影响**: 死代码
- **修复**: 删除此常量

#### 9. SettingsScreen缺少服务器地址和API密钥配置
- **文件**: `app/.../ui/settings/SettingsScreen.kt`
- **问题**: 计划中SettingsScreen应包含"服务器地址配置"和"API密钥配置"，但当前实现只有用户管理和退出登录。HomeScreen中首次引导提示"点击设置配置服务器地址"，但设置页中没有这些配置项
- **影响**: 用户无法在App内修改服务器地址和API密钥
- **修复**: 在SettingsScreen中添加服务器地址和API密钥配置区域

#### 10. HomeScreen首次引导提示指向设置但无settings权限用户看不到设置入口
- **文件**: `app/.../ui/home/HomeScreen.kt` 第109-141行
- **问题**: 首次使用引导提示条显示"点击设置配置服务器地址"，但如果没有`settings`权限，设置卡片被隐藏，提示条仍然显示但点击无效
- **影响**: 用户体验混乱
- **修复**: 引导提示条仅在`hasSettingsPermission`为true时显示

#### 11. 登录页没有服务器连接失败的友好提示
- **文件**: `app/.../ui/login/LoginScreen.kt`
- **问题**: 网络不通时登录会抛出异常，错误信息是原始异常消息（如"Unable to resolve host"），对仓库用户不友好
- **影响**: 用户体验差
- **修复**: 捕获网络异常时显示"无法连接服务器，请检查网络设置"

#### 12. 后端用户路由路径冲突
- **文件**: `backend/app/routers/users.py`
- **问题**: `POST /api/users`（创建用户）和`POST /api/users/login`（登录）在路由注册时，FastAPI按注册顺序匹配。如果`POST /api/users`先注册，`POST /api/users/login`可能不会被匹配到（因为login被当作路径的一部分）
- **影响**: 实际上FastAPI会先匹配更具体的路径`/login`，所以不会出问题。但代码顺序上login在前面，这是正确的
- **修复**: 无需修复，确认当前顺序正确

## 修复计划

### 必须修复（P0）

| # | 文件 | 修改内容 |
|---|------|---------|
| 1 | `backend/app/routers/images.py` | 上传图片添加权限校验（area→manage_area_image, box→manage_box_image），删除图片添加权限校验 |
| 2 | `backend/app/routers/orders.py` | 检查并添加供应商/备注修改接口的权限校验 |
| 3 | `backend/app/database.py` | `_init_default_admin`调用`_hash_password`而非重复实现 |

### 应该修复（P1）

| # | 文件 | 修改内容 |
|---|------|---------|
| 4 | `app/.../data/repository/UserRepository.kt` | 添加token过期自动跳转机制（通过StateFlow通知UI） |
| 5 | `backend/app/routers/users.py` | 登录接口添加简单限流（5次失败锁定5分钟） |
| 6 | `app/.../data/repository/UserRepository.kt` | `restoreFromCache`时缓存用户id |
| 7 | `backend/app/routers/users.py` | 禁用用户时清理其token |

### 建议修复（P2）

| # | 文件 | 修改内容 |
|---|------|---------|
| 8 | `backend/app/auth.py` | 删除未使用的SKIP_USER_TOKEN_PREFIXES |
| 9 | `app/.../ui/settings/SettingsScreen.kt` | 添加服务器地址和API密钥配置 |
| 10 | `app/.../ui/home/HomeScreen.kt` | 引导提示条仅在settings权限时显示 |
| 11 | `app/.../ui/login/LoginScreen.kt` | 网络异常友好提示 |

## 验证步骤

1. 后端图片上传接口：无权限用户调用返回403
2. 后端登录限流：同一用户名连续5次错误后返回429
3. 禁用用户后其token立即失效
4. App端token过期后自动跳转登录页
5. SettingsScreen可配置服务器地址和API密钥
6. `./gradlew assembleDebug` 构建成功
