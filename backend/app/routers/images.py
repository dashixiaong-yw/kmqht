"""图片路由 - 图片上传/查询/删除"""

import logging
import os
import uuid
from typing import List

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.config import IMAGE_DIR
from app.database import get_db
from app.models import BaseResponse, ImageListResponse, ImageResponse
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api", tags=["图片管理"])

# 允许的图片扩展名
_ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


@router.post("/upload", response_model=ImageResponse)
async def upload_image(
    skuOuterId: str = Form(..., max_length=64, description="SKU外部编码"),
    imageType: str = Form(..., description="图片类型: area/box"),
    file: UploadFile = File(..., description="图片文件"),
) -> ImageResponse:
    """上传商品图片（multipart表单）"""
    # 验证图片类型
    if imageType not in ("area", "box"):
        raise HTTPException(status_code=400, detail="imageType必须为area或box")

    # 验证文件扩展名
    _, ext = os.path.splitext(file.filename or "")
    ext = ext.lower()
    if ext not in _ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"不支持的图片格式: {ext}")

    # 检查是否已存在同类型图片，如存在则先删除旧文件
    db = get_db()
    cursor = db.cursor()
    cursor.execute(
        "SELECT id, file_path FROM product_images WHERE sku_outer_id = ? AND image_type = ?",
        (skuOuterId, imageType)
    )
    existing = cursor.fetchone()
    if existing:
        # 删除旧图片文件
        old_file_path = os.path.join(IMAGE_DIR, existing["file_path"])
        try:
            if os.path.exists(old_file_path):
                os.remove(old_file_path)
        except IOError as e:
            logger.warning(f"删除旧图片文件失败: {e}")
        # 删除旧记录
        cursor.execute("DELETE FROM product_images WHERE id = ?", (existing["id"],))

    # 保存文件
    now = beijing_now()
    date_dir = now.strftime("%Y%m%d")
    save_dir = os.path.join(IMAGE_DIR, date_dir)
    os.makedirs(save_dir, exist_ok=True)

    file_name = f"{skuOuterId}_{imageType}_{uuid.uuid4().hex[:8]}{ext}"
    file_path = os.path.join(save_dir, file_name)

    try:
        content = await file.read()
        with open(file_path, "wb") as f:
            f.write(content)
    except IOError as e:
        logger.error(f"保存图片失败: {e}")
        raise HTTPException(status_code=500, detail=f"保存图片失败: {e}")

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
        raise HTTPException(status_code=500, detail=f"保存图片记录失败: {e}")


@router.get("/images/{sku_outer_id}", response_model=ImageListResponse)
def get_images(sku_outer_id: str) -> ImageListResponse:
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
def delete_image(image_id: int) -> BaseResponse:
    """删除图片"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT * FROM product_images WHERE id = ?", (image_id,))
    row = cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="图片不存在")

    # 删除文件
    file_full_path = os.path.join(IMAGE_DIR, row["file_path"])
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
        raise HTTPException(status_code=500, detail=f"删除图片记录失败: {e}")


def _row_to_image_response(row) -> ImageResponse:
    """数据库行转ImageResponse"""
    return ImageResponse(
        id=row["id"],
        skuOuterId=row["sku_outer_id"],
        imageType=row["image_type"],
        imageUrl=row["image_url"],
        filePath=row["file_path"],
        createdAt=row["created_at"],
    )
