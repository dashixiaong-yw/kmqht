# 计划：后端Web管理后台

## 摘要

将系统管理类功能从App端迁移到后端Web管理后台，提供统一的浏览器端配置页面。App端只保留日常业务操作和个人偏好设置。

## 现状分析

### 权限模型（5种权限）

| 权限代码 | 含义 | 操作性质 | 应归属 |
|---------|------|---------|--------|
| `settings` | 设置管理 | 系统管理 | Web后台 |
| `update_supplier` | 修改供应商 | 日常业务 | App端 |
| `update_remark` | 修改备注 | 日常业务 | App端 |
| `manage_area_image` | 库区图管理 | 日常业务 | App端 |
| `manage_box_image` | 箱规图管理 | 日常业务 | App端 |

### 功能归属划分原则

- **Web后台**：系统级配置（用户管理、拣货区管理、快麦凭证、服务器配置）
- **App端**：日常业务操作（取货单、商品详情、图片上传/删除、供应商/备注修改、扫码方式/反馈开关）
- **Web后台可查看**：图片状态（只读），但不做上传/删除操作

### 当前功能分布与迁移计划

| 功能 | 当前位置 | 迁移后位置 | 说明 |
|------|---------|-----------|------|
| 用户管理（增删改查+权限） | App SettingsScreen | **Web后台** | 系统管理，PDA小屏操作体验差 |
| 拣货区管理（增删） | 无UI入口 | **Web后台** | 新增功能，App端不需要 |
| 快麦凭证（状态/刷新） | App SettingsScreen | **Web后台** | 系统管理，管理员操作 |
| 服务器地址+API Key | App SettingsScreen | **Web后台** | 系统配置，管理员操作 |
| 扫码配置二维码 | App SettingsScreen + /setup | **Web后台** | 合并到/admin |
| 图片查看 | App ProductScreen | **两端都有** | App端操作，Web后台只读查看 |
| 图片上传/删除 | App ProductScreen | **App端** | 日常业务，需manage_area/box_image权限 |
| 取货单操作 | App PickDetailScreen | **App端** | 日常业务，无需权限 |
| 供应商修改 | App ProductScreen | **App端** | 日常业务，需update_supplier权限 |
| 备注修改 | App ProductScreen | **App端** | 日常业务，需update_remark权限 |
| 扫码方式/反馈开关 | App SettingsScreen | **App端** | 个人偏好，本地配置 |
| 退出登录 | App SettingsScreen | **App端** | 个人操作 |
| 设置入口可见性 | HomeScreen（settings权限可见） | **App端** | 迁移后设置入口改为"个人设置"，所有用户可见 |

## 方案设计

### Web管理后台架构

```
后端 /admin 页面（SPA式多标签页）
├── 仪表盘（系统概览+扫码配置二维码）
├── 用户管理（增删改查+权限分配+启用禁用）
├── 拣货区管理（增删）
├── 快麦配置（凭证状态+刷新+手动更新）
├── 系统配置（API Key+服务器地址）
└── 图片查看（按SKU查看图片状态，上传/删除操作在App端进行）
```

### 技术方案

- **纯HTML+CSS+JS**，不引入前端框架，内嵌到Python后端
- 后端新增 `GET /admin` 返回管理后台HTML页面
- 管理后台通过 `X-API-Key` 头调用现有API
- 页面使用标签页（Tab）组织不同功能模块
- 响应式设计，PC和平板均可使用

### 认证方案

- `/admin` 页面需要输入API Key才能使用
- 页面加载时显示API Key输入框，验证通过后显示管理内容
- API Key存储在浏览器 sessionStorage 中，关闭标签页后失效

### App端精简

SettingsScreen 改为"个人设置"页面，所有用户可见：
- 当前用户信息（只读）
- 扫码方式选择
- 声音/振动反馈开关
- 退出登录按钮
- 版本号

移除（迁移到Web后台）：
- 服务器配置（地址+API Key+扫码配置）
- 快麦连接状态
- 用户管理

HomeScreen 修改：
- 设置入口改为所有用户可见（不再限制settings权限）
- 首次引导提示改为所有用户可见（不再限制settings权限）

## 具体修改清单

### 后端新增文件

| 文件 | 说明 |
|------|------|
| `backend/app/routers/admin.py` | 管理后台路由，`GET /admin` 返回HTML页面 |

### 后端修改文件

| 文件 | 修改内容 |
|------|---------|
| `backend/app/main.py` | 注册admin路由 |
| `backend/app/auth.py` | `/admin` 路径加入SKIP_AUTH_PREFIXES（页面本身不需要API Key，但API调用需要） |

### App端修改文件

| 文件 | 修改内容 |
|------|---------|
| `SettingsScreen.kt` | 移除服务器配置、快麦连接状态、用户管理3个Card，只保留个人设置 |
| `SettingsViewModel.kt` | 移除不再需要的快麦session相关方法 |
| `HomeScreen.kt` | 设置入口改为所有用户可见，引导提示改为所有用户可见 |

### Web管理后台功能模块

#### Tab 1: 仪表盘
- 系统运行状态（健康检查）
- 快麦session状态（是否过期、剩余天数）
- 用户数量、取货单数量统计
- 服务器地址+扫码配置二维码（复用现有/setup的二维码生成逻辑）

#### Tab 2: 用户管理
- 用户列表（用户名、权限、状态、创建时间）
- 添加用户（用户名+密码+权限选择）
- 编辑用户（修改密码+权限+启用/禁用）
- 删除用户（二次确认）

#### Tab 3: 拣货区管理
- 拣货区列表
- 添加拣货区
- 删除拣货区（二次确认）

#### Tab 4: 快麦配置
- 当前凭证状态（app_key、session有效期、refresh_token）
- 手动刷新session按钮
- 手动更新凭证（app_key、app_secret、session、refresh_token输入框）

#### Tab 5: 系统配置
- API Key显示/修改
- 服务器地址显示
- PDA扫码配置二维码（复用/setup逻辑）

#### Tab 6: 图片查看
- 按SKU搜索图片（只读查看）
- 查看库区图/箱规图缩略图
- **注意**：图片的实际上传/删除操作仍在App端ProductScreen进行
- Web后台仅用于查看图片状态

## Web管理后台API调用

管理后台调用现有API端点，不需要新增API：

| 操作 | API端点 | 方法 |
|------|---------|------|
| 健康检查 | `/health` | GET |
| 用户列表 | `/api/users` | GET |
| 创建用户 | `/api/users` | POST |
| 更新用户 | `/api/users/{id}` | PUT |
| 删除用户 | `/api/users/{id}` | DELETE |
| 拣货区列表 | `/api/areas` | GET |
| 创建拣货区 | `/api/areas` | POST |
| 删除拣货区 | `/api/areas/{id}` | DELETE |
| 快麦session状态 | `/api/kuaimai/session-status` | GET |
| 刷新快麦session | `/api/kuaimai/refresh-session` | POST |
| 图片查询 | `/api/images/{sku}` | GET |

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 浏览器访问 `/admin` 页面正常显示
4. 输入API Key后可以操作所有管理功能
5. App端SettingsScreen精简后功能正常
6. App端所有用户可见设置入口
7. 退出登录、扫码方式切换、反馈开关正常工作
8. App端图片上传/删除权限校验正常
