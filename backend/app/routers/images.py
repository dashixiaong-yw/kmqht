"""图片路由 - 图片上传/查询/删除"""

import logging
import os
import sqlite3
import threading
import time
import uuid
from typing import Dict, List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

from app.auth import check_permission, get_current_user
from app.config import IMAGE_DIR
from app.database import get_db
from app.models import BaseResponse, ImageListResponse, ImageResponse
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api", tags=["图片管理"])

# 允许的图片扩展名
_ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}

# 最大文件大小（2MB）
_MAX_FILE_SIZE = 2 * 1024 * 1024

# 上传速率限制：每用户每分钟最多10次
_UPLOAD_RATE_LIMIT = 10
_UPLOAD_RATE_WINDOW = 60
_upload_counts: Dict[int, List[float]] = {}
_upload_rate_lock = threading.Lock()


def _check_upload_rate(user_id: int) -> None:
    """检查上传速率限制"""
    import time as _time
    with _upload_rate_lock:
        now = _time.time()
        if user_id not in _upload_counts:
            _upload_counts[user_id] = []
        # 清理过期记录
        _upload_counts[user_id] = [t for t in _upload_counts[user_id] if now - t < _UPLOAD_RATE_WINDOW]
        if len(_upload_counts[user_id]) >= _UPLOAD_RATE_LIMIT:
            raise HTTPException(status_code=429, detail="上传过于频繁，请稍后重试")
        _upload_counts[user_id].append(now)


@router.post("/upload", response_model=ImageResponse)
async def upload_image(
    skuOuterId: str = Form(..., max_length=64, description="SKU外部编码"),
    imageType: str = Form(..., description="图片类型: area/box"),
    file: UploadFile = File(..., description="图片文件"),
    user: dict = Depends(get_current_user),
) -> ImageResponse:
    """上传商品图片（multipart表单），需对应图片管理权限"""
    # 上传速率限制
    _check_upload_rate(user["user_id"])

    # 验证图片类型
    if imageType not in ("area", "box"):
        raise HTTPException(status_code=400, detail="imageType必须为area或box")

    # 权限校验：area→manage_area_image, box→manage_box_image
    required_perm = "manage_area_image" if imageType == "area" else "manage_box_image"
    if required_perm not in user["permissions"]:
        raise HTTPException(status_code=403, detail=f"无权限执行此操作: {required_perm}")

    # 验证文件扩展名
    _, ext = os.path.splitext(file.filename or "")
    ext = ext.lower()
    if ext not in _ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"不支持的图片格式: {ext}")

    # 检查是否已存在同类型图片（先记录，稍后删除）
    db = get_db()
    cursor = db.cursor()
    cursor.execute(
        "SELECT id, file_path FROM product_images WHERE sku_outer_id = ? AND image_type = ?",
        (skuOuterId, imageType)
    )
    existing = cursor.fetchone()

    # 先保存新文件，成功后再删除旧文件（防止新文件保存失败时旧文件已丢失）
    now = beijing_now()
    date_dir = now.strftime("%Y%m%d")
    save_dir = os.path.join(IMAGE_DIR, date_dir)
    os.makedirs(save_dir, exist_ok=True)

    file_name = f"{skuOuterId}_{imageType}_{uuid.uuid4().hex[:8]}{ext}"
    file_path = os.path.join(save_dir, file_name)

    try:
        content = await file.read()
        # 检查文件大小
        if len(content) > _MAX_FILE_SIZE:
            raise HTTPException(status_code=400, detail=f"文件大小超过限制（最大2MB），当前{len(content) // 1024}KB")
        with open(file_path, "wb") as f:
            f.write(content)
    except IOError as e:
        logger.error(f"保存图片失败: {e}")
        raise HTTPException(status_code=500, detail="保存图片失败，请稍后重试")

    # 新文件保存成功，删除旧图片文件和记录
    if existing:
        old_file_path = os.path.join(IMAGE_DIR, existing["file_path"])
        try:
            if os.path.exists(old_file_path):
                os.remove(old_file_path)
        except IOError as e:
            logger.warning(f"删除旧图片文件失败: {e}")
        cursor.execute("DELETE FROM product_images WHERE id = ?", (existing["id"],))

    # 构建URL
    image_url = f"/images/{date_dir}/{file_name}"
    relative_file_path = f"{date_dir}/{file_name}"

    try:
        cursor.execute(
            """INSERT INTO product_images (sku_outer_id, image_type, image_url, file_path, created_at)
               VALUES (?, ?, ?, ?, ?)""",
            (skuOuterId, imageType, image_url, relative_file_path, format_beijing(now))
        )
        db.commit()

        cursor.execute(
            "SELECT * FROM product_images WHERE sku_outer_id = ? AND image_type = ?",
            (skuOuterId, imageType)
        )
        row = cursor.fetchone()
        return _row_to_image_response(row)
    except Exception as e:
        db.rollback()
        # 删除已保存的文件
        if os.path.exists(file_path):
            os.remove(file_path)
        logger.error(f"保存图片记录失败: {e}")
        raise HTTPException(status_code=500, detail="保存图片记录失败，请稍后重试")


@router.get("/images/{sku_outer_id}", response_model=ImageListResponse)
def get_images(sku_outer_id: str, user: dict = Depends(get_current_user)) -> ImageListResponse:
    """获取SKU的所有图片"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute(
        "SELECT * FROM product_images WHERE sku_outer_id = ? ORDER BY image_type",
        (sku_outer_id,)
    )
    rows = cursor.fetchall()
    images = [_row_to_image_response(row) for row in rows]
    return ImageListResponse(data=images)


@router.delete("/images/{image_id}", response_model=BaseResponse)
def delete_image(
    image_id: int,
    user: dict = Depends(get_current_user),
) -> BaseResponse:
    """删除图片，需对应图片管理权限"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM product_images WHERE id = ?", (image_id,))
    row = cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="图片不存在")

    # 权限校验：根据图片类型检查对应权限
    required_perm = "manage_area_image" if row["image_type"] == "area" else "manage_box_image"
    if required_perm not in user["permissions"]:
        raise HTTPException(status_code=403, detail=f"无权限执行此操作: {required_perm}")

    # 删除文件（路径遍历防护：确保file_path在IMAGE_DIR内）
    file_full_path = os.path.normpath(os.path.join(IMAGE_DIR, row["file_path"]))
    if not file_full_path.startswith(os.path.normpath(IMAGE_DIR)):
        logger.warning(f"路径遍历攻击被阻止: {row['file_path']}")
        raise HTTPException(status_code=400, detail="文件路径非法")
    try:
        if os.path.exists(file_full_path):
            os.remove(file_full_path)
    except IOError as e:
        logger.warning(f"删除图片文件失败: {e}")

    try:
        cursor.execute("DELETE FROM product_images WHERE id = ?", (image_id,))
        db.commit()
        return BaseResponse(message="图片已删除")
    except Exception as e:
        db.rollback()
        logger.error(f"删除图片记录失败: {e}")
        raise HTTPException(status_code=500, detail="删除图片记录失败，请稍后重试")


def _row_to_image_response(row: sqlite3.Row) -> ImageResponse:
    """数据库行转ImageResponse"""
    return ImageResponse(
        id=row["id"],
        skuOuterId=row["sku_outer_id"],
        imageType=row["image_type"],
        imageUrl=row["image_url"],
        filePath=row["file_path"],
        createdAt=row["created_at"],
    )
