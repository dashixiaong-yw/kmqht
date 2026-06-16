"""SKU缓存服务 - 查询缓存与快麦API"""

import asyncio
import logging
from typing import Any, Dict, Optional

from app.database import get_db
from app.services.kuaimai_api import get_sku_by_outer_id
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)


async def get_sku_info(sku_outer_id: str) -> Optional[Dict[str, Any]]:
    """
    获取SKU信息：先查缓存，缓存未命中则调用快麦API并缓存结果
    :param sku_outer_id: SKU外部编码
    :return: SKU信息字典或None
    """
    db = get_db()
    cursor = db.cursor()

    # 先查缓存
    cursor.execute(
        "SELECT * FROM sku_cache WHERE sku_outer_id = ?",
        (sku_outer_id,)
    )
    cached = cursor.fetchone()
    if cached:
        logger.debug(f"SKU缓存命中: {sku_outer_id}")
        return _cache_row_to_dict(cached)

    # 缓存未命中，调用快麦API
    logger.info(f"SKU缓存未命中，查询快麦API: {sku_outer_id}")
    try:
        sku_data = await get_sku_by_outer_id(sku_outer_id)
    except Exception as e:
        logger.error(f"查询快麦API失败: {e}")
        return None

    if not sku_data:
        logger.warning(f"快麦API未找到SKU: {sku_outer_id}")
        return None

    # 缓存结果
    now = beijing_now()
    try:
        cursor.execute(
            """INSERT OR REPLACE INTO sku_cache
               (sku_outer_id, properties_name, pic_path, supplier_name, supplier_code,
                remark, sys_item_id, sys_sku_id, cached_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                sku_outer_id,
                sku_data.get("properties_name", ""),
                sku_data.get("pic_path", ""),
                sku_data.get("supplier_name", ""),
                sku_data.get("supplier_code", ""),
                sku_data.get("remark", ""),
                sku_data.get("sys_item_id", 0),
                sku_data.get("sys_sku_id", 0),
                format_beijing(now),
            )
        )
        db.commit()
        logger.info(f"SKU缓存已更新: {sku_outer_id}")
    except Exception as e:
        db.rollback()
        logger.error(f"缓存SKU信息失败: {e}")

    return _api_data_to_dict(sku_outer_id, sku_data)


def invalidate_sku_cache(sku_outer_id: str) -> bool:
    """
    使SKU缓存失效（删除缓存记录，非更新cached_at）
    :param sku_outer_id: SKU外部编码
    :return: 是否成功删除
    """
    db = get_db()
    cursor = db.cursor()

    try:
        cursor.execute(
            "DELETE FROM sku_cache WHERE sku_outer_id = ?",
            (sku_outer_id,)
        )
        db.commit()
        deleted = cursor.rowcount > 0
        if deleted:
            logger.info(f"SKU缓存已失效: {sku_outer_id}")
        return deleted
    except Exception as e:
        db.rollback()
        logger.error(f"失效SKU缓存失败: {e}")
        return False


def _cache_row_to_dict(row) -> Dict[str, Any]:
    """缓存行转字典"""
    return {
        "sku_outer_id": row["sku_outer_id"],
        "properties_name": row["properties_name"],
        "pic_path": row["pic_path"],
        "supplier_name": row["supplier_name"],
        "supplier_code": row["supplier_code"],
        "remark": row["remark"],
        "sys_item_id": row["sys_item_id"],
        "sys_sku_id": row["sys_sku_id"],
    }


def _api_data_to_dict(sku_outer_id: str, data: Dict[str, Any]) -> Dict[str, Any]:
    """API响应转字典"""
    return {
        "sku_outer_id": sku_outer_id,
        "properties_name": data.get("properties_name", ""),
        "pic_path": data.get("pic_path", ""),
        "supplier_name": data.get("supplier_name", ""),
        "supplier_code": data.get("supplier_code", ""),
        "remark": data.get("remark", ""),
        "sys_item_id": data.get("sys_item_id", 0),
        "sys_sku_id": data.get("sys_sku_id", 0),
    }
