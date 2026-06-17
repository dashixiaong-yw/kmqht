"""快麦取货通后端服务 - FastAPI主入口"""

import logging
import os
from datetime import timedelta

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from starlette.responses import JSONResponse

from app.auth import ApiKeyMiddleware
from app.config import (
    API_KEY,
    APK_DIR,
    CORS_ORIGINS,
    IMAGE_DIR,
    SERVER_PORT,
    check_session_warning,
    load_kuaimai_config,
    start_config_watcher,
)
from app.database import get_db, init_db
from app.routers import admin, areas, images, orders, system, users
from app.utils.time_utils import beijing_now, format_beijing

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

# 创建FastAPI应用
app = FastAPI(
    title="快麦取货通后端服务",
    description="快麦取货通PDA App后端API",
    version="1.0.0",
)

# CORS中间件（来源从环境变量配置，生产环境应限制为前端域名）
_origins = [o.strip() for o in CORS_ORIGINS.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# API Key认证中间件
if API_KEY:
    app.add_middleware(ApiKeyMiddleware)
    logger.info("API Key认证已启用")
else:
    logger.warning("API_KEY未配置，认证中间件未启用")

# 注册路由
app.include_router(admin.router)
app.include_router(orders.router)
app.include_router(areas.router)
app.include_router(images.router)
app.include_router(system.router)
app.include_router(users.router)


# ==================== 启动与关闭事件 ====================

@app.on_event("startup")
async def startup_event() -> None:
    """应用启动：初始化数据库、加载配置、启动定时任务"""
    logger.info("快麦取货通后端服务启动中...")

    # 确保数据目录存在
    os.makedirs(IMAGE_DIR, exist_ok=True)

    # 初始化数据库
    init_db()
    logger.info("数据库初始化完成")

    # 加载快麦凭证
    load_kuaimai_config()

    # 启动配置文件热重载
    start_config_watcher()

    # 启动定时任务
    _start_scheduler()

    # 挂载静态文件目录（图片访问）
    if os.path.exists(IMAGE_DIR):
        app.mount("/images", StaticFiles(directory=IMAGE_DIR), name="images")
        logger.info(f"静态文件目录已挂载: {IMAGE_DIR}")

    # 创建 APK 目录并挂载
    os.makedirs(APK_DIR, exist_ok=True)
    if os.path.exists(APK_DIR):
        app.mount("/apk", StaticFiles(directory=APK_DIR), name="apk")
        logger.info(f"APK 静态文件目录已挂载: {APK_DIR}")

    logger.info("快麦取货通后端服务启动完成")


# ==================== 定时任务 ====================

def _start_scheduler() -> None:
    """启动定时任务调度器"""
    scheduler = BackgroundScheduler(timezone="Asia/Shanghai")

    # 每1分钟检查取货单超时（12小时超时）
    scheduler.add_job(
        _check_order_timeout,
        "interval",
        minutes=1,
        id="order_timeout_check",
        replace_existing=True,
    )

    # 每天凌晨3:00清理已完成的取货单（30天前）
    scheduler.add_job(
        _cleanup_completed_orders,
        "cron",
        hour=3,
        minute=0,
        id="cleanup_completed_orders",
        replace_existing=True,
    )

    # 每小时清理SKU缓存（24小时前）
    scheduler.add_job(
        _cleanup_sku_cache,
        "interval",
        hours=1,
        id="cleanup_sku_cache",
        replace_existing=True,
    )

    # 每天凌晨4:00清理崩溃日志（30天前）
    scheduler.add_job(
        _cleanup_crash_logs,
        "cron",
        hour=4,
        minute=0,
        id="cleanup_crash_logs",
        replace_existing=True,
    )

    # 每天凌晨3:30清理孤立图片（7天安全期）
    scheduler.add_job(
        _cleanup_orphan_images,
        "cron",
        hour=3,
        minute=30,
        id="cleanup_orphan_images",
        replace_existing=True,
    )

    # 每7天自动刷新快麦session（30天有效期，7天刷新留足余量）
    scheduler.add_job(
        _refresh_kuaimai_session,
        "interval",
        days=7,
        id="kuaimai_session_refresh",
        replace_existing=True,
    )

    # 每24小时检查session过期警告（仅日志提醒，不自动刷新）
    scheduler.add_job(
        check_session_warning,
        "interval",
        hours=24,
        id="session_expiry_warning",
        replace_existing=True,
    )

    scheduler.start()
    logger.info("定时任务调度器已启动")


def _check_order_timeout() -> None:
    """检查取货单超时：expire_at < now 的进行中订单标记为已完成（completion_type=1）"""
    try:
        db = get_db()
        cursor = db.cursor()
        now = beijing_now().strftime("%Y-%m-%d %H:%M:%S")

        # 将超时的进行中取货单标记为已完成
        cursor.execute(
            """UPDATE pick_orders
               SET status = 1, completion_type = 1, completed_at = ?
               WHERE status = 0 AND expire_at < ?""",
            (now, now),
        )
        order_count = cursor.rowcount

        # 将超时取货单内未完成的待办行标记为已完成
        cursor.execute(
            """UPDATE pick_items
               SET status = 1, completed_at = ?
               WHERE order_id IN (
                   SELECT id FROM pick_orders WHERE status = 1 AND completion_type = 1
               ) AND status = 0""",
            (now,),
        )
        item_count = cursor.rowcount

        db.commit()

        if order_count > 0:
            logger.info(
                f"超时自动完成: {order_count}个取货单, {item_count}条待办行"
            )
    except Exception as e:
        logger.error(f"检查取货单超时失败: {e}")


def _cleanup_completed_orders() -> None:
    """清理30天前已完成的取货单"""
    try:
        db = get_db()
        cursor = db.cursor()

        cutoff = (beijing_now() - timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S")
        cursor.execute(
            "DELETE FROM pick_orders WHERE status = 1 AND completed_at < ?",
            (cutoff,)
        )
        deleted = cursor.rowcount
        db.commit()
        if deleted > 0:
            logger.info(f"已清理{deleted}条30天前的已完成取货单")
    except Exception as e:
        logger.error(f"清理已完成取货单失败: {e}")


def _cleanup_sku_cache() -> None:
    """清理24小时前的SKU缓存"""
    try:
        db = get_db()
        cursor = db.cursor()

        cutoff = (beijing_now() - timedelta(hours=24)).strftime("%Y-%m-%d %H:%M:%S")
        cursor.execute(
            "DELETE FROM sku_cache WHERE cached_at < ?",
            (cutoff,)
        )
        deleted = cursor.rowcount
        db.commit()
        if deleted > 0:
            logger.info(f"已清理{deleted}条过期SKU缓存")
    except Exception as e:
        logger.error(f"清理SKU缓存失败: {e}")


def _cleanup_crash_logs() -> None:
    """清理30天前的崩溃日志"""
    try:
        db = get_db()
        cursor = db.cursor()

        cutoff = (beijing_now() - timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S")
        cursor.execute(
            "DELETE FROM crash_logs WHERE created_at < ?",
            (cutoff,)
        )
        deleted = cursor.rowcount
        db.commit()
        if deleted > 0:
            logger.info(f"已清理{deleted}条30天前的崩溃日志")
    except Exception as e:
        logger.error(f"清理崩溃日志失败: {e}")


def _cleanup_orphan_images() -> None:
    """清理孤立图片文件（7天安全期）"""
    try:
        import os as _os

        db = get_db()
        cursor = db.cursor()

        # 获取数据库中所有图片文件路径
        cursor.execute("SELECT file_path FROM product_images")
        db_paths = {row["file_path"] for row in cursor.fetchall()}

        # 遍历图片目录，删除不在数据库中的文件
        if not _os.path.exists(IMAGE_DIR):
            return

        cutoff_time = (beijing_now() - timedelta(days=7)).timestamp()
        deleted_count = 0

        for root, dirs, files in _os.walk(IMAGE_DIR):
            for filename in files:
                full_path = _os.path.join(root, filename)
                relative_path = _os.path.relpath(full_path, IMAGE_DIR).replace("\\", "/")

                # 跳过7天内的文件（安全期）
                if _os.path.getmtime(full_path) > cutoff_time:
                    continue

                # 不在数据库中的文件视为孤立文件
                if relative_path not in db_paths:
                    _os.remove(full_path)
                    deleted_count += 1

        if deleted_count > 0:
            logger.info(f"已清理{deleted_count}个孤立图片文件")
    except Exception as e:
        logger.error(f"清理孤立图片失败: {e}")


def _refresh_kuaimai_session() -> None:
    """定时刷新快麦session（每7天执行一次）"""
    import asyncio
    from app.services.kuaimai_api import refresh_session

    async def _do_refresh() -> None:
        try:
            success = await refresh_session()
            if success:
                logger.info("定时刷新快麦session成功")
            else:
                logger.warning("定时刷新快麦session失败，将在下个周期重试")
        except Exception as e:
            logger.error(f"定时刷新快麦session异常: {e}")

    try:
        asyncio.run(_do_refresh())
    except Exception as e:
        logger.error(f"刷新session失败: {e}")


# ==================== 启动配置 ====================

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=SERVER_PORT,
        reload=True,
    )
