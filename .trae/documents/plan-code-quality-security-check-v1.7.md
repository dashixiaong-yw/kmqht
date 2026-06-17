# v1.7 全面代码质量与安全检查计划

## 概述

对快麦取货通系统进行全面代码质量与安全检查，涵盖：Bug/缺陷、逻辑问题、UTC/北京时间混用、硬编码、前后端一致性、重复代码/死代码/死模块、调用正确性、安全风险。

## 当前状态分析

### 已确认无问题的维度
- **UTC/北京时间混用**：前端所有时间戳使用`TimeUtils.now()`（底层`System.currentTimeMillis()`，epoch毫秒无时区概念），显示时通过`formatTimestamp`转为北京时间；后端统一使用`beijing_now()`。`System.currentTimeMillis()`用于间隔计算（RateLimitInterceptor、ANR检测、扫码计时）属于正确用法。后端`time.time()`用于登录限流内部计时，内部一致。**无UTC/北京时间混用问题**。
- **前端UI色值**：所有颜色引用`Color.kt`常量，无直接写色值。
- **前后端API路径一致性**：所有API端点URL路径、HTTP方法、参数名称前后端完全一致。
- **前后端DTO字段名一致性**：前端DTO（OrderDto/UserDto/AreaDto等）字段名与后端Pydantic模型字段名完全匹配（camelCase）。

### 发现的问题清单

#### 安全风险（6项）

| 编号 | 级别 | 问题 | 文件 | 说明 |
|------|------|------|------|------|
| SEC-01 | P0 | CORS允许所有来源 | `backend/main.py` L44 | `allow_origins=["*"]`，生产环境应限制为前端域名 |
| SEC-02 | P0 | 图片查询接口无认证 | `backend/app/routers/images.py` L117-129 | `get_images`端点无`Depends(get_current_user)`，且`/images`路径在`SKIP_AUTH_PREFIXES`中，任何人可查询图片 |
| SEC-03 | P1 | API Key比较使用!= | `backend/app/auth.py` L40 | `if api_key != API_KEY`存在时序攻击风险，应使用`hmac.compare_digest` |
| SEC-04 | P1 | 默认管理员密码硬编码 | `backend/app/database.py` L186 | 默认密码"admin123"，应首次登录强制修改 |
| SEC-05 | P2 | 错误响应泄露内部信息 | 多个后端路由 | 如`detail=f"创建取货单失败: {e}"`，异常详情暴露给客户端 |
| SEC-06 | P2 | 图片上传无速率限制 | `backend/app/routers/images.py` | 仅登录接口有限流，图片上传无限制 |

#### Bug/逻辑缺陷（4项）

| 编号 | 级别 | 问题 | 文件 | 说明 |
|------|------|------|------|------|
| BUG-01 | P0 | session过期时间从未写入 | `AuthRepository.kt` L45 | `KEY_SESSION_EXPIRE`定义了但从未写入，`getSessionExpireTime()`永远返回0L，HomeScreen会话过期预警功能失效 |
| BUG-02 | P1 | sendCrashReport是空壳方法 | `App.kt` L108-118 | 方法只记录URL日志，从未实际发送崩溃报告，且从未被调用 |
| BUG-03 | P2 | 会话预警天数前后端硬编码不一致 | `backend/app/config.py` L60 / `AppConstants.kt` | 后端`days_left <= 5`硬编码，前端`SESSION_WARNING_DAYS = 5`，两处独立维护 |
| BUG-04 | P2 | 后端5个快麦API方法从未被调用 | `backend/app/services/kuaimai_api.py` | `get_supplier_list`、`get_trade_list`、`get_trade_detail`、`get_delivery_templates`、`search_items`定义但从未使用 |

#### 代码质量（3项）

| 编号 | 级别 | 问题 | 文件 | 说明 |
|------|------|------|------|------|
| CODE-01 | P2 | KEY_USER_TOKEN重复定义 | `ImageUploadService.kt` L36 / `UserRepository.kt` L78 | 两处独立定义相同常量 |
| CODE-02 | P2 | KEY_API_KEY重复定义 | `ApiKeyInterceptor`(NetworkModule.kt) L207 / `AuthRepository.kt` L41 | 两处独立定义相同常量 |
| CODE-03 | P2 | KEY_SERVER_URL重复定义 | `NetworkModule.kt` L41 / `ImageUploadService.kt` L33 | 两处独立定义相同常量 |

## 修复方案

### SEC-01: CORS限制来源（P0）

**文件**: `backend/main.py`

**修改**: 将`allow_origins`改为从环境变量读取，默认仍为`["*"]`（开发兼容），生产环境通过`.env`配置

```python
# config.py 新增
CORS_ORIGINS: str = os.getenv("CORS_ORIGINS", "*")  # 逗号分隔，如 "http://10.0.2.2:8000,http://localhost:8000"

# main.py 修改
origins = [o.strip() for o in CORS_ORIGINS.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    ...
)
```

### SEC-02: 图片查询接口添加认证（P0）

**文件**: `backend/app/routers/images.py` L117-129

**修改**: 为`get_images`端点添加`Depends(get_current_user)`

```python
@router.get("/images/{sku_outer_id}", response_model=ImageListResponse)
def get_images(sku_outer_id: str, user: dict = Depends(get_current_user)) -> ImageListResponse:
```

同时从`SKIP_AUTH_PREFIXES`中移除`/images`（因为静态文件挂载在`/images`路径，需要区分API路由和静态文件）。

**注意**: `/images`路径在SKIP_AUTH_PREFIXES中是因为静态文件（图片文件本身）需要公开访问。但API路由`/api/images/{sku_outer_id}`不应跳过认证。需要将SKIP_AUTH_PREFIXES中的`/images`改为更精确的匹配，或者将静态文件挂载路径与API路径区分开。

**方案**: 将`SKIP_AUTH_PREFIXES`中的`"/images"`改为`"/images/"`（带尾部斜杠，匹配静态文件目录），因为API路由是`/api/images/...`不会匹配`/images/`前缀。

### SEC-03: API Key常量时间比较（P1）

**文件**: `backend/app/auth.py` L40

**修改**: 使用`hmac.compare_digest`替代`!=`

```python
import hmac

if not hmac.compare_digest(api_key, API_KEY):
```

### SEC-04: 首次登录强制修改密码（P1）

**文件**: `backend/app/routers/users.py` + `backend/app/database.py`

**方案**:
1. 在`users`表中添加`must_change_password`字段（INTEGER DEFAULT 0）
2. 默认管理员创建时设置`must_change_password = 1`
3. 登录响应中返回`mustChangePassword`标志
4. 前端登录后检测此标志，弹出修改密码对话框

**简化方案**（避免数据库Migration）: 在登录响应中检查用户名是否为"admin"且密码是否仍为默认值，如果是则返回`mustChangePassword=true`。

```python
# users.py login方法中，登录成功后添加检查
must_change = False
if row["username"] == "admin" and _verify_password("admin123", stored_hash):
    must_change = True

return LoginResponse(
    token=token,
    userId=user_id,
    username=req.username,
    permissions=permissions,
    mustChangePassword=must_change  # 新增字段
)
```

### SEC-05: 错误响应不泄露内部信息（P2）

**文件**: 多个后端路由文件

**修改**: 将异常详情替换为通用错误消息，仅记录日志

```python
# 修改前
raise HTTPException(status_code=500, detail=f"创建取货单失败: {e}")
# 修改后
logger.error(f"创建取货单失败: {e}")
raise HTTPException(status_code=500, detail="创建取货单失败，请稍后重试")
```

涉及文件: `orders.py`、`images.py`、`areas.py`、`users.py`、`system.py`

### SEC-06: 图片上传速率限制（P2）

**文件**: `backend/app/routers/images.py`

**方案**: 添加简单的上传频率限制（每用户每分钟最多10次上传），使用内存字典记录

### BUG-01: 修复session过期时间写入（P0）

**文件**: `UserRepository.kt` + `AuthRepository.kt`

**问题**: `AuthRepository.KEY_SESSION_EXPIRE`从未被写入，导致HomeScreen的会话过期预警功能完全失效。

**修改**:
1. 在`UserRepositoryImpl.login()`成功后，计算token过期时间并写入EncryptedSharedPreferences
2. 使用与`AuthRepository.KEY_SESSION_EXPIRE`相同的key名

```kotlin
// UserRepositoryImpl.login() 中，保存token后添加：
// 计算token过期时间（7天后）
val expireTime = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
prefs.edit()
    .putString(KEY_USER_TOKEN, response.token)
    .putLong(KEY_USER_ID, response.userId)
    .putString(KEY_USER_NAME, response.username)
    .putStringSet(KEY_USER_PERMISSIONS, response.permissions.toSet())
    .putLong("session_expire_time", expireTime)  // 新增
    .apply()
```

**注意**: `KEY_SESSION_EXPIRE`在AuthRepository中定义为`"session_expire_time"`，UserRepository需要使用相同的key。由于两个Repository都使用`@Named("encrypted")`的SharedPreferences，写入后会自动被AuthRepository读取。

### BUG-02: 移除sendCrashReport空壳方法（P1）

**文件**: `App.kt`

**修改**: 删除`sendCrashReport`方法，因为从未被调用且实现为空壳

### BUG-03: 统一会话预警天数常量（P2）

**文件**: `backend/app/config.py`

**修改**: 将硬编码的`5`改为从环境变量读取，默认值与前端`SESSION_WARNING_DAYS`一致

```python
# config.py 新增
SESSION_WARNING_DAYS: int = int(os.getenv("SESSION_WARNING_DAYS", "5"))

# check_session_expiry中使用常量
if days_left <= SESSION_WARNING_DAYS and days_left > 0:
```

### BUG-04: 移除未使用的快麦API方法（P2）

**文件**: `backend/app/services/kuaimai_api.py`

**修改**: 删除5个从未被调用的方法：`get_supplier_list`、`get_trade_list`、`get_trade_detail`、`get_delivery_templates`、`search_items`

### CODE-01/02/03: 消除重复常量定义（P2）

**方案**: 在前端创建`AppKeys`常量类，统一管理SharedPreferences的key名称

**文件**: 新建 `app/src/main/java/com/kuaimai/pda/util/PrefsKeys.kt`

```kotlin
object PrefsKeys {
    const val KEY_USER_TOKEN = "user_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_PERMISSIONS = "user_permissions"
    const val KEY_API_KEY = "api_key"
    const val KEY_APP_KEY = "app_key"
    const val KEY_APP_SECRET = "app_secret"
    const val KEY_BASE_URL = "base_url"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_SESSION_EXPIRE = "session_expire_time"
    const val KEY_SESSION = "session"
}
```

然后各Repository/Service引用`PrefsKeys`中的常量，删除各自内部的重复定义。

## 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `backend/main.py` | SEC-01: CORS来源从环境变量读取 |
| `backend/app/config.py` | SEC-01: 新增CORS_ORIGINS; BUG-03: 新增SESSION_WARNING_DAYS |
| `backend/app/auth.py` | SEC-02: SKIP_AUTH_PREFIXES修改; SEC-03: hmac.compare_digest |
| `backend/app/routers/images.py` | SEC-02: get_images添加认证; SEC-05: 错误消息脱敏; SEC-06: 上传速率限制 |
| `backend/app/routers/orders.py` | SEC-05: 错误消息脱敏 |
| `backend/app/routers/areas.py` | SEC-05: 错误消息脱敏 |
| `backend/app/routers/users.py` | SEC-04: 首次登录强制改密; SEC-05: 错误消息脱敏 |
| `backend/app/routers/system.py` | SEC-05: 错误消息脱敏 |
| `backend/app/services/kuaimai_api.py` | BUG-04: 删除5个未使用方法 |
| `backend/app/models.py` | SEC-04: LoginResponse新增mustChangePassword字段 |
| `app/.../data/repository/UserRepository.kt` | BUG-01: 登录时写入session_expire_time |
| `app/.../App.kt` | BUG-02: 删除sendCrashReport空壳方法 |
| `app/.../util/PrefsKeys.kt` | CODE-01/02/03: 新建统一常量类 |
| `app/.../data/repository/AuthRepository.kt` | CODE-01/02/03: 引用PrefsKeys |
| `app/.../data/api/ImageUploadService.kt` | CODE-01/02/03: 引用PrefsKeys |
| `app/.../di/NetworkModule.kt` | CODE-01/02/03: 引用PrefsKeys |
| `app/.../data/api/dto/UserDto.kt` | SEC-04: LoginResponse新增mustChangePassword字段 |
| `app/.../ui/login/LoginScreen.kt` | SEC-04: 检测mustChangePassword弹出改密对话框 |
| `docker-deploy/.env.docker.example` | SEC-01: 新增CORS_ORIGINS示例 |

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 后端启动无报错
4. 验证`/api/images/{sku_outer_id}`需要认证才能访问
5. 验证默认admin用户首次登录返回`mustChangePassword=true`
6. 验证HomeScreen会话过期预警功能正常工作
7. 验证CORS配置从环境变量读取

## 假设与决策

1. **SEC-02决策**: 保留`/images`静态文件公开访问（PDA需要直接加载图片），仅对API路由`/api/images/`添加认证
2. **SEC-04决策**: 采用简化方案（检查默认密码）而非数据库Migration方案，避免增加复杂度
3. **BUG-01决策**: 在UserRepository中写入session_expire_time，使用与AuthRepository相同的key名，保持兼容
4. **BUG-04决策**: 直接删除未使用的API方法，而非标记为deprecated，因为它们从未被调用
5. **CODE-01/02/03决策**: 创建PrefsKeys统一常量类，各模块引用而非各自定义
