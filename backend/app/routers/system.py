"""系统路由 - 健康检查、崩溃报告、版本信息、快麦会话管理"""
import json
import logging
import os

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import RedirectResponse

from app.auth import check_permission, get_current_user
from app.config import APK_DIR, APK_VERSION_FILE, SERVER_URL, kuaimai_creds
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
    KuaimaiSupplierItem,
    KuaimaiSuppliersResponse,
)
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(tags=["系统"])


def _load_version_info() -> dict:
    """从 JSON 文件读取版本信息"""
    try:
        with open(APK_VERSION_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


@router.get("/health", response_model=HealthResponse)
def health_check() -> HealthResponse:
    """健康检查"""
    db_status = "ok"
    total_orders = 0
    try:
        db = get_db()
        cursor = db.cursor()
        cursor.execute("SELECT 1")
        cursor.execute("SELECT COUNT(*) as cnt FROM pick_orders")
        row = cursor.fetchone()
        if row:
            total_orders = row["cnt"]
    except Exception as e:
        logger.error(f"数据库健康检查失败: {e}")
        db_status = "error"

    return HealthResponse(
        status="ok",
        database=db_status,
        totalOrders=total_orders,
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
def get_app_version(request: Request) -> AppVersionResponse:
    """获取当前分发的应用版本（无需认证，供PDA启动时自动检查）"""
    info = _load_version_info()
    if not info or not info.get("currentVersion"):
        return AppVersionResponse(latestVersion="", downloadUrl="")

    apk_path = os.path.join(APK_DIR, info.get("apkFileName", ""))
    apk_size = os.path.getsize(apk_path) if os.path.exists(apk_path) else 0
    # 优先用环境变量 SERVER_URL（兼容反向代理），否则从请求 Host 自动获取
    base_url = SERVER_URL.rstrip("/") if SERVER_URL else str(request.base_url).rstrip("/")

    return AppVersionResponse(
        latestVersion=info.get("currentVersion", ""),
        downloadUrl=f"{base_url}/apk/{info.get('apkFileName', '')}",
        updateNotes=info.get("updateNotes", ""),
        forceUpdate=info.get("forceUpdate", False),
        apkSize=apk_size,
        publishedAt=info.get("publishedAt", ""),
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


@router.get("/api/kuaimai/suppliers", response_model=KuaimaiSuppliersResponse)
async def get_kuaimai_suppliers(user: dict = Depends(check_permission("settings"))) -> KuaimaiSuppliersResponse:
    """获取快麦供应商列表（含编码，采购权限）"""
    from app.services.kuaimai_api import get_supplier_list

    try:
        items = await get_supplier_list()
        if items is None:
            raise HTTPException(status_code=502, detail="快麦供应商列表获取失败")
        suppliers = [KuaimaiSupplierItem(code=s.get("code", ""), name=s.get("name", ""), id=s.get("id", 0)) for s in items]
        return KuaimaiSuppliersResponse(suppliers=suppliers)
    except Exception as e:
        logger.error(f"获取供应商列表失败: {e}")
        raise HTTPException(status_code=502, detail=f"获取供应商列表失败: {e}")


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
        from app.services.kuaimai_api import _config_lock
        with _config_lock:
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
    return RedirectResponse(url="/admin", status_code=302)
