"""系统路由 - 健康检查、崩溃报告、版本信息、快麦会话管理、扫码配置页面"""

import base64
import io
import logging
import os
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import HTMLResponse
from starlette.requests import Request

from app.auth import get_current_user
from app.config import API_KEY, SERVER_URL, kuaimai_creds
from app.database import get_db
from app.models import (
    AppVersionResponse,
    BaseResponse,
    CrashReportRequest,
    HealthResponse,
    KuaimaiCredentialsRequest,
    KuaimaiRefreshResponse,
    KuaimaiSessionStatusResponse,
)
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(tags=["系统"])

# APK下载地址（从环境变量读取，默认为空）
APK_DOWNLOAD_URL: str = os.getenv("APK_DOWNLOAD_URL", "")
LATEST_VERSION: str = os.getenv("LATEST_VERSION", "1.0")


@router.get("/health", response_model=HealthResponse)
def health_check() -> HealthResponse:
    """健康检查"""
    db_status = "ok"
    try:
        db = get_db()
        cursor = db.cursor()
        cursor.execute("SELECT 1")
    except Exception as e:
        logger.error(f"数据库健康检查失败: {e}")
        db_status = "error"

    return HealthResponse(
        status="ok",
        database=db_status,
    )


@router.post("/api/crash-report", response_model=BaseResponse)
def crash_report(req: CrashReportRequest, user: dict = Depends(get_current_user)) -> BaseResponse:
    """接收客户端崩溃报告"""
    db = get_db()
    cursor = db.cursor()

    now = beijing_now()
    try:
        cursor.execute(
            """INSERT INTO crash_logs (app_version, device_model, error_message, stack_trace, created_at)
               VALUES (?, ?, ?, ?, ?)""",
            (req.appVersion, req.deviceModel, req.errorMessage, req.stackTrace, format_beijing(now))
        )
        db.commit()
        logger.info(f"收到崩溃报告: app={req.appVersion}, device={req.deviceModel}")
        return BaseResponse(message="崩溃报告已记录")
    except Exception as e:
        db.rollback()
        logger.error(f"保存崩溃报告失败: {e}")
        raise HTTPException(status_code=500, detail="保存崩溃报告失败，请稍后重试")


@router.get("/api/app-version", response_model=AppVersionResponse)
def get_app_version() -> AppVersionResponse:
    """获取最新应用版本及下载地址"""
    return AppVersionResponse(
        latestVersion=LATEST_VERSION,
        downloadUrl=APK_DOWNLOAD_URL,
    )


@router.get("/api/kuaimai/session-status", response_model=KuaimaiSessionStatusResponse)
def get_kuaimai_session_status(user: dict = Depends(check_permission("settings"))) -> KuaimaiSessionStatusResponse:
    """查询快麦session状态（剩余天数、是否有效等）"""
    days_left = kuaimai_creds.get_days_left()
    is_valid = kuaimai_creds.is_valid() and (days_left is None or days_left > 0)

    return KuaimaiSessionStatusResponse(
        isValid=is_valid,
        daysLeft=days_left,
        updatedAt=kuaimai_creds.updated_at,
        hasRefreshToken=kuaimai_creds.has_refresh_token(),
    )


@router.post("/api/kuaimai/refresh-session", response_model=KuaimaiRefreshResponse)
async def refresh_kuaimai_session(user: dict = Depends(check_permission("settings"))) -> KuaimaiRefreshResponse:
    """手动刷新快麦session"""
    from app.services.kuaimai_api import refresh_session

    if not kuaimai_creds.has_refresh_token():
        return KuaimaiRefreshResponse(
            success=False,
            message="refreshToken未配置，无法刷新。请在kuaimai.json中配置refresh_token",
        )

    success = await refresh_session()
    if success:
        days_left = kuaimai_creds.get_days_left()
        return KuaimaiRefreshResponse(
            success=True,
            message="session刷新成功",
            daysLeft=days_left,
        )
    else:
        return KuaimaiRefreshResponse(
            success=False,
            message="session刷新失败，请检查refreshToken是否有效或联系快麦客服",
        )


@router.post("/api/kuaimai/update-credentials", response_model=BaseResponse)
def update_kuaimai_credentials(
    req: KuaimaiCredentialsRequest,
    user: dict = Depends(check_permission("settings"))
) -> BaseResponse:
    """手动更新快麦凭证（Web管理后台使用）"""
    from app.config import save_kuaimai_config

    try:
        kuaimai_creds.app_key = req.app_key
        kuaimai_creds.app_secret = req.app_secret
        kuaimai_creds.session = req.session
        kuaimai_creds.refresh_token = req.refresh_token
        kuaimai_creds.updated_at = format_beijing(beijing_now())
        save_kuaimai_config()
        logger.info(f"快麦凭证已由用户 {user.get('username', '?')} 手动更新")
        return BaseResponse(message="凭证更新成功")
    except Exception as e:
        logger.error(f"更新快麦凭证失败: {e}")
        raise HTTPException(status_code=500, detail="凭证更新失败，请稍后重试")


@router.get("/setup", response_class=HTMLResponse)
def setup_page(request: Request) -> HTMLResponse:
    """扫码配置页面：显示服务器地址二维码，供PDA扫码配置"""
    # 确定服务器地址：优先使用环境变量，其次使用请求的host
    server_url = SERVER_URL
    if not server_url:
        # 从请求中推断地址
        host = request.headers.get("host", "")
        if host:
            scheme = "https" if request.url.scheme == "https" else "http"
            server_url = f"{scheme}://{host}"

    if not server_url:
        return HTMLResponse(content=_build_error_html(
            "未配置服务器地址",
            "请在 .env 文件中设置 SERVER_URL=http://NAS_IP:8900 后重启服务"
        ))

    # 生成二维码内容：kuaimai://setup?server=xxx&apikey=xxx
    qr_params: dict[str, str] = {"server": server_url}
    if API_KEY:
        qr_params["apikey"] = API_KEY
    qr_content = f"kuaimai://setup?{urlencode(qr_params)}"

    # 生成二维码图片（base64）
    qr_base64 = _generate_qr_base64(qr_content)

    return HTMLResponse(content=_build_setup_html(server_url, qr_base64, bool(API_KEY)))


def _generate_qr_base64(data: str) -> str:
    """生成二维码图片的base64编码"""
    import qrcode
    img = qrcode.make(data, version=1, box_size=10, border=2)
    buffer = io.BytesIO()
    img.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def _build_setup_html(server_url: str, qr_base64: str, has_api_key: bool) -> str:
    """构建配置页面HTML"""
    api_key_status = "已配置" if has_api_key else "未配置（请在.env中设置API_KEY）"
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>快麦取货通 - 扫码配置</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
               background: #f5f5f5; display: flex; justify-content: center; align-items: center;
               min-height: 100vh; padding: 20px; }}
        .card {{ background: white; border-radius: 16px; padding: 40px; max-width: 420px;
                 width: 100%; box-shadow: 0 2px 12px rgba(0,0,0,0.08); text-align: center; }}
        h1 {{ color: #2563EB; font-size: 24px; margin-bottom: 8px; }}
        .subtitle {{ color: #666; font-size: 14px; margin-bottom: 24px; }}
        .qr-wrapper {{ background: white; padding: 16px; border-radius: 12px;
                       display: inline-block; margin-bottom: 20px; border: 1px solid #e5e7eb; }}
        .qr-wrapper img {{ width: 240px; height: 240px; }}
        .info {{ text-align: left; background: #f0f7ff; border-radius: 8px; padding: 16px; }}
        .info-row {{ display: flex; justify-content: space-between; padding: 6px 0;
                     font-size: 14px; border-bottom: 1px solid #e0eaff; }}
        .info-row:last-child {{ border-bottom: none; }}
        .info-label {{ color: #666; }}
        .info-value {{ color: #1d4ed8; font-weight: 600; word-break: break-all; }}
        .tip {{ margin-top: 16px; font-size: 12px; color: #999; line-height: 1.6; }}
    </style>
</head>
<body>
    <div class="card">
        <h1>快麦取货通</h1>
        <p class="subtitle">使用PDA扫描下方二维码完成服务器配置</p>
        <div class="qr-wrapper">
            <img src="data:image/png;base64,{qr_base64}" alt="配置二维码" />
        </div>
        <div class="info">
            <div class="info-row">
                <span class="info-label">服务器地址</span>
                <span class="info-value">{server_url}</span>
            </div>
            <div class="info-row">
                <span class="info-label">API Key</span>
                <span class="info-value">{api_key_status}</span>
            </div>
        </div>
        <p class="tip">
            PDA首次启动App → 引导页 → 点击"扫码配置" → 扫描上方二维码<br>
            也可在App设置页面随时修改服务器地址
        </p>
    </div>
</body>
</html>"""


def _build_error_html(title: str, message: str) -> str:
    """构建错误页面HTML"""
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>快麦取货通 - 配置错误</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, sans-serif; background: #f5f5f5;
               display: flex; justify-content: center; align-items: center; min-height: 100vh; }}
        .card {{ background: white; border-radius: 16px; padding: 40px; max-width: 420px;
                 width: 100%; box-shadow: 0 2px 12px rgba(0,0,0,0.08); text-align: center; }}
        h1 {{ color: #dc2626; font-size: 20px; margin-bottom: 12px; }}
        p {{ color: #666; font-size: 14px; line-height: 1.6; }}
        code {{ background: #f0f0f0; padding: 2px 6px; border-radius: 4px; font-size: 13px; }}
    </style>
</head>
<body>
    <div class="card">
        <h1>{title}</h1>
        <p>{message}</p>
    </div>
</body>
</html>"""
