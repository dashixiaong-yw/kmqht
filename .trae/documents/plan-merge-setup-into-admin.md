# 计划：将扫码配置合并到管理后台

## 摘要

将 `/setup`（扫码配置）合并到 `/admin`（管理后台），实现一个地址搞定所有功能。新 PDA 无需 API Key 也能扫码配置，管理员登录后可管理系统。

## 当前状态分析

### 现有路由架构

```
/admin     (admin.py) — API Key认证，含二维码（已跳过API Key中间件，前端JS验证）
/setup     (system.py) — 无认证，独立二维码页面
```

### 关键发现

1. **`/admin` 已跳过 API Key 中间件**（`auth.py` 中 `SKIP_AUTH_PREFIXES` 包含 `/admin`）
    - 这意味着服务器返回 `/admin` 的 HTML 不需要 API Key
    - API Key 验证是在浏览器端 JavaScript 中进行的

2. **两个文件有重复代码**：
    - `admin.py` 有 `_generate_qr_base64()`（`box_size=8`）
    - `system.py` 有 `_generate_qr_base64()`（`box_size=10`）
    - 完全一样的功能，只是参数不同

3. **管理后台已经包含二维码**：仪表盘标签页已有扫码配置二维码

4. **鸡生蛋问题不存在**：正因为 `/admin` 跳过 API Key 中间件，HTML 可以展示公开内容

### 实现可行性

| 需求 | 可行 | 原因 |
|------|:----:|------|
| 公开显示二维码（无需API Key） | ✅ | `/admin` 已跳过API Key中间件 |
| 管理功能需API Key | ✅ | 前端JS验证机制不变 |
| 单个地址搞定所有 | ✅ | 一个页面分上下两部分：公开区+认证区 |
| 去掉 `/setup` | ✅ | 可保留302跳转，向后兼容 |

## 修改方案

### 页面布局变化

```
当前 /admin 页面                  改造后 /admin 页面
┌─────────────────────┐          ┌─────────────────────┐
│  [API Key登录面板]    │          │  ┌─ 扫码配置（公开）┐ │
│                      │          │  │ [QR Code]        │ │
│  验证通过后显示 ↓     │          │  │ 服务器:xxx        │ │
│                      │          │  │ API Key: 已配置   │ │
│  [标签页导航]         │          │  └──────────────────┘ │
│  [内容区域]           │          │  ──── 分割线 ────────  │
│                      │          │  [API Key 登录面板]    │
│                      │          │                      │
│                      │          │  验证通过后显示 ↓      │
│                      │          │                      │
│                      │          │  [标签页导航]          │
│                      │          │  [内容区域]            │
└─────────────────────┘          └─────────────────────┘
```

### 涉及文件和修改内容

#### 1. `backend/app/routers/admin.py` — 主要修改

**修改 `_build_admin_html()`**：将页面分3层结构

```html
<!-- 第一层：扫码配置区（始终可见，无需API Key）-->
<div id="setup-section">
  <h2>PDA 扫码配置</h2>
  <div>二维码（SERVER_URL生成）</div>
  <div>服务器地址、API Key状态</div>
</div>

<hr>

<!-- 第二层：API Key 登录面板 -->
<div id="login-section">
  <input type="password" placeholder="请输入API Key">
  <button onclick="verifyApiKey()">验证</button>
</div>

<!-- 第三层：管理功能（登录后可见） -->
<div id="admin-section" style="display:none">
  <div class="tabs">...</div>
  <div class="content">...</div>
</div>
```

**前端JS新增逻辑**：
- 页面加载时自动从 `sessionStorage` 读取已存储的 API Key
- 如果有则自动验证，无需用户重复输入
- 验证通过后隐藏登录面板，显示管理功能

#### 2. `backend/app/utils/qr_utils.py` — 新增（提取公共函数）

```python
"""二维码工具"""
import base64, io

def generate_qr_base64(data: str, box_size: int = 8, border: int = 2) -> str:
    """生成二维码图片的base64编码"""
    import qrcode
    img = qrcode.make(data, version=1, box_size=box_size, border=border)
    buffer = io.BytesIO()
    img.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")
```

#### 3. `backend/app/routers/system.py` — 修改

- 删除本文件内的 `_generate_qr_base64()` 函数
- 改为从 `app.utils.qr_utils` import 共享函数
- `/setup` 路由改为 302 重定向到 `/admin`

```python
@router.get("/setup", response_class=HTMLResponse)
def setup_page():
    """已合并到管理后台，跳转到 /admin"""
    from fastapi.responses import RedirectResponse
    return RedirectResponse(url="/admin")
```

#### 4. `docker-deploy/.env.docker.example` — 更新备注

- 添加注释说明：扫码配置已合入管理后台，无需单独访问 `/setup`

### 不涉及的文件

| 文件 | 原因 |
|------|------|
| `config.py` | 环境变量不变，`SERVER_URL`/`API_KEY` 继续使用 |
| `auth.py` | `/admin` 已跳过中间件，无需修改 |
| `main.py` | 路由注册不变，`admin.router` 已在 |
| 所有app端代码 | PDA通过扫码获取地址，不影响App逻辑 |

## 验证步骤

1. **不登录访问 /admin**：
   - 应显示扫码配置二维码和API Key登录面板
   - 管理标签页不可见

2. **输入正确 API Key 登录**：
   - 扫码配置区保持可见
   - 登录面板隐藏，管理标签页出现
   - 所有功能正常（用户/拣货区/快麦/系统/图片）

3. **输入错误 API Key**：
   - 显示错误提示
   - 管理标签页不可见

4. **已登录后刷新页面**：
   - sessionStorage 中有 API Key，自动认证
   - 直接显示完整页面

5. **访问 /setup**：
   - 302 跳转到 /admin

6. **未配置 SERVER_URL 时**：
   - 扫码配置区显示配置提示，无二维码
