# 后端自动刷新快麦 Session 测试方案

## 现状分析

管理后台「快麦配置」已有功能：

| 已有功能 | 说明 |
|:---------|:-----|
| 凭证状态展示（绿色/黄色/红色） | 显示 isValid、daysLeft、updatedAt、hasRefreshToken |
| 刷新 Session 按钮 | 调 `POST /api/kuaimai/refresh-session` |
| 手动更新凭证表单 | 4 个输入框（appKey/appSecret/session/refreshToken） |

**缺少的**：没有专门验证完整自动刷新链路的诊断工具。现有"刷新Session"按钮只返回成功/失败，不展示详细诊断信息。

## 方案

在管理后台「快麦配置」面板新增一个**诊断测试区域**，包含：

1. **一键测试**按钮 → 执行完整的刷新链路
2. **测试结果展示** → 显示每一步的诊断信息

### 改动1：新增 API  `GET /api/kuaimai/diagnose-refresh`

**文件**：[system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py)

新增诊断接口，返回完整的刷新链路诊断信息：

```python
@router.get("/api/kuaimai/diagnose-refresh", response_model=KuaimaiDiagnoseResponse)
def diagnose_kuaimai_refresh(user: dict = Depends(check_permission("settings"))) -> KuaimaiDiagnoseResponse:
    """诊断快麦 session 自动刷新链路的全流程"""
    # 检查1: refresh_token 是否配置
    has_refresh = kuaimai_creds.has_refresh_token()
    
    # 检查2: 当前 session 状态
    days_left = kuaimai_creds.get_days_left()
    updated_at = kuaimai_creds.updated_at
    
    # 检查3: 执行一次实际的 refresh_session
    import asyncio
    from app.services.kuaimai_api import refresh_session
    refresh_success = asyncio.run(refresh_session())
    
    # 检查4: 刷新后的状态
    new_days_left = kuaimai_creds.get_days_left()
    new_updated_at = kuaimai_creds.updated_at
    
    return KuaimaiDiagnoseResponse(
        hasRefreshToken=has_refresh,
        updatedAt=updated_at,
        daysLeft=days_left,
        refreshCalled=True,
        refreshSuccess=refresh_success,
        updatedAtAfter=kuaimai_creds.updated_at,
        daysLeftAfter=kuaimai_creds.get_days_left()
    )
```

**新增模型**：[models.py](file:///d:/trea项目/快麦取货通/backend/app/models.py)

```python
class KuaimaiDiagnoseResponse(BaseModel):
    """快麦 session 自动刷新诊断响应"""
    success: bool = True
    message: str = "操作成功"
    hasRefreshToken: bool = False
    updatedAt: str = ""
    daysLeft: Optional[int] = None
    refreshCalled: bool = False
    refreshSuccess: bool = False
    updatedAtAfter: str = ""
    daysLeftAfter: Optional[int] = None
```

### 改动2：管理后台新增诊断区域

**文件**：[admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py)

在「快麦配置」面板的"操作"卡片下新增诊断区域 HTML：

```html
<div class="card" style="margin-top:12px">
  <h3>自动刷新诊断</h3>
  <div id="diagnoseResult" style="font-size:13px;margin-bottom:8px">
    点击下方按钮测试自动刷新链路
  </div>
  <button class="btn btn-warning" onclick="diagnoseKuaimaiRefresh()" id="btnDiagnose">
    执行自动刷新测试
  </button>
</div>
```

**新增 JS 函数**：

```javascript
async function diagnoseKuaimaiRefresh() {
  const btn = document.getElementById('btnDiagnose');
  const resultDiv = document.getElementById('diagnoseResult');
  btn.disabled = true; btn.textContent = '测试中...';
  resultDiv.innerHTML = '<span style="color:#666">正在执行自动刷新测试...</span>';
  
  try {
    const r = await api('/api/kuaimai/diagnose-refresh', { method: 'POST' });
    const d = r.data || r;
    const lines = [
      `<b>自动刷新诊断结果：</b>`,
      `• Refresh Token: ${d.hasRefreshToken ? '✅ 已配置' : '❌ 未配置'}`,
      d.hasRefreshToken ? '' : '  <span style="color:#dc2626">→ 自动刷新功能不可用，请在kuaimai.json中配置refresh_token</span>',
      `• 刷新前更新时间: ${d.updatedAt || '-'}`,
      `• 刷新前剩余天数: ${d.daysLeft !== null ? d.daysLeft + '天' : '-'}`,
      `• 刷新调用: ${d.refreshCalled ? '✅ 已调用' : '❌ 未调用'}`,
      `• 刷新结果: ${d.refreshSuccess ? '✅ 成功' : '❌ 失败'}`,
      d.refreshSuccess ? '' : '  <span style="color:#dc2626">→ 请检查refreshToken是否有效或快麦API是否可达</span>',
      `• 刷新后更新时间: ${d.updatedAtAfter || '-'}`,
      `• 刷新后剩余天数: ${d.daysLeftAfter !== null ? d.daysLeftAfter + '天' : '-'}`,
      d.refreshSuccess ? '<br><span style="color:#16a34a">✅ 自动刷新机制正常工作</span>' : ''
    ];
    resultDiv.innerHTML = lines.filter(l => l !== '').join('<br>') + 
      '<br><br><span style="color:#666">（后端每24小时自动执行一次同样的操作）</span>';
  } catch(e) {
    resultDiv.innerHTML = '<span style="color:#dc2626">诊断失败: ' + e.message + '</span>';
  } finally {
    btn.disabled = false; btn.textContent = '执行自动刷新测试';
  }
}
```

### 改动3：或者无需代码——直接使用 curl 手动测试

如果用户不想改代码，也可以部署后用一条 curl 命令测试：

```bash
# 登录管理后台 → F12 → Application → Cookies 获取 api_key
curl -X POST http://NAS_IP:8900/api/kuaimai/refresh-session \
  -H "X-API-Key: zxf199333" \
  -H "Content-Type: application/json"
```

但管理后台的 API 调用需要 `settings` 权限且走 API Key 认证较复杂。**建议用按钮方案。**

---

## 改动清单

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| models.py | 新增 `KuaimaiDiagnoseResponse` 模型 | ~8 行 |
| system.py | 新增 `GET /api/kuaimai/diagnose-refresh` 接口 | ~20 行 |
| admin.py | 快麦配置面板新增诊断区域 HTML + JS | ~30 行 |

## 验证

部署后打开管理后台 → 快麦配置 → 点击 "执行自动刷新测试"：

| 场景 | 预期结果 |
|:-----|:---------|
| refresh_token 已配置 + 快麦可达 | 全绿，显示刷新前/后对比 |
| refresh_token 已配置 + 网络不通 | 刷新失败提示，无报错 |
| refresh_token 未配置 | 显示红线提示"不可用" |

## 不涉及的

- 不修改 Android 端代码
- 不修改数据库
- 不修改 Dockerfile
