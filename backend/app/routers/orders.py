"""取货单路由 - 取货单CRUD及明细操作"""

import logging
import os
import sqlite3
from datetime import datetime, timedelta, timezone
from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query

from app.config import IMAGE_DIR
from app.database import get_db
from app.models import (
    AddItemRequest,
    BaseResponse,
    CreateOrderRequest,
    ItemResponse,
    OrderDetailResponse,
    OrderListResponse,
    OrderResponse,
)
from app.services.barcode import clean_barcode, validate_barcode
from app.services.cache import get_sku_info
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/orders", tags=["取货单"])

_BEIJING_TZ = timezone(timedelta(hours=8))


@router.post("", response_model=OrderResponse)
def create_order(req: CreateOrderRequest) -> OrderResponse:
    """创建取货单，自动生成单号: yyyyMMdd-拣货区X"""
    db = get_db()
    cursor = db.cursor()

    now = beijing_now()
    date_str = now.strftime("%Y%m%d")

    # 查找当日该拣货区已有单号，递增序号
    prefix = f"{date_str}-{req.areaName}"
    cursor.execute(
        "SELECT order_no FROM pick_orders WHERE order_no LIKE ? ORDER BY id DESC",
        (f"{prefix}%",)
    )
    existing = cursor.fetchall()
    if len(existing) == 0:
        order_no = prefix
    else:
        # 同区域同日第2+单加(1)(2)...
        order_no = f"{prefix}({len(existing)})"

    # 默认12小时后过期
    expire_at = now + timedelta(hours=12)

    try:
        cursor.execute(
            """INSERT INTO pick_orders (order_no, status, completion_type, total_count, completed_count, created_at, expire_at)
               VALUES (?, 0, 0, 0, 0, ?, ?)""",
            (order_no, format_beijing(now), format_beijing(expire_at))
        )
        db.commit()

        cursor.execute("SELECT * FROM pick_orders WHERE order_no = ?", (order_no,))
        row = cursor.fetchone()
        return _row_to_order_response(row)
    except Exception as e:
        db.rollback()
        logger.error(f"创建取货单失败: {e}")
        raise HTTPException(status_code=500, detail=f"创建取货单失败: {e}")


@router.get("", response_model=OrderListResponse)
def list_orders(status: Optional[int] = Query(None, description="状态过滤: 0=进行中, 1=已完成")) -> OrderListResponse:
    """获取取货单列表，按创建时间倒序"""
    db = get_db()
    cursor = db.cursor()

    if status is not None:
        cursor.execute(
            "SELECT * FROM pick_orders WHERE status = ? ORDER BY created_at DESC",
            (status,)
        )
    else:
        cursor.execute("SELECT * FROM pick_orders ORDER BY created_at DESC")

    rows = cursor.fetchall()
    orders = [_row_to_order_response(row) for row in rows]
    return OrderListResponse(data=orders)


@router.get("/{order_id}", response_model=OrderDetailResponse)
def get_order(order_id: int, supplierName: Optional[str] = Query(None, description="供应商名称过滤")) -> OrderDetailResponse:
    """获取取货单详情（含明细），支持供应商过滤"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if not order_row:
        raise HTTPException(status_code=404, detail="取货单不存在")

    # 查询明细
    if supplierName:
        cursor.execute(
            "SELECT * FROM pick_items WHERE order_id = ? AND supplier_name = ? ORDER BY id",
            (order_id, supplierName)
        )
    else:
        cursor.execute(
            "SELECT * FROM pick_items WHERE order_id = ? ORDER BY id",
            (order_id,)
        )

    item_rows = cursor.fetchall()
    items = [_row_to_item_response(row) for row in item_rows]

    order = _row_to_order_response(order_row)
    return OrderDetailResponse(
        id=order.id,
        orderNo=order.orderNo,
        status=order.status,
        completionType=order.completionType,
        totalCount=order.totalCount,
        completedCount=order.completedCount,
        createdAt=order.createdAt,
        completedAt=order.completedAt,
        expireAt=order.expireAt,
        items=items,
    )


@router.post("/{order_id}/items", response_model=ItemResponse)
async def add_item(order_id: int, req: AddItemRequest) -> ItemResponse:
    """添加取货明细，后端查询快麦API并缓存"""
    db = get_db()
    cursor = db.cursor()

    # 检查取货单是否存在
    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if not order_row:
        raise HTTPException(status_code=404, detail="取货单不存在")

    if order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="取货单已完成，无法添加明细")

    # 清理并验证条码
    sku_outer_id = clean_barcode(req.skuOuterId)
    if not validate_barcode(sku_outer_id):
        raise HTTPException(status_code=400, detail="条码格式无效")

    # 检查是否已添加
    cursor.execute(
        "SELECT id FROM pick_items WHERE order_id = ? AND sku_outer_id = ?",
        (order_id, sku_outer_id)
    )
    if cursor.fetchone():
        raise HTTPException(status_code=409, detail="该SKU已存在于取货单中")

    # 查询SKU信息（先查缓存，再查快麦API）
    sku_info = get_sku_info(sku_outer_id)
    if not sku_info:
        raise HTTPException(status_code=404, detail=f"未找到SKU信息: {sku_outer_id}")

    now = beijing_now()
    try:
        cursor.execute(
            """INSERT INTO pick_items (order_id, sku_outer_id, sys_item_id, sys_sku_id,
               properties_name, pic_path, status, supplier_name, supplier_code, remark, created_at)
               VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)""",
            (
                order_id,
                sku_outer_id,
                sku_info["sys_item_id"],
                sku_info["sys_sku_id"],
                sku_info["properties_name"],
                sku_info["pic_path"],
                sku_info["supplier_name"],
                sku_info["supplier_code"],
                sku_info["remark"],
                format_beijing(now),
            )
        )
        # 更新取货单总数
        cursor.execute(
            "UPDATE pick_orders SET total_count = total_count + 1 WHERE id = ?",
            (order_id,)
        )
        db.commit()

        cursor.execute("SELECT * FROM pick_items WHERE order_id = ? AND sku_outer_id = ?", (order_id, sku_outer_id))
        item_row = cursor.fetchone()
        return _row_to_item_response(item_row)
    except Exception as e:
        db.rollback()
        logger.error(f"添加取货明细失败: {e}")
        raise HTTPException(status_code=500, detail=f"添加取货明细失败: {e}")


@router.put("/{order_id}/items/{item_id}/complete", response_model=BaseResponse)
def complete_item(order_id: int, item_id: int) -> BaseResponse:
    """完成取货明细（幂等操作）"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_items WHERE id = ? AND order_id = ?", (item_id, order_id))
    item_row = cursor.fetchone()
    if not item_row:
        raise HTTPException(status_code=404, detail="取货明细不存在")

    # 幂等：已完成的不再处理
    if item_row["status"] == 1:
        return BaseResponse(message="该明细已完成")

    now = beijing_now()
    try:
        cursor.execute(
            "UPDATE pick_items SET status = 1, completed_at = ? WHERE id = ?",
            (format_beijing(now), item_id)
        )
        cursor.execute(
            "UPDATE pick_orders SET completed_count = completed_count + 1 WHERE id = ?",
            (order_id,)
        )
        # 检查是否全部完成
        cursor.execute(
            "SELECT total_count, completed_count + 1 as new_completed FROM pick_orders WHERE id = ?",
            (order_id,)
        )
        order_info = cursor.fetchone()
        if order_info and order_info["new_completed"] >= order_info["total_count"]:
            cursor.execute(
                "UPDATE pick_orders SET status = 1, completed_at = ? WHERE id = ?",
                (format_beijing(now), order_id)
            )
        db.commit()
        return BaseResponse(message="取货明细已完成")
    except Exception as e:
        db.rollback()
        logger.error(f"完成取货明细失败: {e}")
        raise HTTPException(status_code=500, detail=f"完成取货明细失败: {e}")


@router.put("/{order_id}/items/{item_id}/restore", response_model=BaseResponse)
def restore_item(order_id: int, item_id: int) -> BaseResponse:
    """恢复取货明细（撤销完成）"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_items WHERE id = ? AND order_id = ?", (item_id, order_id))
    item_row = cursor.fetchone()
    if not item_row:
        raise HTTPException(status_code=404, detail="取货明细不存在")

    if item_row["status"] == 0:
        return BaseResponse(message="该明细未完成，无需恢复")

    try:
        cursor.execute(
            "UPDATE pick_items SET status = 0, completed_at = NULL WHERE id = ?",
            (item_id,)
        )
        cursor.execute(
            "UPDATE pick_orders SET completed_count = completed_count - 1 WHERE id = ?",
            (order_id,)
        )
        db.commit()
        return BaseResponse(message="取货明细已恢复")
    except Exception as e:
        db.rollback()
        logger.error(f"恢复取货明细失败: {e}")
        raise HTTPException(status_code=500, detail=f"恢复取货明细失败: {e}")


@router.put("/{order_id}/complete-all", response_model=BaseResponse)
def complete_all_items(order_id: int) -> BaseResponse:
    """批量完成取货单所有明细"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if not order_row:
        raise HTTPException(status_code=404, detail="取货单不存在")

    now = beijing_now()
    try:
        # 完成所有未完成的明细
        cursor.execute(
            "UPDATE pick_items SET status = 1, completed_at = ? WHERE order_id = ? AND status = 0",
            (format_beijing(now), order_id)
        )
        updated_count = cursor.rowcount

        # 更新取货单完成数和状态
        cursor.execute(
            "UPDATE pick_orders SET completed_count = total_count, status = 1, completed_at = ? WHERE id = ?",
            (format_beijing(now), order_id)
        )
        db.commit()
        return BaseResponse(message=f"已批量完成{updated_count}条明细")
    except Exception as e:
        db.rollback()
        logger.error(f"批量完成失败: {e}")
        raise HTTPException(status_code=500, detail=f"批量完成失败: {e}")


@router.delete("/{order_id}", response_model=BaseResponse)
def delete_order(order_id: int) -> BaseResponse:
    """删除取货单（级联删除明细）"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT id FROM pick_orders WHERE id = ?", (order_id,))
    if not cursor.fetchone():
        raise HTTPException(status_code=404, detail="取货单不存在")

    try:
        # 先收集该订单关联的SKU（在级联删除前）
        cursor.execute(
            "SELECT DISTINCT sku_outer_id FROM pick_items WHERE order_id = ?", (order_id,)
        )
        deleted_skus = [row["sku_outer_id"] for row in cursor.fetchall()]

        # 删除取货单（级联删除明细）
        cursor.execute("DELETE FROM pick_orders WHERE id = ?", (order_id,))

        # 清理不被其他订单引用的SKU图片
        for sku_outer_id in deleted_skus:
            cursor.execute(
                "SELECT COUNT(*) as cnt FROM pick_items WHERE sku_outer_id = ? AND order_id != ?",
                (sku_outer_id, order_id)
            )
            ref_count = cursor.fetchone()["cnt"]
            if ref_count == 0:
                # 该SKU不被其他订单引用，清理图片
                _cleanup_sku_images(cursor, sku_outer_id)

        db.commit()
        return BaseResponse(message="取货单已删除")
    except Exception as e:
        db.rollback()
        logger.error(f"删除取货单失败: {e}")
        raise HTTPException(status_code=500, detail=f"删除取货单失败: {e}")


@router.delete("/{order_id}/items/{item_id}", response_model=BaseResponse)
def delete_item(order_id: int, item_id: int) -> BaseResponse:
    """删除取货明细"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_items WHERE id = ? AND order_id = ?", (item_id, order_id))
    item_row = cursor.fetchone()
    if not item_row:
        raise HTTPException(status_code=404, detail="取货明细不存在")

    try:
        # 如果该明细已完成，需减少完成数
        if item_row["status"] == 1:
            cursor.execute(
                "UPDATE pick_orders SET completed_count = completed_count - 1 WHERE id = ?",
                (order_id,)
            )
        cursor.execute(
            "UPDATE pick_orders SET total_count = total_count - 1 WHERE id = ?",
            (order_id,)
        )
        cursor.execute("DELETE FROM pick_items WHERE id = ?", (item_id,))
        db.commit()
        return BaseResponse(message="取货明细已删除")
    except Exception as e:
        db.rollback()
        logger.error(f"删除取货明细失败: {e}")
        raise HTTPException(status_code=500, detail=f"删除取货明细失败: {e}")


@router.get("/{order_id}/suppliers", response_model=List[str])
def get_suppliers(order_id: int) -> List[str]:
    """获取取货单中的供应商列表（去重）"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute(
        "SELECT DISTINCT supplier_name FROM pick_items WHERE order_id = ? AND supplier_name != ''",
        (order_id,)
    )
    rows = cursor.fetchall()
    return [row["supplier_name"] for row in rows]


# ==================== 辅助函数 ====================

def _row_to_order_response(row: sqlite3.Row) -> OrderResponse:
    """数据库行转OrderResponse"""
    return OrderResponse(
        id=row["id"],
        orderNo=row["order_no"],
        status=row["status"],
        completionType=row["completion_type"],
        totalCount=row["total_count"],
        completedCount=row["completed_count"],
        createdAt=row["created_at"],
        completedAt=row["completed_at"],
        expireAt=row["expire_at"],
    )


def _row_to_item_response(row: sqlite3.Row) -> ItemResponse:
    """数据库行转ItemResponse"""
    return ItemResponse(
        id=row["id"],
        skuOuterId=row["sku_outer_id"],
        sysItemId=row["sys_item_id"],
        sysSkuId=row["sys_sku_id"],
        propertiesName=row["properties_name"],
        picPath=row["pic_path"],
        status=row["status"],
        supplierName=row["supplier_name"],
        supplierCode=row["supplier_code"],
        remark=row["remark"],
        createdAt=row["created_at"],
        completedAt=row["completed_at"],
    )


def _cleanup_sku_images(cursor, sku_outer_id: str) -> None:
    """清理不被其他订单引用的SKU图片文件和记录"""
    try:
        cursor.execute(
            "SELECT file_path FROM product_images WHERE sku_outer_id = ?",
            (sku_outer_id,)
        )
        image_rows = cursor.fetchall()
        for img_row in image_rows:
            file_full_path = os.path.join(IMAGE_DIR, img_row["file_path"])
            try:
                if os.path.exists(file_full_path):
                    os.remove(file_full_path)
            except IOError as e:
                logger.warning(f"删除SKU图片文件失败: {e}")
        cursor.execute(
            "DELETE FROM product_images WHERE sku_outer_id = ?",
            (sku_outer_id,)
        )
        if image_rows:
            logger.info(f"已清理SKU {sku_outer_id} 的{len(image_rows)}张图片")
    except Exception as e:
        logger.error(f"清理SKU图片失败 sku_outer_id={sku_outer_id}: {e}")
