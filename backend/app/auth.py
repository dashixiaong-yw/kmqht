"""认证模块 - 用户Token校验 + 权限检查"""

import hashlib
import hmac
import logging
from typing import List, Optional

from fastapi import Depends, HTTPException, Request

from app.config import API_KEY
from app.database import get_db

logger = logging.getLogger(__name__)

VALID_PERMISSIONS = {"settings", "update_supplier", "update_remark", "manage_area_image", "manage_box_image"}


def get_current_user(request: Request) -> dict:
    """
    从X-User-Token头解析当前用户
    或从X-API-Key头验证管理员身份（管理后台用）
    返回 {"user_id": int, "username": str, "permissions": List[str]}
    """
    # 支持API Key认证（管理后台使用）
    api_key = request.headers.get("X-API-Key", "")
    if api_key and API_KEY and hmac.compare_digest(api_key, API_KEY):
        return {
            "user_id": 0,
            "username": "admin",
            "permissions": list(VALID_PERMISSIONS)
        }

    # 原有User Token认证
    token = request.headers.get("X-User-Token", "")
    if not token:
        raise HTTPException(status_code=401, detail="未登录，请先登录")

    db = get_db()
    cursor = db.cursor()

    # 查询token有效性
    cursor.execute(
        """SELECT ut.user_id, ut.expires_at, u.username, u.is_active
           FROM user_tokens ut
           JOIN users u ON ut.user_id = u.id
           WHERE ut.token = ?""",
        (token,)
    )
    row = cursor.fetchone()

    if not row:
        raise HTTPException(status_code=401, detail="登录已失效，请重新登录")

    from app.utils.time_utils import beijing_now, parse_beijing
    expires_at = parse_beijing(row["expires_at"])
    if beijing_now() > expires_at:
        # token过期，清理
        cursor.execute("DELETE FROM user_tokens WHERE token = ?", (token,))
        db.commit()
        raise HTTPException(status_code=401, detail="登录已过期，请重新登录")

    if not row["is_active"]:
        raise HTTPException(status_code=403, detail="用户已被禁用")

    user_id = row["user_id"]
    username = row["username"]

    # 查询用户权限
    cursor.execute(
        "SELECT permission FROM user_permissions WHERE user_id = ?",
        (user_id,)
    )
    permissions = [r["permission"] for r in cursor.fetchall()]

    return {"user_id": user_id, "username": username, "permissions": permissions}


def check_permission(perm: str):
    """
    权限检查依赖函数
    用法: Depends(check_permission("settings"))
    """
    def _check(user: dict = Depends(get_current_user)) -> dict:
        if perm not in user["permissions"]:
            raise HTTPException(status_code=403, detail=f"无权限执行此操作: {perm}")
        return user
    return _check
