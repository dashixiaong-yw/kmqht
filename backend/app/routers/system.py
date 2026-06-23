"""系统路由 - 健康检查、崩溃报告、版本信息、快麦会话管理"""
import json
import logging
import os

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import FileResponse, RedirectResponse

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
    SkuDetailResponse,
)
from app.utils.qr_utils import generate_qr_base64
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

    apk_filename = info.get("apkFileName", "")
    if not apk_filename:
        return AppVersionResponse(latestVersion="", downloadUrl="")
    apk_path = os.path.join(APK_DIR, apk_filename)
    if not os.path.exists(apk_path):
        return AppVersionResponse(latestVersion="", downloadUrl="")
    apk_size = os.path.getsize(apk_path)
    # 优先用环境变量 SERVER_URL（兼容反向代理），否则从请求 Host 自动获取
    base_url = SERVER_URL.rstrip("/") if SERVER_URL else str(request.base_url).rstrip("/")

    return AppVersionResponse(
        latestVersion=info.get("currentVersion", ""),
        downloadUrl=f"{base_url}/api/app-version/download",
        updateNotes=info.get("updateNotes", ""),
        forceUpdate=info.get("forceUpdate", False),
        apkSize=apk_size,
        publishedAt=info.get("publishedAt", ""),
    )


@router.get("/api/app-version/download")
def download_apk(request: Request) -> FileResponse:
    """下载APK文件（设置正确的Content-Type，PDA/浏览器扫码下载用）"""
    info = _load_version_info()
    if not info or not info.get("currentVersion") or not info.get("publishedAt"):
        raise HTTPException(status_code=404, detail="暂无已分发的版本")

    file_name = info.get("apkFileName", "")
    if not file_name:
        raise HTTPException(status_code=404, detail="暂无已分发的版本")
    file_path = os.path.normpath(os.path.join(APK_DIR, file_name))
    apk_dir_norm = os.path.normpath(APK_DIR)
    if not file_path.startswith(apk_dir_norm):
        raise HTTPException(status_code=403, detail="非法文件名")
    if not os.path.exists(file_path):
        logger.warning(f"APK文件不存在: {file_path}，尝试模糊匹配")
        if os.path.exists(APK_DIR):
            candidates = sorted(
                [f for f in os.listdir(APK_DIR) if f.endswith(".apk")],
                reverse=True
            )
            if candidates:
                file_path = os.path.normpath(os.path.join(APK_DIR, candidates[0]))
                logger.info(f"模糊匹配到: {file_path}")
        if not os.path.exists(file_path):
            dir_contents = os.listdir(APK_DIR) if os.path.exists(APK_DIR) else "NOT_EXIST"
            logger.error(f"APK_DIR={APK_DIR} 内容: {dir_contents}")
            raise HTTPException(status_code=404, detail="文件不存在")

    return FileResponse(
        file_path,
        media_type="application/vnd.android.package-archive",
        filename=file_name,
    )


@router.get("/api/app-version/qrcode")
def get_app_version_qrcode(request: Request) -> dict:
    """获取 APK 下载二维码（base64 PNG，公开访问）"""
    info = _load_version_info()
    if not info or not info.get("currentVersion") or not info.get("publishedAt"):
        return {"success": False, "message": "暂无已分发的版本", "qrcode": ""}

    base_url = SERVER_URL.rstrip("/") if SERVER_URL else str(request.base_url).rstrip("/")
    download_url = f"{base_url}/api/app-version/download"
    qr_base64 = generate_qr_base64(download_url)

    return {"success": True, "qrcode": qr_base64}


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
async def get_kuaimai_suppliers(user: dict = Depends(check_permission("update_supplier"))) -> KuaimaiSuppliersResponse:
    """获取快麦供应商列表（含编码）"""
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
        from app.config import kuaimai_config_lock
        with kuaimai_config_lock:
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


@router.get("/api/sku/{sku_outer_id}", response_model=SkuDetailResponse)
async def get_sku_detail(sku_outer_id: str, user: dict = Depends(get_current_user)) -> SkuDetailResponse:
    """获取单个SKU详细信息（实时从快麦获取备注、供应商、标题等）"""
    from app.services.cache import get_sku_info
    from app.services.kuaimai_api import _build_common_params, _sign, _get_client, KUAIMAI_API_BASE

    try:
        sku_info = await get_sku_info(sku_outer_id)
        if sku_info is None:
            raise HTTPException(status_code=404, detail="SKU不存在")
        item_title = sku_info.get("properties_name", "")
        item_outer_id = sku_info.get("item_outer_id", "")
        if item_outer_id:
            from app.config import kuaimai_creds, kuaimai_config_lock
            try:
                params = _build_common_params("item.single.get")
                params["outerId"] = item_outer_id
                with kuaimai_config_lock:
                    secret_snapshot = kuaimai_creds.app_secret
                params["sign"] = _sign(params, secret_snapshot)
                client = _get_client()
                response = await client.post(KUAIMAI_API_BASE, data=params)
                response.raise_for_status()
                item_result = response.json()
                wrapper = item_result.get("item_single_get_response", item_result)
                item_data = wrapper.get("item", wrapper)
                fetched_title = item_data.get("title", "")
                if fetched_title:
                    item_title = fetched_title
            except Exception as e:
                logger.warning(f"获取商品标题失败，使用propertiesName: {e}")
        return SkuDetailResponse(
            skuOuterId=sku_info.get("sku_outer_id", sku_outer_id),
            propertiesName=sku_info.get("properties_name", ""),
            picPath=sku_info.get("pic_path", ""),
            remark=sku_info.get("remark", ""),
            supplierName=sku_info.get("supplier_name", ""),
            supplierCode=sku_info.get("supplier_code", ""),
            itemTitle=item_title,
            sysItemId=sku_info.get("sys_item_id", 0),
            sysSkuId=sku_info.get("sys_sku_id", 0),
            itemOuterId=sku_info.get("item_outer_id", ""),
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"查询SKU详情失败: {e}")
        raise HTTPException(status_code=502, detail=f"查询SKU详情失败: {e}")
