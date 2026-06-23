"""SKU缓存服务 - 查询缓存与快麦API"""

import asyncio
import logging
import sqlite3
from typing import Any, Dict, Optional

from app.database import get_db
from app.services.kuaimai_api import get_sku_by_outer_id
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)


async def get_sku_info(sku_outer_id: str) -> Optional[Dict[str, Any]]:
    """
    比对式缓存获取SKU信息：每次透传调用快麦API获取最新数据，
    对比modified时间戳决定是否更新缓存。API失败时降级返回缓存。
    :param sku_outer_id: SKU外部编码
    :return: SKU信息字典或None
    """
    db = get_db()
    cursor = db.cursor()

    # 查缓存用于比对
    cursor.execute(
        "SELECT * FROM sku_cache WHERE sku_outer_id = ?",
        (sku_outer_id,)
    )
    cached = cursor.fetchone()

    # 调快麦API获取最新数据
    try:
        sku_data = await get_sku_by_outer_id(sku_outer_id)
    except Exception as e:
        logger.error(f"查询快麦API失败: {e}, sku={sku_outer_id}")
        if cached:
            logger.warning(f"API不可用，降级使用缓存: {sku_outer_id}")
            return _cache_row_to_dict(cached)
        return None

    if not sku_data:
        if cached:
            logger.warning(f"快麦API未找到SKU {sku_outer_id}，降级使用缓存")
            return _cache_row_to_dict(cached)
        logger.warning(f"快麦API未找到SKU: {sku_outer_id}")
        return None

    api_modified = sku_data.get("modified", 0)
    cached_modified = cached["cached_modified"] if cached else 0

    if cached and api_modified == cached_modified:
        logger.debug(f"SKU数据未变更(modified={api_modified})，返回缓存: {sku_outer_id}")
        return _cache_row_to_dict(cached)

    # 数据已变更或首次缓存，写入新缓存
    logger.info(f"SKU数据已变更(modified={cached_modified}→{api_modified})，更新缓存: {sku_outer_id}")
    now = beijing_now()
    try:
        cursor.execute(
            """INSERT OR REPLACE INTO sku_cache
               (sku_outer_id, properties_name, pic_path, supplier_name, supplier_code,
                remark, sys_item_id, sys_sku_id, item_outer_id, cached_modified, cached_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    sku_data.get("sku_outer_id", sku_outer_id),  # 快麦API返回的正确大小写
                sku_data.get("properties_name", ""),
                sku_data.get("pic_path", ""),
                sku_data.get("supplier_name", ""),
                sku_data.get("supplier_code", ""),
                sku_data.get("remark", ""),
                sku_data.get("sys_item_id", 0),
                sku_data.get("sys_sku_id", 0),
                sku_data.get("item_outer_id", ""),
                api_modified,
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


def _cache_row_to_dict(row: sqlite3.Row) -> Dict[str, Any]:
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
        "item_outer_id": row["item_outer_id"],
    }


def _api_data_to_dict(sku_outer_id: str, data: Dict[str, Any]) -> Dict[str, Any]:
    """API响应转字典"""
    return {
        "sku_outer_id": data.get("sku_outer_id", sku_outer_id),
        "properties_name": data.get("properties_name", ""),
        "pic_path": data.get("pic_path", ""),
        "supplier_name": data.get("supplier_name", ""),
        "supplier_code": data.get("supplier_code", ""),
        "remark": data.get("remark", ""),
        "sys_item_id": data.get("sys_item_id", 0),
        "sys_sku_id": data.get("sys_sku_id", 0),
        "item_outer_id": data.get("item_outer_id", ""),
    }
