# 快麦ERP开放平台Token自动刷新修复计划

## 问题概述

快麦ERP开放平台的 `accessToken`（session）有效期为 **30天**，过期后所有API调用将被拒绝。当前项目：
- 未存储 `refreshToken`
- 未实现 `open.token.refresh` 刷新会话接口
- 现有的 `check_session_expiry()` 仅输出日志警告，不会自动刷新
- 存在API地址和版本号与官方文档不一致的问题

## 官方文档关键信息

| 项目 | 官方文档 | 当前代码 | 状态 |
|------|---------|---------|------|
| API地址 | `https://gw.superboss.cc/router` | `https://openapi.kuaimai.com/router` | 需确认 |
| version参数 | `1.0` | `2.0` | 需确认 |
| accessToken有效期 | 30天 | 已知 | OK |
| 刷新接口 | `open.token.refresh` | 未实现 | **缺失** |
| refreshToken | 必须存储用于刷新 | 未存储 | **缺失** |
| 刷新后token值 | accessToken和refreshToken不变，仅延长30天 | - | - |
| 过期后恢复 | 需联系客服人工刷新授权 | - | - |

## 修改计划

### 1. 后端：KuaimaiCredentials 增加 refreshToken 字段

**文件**：`backend/app/config.py`

- `KuaimaiCredentials.__init__` 增加 `self.refresh_token: str = ""`
- `is_valid()` 不变（refresh_token不影响基本凭证有效性判断）
- `load_kuaimai_config()` 增加读取 `refresh_token` 字段
- 新增 `save_kuaimai_config()` 方法：刷新成功后将新的 updated_at 写回 `kuaimai.json`

### 2. 后端：kuaimai.example.json 增加 refresh_token 字段

**文件**：`backend/kuaimai.example.json`

```json
{
  "app_key": "",
  "app_secret": "",
  "session": "",
  "refresh_token": "",
  "updated_at": ""
}
```

### 3. 后端：kuaimai_api.py 增加刷新会话接口

**文件**：`backend/app/services/kuaimai_api.py`

新增 `async def refresh_session() -> bool` 函数：
- 调用 `open.token.refresh` 方法，传入 `refreshToken` 参数
- 刷新成功后更新 `kuaimai_creds.session`（值不变但确认有效）和 `kuaimai_creds.updated_at`
- 调用 `save_kuaimai_config()` 将 updated_at 写回文件
- 返回是否成功

### 4. 后端：config.py 增加 save_kuaimai_config() 方法

**文件**：`backend/app/config.py`

新增函数 `save_kuaimai_config()`：
- 将当前 `kuaimai_creds` 的 updated_at 写回 `kuaimai.json` 文件
- 触发 watchfiles 热重载（文件变更自动触发 load_kuaimai_config）

### 5. 后端：main.py 定时任务增加自动刷新

**文件**：`backend/main.py`

在 `_start_scheduler()` 中新增定时任务：
- **每7天自动刷新session**（30天有效期，7天刷新一次留足余量）
- 刷新失败时记录错误日志，不影响其他定时任务
- 刷新成功时记录 info 日志

替换现有的 `check_session_warning` 任务（仅日志警告 → 自动刷新+日志）

### 6. 后端：system.py 增加手动刷新接口

**文件**：`backend/app/routers/system.py`

新增 `POST /api/kuaimai/refresh-session` 接口：
- 需要登录认证（`get_current_user`）
- 调用 `refresh_session()` 刷新
- 返回刷新结果（成功/失败+剩余天数）
- 用于紧急情况下手动触发刷新

### 7. 后端：system.py 增加session状态查询接口

**文件**：`backend/app/routers/system.py`

新增 `GET /api/kuaimai/session-status` 接口：
- 需要登录认证
- 返回 session 是否有效、剩余天数、最后更新时间
- 供前端展示session状态

### 8. 前端：SettingsScreen 增加快麦session状态展示

**文件**：`app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt`

在"服务器配置"Card下方新增"快麦连接状态"Card：
- 显示session状态（有效/即将过期/已过期）
- 显示剩余天数
- 显示最后更新时间
- "手动刷新"按钮（调用后端 `/api/kuaimai/refresh-session`）

### 9. 前端：SettingsViewModel 增加session状态逻辑

**文件**：`app/src/main/java/com/kuaimai/pda/ui/settings/SettingsViewModel.kt`

- 新增 `sessionStatus` StateFlow
- 新增 `loadSessionStatus()` 方法调用后端接口
- 新增 `refreshSession()` 方法调用后端刷新接口

### 10. 前端：新增API接口定义

**文件**：`app/src/main/java/com/kuaimai/pda/data/api/` 相关文件

- 在现有API Service中增加 `refreshSession()` 和 `getSessionStatus()` 接口定义
- 新增对应的DTO数据类

## 不修改的内容

- **API地址**：`https://openapi.kuaimai.com/router` — 这可能是旧版地址，如果当前能正常调用API则不修改。如果调用失败再改为官方文档的 `https://gw.superboss.cc/router`
- **version参数**：同理，当前 `v=2.0` 如果能正常工作则不修改
- **App端KuaimaiInterceptor**：App端不直接调用快麦API（通过后端中转），不需要修改

## 假设与决策

1. **刷新频率**：选择每7天自动刷新（30天有效期，7天刷新留足余量，且API近60天无调用会被回收权限，7天刷新也能保持活跃）
2. **刷新后token值不变**：根据官方文档，刷新成功后 accessToken 和 refreshToken 值不变，仅延长有效期，因此不需要更新 `kuaimai.json` 中的 session 和 refresh_token 值，只需更新 `updated_at`
3. **App端不直接调快麦API**：所有快麦API调用通过后端中转，App端只需展示状态和触发刷新
4. **手动刷新接口需要登录认证**：防止未授权用户触发刷新

## 验证步骤

1. 后端启动后检查定时任务日志，确认 `kuaimai_session_refresh` 任务已注册
2. 手动调用 `POST /api/kuaimai/refresh-session` 验证刷新逻辑
3. 调用 `GET /api/kuaimai/session-status` 验证状态查询
4. 前端SettingsScreen展示session状态和刷新按钮
5. `./gradlew lint` 通过
6. `./gradlew assembleDebug` 构建成功
