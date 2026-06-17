# 代码审查计划 - 第四轮（硬编码/一致性/重复代码/无用代码）

## 审查重点

1. 硬编码问题
2. 前后端函数名/字段名一致性
3. 重复代码
4. 无用代码
5. UTC与北京时间混用
6. 逻辑bug

---

## 发现问题

### P1 - 硬编码问题

#### HC-01: DEFAULT_SERVER_URL "http://10.0.2.2:8000" 在4处重复定义
- **位置**:
  - `NetworkModule.kt` L40: `DEFAULT_SERVER_URL = "http://10.0.2.2:8000"`
  - `ImageUploadService.kt` L30: `DEFAULT_SERVER_URL = "http://10.0.2.2:8000"`
  - `ProductViewModel.kt` L71: `DEFAULT_SERVER_URL = "http://10.0.2.2:8000"`
  - `AuthRepository.kt` L36: `DEFAULT_BASE_URL = "https://api.kuaimai.com/router"`
- **问题**: 同一个默认服务器地址在4处重复定义，修改时容易遗漏。`10.0.2.2` 是模拟器专用地址，真机部署需修改。
- **修复**: 提取为 `BuildConfig` 字段或统一常量类，所有地方引用同一常量

#### HC-02: 后端 config.py 中 KUAIMAI_API_BASE 硬编码
- **位置**: `config.py` L36: `KUAIMAI_API_BASE = "https://openapi.kuaimai.com/router"`
- **问题**: 快麦API基础URL硬编码，无法通过环境变量配置
- **修复**: 改为 `os.getenv("KUAIMAI_API_BASE", "https://openapi.kuaimai.com/router")`

#### HC-03: 后端 kuaimai_api.py 超时时间硬编码
- **位置**: `kuaimai_api.py` 中 httpx.AsyncClient 的 timeout
- **问题**: 超时时间硬编码，无法通过配置调整
- **修复**: 从 config.py 读取超时配置

#### HC-04: PickListViewModel 中 7天 和 12小时 魔法数字
- **位置**: `PickListViewModel.kt` L89: `7 * 24 * 60 * 60 * 1000L`，L129: `12 * 60 * 60 * 1000L`
- **问题**: 魔法数字，不直观
- **修复**: 提取为命名常量

#### HC-05: PickDetailViewModel 中 12小时 魔法数字
- **位置**: `PickDetailViewModel.kt` 中 `12 * 60 * 60 * 1000L`
- **问题**: 同HC-04，且与PickListViewModel重复
- **修复**: 提取为TimeUtils的常量或方法

### P1 - 前后端一致性问题

#### CONSIST-01: 后端 AreaResponse.created_at vs App端 AreaResponse.createdAt
- **位置**: `models.py` L35 `created_at` vs `AreaDto.kt` L11 `createdAt`
- **问题**: 后端Pydantic模型使用snake_case `created_at`，但FastAPI默认会按字段名序列化。需要确认FastAPI是否配置了alias或camelCase转换。如果没有，App端收到的JSON字段名是 `created_at` 而非 `createdAt`，Gson解析会失败。
- **分析**: 检查后端所有Pydantic模型，发现混用了snake_case和camelCase：
  - `AreaResponse.created_at` (snake_case) ← 不一致
  - `OrderResponse.orderNo` (camelCase) ← 一致
  - `ItemResponse.skuOuterId` (camelCase) ← 一致
- **修复**: `AreaResponse.created_at` 改为 `createdAt`，与App端和其他模型保持一致

#### CONSIST-02: 后端 ImageResponse.filePath 不应暴露给App端
- **位置**: `models.py` L114 `filePath`
- **问题**: App端不需要服务器文件路径，暴露 filePath 有安全风险
- **修复**: App端DTO中不包含filePath即可（当前已如此），但后端应考虑是否在公开API中返回此字段

#### CONSIST-03: PickOrderRepositoryImpl.updateItemRemark/updateItemSupplier 中的JSON payload未转义
- **位置**: `PickOrderRepository.kt` L122, L137
- **问题**: 与BUG-32/33相同的问题，ProductViewModel已修复但Repository层未修复
- **修复**: 添加JSON转义或复用escapeJson方法

### P2 - 重复代码

#### DUP-01: OrderResponse 和 OrderDetailResponse 字段完全重复
- **位置**: `OrderDto.kt` L14-24 和 L27-38
- **问题**: OrderDetailResponse 重复了 OrderResponse 的所有8个字段，仅多了一个 `items`
- **修复**: OrderDetailResponse 继承 OrderResponse（Kotlin data class 不可继承，但可以用组合或直接内联items字段到OrderResponse）

#### DUP-02: PickOrderRepositoryImpl 和 ProductViewModel 都有 enqueuePendingOperation/enqueueOperation
- **位置**: `PickOrderRepository.kt` L153-168, `ProductViewModel.kt` L357-365
- **问题**: 两个类各自实现了写入离线操作队列的逻辑，代码几乎相同
- **修复**: ProductViewModel 应通过 PickOrderRepository 来写入离线队列，而非直接操作 PendingOperationDao

#### DUP-03: DEFAULT_SERVER_URL 在3个文件中重复
- **位置**: 同HC-01
- **修复**: 同HC-01

#### DUP-04: PickListViewModel 和 PickDetailViewModel 中 12小时过期时间计算重复
- **位置**: 同HC-04/HC-05
- **修复**: 提取为 TimeUtils 的常量或方法

### P2 - 无用代码

#### DEAD-01: database.py 中 get_db_ctx() 未被使用
- **位置**: `database.py` L49-58
- **问题**: `get_db_ctx()` 上下文管理器定义了但没有任何路由使用它，所有路由都手动调用 `get_db()` + `db.commit()` / `db.rollback()`
- **修复**: 删除此函数，或重构路由使用它（后者改动太大，暂删除）

#### DEAD-02: ItemRepositoryImpl 注入了未使用的 DAO
- **位置**: `ItemRepository.kt` L27-29
- **问题**: `ItemRepositoryImpl` 注入了 `pickOrderDao`、`pickItemDao`、`productImageDao` 但从未使用
- **修复**: 删除未使用的构造参数

#### DEAD-03: config.py 中 _BEIJING_TZ 仅用于 check_session_expiry
- **位置**: `config.py` L18
- **问题**: `_BEIJING_TZ` 在 config.py 中定义，但只在 `check_session_expiry` 中使用。而 `time_utils.py` 中也有独立的北京时间定义。两处定义不统一。
- **修复**: config.py 中的 `check_session_expiry` 改用 `time_utils.py` 的 `beijing_now()`，删除 config.py 中的 `_BEIJING_TZ`

---

## 修复步骤

### 第1步：修复硬编码问题（HC-01~05）
1. 创建 `AppConstants.kt` 统一常量类
2. 修改 NetworkModule/ImageUploadService/ProductViewModel 引用统一常量
3. 后端 config.py KUAIMAI_API_BASE 改为环境变量
4. 提取魔法数字为命名常量

### 第2步：修复前后端一致性问题（CONSIST-01~03）
1. 后端 AreaResponse.created_at 改为 createdAt
2. PickOrderRepositoryImpl 添加 JSON 转义

### 第3步：修复重复代码和无用代码（DUP-01~04, DEAD-01~03）
1. OrderDetailResponse 改为组合模式
2. ProductViewModel 通过 Repository 写离线队列
3. 删除 get_db_ctx()
4. 删除 ItemRepositoryImpl 未使用的 DAO
5. config.py 改用 time_utils.beijing_now()

### 第4步：构建验证

### 第5步：版本号更新 + 知识图谱 + Git

---

## 涉及文件清单

| 文件 | 修改内容 |
|------|----------|
| `app/.../util/AppConstants.kt` | 新建统一常量类 |
| `app/.../di/NetworkModule.kt` | HC-01 引用统一常量 |
| `app/.../data/api/ImageUploadService.kt` | HC-01 引用统一常量 |
| `app/.../ui/product/ProductViewModel.kt` | HC-01/05 引用统一常量 |
| `app/.../ui/picklist/PickListViewModel.kt` | HC-04 魔法数字常量化 |
| `app/.../ui/pickdetail/PickDetailViewModel.kt` | HC-05 魔法数字常量化 |
| `app/.../util/TimeUtils.kt` | HC-04/05 添加12小时过期常量 |
| `backend/app/config.py` | HC-02/DEAD-03 环境变量+删除_BEIJING_TZ |
| `backend/app/models.py` | CONSIST-01 AreaResponse.created_at→createdAt |
| `app/.../data/repository/PickOrderRepository.kt` | CONSIST-03/DUP-02 JSON转义+复用 |
| `app/.../data/api/dto/OrderDto.kt` | DUP-01 OrderDetailResponse组合 |
| `app/.../data/repository/ItemRepository.kt` | DEAD-02 删除未使用DAO |
| `backend/app/database.py` | DEAD-01 删除get_db_ctx |
