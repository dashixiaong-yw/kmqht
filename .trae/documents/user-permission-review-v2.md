# 用户权限管理功能全面检查报告

## 检查范围
后端：auth.py、users.py、images.py、database.py、models.py、orders.py、areas.py
App端：UserRepository.kt、UserApiService.kt、UserDto.kt、LoginScreen.kt、AppNavigation.kt、SettingsScreen.kt、HomeScreen.kt、ProductScreen.kt、NetworkModule.kt、RepositoryModule.kt、MainActivity.kt

---

## 发现的问题（共14个）

### P0 - 严重BUG（3个）

#### P0-1: images.py缺少sqlite3导入
- **文件**: `backend/app/routers/images.py` 第168行
- **问题**: `_row_to_image_response(row: sqlite3.Row)` 使用了`sqlite3.Row`类型注解，但文件头部未`import sqlite3`
- **影响**: 运行时`NameError: name 'sqlite3' is not defined`，导致图片查询接口崩溃
- **修复**: 添加`import sqlite3`

#### P0-2: UserRepository.kt引用未定义的KEY_USER_ID常量
- **文件**: `app/.../data/repository/UserRepository.kt` 第191行
- **问题**: `validateToken()`中使用了`putLong(KEY_USER_ID, user.id)`，但`companion object`中只定义了`KEY_USER_TOKEN`、`KEY_USER_NAME`、`KEY_USER_PERMISSIONS`，没有`KEY_USER_ID`
- **影响**: 编译错误，APK无法构建
- **修复**: 在companion object中添加`private const val KEY_USER_ID = "user_id"`

#### P0-3: UserRepository.kt的handleAuthError使用GlobalScope.launch
- **文件**: `app/.../data/repository/UserRepository.kt` 第213行
- **问题**: `kotlinx.coroutines.GlobalScope.launch`在非suspend函数中使用，存在以下问题：
  1. GlobalScope是结构化并发反模式，生命周期不受控
  2. launch中的emit可能因SharedFlow无收集者而丢失
  3. 更好的方式：使用`CoroutineScope(Dispatchers.Main)`注入
- **影响**: token过期时事件可能丢失，UI不会跳转登录页
- **修复**: 注入`CoroutineScope`，替换GlobalScope

### P1 - 中等问题（6个）

#### P1-1: AppNavigation未监听loginRequired事件
- **文件**: `app/.../ui/navigation/AppNavigation.kt`
- **问题**: UserRepository已定义`loginRequired: SharedFlow<Unit>`事件流，但AppNavigation中没有任何代码监听此事件。当token过期时，handleAuthError发出事件但无人接收，用户不会被自动跳转到登录页
- **影响**: token过期后用户停留在当前页面，操作会反复失败但不会引导重新登录
- **修复**: 在AppNavigation中添加LaunchedEffect监听`userRepository.loginRequired`，收到事件时导航到LOGIN路由

#### P1-2: restoreFromCache未恢复用户id
- **文件**: `app/.../data/repository/UserRepository.kt` 第220-231行
- **问题**: `restoreFromCache()`从SharedPreferences恢复username和permissions，但没有恢复user id。`validateToken()`中已写入`putLong(KEY_USER_ID, user.id)`，但restoreFromCache没有读取
- **影响**: 冷启动时currentUser.id为默认值0，SettingsScreen中`currentUserId`为0，导致删除按钮判断失效（所有用户都显示删除按钮，包括自己）
- **修复**: 在restoreFromCache中读取KEY_USER_ID并设置到UserResponse.id

#### P1-3: 禁用用户时未清理其token
- **文件**: `backend/app/routers/users.py` 第206-210行
- **问题**: `update_user()`中当`isActive=False`时，只更新了is_active字段，没有清理该用户的token。被禁用的用户如果持有有效token，仍可继续访问API直到token过期
- **影响**: 禁用用户后，该用户在7天内仍可正常使用系统
- **修复**: 在设置isActive=False时，同时`DELETE FROM user_tokens WHERE user_id = ?`

#### P1-4: 登录接口无限流保护
- **文件**: `backend/app/routers/users.py` login函数
- **问题**: 登录接口没有暴力破解防护，攻击者可以无限次尝试密码
- **影响**: 安全风险，密码可被暴力破解
- **修复**: 添加内存级限流（5次失败锁定5分钟），使用字典记录失败次数

#### P1-5: UserDto.kt的UserResponse.id默认值为0
- **文件**: `app/.../data/api/dto/UserDto.kt` 第39行
- **问题**: `val id: Long = 0`默认值为0，当从login响应构造UserResponse时没有设置id（LoginResponse不返回id），导致currentUser.id始终为0
- **影响**: 与P1-2相同，SettingsScreen中删除按钮判断失效
- **修复**: 配合P1-2一起修复，在restoreFromCache中恢复id

#### P1-6: 修改自己权限后本地缓存不更新
- **文件**: `app/.../data/repository/UserRepository.kt`
- **问题**: 管理员在SettingsScreen修改自己的权限后，后端已更新但App端currentUser仍是旧权限（因为updateUser成功后没有刷新本地缓存的权限列表）
- **影响**: 修改自己权限后需要重新登录才能生效
- **修复**: 在updateUser成功后，如果修改的是当前用户，刷新本地缓存（调用getMe接口或直接更新currentUser）

### P2 - 轻微问题（5个）

#### P2-1: auth.py中SKIP_USER_TOKEN_PREFIXES死代码
- **文件**: `backend/app/auth.py` 第19行
- **问题**: `SKIP_USER_TOKEN_PREFIXES = ("/api/users/login",)`已定义但从未被任何代码引用。login接口不使用`get_current_user`依赖，所以不需要这个变量
- **影响**: 代码冗余，可能误导维护者
- **修复**: 删除该常量

#### P2-2: HomeScreen引导提示条对所有用户显示
- **文件**: `app/.../ui/home/HomeScreen.kt` 第139行
- **问题**: 首次使用引导提示条"首次使用？点击设置配置服务器地址和扫码方式"对所有用户显示，但只有settings权限用户才能进入设置页。无settings权限的用户点击后进入设置页但无法操作
- **影响**: 无settings权限用户看到引导提示但点击后无意义
- **修复**: 仅在hasSettingsPermission为true时显示引导提示条

#### P2-3: LoginScreen网络异常提示不友好
- **文件**: `app/.../ui/login/LoginScreen.kt` 第166行
- **问题**: 登录失败时直接显示`result.exceptionOrNull()?.message ?: "登录失败"`，网络异常时显示的是`java.net.ConnectException: Failed to connect to ...`等技术性错误
- **影响**: 用户体验差，普通用户无法理解错误含义
- **修复**: 对常见网络异常（ConnectException、SocketTimeoutException、UnknownHostException）提供中文友好提示

#### P2-4: ProductScreen图片区域无权限时完全隐藏
- **文件**: `app/.../ui/product/ProductScreen.kt` 第404-420行
- **问题**: 当用户没有manage_area_image或manage_box_image权限时，对应图片上传槽位完全隐藏（`if (canManageAreaImage)`）。但用户可能仍需查看图片（只读模式）
- **影响**: 无图片管理权限的用户完全看不到商品图片
- **修复**: 无权限时仍显示图片（只读），仅隐藏上传/删除操作按钮

#### P2-5: 后端login函数中from datetime import timedelta在函数内部
- **文件**: `backend/app/routers/users.py` 第59行
- **问题**: `from datetime import timedelta`在login函数内部导入，每次调用都执行import语句（虽然Python会缓存，但不符合规范）
- **影响**: 代码风格问题
- **修复**: 将`from datetime import timedelta`移到文件头部

---

## 修复计划

### 第一步：修复P0问题（编译/运行时错误）
1. images.py添加`import sqlite3`
2. UserRepository.kt companion object添加`KEY_USER_ID`
3. UserRepository.kt handleAuthError改用注入的CoroutineScope

### 第二步：修复P1问题（功能缺陷）
4. AppNavigation.kt添加LaunchedEffect监听loginRequired事件
5. UserRepository.kt restoreFromCache恢复user id
6. users.py update_user禁用用户时清理token
7. users.py login添加限流保护
8. UserRepository.kt updateUser修改自己权限后刷新本地缓存

### 第三步：修复P2问题（体验优化）
9. auth.py删除SKIP_USER_TOKEN_PREFIXES死代码
10. HomeScreen引导提示仅settings权限显示
11. LoginScreen网络异常友好提示
12. ProductScreen无权限时图片只读显示
13. users.py timedelta导入移到文件头部

### 第四步：验证
14. `./gradlew lint` 通过
15. `./gradlew assembleDebug` 构建成功
16. 更新版本号（3处一致）
17. 更新知识图谱
18. 同步docker-deploy
19. Git提交推送
