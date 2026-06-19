"""取货单路由 - 取货单CRUD及明细操作"""

import asyncio
import logging
import os
import sqlite3
from datetime import timedelta
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query

from app.auth import get_current_user
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


@router.post("", response_model=OrderResponse)
def create_order(req: CreateOrderRequest, user: dict = Depends(get_current_user)) -> OrderResponse:
    """创建取货单，自动生成单号: yyyyMMdd-拣货区X"""
    db = get_db()
    cursor = db.cursor()

    now = beijing_now()
    date_str = now.strftime("%Y%m%d")

    # 查找当日该拣货区已有单号，递增序号
    # 转义SQL LIKE通配符，防止拣货区名称包含%或_导致意外匹配
    escaped_area_name = req.areaName.replace("%", "\\%").replace("_", "\\_")
    prefix = f"{req.areaName}-{date_str}"
    escaped_prefix = f"{escaped_area_name}-{date_str}"
    cursor.execute(
        "SELECT order_no FROM pick_orders WHERE order_no LIKE ? ESCAPE '\\' ORDER BY id DESC",
        (f"{escaped_prefix}-%",)
    )
    existing = cursor.fetchall()
    # 单号格式：拣货区-yyyyMMdd-X（X从1开始递增）
    order_no = f"{prefix}-{len(existing) + 1}"

    # 默认12小时后过期
    expire_at = now + timedelta(hours=12)

    # 并发重试：UNIQUE约束冲突时重新生成单号
    max_retries = 3
    for attempt in range(max_retries):
        try:
            cursor.execute(
                """INSERT INTO pick_orders (order_no, status, completion_type, total_count, completed_count, created_at, expire_at, created_by, assigned_to, visibility)
                   VALUES (?, 0, 0, 0, 0, ?, ?, ?, ?, 'private')""",
                (order_no, format_beijing(now), format_beijing(expire_at), user["username"], user["username"])
            )
            db.commit()
            break
        except Exception as e:
            db.rollback()
            if "UNIQUE constraint" in str(e) and attempt < max_retries - 1:
                # 单号冲突，重新查询并生成
                cursor.execute(
                    "SELECT order_no FROM pick_orders WHERE order_no LIKE ? ESCAPE '\\' ORDER BY id DESC",
                    (f"{escaped_prefix}-%",)
                )
                existing = cursor.fetchall()
                order_no = f"{prefix}-{len(existing) + 1}"
                logger.warning(f"单号冲突，重试生成: {order_no} (attempt={attempt + 1})")
            else:
                logger.error(f"创建取货单失败: {e}")
                raise HTTPException(status_code=409, detail="该SKU已存在于取货单中") HTTPException(status_code=409, detail="该SKU已存在于取货单中") HTTPException(status_code=500, detail="创建取货单失败，请稍后重试")

    cursor.execute("SELECT * FROM pick_orders WHERE order_no = ?", (order_no,))
    row = cursor.fetchone()
    return _row_to_order_response(row)


@router.get("", response_model=OrderListResponse)
def list_orders(status: Optional[int] = Query(None, description="状态过滤: 0=进行中, 1=已完成"), user: dict = Depends(get_current_user)) -> OrderListResponse:
    """获取取货单列表，按访问权限+创建时间倒序"""
    db = get_db()
    cursor = db.cursor()
    username = user["username"]

    base_sql = "SELECT * FROM pick_orders WHERE (assigned_to = ? OR created_by = ? OR (visibility = 'public' AND assigned_to = ''))"
    params: list = [username, username]

    if status is not None:
        base_sql += " AND status = ?"
        params.append(status)

    base_sql += " ORDER BY created_at DESC"
    cursor.execute(base_sql, params)

    rows = cursor.fetchall()
    orders = [_row_to_order_response(row) for row in rows]
    return OrderListResponse(data=orders)


@router.post("/{order_id}/publish", response_model=BaseResponse)
def publish_order(order_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """发布取货单到公共列表（仅创建者可操作）"""
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    row = cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="取货单不存在")
    if row["created_by"] != user["username"]:
        raise HTTPException(status_code=403, detail="仅创建者可发布此取货单")
    cursor.execute(
        "UPDATE pick_orders SET visibility = 'public', assigned_to = '' WHERE id = ?",
        (order_id,)
    )
    db.commit()
    return BaseResponse(message="取货单已发布")


@router.post("/{order_id}/claim", response_model=BaseResponse)
def claim_order(order_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """领取公开取货单（仅可领取未被领取的）"""
    db = get_db()
    cursor = db.cursor()
    cursor.execute(
        "UPDATE pick_orders SET visibility = 'private', assigned_to = ? WHERE id = ? AND visibility = 'public' AND assigned_to = ''",
        (user["username"], order_id)
    )
    if cursor.rowcount == 0:
        db.rollback()
        raise HTTPException(status_code=409, detail="此取货单已被其他用户领取")
    db.commit()
    return BaseResponse(message="取货单领取成功")


@router.get("/{order_id}", response_model=OrderDetailResponse)
def get_order(order_id: int, supplierName: Optional[str] = Query(None, description="供应商名称过滤"), user: dict = Depends(get_current_user)) -> OrderDetailResponse:
    """获取取货单详情（含明细），支持供应商过滤"""
    order_row = _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

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
        createdBy=order.createdBy,
        assignedTo=order.assignedTo,
        visibility=order.visibility,
        items=items,
    )


@router.post("/{order_id}/items", response_model=ItemResponse)
def add_item(order_id: int, req: AddItemRequest, user: dict = Depends(get_current_user)) -> ItemResponse:
    """添加取货明细，后端查询快麦API并缓存"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    # 检查取货单状态
    cursor.execute("SELECT status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
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

    # 同步调用快麦API获取SKU信息（同步def在线程池中运行，连接独占，无并发问题）
    try:
        sku_info = asyncio.run(get_sku_info(sku_outer_id))
    except Exception as e:
        logger.error(f"查询SKU信息失败: {e}")
        raise HTTPException(status_code=500, detail=f"查询SKU信息失败: {e}")
    if not sku_info:
        raise HTTPException(status_code=404, detail=f"未找到SKU信息: {sku_outer_id}")

    now = beijing_now()
    try:
        # 重新验证订单存在性（缩小TOCTOU窗口）
        cursor.execute("SELECT id FROM pick_orders WHERE id = ?", (order_id,))
        if not cursor.fetchone():
            raise HTTPException(status_code=410, detail="取货单已被删除")

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
    except sqlite3.IntegrityError as e:
        db.rollback()
        if "FOREIGN KEY constraint failed" in str(e):
            logger.warning(f"取货单 {order_id} 已被删除，添加明细失败")
            raise HTTPException(status_code=410, detail="取货单已被删除，请刷新列表")
        raise HTTPException(status_code=409, detail="该SKU已存在于取货单中")
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        logger.error(f"添加取货明细失败: {e}")
        raise HTTPException(status_code=500, detail="添加取货明细失败，请稍后重试")


@router.put("/{order_id}/items/{item_id}/complete", response_model=BaseResponse)
def complete_item(order_id: int, item_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """完成取货明细（幂等操作）"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_items WHERE id = ? AND order_id = ?", (item_id, order_id))
    item_row = cursor.fetchone()
    if not item_row:
        raise HTTPException(status_code=404, detail="取货明细不存在")

    cursor.execute("SELECT status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if order_row and order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="已完成的取货单不能完成明细")

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
            "SELECT total_count, completed_count as new_completed FROM pick_orders WHERE id = ?",
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
        raise HTTPException(status_code=500, detail="完成取货明细失败，请稍后重试")


@router.put("/{order_id}/items/{item_id}/restore", response_model=BaseResponse)
def restore_item(order_id: int, item_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """恢复取货明细（撤销完成）"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_items WHERE id = ? AND order_id = ?", (item_id, order_id))
    item_row = cursor.fetchone()
    if not item_row:
        raise HTTPException(status_code=404, detail="取货明细不存在")

    if item_row["status"] == 0:
        return BaseResponse(message="该明细未完成，无需恢复")

    # 检查取货单是否已完成
    cursor.execute("SELECT status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if order_row and order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="已完成的取货单不能恢复明细")

    try:
        cursor.execute(
            "UPDATE pick_items SET status = 0, completed_at = NULL WHERE id = ?",
            (item_id,)
        )
        cursor.execute(
            "UPDATE pick_orders SET completed_count = completed_count - 1 WHERE id = ? AND completed_count > 0",
            (order_id,)
        )
        # 检查是否需要将取货单状态从已完成恢复为进行中
        cursor.execute(
            "SELECT total_count, completed_count FROM pick_orders WHERE id = ?",
            (order_id,)
        )
        order_info = cursor.fetchone()
        if order_info and order_info["completed_count"] < order_info["total_count"]:
            cursor.execute(
                "UPDATE pick_orders SET status = 0, completed_at = NULL WHERE id = ?",
                (order_id,)
            )
        db.commit()
        return BaseResponse(message="取货明细已恢复")
    except Exception as e:
        db.rollback()
        logger.error(f"恢复取货明细失败: {e}")
        raise HTTPException(status_code=500, detail="恢复取货明细失败，请稍后重试")


@router.put("/{order_id}/complete-all", response_model=BaseResponse)
def complete_all_items(order_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """批量完成取货单所有明细"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()

    # 空取货单不允许完成
    if order_row["total_count"] == 0:
        raise HTTPException(status_code=400, detail="取货单无明细，无法完成")

    # 幂等：已完成的不再处理
    if order_row["status"] == 1:
        return BaseResponse(message="取货单已完成")

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
        raise HTTPException(status_code=500, detail="批量完成失败，请稍后重试")


@router.delete("/{order_id}", response_model=BaseResponse)
def delete_order(order_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """删除取货单（仅创建者可删除，级联删除明细）"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT id, status, created_by FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()

    if order_row["created_by"] != user["username"]:
        raise HTTPException(status_code=403, detail="仅创建者可删除此取货单")

    # 不允许删除已完成的取货单
    if order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="已完成的取货单不能删除")

    try:
        # 先收集该订单关联的SKU（在级联删除前）
        cursor.execute(
            "SELECT DISTINCT sku_outer_id FROM pick_items WHERE order_id = ?", (order_id,)
        )
        deleted_skus = [row["sku_outer_id"] for row in cursor.fetchall()]

        # 删除取货单（级联删除明细）
        cursor.execute("DELETE FROM pick_orders WHERE id = ? AND status != 1", (order_id,))

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
        raise HTTPException(status_code=500, detail="删除取货单失败，请稍后重试")


@router.delete("/{order_id}/items/{item_id}", response_model=BaseResponse)
def delete_item(order_id: int, item_id: int, user: dict = Depends(get_current_user)) -> BaseResponse:
    """删除取货明细"""
    _check_order_access(order_id, user["username"])
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_items WHERE id = ? AND order_id = ?", (item_id, order_id))
    item_row = cursor.fetchone()
    if not item_row:
        raise HTTPException(status_code=404, detail="取货明细不存在")

    cursor.execute("SELECT status FROM pick_orders WHERE id = ?", (order_id,))
    order_row = cursor.fetchone()
    if order_row and order_row["status"] == 1:
        raise HTTPException(status_code=400, detail="已完成的取货单不能操作明细")

    try:
        # 如果该明细已完成，需减少完成数
        if item_row["status"] == 1:
            cursor.execute(
                "UPDATE pick_orders SET completed_count = completed_count - 1 WHERE id = ? AND completed_count > 0",
                (order_id,)
            )
        cursor.execute(
            "UPDATE pick_orders SET total_count = total_count - 1 WHERE id = ?",
            (order_id,)
        )
        cursor.execute("DELETE FROM pick_items WHERE id = ?", (item_id,))

        # 检查是否需要将取货单状态从已完成恢复为进行中
        cursor.execute(
            "SELECT total_count, completed_count, status FROM pick_orders WHERE id = ?",
            (order_id,)
        )
        order_info = cursor.fetchone()
        if order_info and order_info["status"] == 1:
            new_total = order_info["total_count"]
            new_completed = order_info["completed_count"]
            if new_completed < new_total:
                cursor.execute(
                    "UPDATE pick_orders SET status = 0, completed_at = NULL WHERE id = ?",
                    (order_id,)
                )

        db.commit()
        return BaseResponse(message="取货明细已删除")
    except Exception as e:
        db.rollback()
        logger.error(f"删除取货明细失败: {e}")
        raise HTTPException(status_code=500, detail="删除取货明细失败，请稍后重试")


@router.get("/{order_id}/suppliers", response_model=List[str])
def get_suppliers(order_id: int, user: dict = Depends(get_current_user)) -> List[str]:
    """获取取货单中的供应商列表（去重）"""
    _check_order_access(order_id, user["username"])
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
        createdBy=row["created_by"],
        assignedTo=row["assigned_to"],
        visibility=row["visibility"],
    )


def _check_order_access(order_id: int, username: str) -> sqlite3.Row:
    """检查当前用户是否有权访问此取货单，通过返回行数据，不通过抛403"""
    db = get_db()
    cursor = db.cursor()
    cursor.execute("SELECT * FROM pick_orders WHERE id = ?", (order_id,))
    row = cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="取货单不存在")
    if row["assigned_to"] != username and row["created_by"] != username and not (
        row["visibility"] == "public" and not row["assigned_to"]
    ):
        raise HTTPException(status_code=403, detail="无权操作此取货单")
    return row


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


def _cleanup_sku_images(cursor: sqlite3.Cursor, sku_outer_id: str) -> None:
    """清理不被其他订单引用的SKU图片文件和记录"""
    try:
        cursor.execute(
            "SELECT file_path FROM product_images WHERE sku_outer_id = ?",
            (sku_outer_id,)
        )
        image_rows = cursor.fetchall()
        for img_row in image_rows:
            file_full_path = os.path.normpath(os.path.join(IMAGE_DIR, img_row["file_path"]))
            if not file_full_path.startswith(os.path.normpath(IMAGE_DIR)):
                logger.warning(f"路径遍历攻击被阻止: {img_row['file_path']}")
                continue
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
