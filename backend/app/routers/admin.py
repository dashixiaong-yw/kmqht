"""管理后台路由 - Web管理页面"""

import json
import logging
import os
import re
import shutil
from html import escape
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import HTMLResponse

from app.auth import check_permission
from app.config import API_KEY, APK_DIR, APK_VERSION_FILE, SERVER_URL, kuaimai_creds
from app.database import get_db
from app.utils.qr_utils import generate_qr_base64
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(tags=["管理后台"])


@router.get("/admin", response_class=HTMLResponse)
def admin_page(request: Request) -> HTMLResponse:
    """管理后台页面"""
    # 优先用环境变量 SERVER_URL（兼容反向代理），否则从请求 Host 自动获取
    base_url = escape(SERVER_URL if SERVER_URL else str(request.base_url).rstrip("/"))
    return HTMLResponse(content=_build_admin_html(base_url))


@router.post("/api/app-version/upload")
def upload_app_version(
    file: UploadFile = File(...),
    latestVersion: str = Form(...),
    updateNotes: str = Form(""),
    forceUpdate: bool = Form(False),
    user: dict = Depends(check_permission("settings")),
) -> dict:
    """上传新版本 APK（暂存，删除旧文件）"""
    if not latestVersion.strip():
        raise HTTPException(status_code=400, detail="版本号不能为空")
    if not re.match(r'^\d+\.\d+$', latestVersion.strip()):
        raise HTTPException(status_code=400, detail="版本号格式错误，仅支持主版本.次版本（如 1.22）")
    if not file.filename.endswith(".apk"):
        raise HTTPException(status_code=400, detail="仅支持 .apk 文件")
    mime = file.content_type or ""
    if mime and "octet-stream" not in mime and "java-archive" not in mime and "vnd.android" not in mime:
        raise HTTPException(status_code=400, detail="文件类型不合法")
    os.makedirs(APK_DIR, exist_ok=True)
    # 先读取新文件到内存，再处理旧文件（防止读取失败导致旧文件丢失）
    try:
        content = file.file.read()
        if len(content) > 100 * 1024 * 1024:
            raise HTTPException(status_code=400, detail="APK 文件过大（最大100MB）")
    except Exception as e:
        logger.error(f"读取APK文件失败: {e}")
        raise HTTPException(status_code=400, detail="读取APK文件失败")
    # 删除旧 APK 文件
    if os.path.exists(APK_DIR):
        for old_file in os.listdir(APK_DIR):
            if old_file.endswith(".apk"):
                os.remove(os.path.join(APK_DIR, old_file))
    # 保存新 APK
    apk_filename = f"快麦取货通-{latestVersion}.apk"
    apk_path = os.path.join(APK_DIR, apk_filename)
    try:
        with open(apk_path, "wb") as f:
            f.write(content)
    except Exception as e:
        logger.error(f"保存APK文件失败: {e}")
        raise HTTPException(status_code=500, detail="保存APK文件失败")
    # 更新版本信息 JSON
    info = _load_version_info()
    info["currentVersion"] = latestVersion
    info["apkFileName"] = apk_filename
    info["updateNotes"] = updateNotes
    info["forceUpdate"] = forceUpdate
    if "publishedAt" in info:
        del info["publishedAt"]
    _save_version_info(info)
    logger.info(f"用户 {user.get('username', '?')} 上传了新版本 {latestVersion}")
    return {
        "success": True,
        "message": "上传成功，点击分发后所有PDA将收到更新",
        "latestVersion": latestVersion,
        "apkFileName": apk_filename,
        "updateNotes": updateNotes,
        "forceUpdate": forceUpdate,
        "apkSize": len(content),
        "publishedAt": "",
    }


@router.post("/api/app-version/publish")
def publish_app_version(
    user: dict = Depends(check_permission("settings")),
) -> dict:
    """分发当前暂存的版本（正式发布）"""
    info = _load_version_info()
    if not info.get("currentVersion"):
        raise HTTPException(status_code=400, detail="没有待分发的版本，请先上传")
    info["publishedAt"] = format_beijing(beijing_now())
    _save_version_info(info)
    logger.info(f"用户 {user.get('username', '?')} 分发了版本 {info.get('currentVersion')}")
    return {"success": True, "message": "分发成功，所有PDA下次启动将自动更新"}


def _load_version_info() -> dict:
    """读取版本信息 JSON"""
    try:
        with open(APK_VERSION_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def _save_version_info(info: dict) -> None:
    """写入版本信息 JSON（原子写入）"""
    tmp_path = APK_VERSION_FILE + ".tmp"
    with open(tmp_path, "w", encoding="utf-8") as f:
        json.dump(info, f, ensure_ascii=False, indent=2)
    os.replace(tmp_path, APK_VERSION_FILE)


def _build_admin_html(base_url: str) -> str:
    """构建管理后台HTML页面"""
    # 生成扫码配置二维码（公开区域，无需API Key）
    server_url = base_url
    qr_html = ""
    if server_url:
        qr_params: dict[str, str] = {"server": server_url}
        if API_KEY:
            qr_params["apikey"] = API_KEY
        qr_content = f"kuaimai://setup?{urlencode(qr_params)}"
        qr_base64 = generate_qr_base64(qr_content)
        qr_html = f'<img src="data:image/png;base64,{qr_base64}" style="width:160px;height:160px" />'
    else:
        qr_html = '<p style="color:#dc2626">无法获取服务器地址</p>'

    api_key_status = "已配置" if API_KEY else '<span style="color:#dc2626">未配置</span>'

    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>快麦取货通 - 管理后台</title>
<style>
* {{ margin:0; padding:0; box-sizing:border-box; }}
body {{ font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans SC",sans-serif;
        background:#f0f2f5; color:#333; }}
.header {{ background:linear-gradient(135deg,#2563eb,#1d4ed8); color:#fff; padding:16px 24px;
           display:flex; align-items:center; justify-content:space-between; }}
.header h1 {{ font-size:20px; font-weight:600; }}
.header .status {{ font-size:13px; opacity:0.9; }}
.container {{ max-width:1200px; margin:0 auto; padding:20px; }}
.tabs {{ display:flex; gap:0; background:#fff; border-radius:12px 12px 0 0;
         overflow:hidden; border-bottom:2px solid #e5e7eb; }}
.tab {{ padding:12px 20px; cursor:pointer; font-size:14px; font-weight:500;
        border:none; background:none; color:#666; transition:all 0.2s;
        border-bottom:3px solid transparent; }}
.tab:hover {{ background:#f0f7ff; color:#2563eb; }}
.tab.active {{ color:#2563eb; border-bottom-color:#2563eb; background:#f0f7ff; }}
.content {{ background:#fff; border-radius:0 0 12px 12px; padding:24px; min-height:400px; }}
.panel {{ display:none; }}
.panel.active {{ display:block; }}
.card {{ background:#f8fafc; border:1px solid #e5e7eb; border-radius:8px; padding:16px;
         margin-bottom:16px; }}
.card h3 {{ font-size:15px; color:#1d4ed8; margin-bottom:8px; }}
.stat-grid {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:16px; }}
.stat-item {{ background:linear-gradient(135deg,#eff6ff,#dbeafe); border-radius:8px;
              padding:16px; text-align:center; }}
.stat-item .value {{ font-size:28px; font-weight:700; color:#1d4ed8; }}
.stat-item .label {{ font-size:13px; color:#666; margin-top:4px; }}
table {{ width:100%; border-collapse:collapse; }}
th,td {{ padding:10px 12px; text-align:left; border-bottom:1px solid #e5e7eb; font-size:14px; }}
th {{ background:#f8fafc; font-weight:600; color:#555; }}
tr:hover {{ background:#f0f7ff; }}
.btn {{ padding:6px 14px; border:none; border-radius:6px; cursor:pointer;
        font-size:13px; font-weight:500; transition:all 0.2s; }}
.btn-primary {{ background:#2563eb; color:#fff; }}
.btn-primary:hover {{ background:#1d4ed8; }}
.btn-danger {{ background:#fee2e2; color:#dc2626; }}
.btn-danger:hover {{ background:#fecaca; }}
.btn-success {{ background:#dcfce7; color:#15803d; }}
.btn-success:hover {{ background:#bbf7d0; }}
.btn-warning {{ background:#fef3c7; color:#92400e; }}
.btn-warning:hover {{ background:#fde68a; }}
.btn-sm {{ padding:4px 10px; font-size:12px; }}
input,select {{ padding:8px 12px; border:1px solid #d1d5db; border-radius:6px;
               font-size:14px; outline:none; transition:border 0.2s; }}
input:focus,select:focus {{ border-color:#2563eb; }}
.form-group {{ margin-bottom:12px; }}
.form-group label {{ display:block; font-size:13px; font-weight:500; color:#555;
                     margin-bottom:4px; }}
.badge {{ display:inline-block; padding:2px 8px; border-radius:12px; font-size:12px; font-weight:500; }}
.badge-green {{ background:#dcfce7; color:#15803d; }}
.badge-red {{ background:#fee2e2; color:#dc2626; }}
.badge-yellow {{ background:#fef3c7; color:#92400e; }}
.badge-blue {{ background:#dbeafe; color:#1d4ed8; }}
.modal-overlay {{ display:none; position:fixed; top:0; left:0; width:100%; height:100%;
                   background:rgba(0,0,0,0.5); z-index:1000; justify-content:center;
                   align-items:center; }}
.modal-overlay.show {{ display:flex; }}
.modal {{ background:#fff; border-radius:12px; padding:24px; width:90%; max-width:480px;
          max-height:80vh; overflow-y:auto; }}
.modal h3 {{ margin-bottom:16px; font-size:16px; }}
.empty {{ text-align:center; padding:40px; color:#999; font-size:14px; }}
.qr-box {{ display:inline-block; background:#fff; padding:12px; border-radius:8px;
            border:1px solid #e5e7eb; }}
.checkbox-group {{ display:flex; flex-wrap:wrap; gap:8px; }}
.checkbox-item {{ display:flex; align-items:center; gap:4px; font-size:13px; }}
.checkbox-item input {{ width:auto; }}
</style>
</head>
<body>
<div class="header">
  <h1>快麦取货通 - 管理后台</h1>
  <div class="status" id="connStatus">未连接</div>
</div>
<div class="container">
  <!-- ====== API Key 登录区域 ====== -->
  <div id="loginSection" style="display:block">
    <div style="max-width:360px;margin:0 auto 24px;text-align:center">
      <h2 style="margin-bottom:20px;color:#2563eb">管理后台登录</h2>
      <input type="password" id="apiKeyInput" placeholder="请输入API Key"
             style="width:100%;margin-bottom:12px;padding:10px 12px;font-size:15px" />
      <button class="btn btn-primary" style="width:100%;padding:10px;font-size:15px" onclick="doLogin()">
        验证并登录
      </button>
      <p id="loginError" style="color:#dc2626;margin-top:8px;font-size:13px"></p>
    </div>
  </div>

  <!-- ====== 管理功能区域（登录后可见） ====== -->
  <div id="adminSection" style="display:none">
    <div class="tabs" id="tabs">
      <button class="tab active" data-tab="dashboard">仪表盘</button>
      <button class="tab" data-tab="users">用户管理</button>
      <button class="tab" data-tab="areas">拣货区管理</button>
      <button class="tab" data-tab="kuaimai">快麦配置</button>
      <button class="tab" data-tab="system">系统配置</button>
      <button class="tab" data-tab="images">图片查看</button>
      <button class="tab" data-tab="apk">APK管理</button>
    </div>
    <div class="content">
      <!-- 仪表盘 -->
      <div id="panel-dashboard" class="panel active">
        <div class="stat-grid" id="statsGrid"></div>
        <div class="card" style="margin-top:20px">
          <h3>PDA扫码配置</h3>
          <div style="display:flex;align-items:center;gap:20px;flex-wrap:wrap">
            <div class="qr-box">{qr_html}</div>
            <div>
              <p style="font-size:13px;color:#666;margin-bottom:4px">服务器地址</p>
              <p style="font-size:15px;font-weight:600;color:#1d4ed8;word-break:break-all">
                {server_url or "未配置"}
              </p>
              <p style="font-size:12px;color:#999;margin-top:8px">
                PDA首次启动App → 引导页 → 扫码配置 → 扫描上方二维码
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- 用户管理 -->
      <div id="panel-users" class="panel">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
          <h3>用户列表</h3>
          <button class="btn btn-primary" onclick="showAddUser()">添加用户</button>
        </div>
        <table>
          <thead><tr><th>ID</th><th>用户名</th><th>权限</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody id="userTableBody"></tbody>
        </table>
      </div>

      <!-- 拣货区管理 -->
      <div id="panel-areas" class="panel">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
          <h3>拣货区列表</h3>
          <div style="display:flex;gap:8px">
            <input id="newAreaName" placeholder="新拣货区名称" style="width:160px" />
            <button class="btn btn-primary" onclick="addArea()">添加</button>
          </div>
        </div>
        <table>
          <thead><tr><th>ID</th><th>名称</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody id="areaTableBody"></tbody>
        </table>
      </div>

      <!-- 快麦配置 -->
      <div id="panel-kuaimai" class="panel">
        <div class="card">
          <h3>凭证状态</h3>
          <div id="kuaimaiStatus"></div>
        </div>
        <div class="card">
          <h3>操作</h3>
          <button class="btn btn-success" onclick="refreshKuaimaiSession()" id="btnRefreshSession">
            刷新Session
          </button>
        </div>
        <div class="card">
          <h3>手动更新凭证</h3>
          <div class="form-group"><label>App Key</label><input id="kmAppKey" style="width:100%" /></div>
          <div class="form-group"><label>App Secret</label><input id="kmAppSecret" style="width:100%" /></div>
          <div class="form-group"><label>Session</label><input id="kmSession" style="width:100%" /></div>
          <div class="form-group"><label>Refresh Token</label><input id="kmRefreshToken" style="width:100%" /></div>
          <button class="btn btn-primary" onclick="updateKuaimaiCreds()">保存凭证</button>
        </div>
      </div>

      <!-- 系统配置 -->
      <div id="panel-system" class="panel">
        <div class="card">
          <h3>API Key</h3>
          <p style="font-size:14px;color:#666">当前API Key: <code id="currentApiKey" style="background:#f0f0f0;padding:2px 6px;border-radius:4px">****</code></p>
        </div>
        <div class="card">
          <h3>服务器地址</h3>
          <p style="font-size:14px;color:#666">当前地址: <code id="currentServerUrl" style="background:#f0f0f0;padding:2px 6px;border-radius:4px">{server_url or "未配置"}</code></p>
          <p style="font-size:12px;color:#999;margin-top:4px">修改服务器地址请编辑 .env 文件中的 SERVER_URL 并重启服务</p>
        </div>
      </div>

      <!-- 图片查看 -->
      <div id="panel-images" class="panel">
        <div style="display:flex;gap:8px;margin-bottom:16px">
          <input id="imageSkuInput" placeholder="输入SKU编码" style="width:200px" />
          <button class="btn btn-primary" onclick="searchImages()">搜索</button>
        </div>
        <div id="imageResults"></div>
      </div>

      <!-- APK 管理 -->
      <div id="panel-apk" class="panel">
        <div id="apkStatus"></div>
        <div class="card" id="apkUploadSection">
          <h3>上传新版本</h3>
          <div class="form-group"><label>APK 文件</label><input type="file" id="apkFileInput" accept=".apk" /></div>
          <div class="form-group"><label>版本号</label><input id="apkVersionInput" placeholder="例如 1.19" style="width:100%" /></div>
          <div class="form-group"><label>更新说明</label><textarea id="apkNotesInput" rows="4" style="width:100%" placeholder="每行一条更新说明"></textarea></div>
          <div class="form-group">
            <label class="checkbox-item"><input type="checkbox" id="apkForceUpdate" /> 强制更新（用户无法取消）</label>
          </div>
          <button class="btn btn-primary" onclick="uploadApk()" id="btnUploadApk">上传</button>
        </div>
      </div>
    </div>
  </div>
</div>

<!-- 用户编辑弹窗 -->
<div class="modal-overlay" id="userModal">
  <div class="modal">
    <h3 id="userModalTitle">添加用户</h3>
    <input type="hidden" id="editUserId" />
    <div class="form-group"><label>用户名</label><input id="editUsername" style="width:100%" /></div>
    <div class="form-group"><label>密码</label><input id="editPassword" type="password" style="width:100%" /></div>
    <div class="form-group" id="activeGroup" style="display:none">
      <label><input type="checkbox" id="editActive" checked /> 启用账户</label>
    </div>
    <div class="form-group">
      <label>权限分配</label>
      <div class="checkbox-group">
        <label class="checkbox-item"><input type="checkbox" value="settings" /> 设置管理</label>
        <label class="checkbox-item"><input type="checkbox" value="update_supplier" /> 修改供应商</label>
        <label class="checkbox-item"><input type="checkbox" value="update_remark" /> 修改备注</label>
        <label class="checkbox-item"><input type="checkbox" value="manage_area_image" /> 库区图管理</label>
        <label class="checkbox-item"><input type="checkbox" value="manage_box_image" /> 箱规图管理</label>
      </div>
    </div>
    <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">
      <button class="btn" onclick="closeUserModal()" style="background:#f0f0f0">取消</button>
      <button class="btn btn-primary" onclick="saveUser()">保存</button>
    </div>
  </div>
</div>

<!-- 确认弹窗 -->
<div class="modal-overlay" id="confirmModal">
  <div class="modal">
    <h3>确认操作</h3>
    <p id="confirmText" style="margin:12px 0"></p>
    <div style="display:flex;gap:8px;justify-content:flex-end">
      <button class="btn" onclick="closeConfirm()" style="background:#f0f0f0">取消</button>
      <button class="btn btn-danger" id="confirmBtn" onclick="">确定</button>
    </div>
  </div>
</div>

<script>
// ========== 全局状态 ==========
let API_BASE = window.location.origin;
let apiKey = sessionStorage.getItem('adminApiKey') || '';
let loggedIn = false;

// HTML转义函数（XSS防护）
function escapeHtml(str) {{
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}}

// ========== 登录 ==========
async function doLogin() {{
  const key = document.getElementById('apiKeyInput').value.trim();
  if (!key) {{ document.getElementById('loginError').textContent = '请输入API Key'; return; }}
  try {{
    const r = await fetch(API_BASE + '/health', {{ headers: {{ 'X-API-Key': key }} }});
    if (r.ok) {{
      apiKey = key;
      sessionStorage.setItem('adminApiKey', key);
      loggedIn = true;
      document.getElementById('loginError').textContent = '';
      showMainPanel();
    }} else {{
      document.getElementById('loginError').textContent = 'API Key无效';
    }}
  }} catch(e) {{
    document.getElementById('loginError').textContent = '连接失败: ' + e.message;
  }}
}}

function showMainPanel() {{
  document.getElementById('loginSection').style.display = 'none';
  document.getElementById('adminSection').style.display = 'block';
  document.getElementById('connStatus').textContent = '已连接';
  loadDashboard();
}}

// ========== API请求封装 ==========
async function api(path, opts = {{}}) {{
  const headers = {{ 'X-API-Key': apiKey, ...((opts.headers || {{}})) }};
  if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {{
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.body);
  }}
  const r = await fetch(API_BASE + path, {{ ...opts, headers }});
  if (!r.ok) {{
    const data = await r.json().catch(() => ({{}}));
    throw new Error(data.detail || data.message || '请求失败');
  }}
  return r.json();
}}

// ========== 标签页切换 ==========
document.querySelectorAll('.tab').forEach(tab => {{
  tab.onclick = () => {{
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    tab.classList.add('active');
    const name = tab.dataset.tab;
    document.getElementById('panel-' + name).classList.add('active');
    if (name === 'dashboard') loadDashboard();
    else if (name === 'users') loadUsers();
    else if (name === 'areas') loadAreas();
    else if (name === 'kuaimai') loadKuaimai();
    else if (name === 'system') loadSystem();
    else if (name === 'apk') loadApk();
  }};
}});

// ========== 仪表盘 ==========
async function loadDashboard() {{
  try {{
    const health = await api('/health');
    const users = await api('/api/users');
    const areas = await api('/api/areas');
    const userCount = (users.data || []).length;
    const areaCount = (areas.data || []).length;
    const orderCount = health.totalOrders || 0;
    document.getElementById('statsGrid').innerHTML = `
      <div class="stat-item"><div class="value">${{userCount}}</div><div class="label">用户数</div></div>
      <div class="stat-item"><div class="value">${{areaCount}}</div><div class="label">拣货区</div></div>
      <div class="stat-item"><div class="value">${{orderCount}}</div><div class="label">取货单</div></div>
      <div class="stat-item"><div class="value">${{health.status === 'ok' ? '正常' : '异常'}}</div><div class="label">系统状态</div></div>
    `;
  }} catch(e) {{
    document.getElementById('statsGrid').innerHTML = '<div class="empty">加载失败: ' + e.message + '</div>';
  }}
}}

// ========== 用户管理 ==========
const PERM_LABELS = {{
  'settings': '设置管理', 'update_supplier': '修改供应商', 'update_remark': '修改备注',
  'manage_area_image': '库区图管理', 'manage_box_image': '箱规图管理'
}};

async function loadUsers() {{
  try {{
    const r = await api('/api/users');
    const users = r.data || [];
    const tbody = document.getElementById('userTableBody');
    if (users.length === 0) {{
      tbody.innerHTML = '<tr><td colspan="6" class="empty">暂无用户</td></tr>';
      return;
    }}
    tbody.innerHTML = users.map(u => `<tr>
      <td>${{u.id}}</td>
      <td>${{escapeHtml(u.username)}}</td>
      <td>${{(u.permissions || []).map(p => escapeHtml(PERM_LABELS[p] || p)).join('、') || '无'}}</td>
      <td><span class="badge ${{u.isActive ? 'badge-green' : 'badge-red'}}">${{u.isActive ? '启用' : '禁用'}}</span></td>
      <td>${{u.createdAt || '-'}}</td>
      <td>
        <button class="btn btn-primary btn-sm" onclick='editUser("${{encodeURIComponent(JSON.stringify(u)).replaceAll("'", "&#39;")}}")'>编辑</button>
        <button class="btn btn-danger btn-sm" onclick='confirmDeleteUser(${{u.id}},"${{escapeHtml(u.username)}}")'>删除</button>
      </td>
    </tr>`).join('');
  }} catch(e) {{
    document.getElementById('userTableBody').innerHTML = '<tr><td colspan="6" class="empty">加载失败</td></tr>';
  }}
}}

function showAddUser() {{
  document.getElementById('userModalTitle').textContent = '添加用户';
  document.getElementById('editUserId').value = '';
  document.getElementById('editUsername').value = '';
  document.getElementById('editUsername').disabled = false;
  document.getElementById('editPassword').value = '';
  document.getElementById('editPassword').placeholder = '密码';
  document.getElementById('activeGroup').style.display = 'none';
  document.querySelectorAll('#userModal .checkbox-group input').forEach(c => c.checked = false);
  document.getElementById('userModal').classList.add('show');
}}

function editUser(encoded) {{
  const user = JSON.parse(decodeURIComponent(encoded));
  document.getElementById('userModalTitle').textContent = '编辑用户';
  document.getElementById('editUserId').value = user.id;
  document.getElementById('editUsername').value = user.username;
  document.getElementById('editUsername').disabled = true;
  document.getElementById('editPassword').value = '';
  document.getElementById('editPassword').placeholder = '新密码（留空不修改）';
  document.getElementById('activeGroup').style.display = 'block';
  document.getElementById('editActive').checked = user.isActive;
  document.querySelectorAll('#userModal .checkbox-group input').forEach(c => {{
    c.checked = (user.permissions || []).includes(c.value);
  }});
  document.getElementById('userModal').classList.add('show');
}}

function closeUserModal() {{
  document.getElementById('userModal').classList.remove('show');
}}

async function saveUser() {{
  const userId = document.getElementById('editUserId').value;
  const username = document.getElementById('editUsername').value.trim();
  const password = document.getElementById('editPassword').value;
  const isActive = document.getElementById('editActive').checked;
  const permissions = [];
  document.querySelectorAll('#userModal .checkbox-group input:checked').forEach(c => permissions.push(c.value));

  try {{
    if (userId) {{
      // 编辑
      const body = {{ permissions }};
      if (password) body.password = password;
      body.isActive = isActive;
      await api('/api/users/' + userId, {{ method: 'PUT', body }});
    }} else {{
      // 新增
      if (!username || !password) {{ alert('用户名和密码不能为空'); return; }}
      await api('/api/users', {{ method: 'POST', body: {{ username, password, permissions }} }});
    }}
    closeUserModal();
    loadUsers();
  }} catch(e) {{
    alert('保存失败: ' + e.message);
  }}
}}

function confirmDeleteUser(id, name) {{
  document.getElementById('confirmText').textContent = '确定要删除用户 ' + name + ' 吗？';
  document.getElementById('confirmBtn').onclick = async () => {{
    try {{
      await api('/api/users/' + id, {{ method: 'DELETE' }});
      closeConfirm();
      loadUsers();
    }} catch(e) {{ alert('删除失败: ' + e.message); }}
  }};
  document.getElementById('confirmModal').classList.add('show');
}}

// ========== 拣货区管理 ==========
async function loadAreas() {{
  try {{
    const r = await api('/api/areas');
    const areas = r.data || [];
    const tbody = document.getElementById('areaTableBody');
    if (areas.length === 0) {{
      tbody.innerHTML = '<tr><td colspan="4" class="empty">暂无拣货区</td></tr>';
      return;
    }}
    tbody.innerHTML = areas.map(a => `<tr>
      <td>${{a.id}}</td><td>${{escapeHtml(a.name)}}</td><td>${{a.createdAt || '-'}}</td>
      <td><button class="btn btn-danger btn-sm" onclick="confirmDeleteArea(${{a.id}},'${{escapeHtml(a.name)}}')">删除</button></td>
    </tr>`).join('');
  }} catch(e) {{
    document.getElementById('areaTableBody').innerHTML = '<tr><td colspan="4" class="empty">加载失败</td></tr>';
  }}
}}

async function addArea() {{
  const name = document.getElementById('newAreaName').value.trim();
  if (!name) {{ alert('请输入拣货区名称'); return; }}
  try {{
    await api('/api/areas', {{ method: 'POST', body: {{ name }} }});
    document.getElementById('newAreaName').value = '';
    loadAreas();
  }} catch(e) {{ alert('添加失败: ' + e.message); }}
}}

function confirmDeleteArea(id, name) {{
  document.getElementById('confirmText').textContent = '确定要删除拣货区 ' + name + ' 吗？';
  document.getElementById('confirmBtn').onclick = async () => {{
    try {{
      await api('/api/areas/' + id, {{ method: 'DELETE' }});
      closeConfirm();
      loadAreas();
    }} catch(e) {{ alert('删除失败: ' + e.message); }}
  }};
  document.getElementById('confirmModal').classList.add('show');
}}

// ========== 快麦配置 ==========
async function loadKuaimai() {{
  try {{
    const r = await api('/api/kuaimai/session-status');
    const s = r.data || r;
    const isValid = s.is_valid || s.isValid;
    const daysLeft = s.days_left ?? s.daysLeft;
    let statusBadge = isValid ? '<span class="badge badge-green">正常</span>' : '<span class="badge badge-red">已过期</span>';
    if (isValid && daysLeft !== null && daysLeft <= 5) statusBadge = '<span class="badge badge-yellow">即将过期</span>';
    document.getElementById('kuaimaiStatus').innerHTML = `
      <p>Session状态: ${{statusBadge}} ${{daysLeft !== null ? '（剩余' + daysLeft + '天）' : ''}}</p>
      <p style="font-size:13px;color:#666;margin-top:4px">最后更新: ${{s.updated_at || s.updatedAt || '-'}}</p>
      <p style="font-size:13px;color:#666">Refresh Token: ${{s.has_refresh_token || s.hasRefreshToken ? '已配置' : '未配置'}}</p>
    `;
  }} catch(e) {{
    document.getElementById('kuaimaiStatus').innerHTML = '<p style="color:#dc2626">加载失败: ' + e.message + '</p>';
  }}
}}

async function refreshKuaimaiSession() {{
  const btn = document.getElementById('btnRefreshSession');
  btn.disabled = true; btn.textContent = '刷新中...';
  try {{
    const r = await api('/api/kuaimai/refresh-session', {{ method: 'POST' }});
    alert(r.success ? '刷新成功' : '刷新失败: ' + (r.message || ''));
    loadKuaimai();
  }} catch(e) {{ alert('刷新失败: ' + e.message); }}
  finally {{ btn.disabled = false; btn.textContent = '刷新Session'; }}
}}

async function updateKuaimaiCreds() {{
  const appKey = document.getElementById('kmAppKey').value.trim();
  const appSecret = document.getElementById('kmAppSecret').value.trim();
  const session = document.getElementById('kmSession').value.trim();
  const refreshToken = document.getElementById('kmRefreshToken').value.trim();
  if (!appKey || !appSecret || !session) {{ alert('App Key、App Secret和Session为必填'); return; }}
  try {{
    const r = await api('/api/kuaimai/update-credentials', {{
      method: 'POST',
      body: {{ app_key: appKey, app_secret: appSecret, session, refresh_token: refreshToken }}
    }});
    alert(r.success ? '凭证更新成功' : '更新失败: ' + (r.message || ''));
    loadKuaimai();
  }} catch(e) {{ alert('更新失败: ' + e.message); }}
}}

// ========== 系统配置 ==========
function loadSystem() {{
  document.getElementById('currentApiKey').textContent = apiKey ? apiKey.substring(0, 4) + '****' : '未配置';
  document.getElementById('currentServerUrl').textContent = '{server_url or "未获取到服务器地址"}';
}}

// ========== 图片查看 ==========
async function searchImages() {{
  const sku = document.getElementById('imageSkuInput').value.trim();
  if (!sku) {{ alert('请输入SKU编码'); return; }}
  try {{
    const r = await api('/api/images/' + encodeURIComponent(sku));
    const images = r.data || [];
    const container = document.getElementById('imageResults');
    if (images.length === 0) {{
      container.innerHTML = '<div class="empty">未找到图片</div>';
      return;
    }}
    container.innerHTML = images.map(img => `
      <div style="display:inline-block;margin:8px;text-align:center">
        <img src="${{API_BASE}}/images/${{img.filePath}}"
             style="max-width:200px;max-height:200px;border-radius:8px;border:1px solid #e5e7eb"
             onerror="this.src='';this.alt='图片加载失败'" />
        <p style="font-size:12px;color:#666;margin-top:4px">${{img.imageType === 'area' ? '库区图' : '箱规图'}}</p>
      </div>
    `).join('');
  }} catch(e) {{
    document.getElementById('imageResults').innerHTML = '<div class="empty">查询失败: ' + e.message + '</div>';
  }}
}}

// ========== APK 管理 ==========
async function loadApk() {{
  try {{
    const r = await api('/api/app-version');
    const container = document.getElementById('apkStatus');
    const uploadSection = document.getElementById('apkUploadSection');
    if (r.latestVersion) {{
      const forceLabel = r.forceUpdate ? '<span class="badge badge-red">强制更新</span>' : '<span class="badge badge-green">可选更新</span>';
      const sizeStr = r.apkSize ? (r.apkSize / 1024 / 1024).toFixed(1) + ' MB' : '未知';
      const publishedInfo = r.publishedAt ? '<p style="font-size:13px;color:#666;margin-top:4px">已分发时间: ' + r.publishedAt + '</p>' : '<p style="font-size:13px;color:#dc2626;margin-top:4px">尚未分发</p>';
      const publishBtn = r.publishedAt ? '' : '<button class="btn btn-success" onclick="publishApk()" style="margin-top:8px">立即分发</button>';
      container.innerHTML = `
        <div class="card">
          <h3>当前版本信息</h3>
          <p>版本号: <strong>${{escapeHtml(r.latestVersion)}}</strong> ${{forceLabel}}</p>
          <p>APK 大小: ${{sizeStr}}</p>
          <p>更新说明:</p>
          <pre style="background:#f8fafc;padding:8px;border-radius:4px;font-size:13px;white-space:pre-wrap">${{escapeHtml(r.updateNotes || '无')}}</pre>
          ${{publishedInfo}}
          ${{publishBtn}}
          <div id="apkQrCode"></div>
        </div>
      `;
      uploadSection.style.display = 'block';
      // 已分发时加载下载二维码
      if (r.publishedAt && r.downloadUrl) {{
        api('/api/app-version/qrcode').then(qrResp => {{
          if (qrResp.qrcode) {{
            document.getElementById('apkQrCode').innerHTML = '<img src="data:image/png;base64,' + qrResp.qrcode + '" style="width:160px;height:160px;margin-top:12px" /><p style="font-size:12px;color:#666;margin-top:4px">PDA 扫码下载 APK</p>';
          }}
        }}).catch(e => console.warn('加载下载二维码失败', e));
      }}
    }} else {{
      container.innerHTML = '<div class="empty">暂无版本信息</div>';
      uploadSection.style.display = 'block';
    }}
  }} catch(e) {{
    document.getElementById('apkStatus').innerHTML = '<div class="empty">加载失败: ' + e.message + '</div>';
  }}
}}

async function uploadApk() {{
  const fileInput = document.getElementById('apkFileInput');
  const version = document.getElementById('apkVersionInput').value.trim();
  const notes = document.getElementById('apkNotesInput').value.trim();
  const forceUpdate = document.getElementById('apkForceUpdate').checked;
  if (!fileInput.files || !fileInput.files[0]) {{ alert('请选择 APK 文件'); return; }}
  if (!version) {{ alert('请输入版本号'); return; }}
  const formData = new FormData();
  formData.append('file', fileInput.files[0]);
  formData.append('latestVersion', version);
  formData.append('updateNotes', notes);
  formData.append('forceUpdate', forceUpdate ? 'true' : 'false');
  const btn = document.getElementById('btnUploadApk');
  btn.disabled = true; btn.textContent = '上传中...';
  try {{
    const r = await api('/api/app-version/upload', {{ method: 'POST', body: formData, headers: {{}} }});
    alert(r.message || '上传成功');
    if (r.latestVersion) {{
      renderApkCard(r);
    }} else {{
      loadApk();
    }}
  }} catch(e) {{
    alert('上传失败: ' + e.message);
  }} finally {{
    btn.disabled = false; btn.textContent = '上传';
  }}
}}

function renderApkCard(r) {{
  const container = document.getElementById('apkStatus');
  const uploadSection = document.getElementById('apkUploadSection');
  const forceLabel = r.forceUpdate ? '<span class="badge badge-red">强制更新</span>' : '<span class="badge badge-green">可选更新</span>';
  const sizeStr = r.apkSize ? (r.apkSize / 1024 / 1024).toFixed(1) + ' MB' : '未知';
  const publishedInfo = '<p style="font-size:13px;color:#dc2626;margin-top:4px">尚未分发</p>';
  const publishBtn = '<button class="btn btn-success" onclick="publishApk()" style="margin-top:8px">立即分发</button>';
  container.innerHTML = `
    <div class="card">
      <h3>当前版本信息</h3>
      <p>版本号: <strong>${{escapeHtml(r.latestVersion)}}</strong> ${{forceLabel}}</p>
      <p>APK 大小: ${{sizeStr}}</p>
      <p>更新说明:</p>
      <pre style="background:#f8fafc;padding:8px;border-radius:4px;font-size:13px;white-space:pre-wrap">${{escapeHtml(r.updateNotes || '无')}}</pre>
      ${{publishedInfo}}
      ${{publishBtn}}
      <div id="apkQrCode"></div>
    </div>
  `;
  uploadSection.style.display = 'block';
}}

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

// ========== 通用 ==========
function closeConfirm() {{
  document.getElementById('confirmModal').classList.remove('show');
}}

// 自动登录：sessionStorage中有API Key则自动验证
if (apiKey) {{
  doLogin();
}}
</script>
</body>
</html>"""
