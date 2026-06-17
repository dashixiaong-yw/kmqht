"""用户管理路由 - 登录/用户CRUD/权限管理"""

import logging
import threading
import uuid
from datetime import timedelta
from typing import List

from fastapi import APIRouter, Depends, HTTPException

from app.auth import VALID_PERMISSIONS, check_permission, get_current_user
from app.database import get_db
from app.models import (
    BaseResponse,
    CreateUserRequest,
    LoginRequest,
    LoginResponse,
    UpdateUserRequest,
    UserListResponse,
    UserResponse,
)
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/users", tags=["用户管理"])

# Token有效期7天
TOKEN_EXPIRE_DAYS = 7

# 登录限流：5次失败锁定5分钟
_LOGIN_FAIL_COUNTS: dict[str, int] = {}
_LOGIN_LOCK_UNTIL: dict[str, float] = {}
_login_lock = threading.Lock()
_MAX_LOGIN_FAILS = 5
_LOGIN_LOCK_SECONDS = 300


@router.post("/login", response_model=LoginResponse)
def login(req: LoginRequest) -> LoginResponse:
    """用户登录（含限流保护：5次失败锁定5分钟）"""
    import time

    # 限流检查
    with _login_lock:
        lock_until = _LOGIN_LOCK_UNTIL.get(req.username, 0)
        if lock_until > time.time():
            remaining = int(lock_until - time.time())
            raise HTTPException(
                status_code=429,
                detail=f"登录尝试过于频繁，请{remaining}秒后再试"
            )

    db = get_db()
    cursor = db.cursor()

    # 查询用户
    cursor.execute(
        "SELECT id, username, password_hash, is_active FROM users WHERE username = ?",
        (req.username,)
    )
    row = cursor.fetchone()

    if not row:
        _record_login_fail(req.username)
        raise HTTPException(status_code=401, detail="用户名或密码错误")

    if not row["is_active"]:
        raise HTTPException(status_code=403, detail="用户已被禁用")

    # 校验密码
    stored_hash = row["password_hash"]
    if not _verify_password(req.password, stored_hash):
        _record_login_fail(req.username)
        raise HTTPException(status_code=401, detail="用户名或密码错误")

    # 登录成功，清除失败计数
    with _login_lock:
        _LOGIN_FAIL_COUNTS.pop(req.username, None)
        _LOGIN_LOCK_UNTIL.pop(req.username, None)

    user_id = row["id"]

    # 生成token
    token = uuid.uuid4().hex
    now = beijing_now()
    expires_at = now + timedelta(days=TOKEN_EXPIRE_DAYS)

    # 清理该用户的旧token
    cursor.execute("DELETE FROM user_tokens WHERE user_id = ?", (user_id,))

    # 保存新token
    cursor.execute(
        "INSERT INTO user_tokens (user_id, token, expires_at) VALUES (?, ?, ?)",
        (user_id, token, format_beijing(expires_at))
    )
    db.commit()

    # 查询权限
    cursor.execute(
        "SELECT permission FROM user_permissions WHERE user_id = ?",
        (user_id,)
    )
    permissions = [r["permission"] for r in cursor.fetchall()]

    logger.info(f"用户登录成功: {req.username}")

    return LoginResponse(
        token=token,
        username=req.username,
        permissions=permissions
    )


@router.get("/me", response_model=UserResponse)
def get_me(user: dict = Depends(get_current_user)) -> UserResponse:
    """获取当前用户信息"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute(
        "SELECT id, username, is_active, created_at FROM users WHERE id = ?",
        (user["user_id"],)
    )
    row = cursor.fetchone()

    if not row:
        raise HTTPException(status_code=404, detail="用户不存在")

    return UserResponse(
        id=row["id"],
        username=row["username"],
        isActive=bool(row["is_active"]),
        permissions=user["permissions"],
        createdAt=row["created_at"]
    )


@router.get("", response_model=UserListResponse)
def list_users(user: dict = Depends(check_permission("settings"))) -> UserListResponse:
    """获取用户列表（需settings权限）"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("SELECT id, username, is_active, created_at FROM users ORDER BY id")
    rows = cursor.fetchall()

    users = []
    for row in rows:
        cursor.execute(
            "SELECT permission FROM user_permissions WHERE user_id = ?",
            (row["id"],)
        )
        perms = [r["permission"] for r in cursor.fetchall()]
        users.append(UserResponse(
            id=row["id"],
            username=row["username"],
            isActive=bool(row["is_active"]),
            permissions=perms,
            createdAt=row["created_at"]
        ))

    return UserListResponse(data=users)


@router.post("", response_model=BaseResponse)
def create_user(
    req: CreateUserRequest,
    user: dict = Depends(check_permission("settings"))
) -> BaseResponse:
    """创建用户（需settings权限）"""
    # 校验权限代码
    for perm in req.permissions:
        if perm not in VALID_PERMISSIONS:
            raise HTTPException(status_code=400, detail=f"无效的权限代码: {perm}")

    db = get_db()
    cursor = db.cursor()

    # 检查用户名是否已存在
    cursor.execute("SELECT id FROM users WHERE username = ?", (req.username,))
    if cursor.fetchone():
        raise HTTPException(status_code=400, detail="用户名已存在")

    now = format_beijing(beijing_now())
    password_hash = _hash_password(req.password)

    cursor.execute(
        "INSERT INTO users (username, password_hash, is_active, created_at) VALUES (?, ?, 1, ?)",
        (req.username, password_hash, now)
    )
    new_id = cursor.lastrowid

    for perm in req.permissions:
        cursor.execute(
            "INSERT INTO user_permissions (user_id, permission) VALUES (?, ?)",
            (new_id, perm)
        )

    db.commit()
    logger.info(f"用户创建成功: {req.username}, 权限: {req.permissions}, 操作者: {user['username']}")

    return BaseResponse(message="用户创建成功")


@router.put("/{user_id}", response_model=BaseResponse)
def update_user(
    user_id: int,
    req: UpdateUserRequest,
    user: dict = Depends(check_permission("settings"))
) -> BaseResponse:
    """更新用户（需settings权限）"""
    db = get_db()
    cursor = db.cursor()

    # 检查用户是否存在
    cursor.execute("SELECT id, username FROM users WHERE id = ?", (user_id,))
    row = cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="用户不存在")

    target_username = row["username"]

    # 更新密码
    if req.password is not None:
        password_hash = _hash_password(req.password)
        cursor.execute(
            "UPDATE users SET password_hash = ? WHERE id = ?",
            (password_hash, user_id)
        )

    # 更新启用状态
    if req.isActive is not None:
        cursor.execute(
            "UPDATE users SET is_active = ? WHERE id = ?",
            (1 if req.isActive else 0, user_id)
        )
        # 禁用用户时清理其token，使其立即失效
        if not req.isActive:
            cursor.execute("DELETE FROM user_tokens WHERE user_id = ?", (user_id,))

    # 更新权限
    if req.permissions is not None:
        # 校验权限代码
        for perm in req.permissions:
            if perm not in VALID_PERMISSIONS:
                raise HTTPException(status_code=400, detail=f"无效的权限代码: {perm}")

        cursor.execute("DELETE FROM user_permissions WHERE user_id = ?", (user_id,))
        for perm in req.permissions:
            cursor.execute(
                "INSERT INTO user_permissions (user_id, permission) VALUES (?, ?)",
                (user_id, perm)
            )

    db.commit()
    logger.info(f"用户更新成功: {target_username}, 操作者: {user['username']}")

    return BaseResponse(message="用户更新成功")


@router.delete("/{user_id}", response_model=BaseResponse)
def delete_user(
    user_id: int,
    user: dict = Depends(check_permission("settings"))
) -> BaseResponse:
    """删除用户（需settings权限）"""
    db = get_db()
    cursor = db.cursor()

    # 禁止删除自己
    if user_id == user["user_id"]:
        raise HTTPException(status_code=400, detail="不能删除当前登录用户")

    # 检查用户是否存在
    cursor.execute("SELECT username FROM users WHERE id = ?", (user_id,))
    row = cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="用户不存在")

    target_username = row["username"]

    # 删除用户（级联删除权限和token）
    cursor.execute("DELETE FROM user_tokens WHERE user_id = ?", (user_id,))
    cursor.execute("DELETE FROM user_permissions WHERE user_id = ?", (user_id,))
    cursor.execute("DELETE FROM users WHERE id = ?", (user_id,))

    db.commit()
    logger.info(f"用户删除成功: {target_username}, 操作者: {user['username']}")

    return BaseResponse(message="用户删除成功")


@router.post("/logout", response_model=BaseResponse)
def logout(user: dict = Depends(get_current_user)) -> BaseResponse:
    """退出登录"""
    db = get_db()
    cursor = db.cursor()

    cursor.execute("DELETE FROM user_tokens WHERE user_id = ?", (user["user_id"],))
    db.commit()

    logger.info(f"用户退出登录: {user['username']}")
    return BaseResponse(message="退出登录成功")


def _hash_password(password: str) -> str:
    """密码哈希"""
    try:
        import bcrypt
        return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
    except ImportError:
        import hashlib
        return hashlib.sha256(password.encode("utf-8")).hexdigest()


def _verify_password(password: str, stored_hash: str) -> bool:
    """校验密码"""
    try:
        import bcrypt
        return bcrypt.checkpw(password.encode("utf-8"), stored_hash.encode("utf-8"))
    except ImportError:
        import hashlib
        return hashlib.sha256(password.encode("utf-8")).hexdigest() == stored_hash


def _record_login_fail(username: str) -> None:
    """记录登录失败次数，超过阈值则锁定"""
    import time

    with _login_lock:
        count = _LOGIN_FAIL_COUNTS.get(username, 0) + 1
        _LOGIN_FAIL_COUNTS[username] = count
        if count >= _MAX_LOGIN_FAILS:
            _LOGIN_LOCK_UNTIL[username] = time.time() + _LOGIN_LOCK_SECONDS
            logger.warning(f"用户 {username} 登录失败{count}次，锁定{_LOGIN_LOCK_SECONDS}秒")
