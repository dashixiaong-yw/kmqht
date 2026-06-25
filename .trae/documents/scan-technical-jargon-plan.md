# 技术用语简化扫描报告

> 扫描全项目面向用户的文案，找出专业/技术术语，评估是否需要简化

---

## 扫描范围

| 区域 | 文件 | 用户群体 |
|:-----|:-----|:---------|
| Web管理后台（快麦配置页） | [backend/app/routers/admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) #L310-L339 | 管理员（非技术人员） |
| Web管理后台（系统配置页） | [backend/app/routers/admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) #L341-L352 | 管理员 |
| Web管理后台（JS登录逻辑） | [backend/app/routers/admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) #L434-L683 | 管理员 |
| Web管理后台（tab标签、按钮） | [backend/app/routers/admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) #L238-L370 | 管理员 |
| Android PDA（首页会话预警） | [app/.../HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) #L85-L113, L247-L259 | PDA操作员 |
| Android PDA（导航弹窗） | [app/.../AppNavigation.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt) #L190-L206 | PDA操作员 |
| Android PDA（登录页错误提示） | [app/.../LoginScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt) #L392-L406 | PDA操作员 |

---

## 发现的技术用语清单

### 1. Web管理后台 - 快麦配置页（问题最集中）

| 原文 | 位置 | 技术度 | 建议 | 优先级 |
|:-----|:-----|:------:|:----|:------:|
| `刷新Session`（按钮） | admin.py:L319 | ⚠️ 中 | `刷新连接` | P1 |
| `自动刷新诊断`（卡片标题） | admin.py:L323 | ⚠️ 中 | `连接检测` | P2 |
| `执行自动刷新测试`（按钮） | admin.py:L328 | ⚠️ 中 | `测试连接` | P2 |
| `手动更新凭证`（卡片标题） | admin.py:L332 | ⚠️ 低 | `手动设置` | P3 |
| `App Key`（输入框标签） | admin.py:L333 | ❌ 高 | 保留（实际平台字段名，不可改） | — |
| `App Secret`（输入框标签） | admin.py:L334 | ❌ 高 | 保留（实际平台字段名，不可改） | — |
| `Session`（输入框标签） | admin.py:L335 | ⚠️ 中 | 保留（实际平台字段名） | — |
| `Refresh Token`（输入框标签） | admin.py:L336 | ⚠️ 中 | 保留（实际平台字段名） | — |
| `Session状态:`（JS渲染） | admin.py:L665 | ⚠️ 中 | `连接状态:` | P2 |
| `Refresh Token: 已配置/未配置`（JS渲染） | admin.py:L667 | ⚠️ 中 | `自动续期: 已开启/未开启` | P1 |
| 诊断结果中的 `Refresh Token` | admin.py:L696-L697 | ⚠️ 中 | `刷新令牌` | P3 |
| 诊断结果中的 `刷新前更新时间` | admin.py:L698 | ⚠️ 低 | `更新前时间` | P3 |
| 诊断结果中的 `刷新调用` | admin.py:L700 | ⚠️ 低 | `刷新操作` | P3 |
| `凭证更新成功`（JS提示） | admin.py:L727 | ⚠️ 低 | `保存成功` | P3 |
| `更新失败:`（JS提示） | admin.py:L727-729 | ⚠️ 低 | `保存失败:` | P3 |

### 2. Web管理后台 - 系统配置页

| 原文 | 位置 | 技术度 | 建议 | 优先级 |
|:-----|:-----|:------:|:----|:------:|
| `API Key`（卡片标题） | admin.py:L344 | ❌ 高 | `管理密码` | P1 |
| `当前API Key:` | admin.py:L345 | ❌ 高 | `当前管理密码:` | P1 |
| `修改服务器地址请编辑 .env 文件中的 SERVER_URL 并重启服务` | admin.py:L350 | ❌ 高 | 保留（管理员操作说明） | — |

### 3. Web管理后台 - Tab 标签

| 原文 | 位置 | 技术度 | 建议 | 优先级 |
|:-----|:-----|:------:|:----|:------:|
| `APK管理`（tab） | admin.py:L260 | ⚠️ 中 | `版本管理` | P2 |
| `快麦配置`（tab） | admin.py:L257 | ⚠️ 低 | 保留（业务品牌名） | — |

### 4. Android PDA - 首页会话预警

| 原文 | 位置 | 技术度 | 建议 | 优先级 |
|:-----|:-----|:------:|:----|:------:|
| `快麦API会话已过期，请重新授权` | HomeScreen.kt:L252 | ❌ 高 | `当前账号已过期，请前往设置页面重新配置` | P1 |
| `会话已过期，请重新授权` | HomeScreen.kt:L95 | ⚠️ 中 | `当前账号已过期，请重新配置` | P1 |
| `会话将在X天后过期，请及时刷新` | HomeScreen.kt:L102 | ⚠️ 中 | `授权将在X天后过期，请及时更新` | P2 |
| `会话即将过期，请立即刷新` | HomeScreen.kt:L105 | ⚠️ 中 | `授权即将过期，请立即更新` | P2 |
| `会话已过期`（弹窗标题） | HomeScreen.kt:L251 | ⚠️ 中 | `授权已过期` | P2 |

### 5. Android PDA - 其它（已较友好，无需修改）

| 原文 | 位置 | 说明 |
|:-----|:-----|:------|
| `当前登录已失效，请重新登录` | AppNavigation.kt:L202 | ✅ 已友好 |
| `登录过期`（弹窗标题） | AppNavigation.kt:L201 | ✅ 已友好 |
| `用户名或密码错误` | LoginScreen.kt:L402 | ✅ 已友好 |
| `连接超时 / 无法连接服务器` | LoginScreen.kt:L396-399 | ✅ 已友好 |
| `选择供应商` | SupplierSelectDialog.kt | ✅ 业务用语无技术问题 |

---

## 建议范围

### 必改项（P1 - 明显技术术语，用户无法理解）

1. **系统配置页**：`API Key` → `管理密码`；`当前API Key:` → `当前管理密码:`
2. **快麦配置页**：`刷新Session` → `刷新连接`
3. **快麦配置页 JS**：`Refresh Token: 已配置/未配置` → `自动续期: 已开启/未开启`
4. **首页会话过期弹窗**：`快麦API会话已过期，请重新授权` → 更友好的表述
5. **首页会话预警**：`会话已过期，请重新授权` → 更友好的表述

### 建议改项（P2 - 技术性强但可通过上下文理解）

6. `自动刷新诊断` → `连接检测`
7. `执行自动刷新测试` → `测试连接`
8. `Session状态:` → `连接状态:`
9. `APK管理` → `版本管理`
10. 首页的 `会话将在X天后过期` 系列 → `授权将在X天后过期`

### 可改可不改（P3 - 内部管理功能，普通用户不常看到）

11. `手动更新凭证` → `手动设置`
12. 诊断结果中的少数术语

### 保留不改（字段名与实际平台一致，改了反而迷惑）

- `App Key` / `App Secret` / `Session` / `Refresh Token` — 这些是快麦平台的实际字段名，管理员配置时需要知道这些名称才能填入正确的值。

---

## 涉及文件

| 优先级 | 文件 | 改行数 | 说明 |
|:------:|:-----|:------:|:-----|
| P1 + P2 | [backend/app/routers/admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) | ~15行 | HTML/JS 文案 |
| P1 + P2 | [app/.../HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) | ~8行 | 弹窗 + 预警文案 |

---

## 验证方式

1. 后端重启后，访问管理后台，检查各 tab 页文案
2. PDA 重新构建 APK 后，触发会话过期场景验证预警/弹窗文案
