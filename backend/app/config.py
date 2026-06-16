"""配置模块 - 加载环境变量和快麦凭证"""

import json
import logging
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv

# 加载 .env 文件（如果存在）
load_dotenv()

logger = logging.getLogger(__name__)

# 北京时间
_BEIJING_TZ = timezone(timedelta(hours=8))

# API 认证密钥
API_KEY: str = os.getenv("API_KEY", "")

# 服务器端口
SERVER_PORT: int = int(os.getenv("SERVER_PORT", "8000"))

# 快麦凭证文件路径
KUAIMAI_CONFIG_PATH: str = os.getenv("KUAIMAI_CONFIG_PATH", "/data/kuaimai.json")

# 图片存储目录
IMAGE_DIR: str = os.getenv("IMAGE_DIR", "/data/product_images")

# 数据库路径
DB_PATH: str = os.getenv("DB_PATH", "/data/kuaimai.db")

# 快麦API基础URL
KUAIMAI_API_BASE: str = "https://openapi.kuaimai.com/router"


class KuaimaiCredentials:
    """快麦平台凭证"""

    def __init__(self) -> None:
        self.app_key: str = ""
        self.app_secret: str = ""
        self.session: str = ""
        self.updated_at: str = ""

    def is_valid(self) -> bool:
        """检查凭证是否已配置"""
        return bool(self.app_key and self.app_secret and self.session)

    def check_session_expiry(self) -> Optional[str]:
        """检查session是否即将过期，返回警告消息或None"""
        if not self.updated_at:
            return None
        try:
            updated_dt = datetime.strptime(self.updated_at, "%Y-%m-%d %H:%M:%S")
            updated_dt = updated_dt.replace(tzinfo=_BEIJING_TZ)
            # 假设session有效期30天
            expire_dt = updated_dt + timedelta(days=30)
            now = datetime.now(_BEIJING_TZ)
            days_left = (expire_dt - now).days
            if days_left <= 5 and days_left > 0:
                return f"快麦session将在{days_left}天后过期，请及时更新"
            if days_left <= 0:
                return "快麦session已过期，请立即更新"
        except ValueError as e:
            logger.warning(f"解析updated_at失败: {e}")
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
        kuaimai_creds.app_key = data.get("app_key", "")
        kuaimai_creds.app_secret = data.get("app_secret", "")
        kuaimai_creds.session = data.get("session", "")
        kuaimai_creds.updated_at = data.get("updated_at", "")
        logger.info("快麦凭证加载成功")
    except (json.JSONDecodeError, IOError) as e:
        logger.error(f"加载快麦凭证失败: {e}")


def start_config_watcher() -> None:
    """启动配置文件热重载监控"""
    import asyncio

    async def _watch_config() -> None:
        """异步监控配置文件变更"""
        try:
            from watchfiles import awatch

            config_dir = str(Path(KUAIMAI_CONFIG_PATH).parent)
            config_name = Path(KUAIMAI_CONFIG_PATH).name
            async for changes in awatch(config_dir):
                for change_type, path in changes:
                    if config_name in path:
                        logger.info(f"检测到配置文件变更: {change_type}")
                        load_kuaimai_config()
        except ImportError as e:
            logger.warning(f"watchfiles未安装，配置热重载不可用: {e}")
        except Exception as e:
            logger.error(f"配置文件监控异常: {e}")

    try:
        loop = asyncio.get_event_loop()
        loop.create_task(_watch_config())
        logger.info("配置文件热重载监控已启动")
    except RuntimeError as e:
        logger.warning(f"启动配置监控失败: {e}")


def check_session_warning() -> None:
    """检查session过期警告（每24小时调用一次）"""
    warning = kuaimai_creds.check_session_expiry()
    if warning:
        logger.warning(warning)
