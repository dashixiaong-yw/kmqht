# APK 分发静默失败修复计划

## 根因

`publishApk()` 使用了原生 `confirm()` 对话框，而 **PDA 浏览器（或部分 Android 手机浏览器）不支持 `confirm()`，自动返回 `false`**：

```javascript
// admin.py L787 - 当前代码
if (!confirm('确定要分发当前版本吗？所有 PDA 下次启动将自动更新。')) return;
```

当 `confirm()` 不被支持时返回 `false` → `!false` = `true` → 函数立即 `return`，**不发请求、不弹窗、无任何反馈**，表现为"静默失败"。

## 已有方案

管理后台已存在自定义确认弹窗 `confirmModal`，`confirmDeleteUser()` 和 `confirmDeleteArea()` 都在使用它。`publishApk()` 应当与它们保持一致。

## 改动

仅修改 [admin.py](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py) 中的 `publishApk()` 函数：

```javascript
// 改前（L786-795）
async function publishApk() {{
  if (!confirm('确定要分发当前版本吗？所有 PDA 下次启动将自动更新。')) return;
  try {{
    const r = await api('/api/app-version/publish', {{ method: 'POST' }});
    alert(r.message || '分发成功');
    loadApk();
  }} catch(e) {{
    alert('分发失败: ' + e.message);
  }}
}}

// 改后
async function publishApk() {{
  document.getElementById('confirmBtn').onclick = async () => {{
    try {{
      const r = await api('/api/app-version/publish', {{ method: 'POST' }});
      alert(r.message || '分发成功');
      loadApk();
    }} catch(e) {{
      alert('分发失败: ' + e.message);
    }}
    closeConfirm();
  }};
  document.getElementById('confirmModal').classList.add('show');
}}
```

与已存在的 `confirmDeleteUser()` / `confirmDeleteArea()` 完全相同的模式。

## 验证

1. `python -c "from app.routers.admin import router"` 导入检查
2. 逻辑验证：确认 `confirmModal` HTML 和 `closeConfirm()` 函数已存在于模板中
