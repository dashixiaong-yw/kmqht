# 用户权限管理功能全面检查报告（v1.3后）

## 检查范围
后端：auth.py、users.py、images.py、database.py、models.py、orders.py、areas.py、system.py
App端：UserRepository.kt、UserApiService.kt、UserDto.kt、LoginScreen.kt、AppNavigation.kt、SettingsScreen.kt、HomeScreen.kt、ProductScreen.kt

---

## v1.3修复验证

v1.3修复的14个问题已全部确认生效：
- ✅ images.py已添加`import sqlite3`
- ✅ KEY_USER_ID常量已定义
- ✅ handleAuthError已改用appScope.launch
- ✅ AppNavigation已监听loginRequired事件
- ✅ restoreFromCache已恢复用户id
- ✅ 禁用用户时已清理token
- ✅ 登录接口已添加5次失败锁定5分钟限流
- ✅ updateUser修改自己权限后已刷新本地缓存
- ✅ SKIP_USER_TOKEN_PREFIXES死代码已删除
- ✅ HomeScreen引导提示仅settings权限显示
- ✅ LoginScreen网络异常友好提示已添加
- ✅ ProductScreen图片只读显示已实现
- ✅ timedelta导入已移到文件头部
- ✅ clearLocalUser已清除KEY_USER_ID

---

## 新发现的问题（共5个）

### P0 - 严重安全缺陷（1个）

#### P0-1: orders.py和areas.py所有接口缺少用户登录认证
- **文件**: `backend/app/routers/orders.py`、`backend/app/routers/areas.py`
- **问题**: 这两个路由文件的所有接口都没有使用`get_current_user`依赖注入，任何知道API地址的人都可以直接调用创建/删除取货单、创建/删除拣货区等操作，无需登录
- **影响**: 严重安全漏洞，未授权用户可操作所有取货单和拣货区数据
- **设计意图**: 知识图谱确认"基础操作（扫码、查看、完成/恢复）无需权限"，但"无需权限"指无需特定权限码，**仍需登录认证**
- **修复方案**:
  - orders.py: 所有写操作（create_order、add_item、complete_item、restore_item、complete_all_items、delete_order、delete_item）添加`user: dict = Depends(get_current_user)`；读操作（list_orders、get_order、get_suppliers）也添加登录认证
  - areas.py: 所有接口添加`user: dict = Depends(get_current_user)`；写操作（create_area、delete_area）可考虑添加settings权限检查
  - App端: OrderApiService和AreaApiService需要添加X-User-Token请求头

### P1 - 中等问题（2个）

#### P1-1: login方法未缓存用户id到SharedPreferences
- **文件**: `app/.../data/repository/UserRepository.kt` 第98-123行
- **问题**: `login()`方法成功后保存了token、username、permissions到SharedPreferences，但**没有保存user id**。虽然`validateToken()`会保存id，但如果用户登录后从未调用validateToken（冷启动才调用），id就不会被缓存
- **影响**: 登录后首次进入SettingsScreen时，`currentUserId`为0，删除按钮判断失效
- **修复**: 在login方法中，登录成功后也调用`putLong(KEY_USER_ID, ...)`。但LoginResponse不返回id，需要两种方案：
  - 方案A：后端LoginResponse添加userId字段（推荐）
  - 方案B：登录成功后立即调用getCurrentUser获取id

#### P1-2: areas.py写操作无权限控制
- **文件**: `backend/app/routers/areas.py`
- **问题**: 拣货区的创建和删除接口没有权限检查。虽然P0-1已要求添加登录认证，但创建/删除拣货区属于管理操作，应限制为settings权限
- **影响**: 任何登录用户都能创建/删除拣货区
- **修复**: create_area和delete_area添加`user: dict = Depends(check_permission("settings"))`

### P2 - 轻微问题（2个）

#### P2-1: system.py的crash-report接口无认证
- **文件**: `backend/app/routers/system.py` 第44行
- **问题**: `/api/crash-report`接口没有认证，任何人都可以提交崩溃报告，可能被恶意刷接口
- **影响**: 低风险，但可能被滥用
- **修复**: 添加`user: dict = Depends(get_current_user)`（仅登录用户可提交）

#### P2-2: bcrypt降级SHA256的密码不安全
- **文件**: `backend/app/routers/users.py` 第307-324行
- **问题**: `_hash_password`和`_verify_password`在bcrypt不可用时降级为SHA256。SHA256无盐值，相同密码产生相同哈希，容易被彩虹表破解
- **影响**: 仅在bcrypt未安装时触发，requirements.txt已包含bcrypt==4.2.0，实际不会触发
- **修复**: 移除SHA256降级逻辑，bcrypt不可用时直接报错（因为requirements.txt已声明依赖）

---

## 修复计划

### 第一步：修复P0-1（orders.py和areas.py添加用户认证）
1. orders.py所有接口添加`user: dict = Depends(get_current_user)`
2. areas.py所有接口添加`user: dict = Depends(get_current_user)`
3. App端OrderApiService添加X-User-Token请求头
4. App端AreaApiService添加X-User-Token请求头
5. App端PickOrderRepository和AreaRepository调用时传递token

### 第二步：修复P1问题
6. 后端LoginResponse添加userId字段 + App端login方法缓存id
7. areas.py的create_area和delete_area添加settings权限检查

### 第三步：修复P2问题
8. system.py的crash-report添加登录认证
9. 移除bcrypt降级SHA256逻辑

### 第四步：验证
10. `./gradlew lint` 通过
11. `./gradlew assembleDebug` 构建成功
12. 更新版本号（3处一致）
13. 更新知识图谱
14. 同步docker-deploy
15. Git提交推送
