"""系统路由 - 健康检查、崩溃报告、版本信息、快麦会话管理"""
import logging
import os

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import RedirectResponse

from app.auth import check_permission, get_current_user
from app.config import kuaimai_creds
from app.database import get_db
from app.models import (
    AppVersionResponse,
    BaseResponse,
    CrashReportRequest,
    HealthResponse,
    KuaimaiCredentialsRequest,
    KuaimaiCredentialsResponse,
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


@router.get("/api/kuaimai/credentials", response_model=KuaimaiCredentialsResponse)
def get_kuaimai_credentials(user: dict = Depends(check_permission("settings"))) -> KuaimaiCredentialsResponse:
    """获取快麦凭证（PDA端登录后同步使用）"""
    return KuaimaiCredentialsResponse(
        appKey=kuaimai_creds.app_key,
        appSecret=kuaimai_creds.app_secret,
        session=kuaimai_creds.session,
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


@router.get("/setup", response_class=RedirectResponse)
def setup_page() -> RedirectResponse:
    """扫码配置页面已合并到管理后台"""
    return RedirectResponse(url="/admin")
