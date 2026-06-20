"""数据库模块 - SQLite连接池与表初始化"""

import logging
import sqlite3
import threading
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


def init_db() -> None:
    """初始化数据库：创建所有表（PRAGMA由get_db()处理）"""
    import os as _os
    db_dir = _os.path.dirname(DB_PATH)
    if db_dir and not _os.path.exists(db_dir):
        _os.makedirs(db_dir, exist_ok=True)
        logger.info(f"数据库目录已创建: {db_dir}")

    conn = get_db()
    cursor = conn.cursor()

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
            expire_at DATETIME NOT NULL,
            created_by VARCHAR(32) NOT NULL DEFAULT '',
            assigned_to VARCHAR(32) NOT NULL DEFAULT '',
            visibility VARCHAR(16) NOT NULL DEFAULT 'private'
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
            item_outer_id VARCHAR(64) NOT NULL DEFAULT '',
            cached_modified BIGINT NOT NULL DEFAULT 0,
            cached_at DATETIME NOT NULL
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_sku_cache_cached_at ON sku_cache(cached_at)")

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

    # 创建用户表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username VARCHAR(32) UNIQUE NOT NULL,
            password_hash VARCHAR(128) NOT NULL,
            is_active INTEGER NOT NULL DEFAULT 1,
            created_at DATETIME NOT NULL
        )
    """)

    # 创建用户权限表（一个用户多个权限）
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS user_permissions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            permission VARCHAR(32) NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE(user_id, permission)
        )
    """)

    # 创建用户Token表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS user_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            token VARCHAR(64) UNIQUE NOT NULL,
            expires_at DATETIME NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    """)

    # 初始化默认管理员用户（仅在表为空时创建）
    cursor.execute("SELECT COUNT(*) FROM users")
    user_count = cursor.fetchone()[0]
    if user_count == 0:
        _init_default_admin(cursor)

    # 迁移: pick_orders 表追加新列（兼容已有数据库）
    migrations = [
        "ALTER TABLE pick_orders ADD COLUMN created_by VARCHAR(32) NOT NULL DEFAULT ''",
        "ALTER TABLE pick_orders ADD COLUMN assigned_to VARCHAR(32) NOT NULL DEFAULT ''",
        "ALTER TABLE pick_orders ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'private'",
        "ALTER TABLE sku_cache ADD COLUMN cached_modified BIGINT NOT NULL DEFAULT 0",
        "ALTER TABLE pick_items ADD COLUMN item_outer_id VARCHAR(64) NOT NULL DEFAULT ''",
    ]
    for sql in migrations:
        try:
            cursor.execute(sql)
            col_name = sql.split("ADD COLUMN")[1].strip().split()[0]
            logger.info(f"数据库迁移: pick_orders 已添加 {col_name} 列")
        except sqlite3.OperationalError as e:
            if "duplicate column" in str(e).lower():
                pass
            else:
                raise

    # 权限迁移: 为拥有 settings 权限的用户追加 update_supplier
    cursor.execute("""
        INSERT OR IGNORE INTO user_permissions (user_id, permission)
        SELECT DISTINCT up.user_id, 'update_supplier'
        FROM user_permissions up
        WHERE up.permission = 'settings'
    """)
    if cursor.rowcount > 0:
        logger.info(f"权限迁移: 已为 {cursor.rowcount} 个用户追加 update_supplier 权限")

    conn.commit()
    logger.info("数据库表初始化完成")


def _init_default_admin(cursor: sqlite3.Cursor) -> None:
    """初始化默认管理员用户（默认禁用，仅作数据库占位）"""
    # 延迟导入避免循环依赖
    from app.routers.users import _hash_password

    password_hash = _hash_password("admin123")

    from app.utils.time_utils import beijing_now, format_beijing
    now = format_beijing(beijing_now())

    cursor.execute(
        "INSERT INTO users (username, password_hash, is_active, created_at) VALUES (?, ?, 0, ?)",
        ("admin", password_hash, now)
    )
    admin_id = cursor.lastrowid

    # 分配全部5个权限
    permissions = ["settings", "update_supplier", "update_remark", "manage_area_image", "manage_box_image"]
    for perm in permissions:
        cursor.execute(
            "INSERT INTO user_permissions (user_id, permission) VALUES (?, ?)",
            (admin_id, perm)
        )
    logger.info("默认管理员用户已创建并禁用: admin")
