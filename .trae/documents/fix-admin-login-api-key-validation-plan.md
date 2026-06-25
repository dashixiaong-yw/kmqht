# 修复管理后台登录 API Key 验证 — 规划

## 一、问题描述

管理后台 Web 页面使用 API Key 登录，点击「验证并登录」按钮没有反应（输入的 key 正确和错误都无反馈）。

## 二、根因分析

### 根因：`/health` 端点不对 API Key 做验证

[backend/app/routers/system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L44-L65) 的 `/health` 健康检查接口**没有认证检查**（无 `Depends(get_current_user)`）：

```python
@router.get("/health", response_model=HealthResponse)
def health_check() -> HealthResponse:
    """健康检查"""
```

而前端登录函数 [admin.py:doLogin()](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L434-L451) 调用 `/health` 来验证 API Key：

```javascript
const r = await fetch(API_BASE + '/health', { headers: { 'X-API-Key': key } });
if (r.ok) {
  // 视为登录成功
  showMainPanel();
} else {
  // 显示 "API Key无效"
}
```

**因为 `/health` 永远返回 200**，所以：
1. **任意 API Key（正确或错误）** → 都返回 200 → `r.ok` 为 true → 进入 `showMainPanel()`
2. 隐藏登录区、显示管理面板
3. 调用 `loadDashboard()` 加载仪表盘数据
4. `api('/api/users')` **才是真正校验 API Key 的地方**（通过 `get_current_user()` → `hmac.compare_digest()`）
5. 如果 key 错误 → 仪表盘显示「加载失败」
6. 如果 key 正确 → 仪表盘正常加载

### 用户感知的「没有反应」

用户描述的「没有反应」是因为：
- **key 错误时**：登录区被隐藏，管理面板出现，但仪表盘显示「加载失败」的错误信息，用户看到的是管理面板但没有数据，或误以为仍然停在登录页
- **key 正确时**：登录应该正常工作，但页面没有明确的「登录成功」反馈

### 历史修改分析

最近所有 backend/ 目录下的 Git 提交（v2.13 ~ v2.25）：

| 版本 | 修改 | 是否影响登录 |
|:----:|------|:----------:|
| v2.25 | admin.py：searchImages 缩略图+URL诊断 | ❌ 无影响 |
| v2.24 | Android端 401弹窗 | ❌ 不影响后端 |
| v2.15 | 修复 dockerignore 排除 kuaimai.json | ❌ 与登录无关 |
| v2.14 | 修复 main.py 缺少导入 | ❌ 启动修复 |
| v2.13 | admin.py：诊断测试功能 | ❌ 不影响登录 |

**结论：最近的后端修改与登录按钮无反应问题无关。** 这是一个早已存在的设计缺陷（`/health` 不做 API Key 校验），只是最近才暴露出来。

## 三、修改方案

### 方案：让 `doLogin()` 使用需要认证的端点

**不改后端**（不新增端点），只改前端 JS 的登录逻辑：

在 [admin.py:doLogin()](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L434-L451) 中，将 `/health` 替换为调用 `/api/kuaimai/credentials` 端点。

理由：
- `/api/kuaimai/credentials` 已有 `Depends(get_current_user)`（见 [system.py:L253-L260](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L253-L260)）
- 该端点只需要登录权限（不要求特定 permission），且轻量无副作用
- 返回 200 = API Key 有效，返回 401 = API Key 无效

**具体修改**：

```javascript
// 修改前
const r = await fetch(API_BASE + '/health', { headers: { 'X-API-Key': key } });

// 修改后
const r = await fetch(API_BASE + '/api/kuaimai/credentials', { headers: { 'X-API-Key': key } });
```

### 前端异常处理完善

`doLogin()` 函数的 catch 分支已正确处理网络异常，显示「连接失败:」错误。无需额外改动。

## 四、涉及文件

| 文件 | 修改内容 |
|:-----|:---------|
| [backend/app/routers/admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L438) | `doLogin()` 中的 fetch URL 从 `/health` 改为 `/api/kuaimai/credentials` |

仅修改 1 行。

## 五、验证步骤

1. 重启后端服务
2. 浏览器访问 `/admin`
3. 输入错误 API Key → 点击「验证并登录」→ 应显示「API Key无效」
4. 输入正确 API Key → 点击「验证并登录」→ 应显示管理面板且仪表盘正常加载
5. 不输入 API Key → 点击「验证并登录」→ 应显示「请输入API Key」
