# 用户权限功能实施计划

## 概述

在快麦取货通App中增加用户登录和权限控制功能。仅用于权限控制，不涉及业务数据隔离。后端SQLite存储用户和权限数据，App端实现登录页和权限校验。

## 当前状态分析

- **App端**：无任何用户/登录模块。首页3个卡片入口（取货列表、商品详情、设置），设置页为空壳（"设置 - 待实现"）
- **后端**：仅有API Key中间件（`ApiKeyMiddleware`），无用户体系。SQLite数据库6张表，无用户/权限相关表
- **认证**：App端通过`AuthRepository`管理快麦API凭证（appKey/appSecret/session），存储在EncryptedSharedPreferences

## 权限控制范围（5个功能点）

| 权限代码 | 功能 | 当前位置 |
|---------|------|---------|
| `settings` | 设置页 | HomeScreen → SettingsScreen |
| `update_supplier` | 修改供应商关联 | 取货详情页F18 |
| `update_remark` | 修改规格备注 | 取货详情页F4 |
| `manage_area_image` | 上传删除库区图 | 商品详情页 |
| `manage_box_image` | 上传删除箱规图 | 商品详情页 |

## 实施方案

### 一、后端变更（4个文件新增/修改）

#### 1. 新增 `backend/app/routers/users.py` — 用户管理API

**接口设计**：

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/users/login` | 用户登录，返回token+权限列表 | 无需认证 |
| GET | `/api/users/me` | 获取当前用户信息+权限 | 需登录 |
| GET | `/api/users` | 用户列表 | 需`settings`权限 |
| POST | `/api/users` | 创建用户 | 需`settings`权限 |
| PUT | `/api/users/{id}` | 更新用户（密码/权限/启用状态） | 需`settings`权限 |
| DELETE | `/api/users/{id}` | 删除用户 | 需`settings`权限 |

**登录流程**：
1. App发送 `{ username, password }` 到 `/api/users/login`
2. 后端校验用户名密码（密码用bcrypt哈希）
3. 成功返回 `{ token, username, permissions: [...] }`
4. token为随机UUID，存入`user_tokens`表，有效期7天
5. App后续请求携带 `X-User-Token` 头

**权限校验中间件**：
- 修改现有`ApiKeyMiddleware`，增加`X-User-Token`校验
- 需要权限的接口通过`Depends(check_permission("settings"))`装饰
- 扫码、查看等基础操作无需权限

#### 2. 修改 `backend/app/database.py` — 新增3张表

```sql
-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(32) UNIQUE NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL
);

-- 用户权限表（一个用户多个权限）
CREATE TABLE IF NOT EXISTS user_permissions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    permission VARCHAR(32) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, permission)
);

-- 用户Token表
CREATE TABLE IF NOT EXISTS user_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    token VARCHAR(64) UNIQUE NOT NULL,
    expires_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

**初始化**：创建默认管理员用户 `admin/admin123`，拥有全部5个权限

#### 3. 修改 `backend/app/models.py` — 新增Pydantic模型

```python
class LoginRequest(BaseModel):
    username: str
    password: str

class LoginResponse(BaseModel):
    success: bool = True
    token: str
    username: str
    permissions: List[str]

class CreateUserRequest(BaseModel):
    username: str
    password: str
    permissions: List[str]  # 权限代码列表

class UpdateUserRequest(BaseModel):
    password: Optional[str] = None
    permissions: Optional[List[str]] = None
    isActive: Optional[bool] = None

class UserResponse(BaseModel):
    id: int
    username: str
    isActive: bool
    permissions: List[str]
    createdAt: str

class UserListResponse(BaseModel):
    success: bool = True
    data: List[UserResponse]
```

#### 4. 修改 `backend/app/auth.py` — 增加用户Token校验

- 现有`ApiKeyMiddleware`保持不变（用于App→后端整体认证）
- 新增`get_current_user()`依赖函数，从`X-User-Token`头解析用户
- 新增`check_permission(perm: str)`依赖函数，校验用户是否拥有指定权限
- 需要权限控制的接口使用`Depends(check_permission("xxx"))`

#### 5. 修改 `backend/main.py` — 注册用户路由

```python
from app.routers import users
app.include_router(users.router)
```

### 二、App端变更（6个文件新增/修改）

#### 1. 新增 `app/src/main/java/com/kuaimai/pda/data/api/UserApiService.kt`

```kotlin
interface UserApiService {
    @POST("api/users/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/users/me")
    suspend fun getCurrentUser(@Header("X-User-Token") token: String): UserResponse

    @GET("api/users")
    suspend fun getUsers(@Header("X-User-Token") token: String): UserListResponse

    @POST("api/users")
    suspend fun createUser(@Header("X-User-Token") token: String, @Body request: CreateUserRequest): BaseResponse

    @PUT("api/users/{id}")
    suspend fun updateUser(@Header("X-User-Token") token: String, @Path("id") id: Long, @Body request: UpdateUserRequest): BaseResponse

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Header("X-User-Token") token: String, @Path("id") id: Long): BaseResponse
}
```

#### 2. 新增 `app/src/main/java/com/kuaimai/pda/data/repository/UserRepository.kt`

- 管理登录状态（token、用户名、权限列表）
- 使用EncryptedSharedPreferences存储token
- 提供 `hasPermission(perm: String): Boolean` 方法
- 提供 `isLoggedIn(): Boolean`、`login(username, password)`、`logout()` 方法
- 启动时自动检查token有效性（调用`/api/users/me`）

#### 3. 新增 `app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt`

- 简洁登录页：用户名输入框 + 密码输入框 + 登录按钮
- 登录成功后跳转首页
- 登录失败显示错误提示
- App启动时检查token有效性，无效则显示登录页

#### 4. 修改 `app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt`

- 新增 `LOGIN` 路由
- 修改启动逻辑：先判断登录状态，未登录→登录页，已登录→首页
- 登录成功后导航到首页

#### 5. 修改 `app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt`

- 设置卡片：无`settings`权限时隐藏或置灰
- 权限不足的功能入口显示锁定图标+提示

#### 6. 修改 `app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt`

- 实现设置页完整UI：
  - 服务器地址配置
  - API密钥配置
  - 用户管理（仅`settings`权限用户可见）
    - 用户列表
    - 添加用户
    - 编辑用户权限
    - 删除用户
  - 退出登录按钮
  - 版本信息

#### 7. 修改权限相关页面（取货详情页、商品详情页）

- 修改供应商关联按钮：无`update_supplier`权限时隐藏或提示"无权限"
- 修改规格备注按钮：无`update_remark`权限时隐藏或提示
- 上传库区图按钮：无`manage_area_image`权限时隐藏或提示
- 上传箱规图按钮：无`manage_box_image`权限时隐藏或提示

### 三、DI依赖注入变更

#### 修改 `app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt`

- 新增`UserApiService`的Retrofit实例
- OkHttp添加`X-User-Token`拦截器（自动附加token）

#### 修改 `app/src/main/java/com/kuaimai/pda/di/RepositoryModule.kt`

- 绑定`UserRepository` → `UserRepositoryImpl`

## 假设与决策

| 决策 | 理由 |
|------|------|
| 密码用bcrypt哈希 | 行业标准，安全性足够 |
| Token为UUID，7天有效 | PDA场景登录频率低，7天平衡安全与便捷 |
| 默认管理员admin/admin123 | 首次部署后管理员可立即登录并创建其他用户 |
| 无权限功能隐藏而非置灰 | 减少用户困惑，PDA屏幕空间有限 |
| 基础操作（扫码、查看）无需权限 | 仓库核心操作不应被权限阻碍 |
| 用户管理放在设置页内 | 用户量少，无需独立管理页面 |

## 验证步骤

1. 后端启动后自动创建admin用户和3张新表
2. 使用admin/admin123登录成功，返回全部5个权限
3. 创建新用户test，仅分配`update_remark`权限
4. test用户登录后，设置页不可见，供应商修改不可见，备注可编辑
5. token过期后自动跳转登录页
6. 退出登录后清除本地token
7. `./gradlew lint` 通过
8. `./gradlew assembleDebug` 构建成功
