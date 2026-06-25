"""配置模块 - 加载环境变量和快麦凭证"""

import json
import logging
import os
import asyncio
import threading
from datetime import timedelta
from pathlib import Path
from typing import Optional

from pydantic_settings import BaseSettings

logger = logging.getLogger(__name__)

# 凭证访问锁（kuaimai_api.py也引用此锁）
kuaimai_config_lock = threading.Lock()


class _Settings(BaseSettings):
    """应用配置 - 自动从环境变量和.env文件加载"""
    api_key: str = ""
    server_port: int = 8900
    kuaimai_config_path: str = "/data/kuaimai.json"
    image_dir: str = "/data/product_images"
    db_path: str = "/data/kuaimai.db"
    kuaimai_api_base: str = "https://gw.superboss.cc/router"
    server_url: str = ""
    cors_origins: str = "*"
    session_warning_days: int = 5
    apk_dir: str = "/data/apk"
    apk_version_file: str = "/data/apk_version.json"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


_settings = _Settings()

# 向后兼容的模块级常量（已有代码无需修改import）
API_KEY = _settings.api_key
SERVER_PORT = _settings.server_port
KUAIMAI_CONFIG_PATH = _settings.kuaimai_config_path
IMAGE_DIR = _settings.image_dir
DB_PATH = _settings.db_path
KUAIMAI_API_BASE = _settings.kuaimai_api_base
SERVER_URL = _settings.server_url
CORS_ORIGINS = _settings.cors_origins
SESSION_WARNING_DAYS = _settings.session_warning_days
APK_DIR = _settings.apk_dir
APK_VERSION_FILE = _settings.apk_version_file


class KuaimaiCredentials:
    """快麦平台凭证"""

    def __init__(self) -> None:
        self.app_key: str = ""
        self.app_secret: str = ""
        self.session: str = ""
        self.refresh_token: str = ""
        self.updated_at: str = ""

    def is_valid(self) -> bool:
        """检查凭证是否已配置"""
        return bool(self.app_key and self.app_secret and self.session)

    def has_refresh_token(self) -> bool:
        """检查是否配置了refreshToken"""
        return bool(self.refresh_token)

    def check_session_expiry(self) -> Optional[str]:
        """检查session是否即将过期，返回警告消息或None"""
        if not self.updated_at:
            return None
        try:
            from app.utils.time_utils import beijing_now, parse_beijing
            updated_dt = parse_beijing(self.updated_at)
            # session有效期30天
            expire_dt = updated_dt + timedelta(days=30)
            now = beijing_now()
            days_left = (expire_dt - now).days
            if days_left <= SESSION_WARNING_DAYS and days_left > 0:
                return f"快麦session将在{days_left}天后过期，请及时更新"
            if days_left <= 0:
                return "快麦session已过期，请立即更新"
        except ValueError as e:
            logger.warning(f"解析updated_at失败: {e}")
        return None

    def get_days_left(self) -> Optional[int]:
        """获取session剩余天数，未配置返回None"""
        if not self.updated_at:
            return None
        try:
            from app.utils.time_utils import beijing_now, parse_beijing
            updated_dt = parse_beijing(self.updated_at)
            expire_dt = updated_dt + timedelta(days=30)
            now = beijing_now()
            return (expire_dt - now).days
        except ValueError:
            return None


# 全局凭证实例
kuaimai_creds = KuaimaiCredentials()


def load_kuaimai_config() -> None:
    """从JSON文件加载快麦凭证"""
    config_path = Path(KUAIMAI_CONFIG_PATH)
    if not config_path.exists():
        logger.warning(f"快麦凭证文件不存在: {KUAIMAI_CONFIG_PATH}")
        return

    try:
        with open(config_path, "r", encoding="utf-8") as f:
            data: dict = json.load(f)
        with kuaimai_config_lock:
            kuaimai_creds.app_key = data.get("app_key", "")
            kuaimai_creds.app_secret = data.get("app_secret", "")
            kuaimai_creds.session = data.get("session", "")
            kuaimai_creds.refresh_token = data.get("refresh_token", "")
            kuaimai_creds.updated_at = data.get("updated_at", "")
        logger.info("快麦凭证加载成功")
    except (json.JSONDecodeError, IOError) as e:
        logger.error(f"加载快麦凭证失败: {e}")


def save_kuaimai_config() -> None:
    """将快麦凭证写回JSON文件（刷新session后更新updated_at）"""
    config_path = Path(KUAIMAI_CONFIG_PATH)
    try:
        data: dict = {}
        if config_path.exists():
            with open(config_path, "r", encoding="utf-8") as f:
                data = json.load(f)

        # 更新字段
        with kuaimai_config_lock:
            data["app_key"] = kuaimai_creds.app_key
            data["app_secret"] = kuaimai_creds.app_secret
            data["updated_at"] = kuaimai_creds.updated_at
            data["session"] = kuaimai_creds.session
            data["refresh_token"] = kuaimai_creds.refresh_token

        # 原子写入：先写临时文件，再替换原文件（防止写入中断导致JSON损坏）
        tmp_path = config_path.with_suffix(".json.tmp")
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        tmp_path.replace(config_path)
        logger.info("快麦凭证已保存到文件")
    except (json.JSONDecodeError, IOError) as e:
        logger.error(f"保存快麦凭证失败: {e}")


_watcher_task: Optional[asyncio.Task] = None
_last_kuaimai_mtime: float = 0.0


def start_config_watcher() -> None:
    """启动配置文件热重载监控"""
    global _watcher_task

    # 抑制 watchfiles 库自身的 INFO 日志（文件变更事件与健康检查冲突，产生大量噪音）
    logging.getLogger("watchfiles.main").setLevel(logging.WARNING)

    import asyncio
    import os

    async def _watch_config() -> None:
        """异步监控配置文件变更（加mtime过滤，避免Docker overlay伪触发重复加载）"""
        global _last_kuaimai_mtime
        try:
            from watchfiles import awatch

            config_path_obj = Path(KUAIMAI_CONFIG_PATH)
            config_dir = str(config_path_obj.parent)
            config_name = config_path_obj.name
            async for changes in awatch(config_dir):
                for change_type, path in changes:
                    if config_name not in path:
                        continue
                    current_mtime = os.path.getmtime(KUAIMAI_CONFIG_PATH)
                    if current_mtime == _last_kuaimai_mtime:
                        continue
                    _last_kuaimai_mtime = current_mtime
                    load_kuaimai_config()
        except ImportError as e:
            logger.warning(f"watchfiles未安装，配置热重载不可用: {e}")
        except Exception as e:
            logger.error(f"配置文件监控异常: {e}")

    try:
        loop = asyncio.get_event_loop()
        _watcher_task = loop.create_task(_watch_config())
        logger.info("配置文件热重载监控已启动")
    except RuntimeError as e:
        logger.warning(f"启动配置监控失败: {e}")


def stop_config_watcher() -> None:
    """停止配置文件热重载监控"""
    global _watcher_task
    if _watcher_task:
        _watcher_task.cancel()
        _watcher_task = None
        logger.info("配置文件热重载监控已停止")


def check_session_warning() -> None:
    """检查session过期警告（每24小时调用一次）"""
    warning = kuaimai_creds.check_session_expiry()
    if warning:
        logger.warning(warning)
