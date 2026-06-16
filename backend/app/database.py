"""数据库模块 - SQLite连接池与表初始化"""

import logging
import sqlite3
import threading
from contextlib import contextmanager
from typing import Optional

from app.config import DB_PATH

logger = logging.getLogger(__name__)

# 线程局部存储，每个线程独立连接
_local = threading.local()

# PRAGMA是否已初始化
_pragma_initialized = False
_pragma_lock = threading.Lock()


def get_db() -> sqlite3.Connection:
    """获取当前线程的数据库连接（线程安全，每个线程独立连接）"""
    global _pragma_initialized
    conn = getattr(_local, "db_connection", None)
    if conn is None:
        conn = sqlite3.connect(DB_PATH)
        conn.row_factory = sqlite3.Row
        _local.db_connection = conn
        logger.info(f"数据库连接已建立: {DB_PATH} (thread={threading.current_thread().name})")

        # 每个连接首次使用时设置PRAGMA
        with _pragma_lock:
            if not _pragma_initialized:
                cursor = conn.cursor()
                cursor.execute("PRAGMA journal_mode=WAL")
                cursor.execute("PRAGMA foreign_keys=ON")
                cursor.execute("PRAGMA busy_timeout=5000")
                _pragma_initialized = True
                logger.info("数据库PRAGMA设置完成")
            else:
                # 后续连接也需要设置PRAGMA（除journal_mode外）
                cursor = conn.cursor()
                cursor.execute("PRAGMA foreign_keys=ON")
                cursor.execute("PRAGMA busy_timeout=5000")

    return conn


@contextmanager
def get_db_ctx():
    """获取数据库连接的上下文管理器，自动处理事务"""
    conn = get_db()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise


def init_db() -> None:
    """初始化数据库：设置PRAGMA并创建所有表"""
    conn = get_db()
    cursor = conn.cursor()

    # PRAGMA设置（仅初始化时执行一次）
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.execute("PRAGMA busy_timeout=5000")

    # 创建取货单表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS pick_orders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            order_no VARCHAR(30) UNIQUE NOT NULL,
            status INTEGER NOT NULL DEFAULT 0 CHECK(status IN (0,1,2)),
            completion_type INTEGER NOT NULL DEFAULT 0 CHECK(completion_type IN (0,1,2)),
            total_count INTEGER NOT NULL DEFAULT 0 CHECK(total_count >= 0),
            completed_count INTEGER NOT NULL DEFAULT 0 CHECK(completed_count >= 0),
            created_at DATETIME NOT NULL,
            completed_at DATETIME,
            expire_at DATETIME NOT NULL
        )
    """)

    # 创建取货明细表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS pick_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            order_id INTEGER NOT NULL,
            sku_outer_id VARCHAR(64) NOT NULL,
            sys_item_id INTEGER NOT NULL,
            sys_sku_id INTEGER NOT NULL,
            properties_name VARCHAR(128) NOT NULL DEFAULT '',
            pic_path VARCHAR(512) NOT NULL DEFAULT '',
            status INTEGER NOT NULL DEFAULT 0 CHECK(status IN (0,1,2)),
            supplier_name VARCHAR(128) NOT NULL DEFAULT '',
            supplier_code VARCHAR(64) NOT NULL DEFAULT '',
            remark VARCHAR(512) NOT NULL DEFAULT '',
            created_at DATETIME NOT NULL,
            completed_at DATETIME,
            UNIQUE(order_id, sku_outer_id),
            FOREIGN KEY (order_id) REFERENCES pick_orders(id) ON DELETE CASCADE
        )
    """)

    # 创建拣货区表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS pick_areas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name VARCHAR(32) UNIQUE NOT NULL,
            created_at DATETIME NOT NULL
        )
    """)

    # 创建商品图片表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS product_images (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sku_outer_id VARCHAR(64) NOT NULL,
            image_type VARCHAR(10) NOT NULL CHECK(image_type IN ('area','box')),
            image_url VARCHAR(512) NOT NULL,
            file_path VARCHAR(512) NOT NULL,
            created_at DATETIME NOT NULL,
            UNIQUE(sku_outer_id, image_type)
        )
    """)

    # 创建SKU缓存表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS sku_cache (
            sku_outer_id VARCHAR(64) PRIMARY KEY NOT NULL,
            properties_name VARCHAR(128) NOT NULL DEFAULT '',
            pic_path VARCHAR(512) NOT NULL DEFAULT '',
            supplier_name VARCHAR(128) NOT NULL DEFAULT '',
            supplier_code VARCHAR(64) NOT NULL DEFAULT '',
            remark VARCHAR(512) NOT NULL DEFAULT '',
            sys_item_id INTEGER NOT NULL,
            sys_sku_id INTEGER NOT NULL,
            cached_at DATETIME NOT NULL
        )
    """)

    # 创建崩溃日志表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS crash_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            app_version VARCHAR(32) NOT NULL,
            device_model VARCHAR(64) NOT NULL,
            error_message TEXT NOT NULL,
            stack_trace TEXT NOT NULL,
            created_at DATETIME NOT NULL
        )
    """)

    conn.commit()
    logger.info("数据库表初始化完成")
