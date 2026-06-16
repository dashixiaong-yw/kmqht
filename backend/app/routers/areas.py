"""拣货区路由 - 拣货区CRUD"""

import logging
from typing import List

from fastapi import APIRouter, HTTPException

from app.database import get_db
from app.models import AreaRequest, AreaResponse, BaseResponse
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/areas", tags=["拣货区"])


@router.get("", response_model=List[AreaResponse])
def list_areas() -> List[AreaResponse]:
    """获取所有拣货区"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM pick_areas ORDER BY id")
    rows = cursor.fetchall()

    return [
        AreaResponse(
            id=row["id"],
            name=row["name"],
            created_at=row["created_at"],
        )
        for row in rows
    ]


@router.post("", response_model=AreaResponse)
def create_area(req: AreaRequest) -> AreaResponse:
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
            created_at=row["created_at"],
        )
    except Exception as e:
        db.rollback()
        logger.error(f"创建拣货区失败: {e}")
        raise HTTPException(status_code=500, detail=f"创建拣货区失败: {e}")


@router.delete("/{area_id}", response_model=BaseResponse)
def delete_area(area_id: int) -> BaseResponse:
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
        raise HTTPException(status_code=500, detail=f"删除拣货区失败: {e}")
