"""条码处理工具 - 清理与验证"""

import re
from typing import Optional


def clean_barcode(barcode: str) -> str:
    """
    清理条码字符串
    - 去除首尾空白
    - 移除控制字符（0x00-0x1F, 0x7F）
    - 移除零宽字符（U+200B, U+FEFF, U+200D等）
    """
    if not barcode:
        return ""

    # 去除首尾空白
    result = barcode.strip()

    # 移除控制字符（ASCII 0-31 和 127）
    result = re.sub(r"[\x00-\x1f\x7f]", "", result)

    # 移除零宽字符
    # U+200B 零宽空格, U+FEFF BOM, U+200D 零宽连接符
    # U+200C 零宽非连接符, U+2060 字连接符, U+180E 蒙古文元音分隔符
    result = re.sub(r"[\u200b\ufeff\u200d\u200c\u2060\u180e]", "", result)

    return result


def validate_barcode(barcode: str) -> bool:
    """
    验证条码格式
    - 长度不超过64
    - 仅允许：字母、数字、横线、下划线、点
    """
    if not barcode:
        return False

    if len(barcode) > 64:
        return False

    # 允许字母、数字、横线、下划线、点、冒号、斜杠
    pattern = r"^[a-zA-Z0-9\-_./:]+$"
    return bool(re.match(pattern, barcode))
