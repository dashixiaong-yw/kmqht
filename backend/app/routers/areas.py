"""拣货区路由 - 拣货区CRUD"""

import logging

from fastapi import APIRouter, Depends, HTTPException

from app.auth import check_permission, get_current_user
from app.database import get_db
from app.models import AreaListResponse, AreaRequest, AreaResponse, BaseResponse
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/areas", tags=["拣货区"])


@router.get("", response_model=AreaListResponse)
def list_areas(user: dict = Depends(get_current_user)) -> AreaListResponse:
    """获取所有拣货区"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_areas ORDER BY id")
    rows = cursor.fetchall()

    areas = [
        AreaResponse(
            id=row["id"],
            name=row["name"],
            createdAt=row["created_at"],
        )
        for row in rows
    ]
    return AreaListResponse(data=areas)


@router.post("", response_model=AreaResponse)
def create_area(req: AreaRequest, user: dict = Depends(check_permission("settings"))) -> AreaResponse:
    """创建拣货区"""
    db = get_db()
    cursor = db.cursor()

    # 检查名称是否重复
    cursor.execute("SELECT id FROM pick_areas WHERE name = ?", (req.name,))
    if cursor.fetchone():
        raise HTTPException(status_code=409, detail=f"拣货区'{req.name}'已存在")

    now = beijing_now()
    try:
        cursor.execute(
            "INSERT INTO pick_areas (name, created_at) VALUES (?, ?)",
            (req.name, format_beijing(now))
        )
        db.commit()

        cursor.execute("SELECT * FROM pick_areas WHERE name = ?", (req.name,))
        row = cursor.fetchone()
        return AreaResponse(
            id=row["id"],
            name=row["name"],
            createdAt=row["created_at"],
        )
    except Exception as e:
        db.rollback()
        logger.error(f"创建拣货区失败: {e}")
        raise HTTPException(status_code=500, detail="创建拣货区失败，请稍后重试")


@router.delete("/{area_id}", response_model=BaseResponse)
def delete_area(area_id: int, user: dict = Depends(check_permission("settings"))) -> BaseResponse:
    """删除拣货区"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT id FROM pick_areas WHERE id = ?", (area_id,))
    if not cursor.fetchone():
        raise HTTPException(status_code=404, detail="拣货区不存在")

    try:
        cursor.execute("DELETE FROM pick_areas WHERE id = ?", (area_id,))
        db.commit()
        return BaseResponse(message="拣货区已删除")
    except Exception as e:
        db.rollback()
        logger.error(f"删除拣货区失败: {e}")
        raise HTTPException(status_code=500, detail="删除拣货区失败，请稍后重试")
