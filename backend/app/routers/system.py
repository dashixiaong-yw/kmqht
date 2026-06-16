"""系统路由 - 健康检查、崩溃报告、版本信息"""

import logging
import os

from fastapi import APIRouter, HTTPException

from app.database import get_db
from app.models import (
    AppVersionResponse,
    BaseResponse,
    CrashReportRequest,
    HealthResponse,
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
def crash_report(req: CrashReportRequest) -> BaseResponse:
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
        raise HTTPException(status_code=500, detail=f"保存崩溃报告失败: {e}")


@router.get("/api/app-version", response_model=AppVersionResponse)
def get_app_version() -> AppVersionResponse:
    """获取最新应用版本及下载地址"""
    return AppVersionResponse(
        latestVersion=LATEST_VERSION,
        downloadUrl=APK_DOWNLOAD_URL,
    )
