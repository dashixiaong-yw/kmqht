"""时间工具 - 北京时间(UTC+8)统一处理"""

from datetime import datetime, timedelta, timezone

# 北京时间时区
BEIJING_TZ = timezone(timedelta(hours=8))


def beijing_now() -> datetime:
    """获取当前北京时间"""
    return datetime.now(BEIJING_TZ)


def format_beijing(dt: datetime) -> str:
    """
    格式化北京时间为字符串
    :param dt: datetime对象
    :return: 格式化字符串 YYYY-MM-DD HH:MM:SS
    """
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def parse_beijing(time_str: str) -> datetime:
    """
    解析北京时间字符串
    :param time_str: 时间字符串 YYYY-MM-DD HH:MM:SS
    :return: 带北京时区的datetime
    """
    dt = datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
    return dt.replace(tzinfo=BEIJING_TZ)


def beijing_timestamp() -> int:
    """获取当前北京时间戳（毫秒）"""
    return int(beijing_now().timestamp() * 1000)
